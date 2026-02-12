package com.healthfood.health_sync_engine.controller;

import com.healthfood.health_sync_engine.consumer.HealthSyncConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health-sync")
public class HealthSyncController {
    private final HealthSyncConsumer healthSyncConsumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HealthSyncController(HealthSyncConsumer healthSyncConsumer) {
        this.healthSyncConsumer = healthSyncConsumer;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "health-sync-engine");
    }

    @PostMapping("/trigger")
    public Map<String, String> triggerSync(@RequestBody Map<String, Object> payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            healthSyncConsumer.consume(message);
            return Map.of("status", "success", "message", "Sync triggered manually");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
