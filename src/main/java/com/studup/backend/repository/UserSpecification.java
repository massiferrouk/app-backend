package com.studup.backend.repository;

import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtres dynamiques de la liste d'administration des comptes (APP-121).
 *
 * Pourquoi une Specification plutôt qu'une @Query avec des paramètres
 * optionnels : la forme « WHERE (:role IS NULL OR u.role = :role) » échoue
 * contre PostgreSQL, qui ne sait pas typer un paramètre confronté à une
 * colonne d'ENUM natif — la requête partait en 500 quel que soit le filtre.
 *
 * Ici, un filtre absent n'ajoute simplement aucune clause : plus aucun
 * paramètre non typé n'est envoyé.
 */
public class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> roleEgale(UserRole role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** Actif = le compte n'est ni suspendu ni banni. */
    public static Specification<User> estActif(Boolean actif) {
        return (root, query, cb) -> cb.equal(root.get("isActive"), actif);
    }
}
