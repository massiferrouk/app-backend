package com.studup.backend.repository;

import com.studup.backend.model.entity.MatchNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchNotificationRepository extends JpaRepository<MatchNotification, UUID> {

    // La paire est toujours stockée avec userAId < userBId (ordre canonique)
    boolean existsByUserAIdAndUserBId(UUID userAId, UUID userBId);
}
