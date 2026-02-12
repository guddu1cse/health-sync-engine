package com.healthfood.health_sync_engine.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_health_connections")
@Data
public class UserHealthConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private HealthProvider provider;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ConnectionStatus status;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private HealthSyncStatus syncStatus = HealthSyncStatus.IDLE;

    @Column(name = "last_sync_duration")
    private Integer lastSyncDuration;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sync_retry_count")
    private Integer syncRetryCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum HealthProvider {
        GOOGLE_FIT, APPLE_HEALTH, SAMSUNG_HEALTH, FITBIT
    }

    public enum ConnectionStatus {
        CONNECTED, DISCONNECTED, ERROR
    }

    public enum HealthSyncStatus {
        IDLE, SYNCING, SUCCESS, FAILED
    }
}
