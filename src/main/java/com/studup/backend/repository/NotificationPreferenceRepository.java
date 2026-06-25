package com.studup.backend.repository;

import com.studup.backend.model.entity.NotificationPreference;
import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndChannel(
            UUID userId, NotificationType type, NotificationChannel channel);
}
