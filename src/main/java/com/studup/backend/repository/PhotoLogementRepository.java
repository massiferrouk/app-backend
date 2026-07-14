package com.studup.backend.repository;

import com.studup.backend.model.entity.PhotoLogement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoLogementRepository extends JpaRepository<PhotoLogement, UUID> {

    List<PhotoLogement> findByLogementIdOrderByOrdreAsc(UUID logementId);

    int countByLogementId(UUID logementId);

    void deleteByLogementId(UUID logementId);

    // Projection des clés MinIO seules (ordre d'affichage) — évite de charger
    // des entités managées avant une suppression (conflit persistence/cascade),
    // et sert à récupérer la photo de couverture (première) pour les listes.
    @Query("SELECT p.fileKey FROM PhotoLogement p WHERE p.logement.id = :logementId ORDER BY p.ordre ASC")
    List<String> findFileKeysByLogementId(@Param("logementId") UUID logementId);
}
