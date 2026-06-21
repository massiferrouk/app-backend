package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.security.JwtBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final JwtBlacklistService jwtBlacklistService;

    public AdminService(UserRepository userRepository,
                        JwtBlacklistService jwtBlacklistService) {
        this.userRepository = userRepository;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UserRole role, Boolean isActive, Pageable pageable) {
        return userRepository.findAllFiltered(role, isActive, pageable)
                .map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse suspendUser(UUID userId, String adminEmail) {
        User user = findUserOrThrow(userId);
        checkNotAdmin(user);

        user.setIsActive(false);
        // Révocation immédiate de tous les tokens actifs de l'utilisateur suspendu
        jwtBlacklistService.revokeAllForUser(userId);

        log.info("Utilisateur {} suspendu par {}", userId, adminEmail);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse banUser(UUID userId, String adminEmail) {
        User user = findUserOrThrow(userId);
        checkNotAdmin(user);

        user.setIsActive(false);
        user.setDeletedAt(java.time.OffsetDateTime.now());
        jwtBlacklistService.revokeAllForUser(userId);

        log.info("Utilisateur {} banni par {}", userId, adminEmail);
        return AdminUserResponse.from(userRepository.save(user));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
    }

    // Un admin ne peut pas suspendre ou bannir un autre admin
    private void checkNotAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new UnauthorizedException("Impossible de suspendre ou bannir un administrateur");
        }
    }
}
