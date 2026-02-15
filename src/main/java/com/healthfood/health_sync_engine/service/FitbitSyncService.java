package com.healthfood.health_sync_engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthfood.health_sync_engine.model.HealthMetricDaily;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FitbitSyncService {
    private static final Logger logger = LoggerFactory.getLogger(FitbitSyncService.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String FITBIT_API_URL = "https://api.fitbit.com/1";

    public FitbitSyncService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<HealthMetricDaily> fetchActivity(String accessToken, LocalDateTime start, LocalDateTime end, String userId) {
        List<HealthMetricDaily> metrics = new ArrayList<>();
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        // Fitbit API requires day-by-day fetching for detailed summary if we want consistent structure
        // Iterating through dates
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            try {
                HealthMetricDaily metric = fetchDailySummary(accessToken, date, userId);
                if (metric != null) {
                    metrics.add(metric);
                }
            } catch (Exception e) {
                logger.error("Failed to fetch Fitbit data for user {} on date {}: {}", userId, date, e.getMessage());
            }
        }
        return metrics;
    }

    private HealthMetricDaily fetchDailySummary(String accessToken, LocalDate date, String userId) throws IOException, InterruptedException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = String.format("%s/user/-/activities/date/%s.json", FITBIT_API_URL, dateStr);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode summary = root.path("summary");

            if (!summary.isMissingNode()) {
                HealthMetricDaily metric = new HealthMetricDaily();
                metric.setUserId(userId);
                metric.setDate(date.atStartOfDay());
                metric.setSourceProvider(UserHealthConnection.HealthProvider.FITBIT);

                // Steps
                if (summary.has("steps")) {
                    metric.setSteps(String.valueOf(summary.path("steps").asInt()));
                }

                // Calories
                if (summary.has("caloriesOut")) {
                    metric.setCalories(String.valueOf(summary.path("caloriesOut").asInt()));
                }

                // Distance
                if (summary.has("distances")) {
                    JsonNode distances = summary.path("distances");
                    if (distances.isArray()) {
                        for (JsonNode d : distances) {
                            if (d.path("activity").asText().equals("total")) {
                                metric.setDistance(String.valueOf(d.path("distance").asDouble()));
                                break;
                            }
                        }
                    }
                }

                // Active Minutes
                int veryActive = summary.path("veryActiveMinutes").asInt(0);
                int fairlyActive = summary.path("fairlyActiveMinutes").asInt(0);
                metric.setActiveMinutes(String.valueOf(veryActive + fairlyActive));

                // Heart Rate (resting)
                if (summary.has("restingHeartRate")) {
                    metric.setHeartRate(String.valueOf(summary.path("restingHeartRate").asInt()));
                }

                return metric;
            }
        } else {
            logger.warn("Fitbit API returned status {} for user {} on date {}", response.statusCode(), userId, date);
            if (response.statusCode() == 401) {
                throw new RuntimeException("AUTH_REVOKED");
            }
        }
        return null;
    }
}
