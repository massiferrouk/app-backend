package com.studup.backend.repository;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogementRepository extends JpaRepository<Logement, UUID> {

    List<Logement> findByOwnerIdAndStatut(UUID ownerId, LogementStatut statut);

    boolean existsByOwnerIdAndVille(UUID ownerId, String ville);
}
