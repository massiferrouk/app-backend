package com.studup.backend.repository;

import com.studup.backend.model.entity.LogementReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LogementReportRepository extends JpaRepository<LogementReport, UUID> {

    boolean existsByLogementIdAndReporterId(UUID logementId, UUID reporterId);

    /**
     * File de modération : signalements portant sur des annonces encore en
     * ligne. Une annonce déjà suspendue a été traitée — la garder dans la file
     * ferait revenir indéfiniment un dossier clos.
     */
    @Query("""
            SELECT lr FROM LogementReport lr WHERE lr.logementId IN
            (SELECT l.id FROM Logement l WHERE l.statut <> 'SUSPENDU')
            ORDER BY lr.createdAt DESC
            """)
    Page<LogementReport> findPendingReports(Pageable pageable);

    /** Même critère, pour le tableau de bord. */
    @Query("""
            SELECT COUNT(lr) FROM LogementReport lr WHERE lr.logementId IN
            (SELECT l.id FROM Logement l WHERE l.statut <> 'SUSPENDU')
            """)
    long countPendingReports();
}
