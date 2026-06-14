package com.studup.backend.repository;

import com.studup.backend.model.entity.ProprietaireProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProprietaireProfileRepository extends JpaRepository<ProprietaireProfile, UUID> {

    Optional<ProprietaireProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
