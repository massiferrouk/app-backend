package com.studup.backend.security;

import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service centralisé pour les vérifications d'ownership.
 * Utilisé via @PreAuthorize dans les controllers.
 * Un ADMIN bypass toujours les checks d'ownership.
 */
@Service("securityService")
public class SecurityService {

    private final UserRepository userRepository;
    private final LogementRepository logementRepository;

    public SecurityService(UserRepository userRepository,
                           LogementRepository logementRepository) {
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
    }

    /**
     * Vérifie que l'utilisateur connecté est le propriétaire du logement.
     * Retourne true si : propriétaire du logement OU rôle ADMIN.
     * Retourne false si : logement inexistant ou appartient à quelqu'un d'autre.
     */
    public boolean isLogementOwner(UUID logementId, Authentication authentication) {
        if (isAdmin(authentication)) return true;

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .flatMap(user -> logementRepository.findById(logementId)
                        .map(logement -> logement.getOwner().getId().equals(user.getId())))
                .orElse(false);
    }

    /**
     * Vérifie que l'utilisateur connecté agit sur son propre profil.
     * Retourne true si : même userId OU rôle ADMIN.
     */
    public boolean isSelf(UUID userId, Authentication authentication) {
        if (isAdmin(authentication)) return true;

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .map(user -> user.getId().equals(userId))
                .orElse(false);
    }

    // Vérifie si l'utilisateur connecté a le rôle ADMIN
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + UserRole.ADMIN.name()));
    }
}
