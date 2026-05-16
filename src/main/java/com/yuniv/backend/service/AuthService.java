package com.yuniv.backend.service;

import com.yuniv.backend.exception.DuplicateEmailException;
import com.yuniv.backend.model.dto.request.RegisterRequest;
import com.yuniv.backend.model.dto.response.UserResponse;
import com.yuniv.backend.model.entity.User;
import com.yuniv.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailConfirmationService emailConfirmationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailConfirmationService emailConfirmationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailConfirmationService = emailConfirmationService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Vérifie l'unicité de l'email avant toute création
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Un compte existe déjà avec cet email");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(request.role())
                .isVerified(false)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        emailConfirmationService.sendConfirmationEmail(savedUser);

        // On ne logue jamais l'email — uniquement l'userId (règle PII)
        log.info("Nouvel utilisateur enregistré : userId={}", savedUser.getId());

        return UserResponse.from(savedUser);
    }
}
