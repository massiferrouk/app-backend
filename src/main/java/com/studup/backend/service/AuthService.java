package com.studup.backend.service;

import com.studup.backend.exception.DuplicateEmailException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RefreshRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.dto.response.AuthResponse;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.model.entity.RefreshToken;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.RefreshTokenRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailConfirmationService emailConfirmationService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailConfirmationService emailConfirmationService,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailConfirmationService = emailConfirmationService;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    // ─── Inscription ──────────────────────────────────────────────────────────

    @Transactional
    public UserResponse register(RegisterRequest request) {
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

        log.info("New user registered: userId={}", savedUser.getId());
        return UserResponse.from(savedUser);
    }

    // ─── Connexion ────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Délègue la vérification email/password à Spring Security
        // Lance BadCredentialsException si mot de passe incorrect → 401
        // Lance LockedException si compte désactivé → 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Si on arrive ici, les credentials sont valides
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String rawRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        // On stocke le hash SHA-256 du refresh token — jamais le token brut
        saveRefreshToken(user.getId(), rawRefreshToken);

        log.info("User logged in: userId={}", user.getId());
        return new AuthResponse(accessToken, rawRefreshToken);
    }

    // ─── Renouvellement des tokens ────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String rawToken = request.refreshToken();

        // Valide la signature et l'expiration du JWT
        if (!jwtUtil.isTokenValid(rawToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Vérifie que le token est bien en base et non révoqué
        String tokenHash = hashToken(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        if (storedToken.getIsRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Rotation : on révoque l'ancien token et on en génère un nouveau
        storedToken.setIsRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRawRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), newRawRefreshToken);

        log.info("Tokens refreshed: userId={}", user.getId());
        return new AuthResponse(newAccessToken, newRawRefreshToken);
    }

    // ─── Déconnexion ──────────────────────────────────────────────────────────

    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
        });

        // On ne log pas d'erreur si le token n'existe pas — le logout doit toujours réussir
        log.info("User logged out");
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    private void saveRefreshToken(java.util.UUID userId, String rawToken) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(rawToken))
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Hash SHA-256 d'un token.
     * On ne stocke jamais le token brut en base — seulement son hash.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
