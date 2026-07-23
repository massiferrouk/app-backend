package com.studup.backend.repository;

import com.studup.backend.model.entity.MotInterdit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MotInterditRepository extends JpaRepository<MotInterdit, UUID> {

    List<MotInterdit> findAll();

    /** Liste ordonnée pour l'écran d'administration (APP-121). */
    List<MotInterdit> findAllByOrderByMotAsc();

    /**
     * La contrainte UNIQUE de PostgreSQL est sensible à la casse : « Con » et
     * « con » y passeraient tous les deux, alors que le filtrage compare en
     * minuscules et les traiterait comme un seul et même mot. On vérifie donc
     * l'existence sans tenir compte de la casse.
     */
    boolean existsByMotIgnoreCase(String mot);
}
