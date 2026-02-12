package com.healthfood.health_sync_engine.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private UserHealthConnection.HealthProvider sourceProvider;

    private String steps = "0";
    private String calories = "0";
    private String distance = "0";

    @Column(name = "active_minutes")
    private String activeMinutes = "0";

    @Column(name = "heart_rate")
    private String heartRate;

    @Column(name = "blood_oxygen")
    private String bloodOxygen;

    @Column(name = "sleep_hours")
    private String sleepHours;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
