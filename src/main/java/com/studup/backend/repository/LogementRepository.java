package com.studup.backend.repository;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.VilleAssociee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogementRepository extends JpaRepository<Logement, UUID>, JpaSpecificationExecutor<Logement> {

    List<Logement> findByOwnerId(UUID ownerId);
}
