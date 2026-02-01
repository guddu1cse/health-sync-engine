package com.healthfood.health_sync_engine.service;

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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Service
public class GoogleFitSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleFitSyncService.class);

    public List<HealthMetricDaily> fetchActivity(String accessToken, LocalDateTime start, LocalDateTime end, String userId) {
        try {
            Fitness fitness = new Fitness.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("HealthAndFood")
                    .build();

            long startTimeMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            AggregateRequest request = new AggregateRequest()
                    .setAggregateBy(List.of(
                            new AggregateBy().setDataTypeName("com.google.step_count.delta"),
                            new AggregateBy().setDataTypeName("com.google.calories.expended"),
                            new AggregateBy().setDataTypeName("com.google.distance.delta"),
                            new AggregateBy().setDataTypeName("com.google.active_minutes")
                    ))
                    .setStartTimeMillis(startTimeMillis)
                    .setEndTimeMillis(endTimeMillis)
                    .setBucketByTime(new BucketByTime().setDurationMillis(86400000L)); // 1 day

            AggregateResponse response = fitness.users().dataset().aggregate("me", request).execute();
            List<HealthMetricDaily> metrics = new ArrayList<>();

            if (response.getBucket() != null) {
                for (AggregateBucket bucket : response.getBucket()) {
                    HealthMetricDaily metric = new HealthMetricDaily();
                    metric.setUserId(userId);
                    metric.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(bucket.getStartTimeMillis()), ZoneId.systemDefault()));
                    metric.setSourceProvider(UserHealthConnection.HealthProvider.GOOGLE_FIT);

                    if (bucket.getDataset() != null) {
                        for (Dataset dataset : bucket.getDataset()) {
                            if (dataset.getPoint() != null) {
                                for (DataPoint point : dataset.getPoint()) {
                                    String type = point.getDataTypeName();
                                    if (type.contains("step_count")) {
                                        metric.setSteps(metric.getSteps() + point.getValue().get(0).getIntVal());
                                    } else if (type.contains("calories")) {
                                        metric.setCalories(metric.getCalories() + point.getValue().get(0).getFpVal());
                                    } else if (type.contains("distance")) {
                                        metric.setDistance(metric.getDistance() + point.getValue().get(0).getFpVal());
                                    } else if (type.contains("active_minutes")) {
                                        metric.setActiveMinutes(metric.getActiveMinutes() + point.getValue().get(0).getIntVal());
                                    }
                                }
                            }
                        }
                    }
                    metrics.add(metric);
                }
            }
            return metrics;
        } catch (Exception e) {
            logger.error("Error fetching Google Fit data: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
