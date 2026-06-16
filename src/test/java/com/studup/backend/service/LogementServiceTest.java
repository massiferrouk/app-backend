package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.PhotoLogement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.PhotoLogementRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogementServiceTest {

    @Mock private LogementRepository logementRepository;
    @Mock private PhotoLogementRepository photoRepository;
    @Mock private UserRepository userRepository;
    @Mock private MinioService minioService;
    @Mock private GeocodingService geocodingService;

    @InjectMocks
    private LogementService logementService;

    private User fakeOwner;
    private Logement fakeLogement;

    @BeforeEach
    void setUp() {
        fakeOwner = User.builder()
                .id(UUID.randomUUID())
                .email("pierre@studup.fr")
                .role(UserRole.PROPRIETAIRE)
                .firstName("Pierre")
                .lastName("Dupont")
                .isVerified(false)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        fakeLogement = Logement.builder()
                .id(UUID.randomUUID())
                .owner(fakeOwner)
                .adresse("12 rue de la Paix")
                .ville("Paris")
                .codePostal("75001")
                .type(LogementType.STUDIO)
                .surface(new BigDecimal("25.00"))
                .nbPieces(1)
                .loyer(new BigDecimal("800.00"))
                .charges(new BigDecimal("50.00"))
                .statut(LogementStatut.BROUILLON)
                .isVerified(false)
                .isMeuble(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── Création ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateLogementAsBrouillon() {
        CreateLogementRequest request = new CreateLogementRequest(
                "12 rue de la Paix", "Paris", "75001",
                LogementType.STUDIO, new BigDecimal("25.00"), 1,
                new BigDecimal("800.00"), new BigDecimal("50.00"),
                "Beau studio", null, true
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(geocodingService.geocode(anyString(), anyString(), anyString()))
                .thenReturn(new GeocodingService.Coordinates(
                        new BigDecimal("48.8698"), new BigDecimal("2.3310")));
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> {
            Logement l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            l.setCreatedAt(OffsetDateTime.now());
            l.setUpdatedAt(OffsetDateTime.now());
            if (l.getStatut() == null) l.setStatut(LogementStatut.BROUILLON);
            if (l.getIsVerified() == null) l.setIsVerified(false);
            return l;
        });

        LogementResponse response = logementService.createLogement("pierre@studup.fr", request);

        assertThat(response.statut()).isEqualTo(LogementStatut.BROUILLON);
        assertThat(response.ville()).isEqualTo("Paris");
        assertThat(response.lat()).isNotNull();
    }

    @Test
    void shouldCreateLogementEvenIfGeocodingFails() {
        CreateLogementRequest request = new CreateLogementRequest(
                "Adresse inconnue", "Ville", "99999",
                LogementType.T1, null, 1, null, null, null, null, true
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(geocodingService.geocode(anyString(), anyString(), anyString())).thenReturn(null);
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> {
            Logement l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            l.setCreatedAt(OffsetDateTime.now());
            l.setUpdatedAt(OffsetDateTime.now());
            if (l.getStatut() == null) l.setStatut(LogementStatut.BROUILLON);
            if (l.getIsVerified() == null) l.setIsVerified(false);
            return l;
        });

        LogementResponse response = logementService.createLogement("pierre@studup.fr", request);

        // La création réussit même sans coordonnées
        assertThat(response.lat()).isNull();
        assertThat(response.statut()).isEqualTo(LogementStatut.BROUILLON);
    }

    // ─── Publication ──────────────────────────────────────────────────────────

    @Test
    void shouldPublishLogement() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepository.findByLogementIdOrderByOrdreAsc(any())).thenReturn(List.of());

        LogementResponse response = logementService.publishLogement("pierre@studup.fr", fakeLogement.getId());

        assertThat(response.statut()).isEqualTo(LogementStatut.ACTIF);
    }

    @Test
    void shouldRejectPublishWhenNotOwner() {
        User autreUser = User.builder()
                .id(UUID.randomUUID())
                .email("autre@studup.fr")
                .build();

        when(userRepository.findByEmail("autre@studup.fr")).thenReturn(Optional.of(autreUser));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));

        assertThatThrownBy(() -> logementService.publishLogement("autre@studup.fr", fakeLogement.getId()))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── Upload photos ────────────────────────────────────────────────────────

    @Test
    void shouldRejectOversizedPhoto() {
        // Fichier de 3 Mo — dépasse la limite de 2 Mo
        byte[] bigContent = new byte[3 * 1024 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", bigContent
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(0);

        assertThatThrownBy(() -> logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(bigFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 Mo");
    }

    @Test
    void shouldRejectInvalidMimeType() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", new byte[100]
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(0);

        assertThatThrownBy(() -> logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(pdfFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format non supporté");
    }

    @Test
    void shouldRejectMoreThanTenPhotos() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        // 9 photos déjà en base + 2 nouvelles = 11 > max 10
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(9);

        assertThatThrownBy(() -> logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(file, file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    void shouldThrowWhenLogementNotFound() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logementService.publishLogement("pierre@studup.fr", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
