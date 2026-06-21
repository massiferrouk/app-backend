package com.studup.backend.repository;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Filtres dynamiques pour la recherche de logements.
 * Chaque méthode retourne une Specification qu'on compose avec and().
 */
public class LogementSpecification {

    private LogementSpecification() {}

    // Toujours appliqué : on ne montre que les logements publiés
    public static Specification<Logement> estActif() {
        return (root, query, cb) -> cb.equal(root.get("statut"), LogementStatut.ACTIF);
    }

    public static Specification<Logement> villeEgale(String ville) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("ville")), ville.toLowerCase());
    }

    public static Specification<Logement> loyerMaxInferieurOuEgal(BigDecimal loyerMax) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("loyer"), loyerMax);
    }

    public static Specification<Logement> surfaceMinSuperieurOuEgal(BigDecimal surfaceMin) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("surface"), surfaceMin);
    }

    public static Specification<Logement> estMeuble(Boolean meuble) {
        return (root, query, cb) -> cb.equal(root.get("isMeuble"), meuble);
    }

    public static Specification<Logement> typeEgal(LogementType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
}
