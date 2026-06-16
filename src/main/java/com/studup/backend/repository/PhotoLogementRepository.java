package com.studup.backend.repository;

import com.studup.backend.model.entity.PhotoLogement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoLogementRepository extends JpaRepository<PhotoLogement, UUID> {

    List<PhotoLogement> findByLogementIdOrderByOrdreAsc(UUID logementId);

    int countByLogementId(UUID logementId);

    void deleteByLogementId(UUID logementId);
}
