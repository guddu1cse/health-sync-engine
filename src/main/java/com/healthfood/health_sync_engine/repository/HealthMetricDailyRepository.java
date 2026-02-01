package com.healthfood.health_sync_engine.repository;

import com.healthfood.health_sync_engine.model.HealthMetricDaily;
import com.healthfood.health_sync_engine.model.UserHealthConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface HealthMetricDailyRepository extends JpaRepository<HealthMetricDaily, String> {
    Optional<HealthMetricDaily> findByUserIdAndDateAndSourceProvider(
            String userId, LocalDateTime date, UserHealthConnection.HealthProvider provider);
}
