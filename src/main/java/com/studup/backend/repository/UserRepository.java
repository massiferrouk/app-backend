package com.studup.backend.repository;

import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Tous les utilisateurs actifs non supprimés — pour le digest hebdomadaire
    List<User> findByIsActiveTrueAndDeletedAtIsNull();

    // Filtre optionnel par rôle et statut actif — null = pas de filtre
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
            AND (:isActive IS NULL OR u.isActive = :isActive)
            ORDER BY u.createdAt DESC
            """)
    Page<User> findAllFiltered(
            @Param("role") UserRole role,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
    // ─── Comptages du tableau de bord admin (APP-121) ────────────────────────

    /**
     * Répartition des comptes par rôle, en UNE requête.
     * Un COUNT par rôle en ferait quatre pour la même information.
     */
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countGroupByRole();

    /** Suspendu = désactivé mais pas supprimé. Le bannissement pose deletedAt. */
    long countByIsActiveFalseAndDeletedAtIsNull();

    long countByDeletedAtIsNotNull();

    long countByCreatedAtAfter(OffsetDateTime depuis);
}
