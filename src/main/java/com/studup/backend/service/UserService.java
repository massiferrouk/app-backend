package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserService {

    // Les deux « modes étudiant » interchangeables : une même personne peut passer
    // de l'un à l'autre (sa situation évolue : L3 étudiant → alternant l'an d'après).
    // PROPRIETAIRE (un bailleur = acteur distinct) et ADMIN (rôle système) n'en
    // font PAS partie : ils ne sont ni une cible ni une source valide.
    private static final Set<UserRole> MODES_ETUDIANT =
            Set.of(UserRole.ETUDIANT, UserRole.ALTERNANT);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Change le mode du compte connecté (APP-117).
     *
     * Autorisé UNIQUEMENT entre ETUDIANT et ALTERNANT. On refuse tout ce qui
     * touche PROPRIETAIRE (bailleur = autre type de compte, pas une évolution
     * d'un étudiant) et ADMIN (sinon un utilisateur s'auto-promouvrait admin —
     * faille d'élévation de privilèges).
     */
    @Transactional
    public UserResponse changeMode(String email, UserRole newRole) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (!MODES_ETUDIANT.contains(user.getRole()) || !MODES_ETUDIANT.contains(newRole)) {
            throw new IllegalArgumentException(
                    "Le changement de mode n'est possible qu'entre étudiant et alternant.");
        }

        user.setRole(newRole);
        user = userRepository.save(user);
        return UserResponse.from(user);
    }
}
