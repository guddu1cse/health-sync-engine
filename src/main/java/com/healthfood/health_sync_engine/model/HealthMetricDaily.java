package com.healthfood.health_sync_engine.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_metrics_daily")
@Data
public class HealthMetricDaily {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_provider", nullable = false)
    private UserHealthConnection.HealthProvider sourceProvider;

    private Integer steps = 0;
    private Double calories = 0.0;
    private Double distance = 0.0;

    @Column(name = "active_minutes")
    private Integer activeMinutes = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
