package com.healthfood.health_sync_engine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthfood.health_sync_engine.model.HealthMetricDaily;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import com.healthfood.health_sync_engine.repository.HealthMetricDailyRepository;
import com.healthfood.health_sync_engine.repository.UserHealthConnectionRepository;
import com.healthfood.health_sync_engine.service.GoogleFitSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class HealthSyncConsumer {
    private static final Logger logger = LoggerFactory.getLogger(HealthSyncConsumer.class);
    private final UserHealthConnectionRepository connectionRepository;
    private final HealthMetricDailyRepository metricRepository;
    private final GoogleFitSyncService googleFitSyncService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HealthSyncConsumer(UserHealthConnectionRepository connectionRepository,
                              HealthMetricDailyRepository metricRepository,
                              GoogleFitSyncService googleFitSyncService,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.connectionRepository = connectionRepository;
        this.metricRepository = metricRepository;
        this.googleFitSyncService = googleFitSyncService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "${app.health.sync.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        logger.info("Received sync request: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String userId = (String) payload.get("userId");
            String providerName = (String) payload.get("provider");
            Boolean isInitialSync = (Boolean) payload.get("isInitialSync");

            UserHealthConnection.HealthProvider provider = UserHealthConnection.HealthProvider.valueOf(providerName);
            Optional<UserHealthConnection> connectionOpt = connectionRepository.findByUserIdAndProvider(userId, provider);

            if (connectionOpt.isEmpty() || connectionOpt.get().getStatus() != UserHealthConnection.ConnectionStatus.CONNECTED) {
                logger.warn("No active connection found for user {} and provider {}", userId, provider);
                return;
            }

            UserHealthConnection connection = connectionOpt.get();
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = isInitialSync ? end.minusDays(30) : end.minusDays(1);

            if (provider == UserHealthConnection.HealthProvider.GOOGLE_FIT) {
                List<HealthMetricDaily> metrics = googleFitSyncService.fetchActivity(connection.getAccessToken(), start, end, userId);
                for (HealthMetricDaily metric : metrics) {
                    Optional<HealthMetricDaily> existing = metricRepository.findByUserIdAndDateAndSourceProvider(
                            userId, metric.getDate(), provider);
                    
                    if (existing.isPresent()) {
                        HealthMetricDaily m = existing.get();
                        m.setSteps(metric.getSteps());
                        m.setCalories(metric.getCalories());
                        m.setDistance(metric.getDistance());
                        m.setActiveMinutes(metric.getActiveMinutes());
                        m.setUpdatedAt(LocalDateTime.now());
                        metricRepository.save(m);
                    } else {
                        metricRepository.save(metric);
                    }
                }
                
                connection.setLastSyncedAt(LocalDateTime.now());
                connectionRepository.save(connection);

                // Notify data ingested
                kafkaTemplate.send("${app.health.ingested.topic}", objectMapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "date", LocalDateTime.now().toString()
                )));
                
                logger.info("Sync completed for user {} and provider {}", userId, provider);
            }

        } catch (Exception e) {
            logger.error("Error processing health sync: {}", e.getMessage());
        }
    }
}
