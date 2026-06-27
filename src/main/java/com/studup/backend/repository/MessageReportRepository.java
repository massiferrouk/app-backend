package com.studup.backend.repository;

import com.studup.backend.model.entity.MessageReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {

    boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

    // Retourne les messages distincts qui ont au moins un signalement et ne sont pas encore masqués
    @Query("SELECT mr FROM MessageReport mr WHERE mr.messageId IN " +
           "(SELECT m.id FROM Message m WHERE m.isHidden = false) " +
           "ORDER BY mr.createdAt DESC")
    Page<MessageReport> findPendingReports(Pageable pageable);
}
