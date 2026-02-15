package com.healthfood.health_sync_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import com.healthfood.health_sync_engine.repository.UserHealthConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PeriodicSyncService {
    private static final Logger logger = LoggerFactory.getLogger(PeriodicSyncService.class);

    private final UserHealthConnectionRepository connectionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.health.sync.requested.topic:health.sync.requested}")
    private String syncRequestedTopic;

    /**
     * Periodic sync every hour.
     * Cron: 0 seconds, 0 minutes, all hours, all days, all months, all weekdays
     */
    @Scheduled(cron = "0 0 * * * *")
    public void triggerPeriodicSync() {
        logger.info("Starting scheduled periodic health sync for all connected users...");

        List<UserHealthConnection> activeConnections = connectionRepository.findByStatus(UserHealthConnection.ConnectionStatus.CONNECTED);
        logger.info("Found {} active connections to sync", activeConnections.size());

        for (UserHealthConnection connection : activeConnections) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "userId", connection.getUserId(),
                        "provider", connection.getProvider().name(),
                        "isInitialSync", false
                ));

                logger.debug("Emitting sync request for user {} on provider {}", connection.getUserId(), connection.getProvider());
                kafkaTemplate.send(syncRequestedTopic, payload);
            } catch (Exception e) {
                logger.error("Failed to trigger periodic sync for user {}: {}", connection.getUserId(), e.getMessage());
            }
        }
    }
}
