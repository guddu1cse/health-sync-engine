package com.healthfood.health_sync_engine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthfood.health_sync_engine.model.HealthMetricDaily;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import com.healthfood.health_sync_engine.repository.HealthMetricDailyRepository;
import com.healthfood.health_sync_engine.repository.UserHealthConnectionRepository;
import com.healthfood.health_sync_engine.service.GoogleFitSyncService;
import com.healthfood.health_sync_engine.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.health.ingested.topic}")
    private String ingestedTopic;

    public HealthSyncConsumer(UserHealthConnectionRepository connectionRepository,
                              HealthMetricDailyRepository metricRepository,
                              GoogleFitSyncService googleFitSyncService,
                              KafkaTemplate<String, String> kafkaTemplate,
                              EncryptionUtil encryptionUtil) {
        this.connectionRepository = connectionRepository;
        this.metricRepository = metricRepository;
        this.googleFitSyncService = googleFitSyncService;
        this.kafkaTemplate = kafkaTemplate;
        this.encryptionUtil = encryptionUtil;
    }

    @KafkaListener(topics = "${app.health.sync.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        logger.info("Received sync request: {}", message);
        try {
            logger.info("Processing Kafka message: {}", message);
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);
            String userId = (String) payload.get("userId");
            String providerName = (String) payload.get("provider");
            Boolean isInitialSync = (Boolean) payload.get("isInitialSync");

            logger.info("Sync Details - User: {}, Provider: {}, Initial: {}", userId, providerName, isInitialSync);

            UserHealthConnection.HealthProvider provider = UserHealthConnection.HealthProvider.valueOf(providerName);
            Optional<UserHealthConnection> connectionOpt = connectionRepository.findByUserIdAndProvider(userId, provider);

            if (connectionOpt.isEmpty() || connectionOpt.get().getStatus() != UserHealthConnection.ConnectionStatus.CONNECTED) {
                logger.warn("No active connection found for user {} and provider {}", userId, provider);
                return;
            }

            UserHealthConnection connection = connectionOpt.get();
            long startTime = System.currentTimeMillis();
            
            // Update status to SYNCING
            connection.setSyncStatus(UserHealthConnection.HealthSyncStatus.SYNCING);
            connectionRepository.save(connection);

            // Decrypt token
            String decryptedToken = encryptionUtil.decrypt(connection.getAccessToken());
            logger.debug("Decrypted Access Token for user {}: {}...", userId, decryptedToken.substring(0, Math.min(10, decryptedToken.length())));

            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = isInitialSync != null && isInitialSync ? end.minusDays(30) : end.minusDays(1);

            if (provider == UserHealthConnection.HealthProvider.GOOGLE_FIT) {
                logger.info("Calling Google Fit API for user {} from {} to {}", userId, start, end);
                List<HealthMetricDaily> metrics = googleFitSyncService.fetchActivity(decryptedToken, start, end, userId);
                logger.info("Received {} daily metric buckets from Google Fit", metrics.size());

                for (HealthMetricDaily metric : metrics) {
                    Optional<HealthMetricDaily> existing = metricRepository.findByUserIdAndDateAndSourceProvider(
                            userId, metric.getDate(), provider);
                    
                    if (existing.isPresent()) {
                        HealthMetricDaily m = existing.get();
                        logger.info("Updating existing record for user {} on {}: Steps {} -> {}", 
                            userId, metric.getDate(), m.getSteps(), metric.getSteps());
                        m.setSteps(metric.getSteps());
                        m.setCalories(metric.getCalories());
                        m.setDistance(metric.getDistance());
                        m.setActiveMinutes(metric.getActiveMinutes());
                        m.setUpdatedAt(LocalDateTime.now());
                        metricRepository.save(m);
                    } else {
                        logger.info("Creating new record for user {} on {}: Steps {}", 
                            userId, metric.getDate(), metric.getSteps());
                        metricRepository.save(metric);
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                connection.setLastSyncedAt(LocalDateTime.now());
                connection.setSyncStatus(UserHealthConnection.HealthSyncStatus.SUCCESS);
                connection.setLastSyncDuration((int) duration);
                connection.setErrorMessage(null);
                connection.setSyncRetryCount(0);
                connectionRepository.save(connection);

                // Notify data ingested
                kafkaTemplate.send(ingestedTopic, objectMapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "date", LocalDateTime.now().toString()
                )));
                
                logger.info("Sync completed for user {} and provider {}", userId, provider);
            }

        } catch (Exception e) {
            logger.error("Error processing health sync: {}", e.getMessage());
            // Update error status if possible
            try {
                Map<String, Object> payload = objectMapper.readValue(message, Map.class);
                String userId = (String) payload.get("userId");
                String providerName = (String) payload.get("provider");
                UserHealthConnection.HealthProvider provider = UserHealthConnection.HealthProvider.valueOf(providerName);
                connectionRepository.findByUserIdAndProvider(userId, provider).ifPresent(conn -> {
                    conn.setSyncStatus(UserHealthConnection.HealthSyncStatus.FAILED);
                    conn.setErrorMessage(e.getMessage());
                    conn.setSyncRetryCount(conn.getSyncRetryCount() + 1);
                    connectionRepository.save(conn);
                });
            } catch (Exception ex) {
                logger.error("Failed to update sync error state: {}", ex.getMessage());
            }
        }
    }
}
