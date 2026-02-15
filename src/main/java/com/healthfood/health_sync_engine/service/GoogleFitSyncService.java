package com.healthfood.health_sync_engine.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.fitness.Fitness;
import com.google.api.services.fitness.model.AggregateBucket;
import com.google.api.services.fitness.model.AggregateBy;
import com.google.api.services.fitness.model.AggregateRequest;
import com.google.api.services.fitness.model.AggregateResponse;
import com.google.api.services.fitness.model.BucketByTime;
import com.google.api.services.fitness.model.Dataset;
import com.google.api.services.fitness.model.DataPoint;
import com.healthfood.health_sync_engine.model.HealthMetricDaily;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Service
public class GoogleFitSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleFitSyncService.class);

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    public SyncResult fetchActivity(String accessToken, String refreshToken, LocalDateTime start, LocalDateTime end, String userId) {
        List<String> dataTypes = new ArrayList<>(List.of(
                "com.google.step_count.delta",
                "com.google.calories.expended",
                "com.google.distance.delta",
                "com.google.active_minutes",
                "com.google.heart_rate.bpm",
                "com.google.oxygen_saturation"
        ));

        return fetchWithRetry(accessToken, refreshToken, start, end, userId, dataTypes);
    }

    private SyncResult fetchWithRetry(String accessToken, String refreshToken, LocalDateTime start, LocalDateTime end, String userId, List<String> dataTypes) {
        try {
            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(GsonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setAccessToken(accessToken)
                    .setRefreshToken(refreshToken);

            Fitness fitness = new Fitness.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("HealthAndFood")
                    .build();

            long startTimeMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            List<AggregateBy> aggregateByList = new ArrayList<>();
            for (String type : dataTypes) {
                aggregateByList.add(new AggregateBy().setDataTypeName(type));
            }

            AggregateRequest request = new AggregateRequest()
                    .setAggregateBy(aggregateByList)
                    .setStartTimeMillis(startTimeMillis)
                    .setEndTimeMillis(endTimeMillis)
                    .setBucketByTime(new BucketByTime().setDurationMillis(86400000L)); // 1 day

            logger.info("Executing Google Fit aggregate request for user: {} with types: {}", userId, dataTypes);
            AggregateResponse response = fitness.users().dataset().aggregate("me", request).execute();
            
            // Check if token was refreshed
            String newAccessToken = credential.getAccessToken(); // Library updates this
            // GoogleCredential generally doesn't get a new refresh token, but we check anyway if available
            String newRefreshToken = credential.getRefreshToken(); 

            List<HealthMetricDaily> metrics = new ArrayList<>();

            if (response != null && response.getBucket() != null) {
                for (AggregateBucket bucket : response.getBucket()) {
                    HealthMetricDaily metric = new HealthMetricDaily();
                    metric.setUserId(userId);
                    // Truncate to start of day (midnight) for idempotency
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(bucket.getStartTimeMillis()), ZoneId.systemDefault());
                    metric.setDate(dateTime.toLocalDate().atStartOfDay());
                    metric.setSourceProvider(UserHealthConnection.HealthProvider.GOOGLE_FIT);

                    if (bucket.getDataset() != null) {
                        for (Dataset dataset : bucket.getDataset()) {
                            if (dataset.getPoint() != null) {
                                for (DataPoint point : dataset.getPoint()) {
                                    String type = point.getDataTypeName();
                                    if (type == null || type.isEmpty()) {
                                        type = dataset.getDataSourceId();
                                    }
                                    if (type == null) type = "";

                                    if (type.contains("step_count")) {
                                        int val = point.getValue().get(0).getIntVal();
                                        int current = Integer.parseInt(metric.getSteps() != null ? metric.getSteps() : "0");
                                        metric.setSteps(String.valueOf(current + val));
                                        logger.debug("Bucket {} steps increased by {} to {}", metric.getDate(), val, metric.getSteps());
                                    } else if (type.contains("calories")) {
                                        double val = point.getValue().get(0).getFpVal();
                                        double current = Double.parseDouble(metric.getCalories() != null ? metric.getCalories() : "0.0");
                                        metric.setCalories(String.valueOf(current + val));
                                    } else if (type.contains("distance")) {
                                        double val = point.getValue().get(0).getFpVal();
                                        double current = Double.parseDouble(metric.getDistance() != null ? metric.getDistance() : "0.0");
                                        metric.setDistance(String.valueOf(current + val));
                                    } else if (type.contains("active_minutes")) {
                                        int val = point.getValue().get(0).getIntVal();
                                        int current = Integer.parseInt(metric.getActiveMinutes() != null ? metric.getActiveMinutes() : "0");
                                        metric.setActiveMinutes(String.valueOf(current + val));
                                    } else if (type.contains("heart_rate")) {
                                        double val = point.getValue().get(0).getFpVal();
                                        metric.setHeartRate(String.valueOf(val)); 
                                    } else if (type.contains("oxygen_saturation")) {
                                        double val = point.getValue().get(0).getFpVal();
                                        metric.setBloodOxygen(String.valueOf(val));
                                    }
                                }
                            }
                        }
                    }
                    metrics.add(metric);
                    logger.info("Bucket for {}: Steps={}, Calories={}, Minutes={}, HeartRate={}, SpO2={}", 
                        metric.getDate(), metric.getSteps(), metric.getCalories(), metric.getActiveMinutes(), metric.getHeartRate(), metric.getBloodOxygen());
                }
            }
            
            return new SyncResult(metrics, newAccessToken, newRefreshToken);

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403 && dataTypes.size() > 1) {
                String message = e.getDetails().getMessage();
                logger.warn("Permission denied for some Google Fit data: {}. Retrying with fewer types.", message);
                
                List<String> nextTypes = new ArrayList<>(dataTypes);
                boolean removed = false;
                for (String type : dataTypes) {
                    if (message.contains(type)) {
                        nextTypes.remove(type);
                        removed = true;
                        break;
                    }
                }
                if (!removed) {
                    nextTypes.remove("com.google.distance.delta");
                }
                
                return fetchWithRetry(accessToken, refreshToken, start, end, userId, nextTypes);
            }
            logger.error("Google Fit API error: {} - {}", e.getStatusCode(), e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage());
            return new SyncResult(Collections.emptyList(), accessToken, refreshToken);
        } catch (Exception e) {
            logger.error("Unexpected error fetching Google Fit data: {}", e.getMessage());
            return new SyncResult(Collections.emptyList(), accessToken, refreshToken);
        }
    }
    
    public static class SyncResult {
        private final List<HealthMetricDaily> metrics;
        private final String newAccessToken;
        private final String newRefreshToken;

        public SyncResult(List<HealthMetricDaily> metrics, String newAccessToken, String newRefreshToken) {
            this.metrics = metrics;
            this.newAccessToken = newAccessToken;
            this.newRefreshToken = newRefreshToken;
        }

        public List<HealthMetricDaily> getMetrics() { return metrics; }
        public String getNewAccessToken() { return newAccessToken; }
        public String getNewRefreshToken() { return newRefreshToken; }
    }
}
