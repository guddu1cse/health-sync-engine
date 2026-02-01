package com.healthfood.health_sync_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealthSyncEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthSyncEngineApplication.class, args);
    }
}
