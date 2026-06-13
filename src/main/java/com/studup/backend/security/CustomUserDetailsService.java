package com.studup.backend.security;

import com.studup.backend.model.entity.User;
import com.studup.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implémentation de UserDetailsService pour Spring Security.
 * Charge un utilisateur depuis la base de données à partir de son email.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Spring Security appelle cette méthode lors d'une tentative de connexion.
     * Le paramètre "username" correspond à l'email dans notre application.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));

        // On convertit notre rôle métier (ex: ALTERNANT) en autorité Spring Security (ex: ROLE_ALTERNANT)
        // Spring Security exige le préfixe "ROLE_" pour les vérifications @PreAuthorize("hasRole('ALTERNANT')")
        String authority = "ROLE_" + user.getRole().name();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(authority)))
                .accountLocked(!user.getIsActive())
                .build();
    }
}
