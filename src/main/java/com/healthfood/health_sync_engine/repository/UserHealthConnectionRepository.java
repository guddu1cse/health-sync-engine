package com.healthfood.health_sync_engine.repository;

import com.healthfood.health_sync_engine.model.UserHealthConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserHealthConnectionRepository extends JpaRepository<UserHealthConnection, String> {
    Optional<UserHealthConnection> findByUserIdAndProvider(String userId, UserHealthConnection.HealthProvider provider);
    java.util.List<UserHealthConnection> findByStatus(UserHealthConnection.ConnectionStatus status);
}
