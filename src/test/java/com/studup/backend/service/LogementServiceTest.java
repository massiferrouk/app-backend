package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.AssocierVilleRequest;
import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.request.LogementSearchRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.PageResponse;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.PhotoLogement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.model.enums.VilleAssociee;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.AlternantProfileRepository;
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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    @Mock private AlternantProfileRepository alternantProfileRepository;
    @Mock private AccordRepository accordRepository;
    @Mock private MinioService minioService;
    @Mock private GeocodingService geocodingService;
    @Mock private FileValidationService fileValidationService;

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

    // ─── Détail logement ──────────────────────────────────────────────────────

    @Test
    void shouldReturnLogementDetailWithPhotoUrls() {
        PhotoLogement photo = PhotoLogement.builder()
                .id(UUID.randomUUID())
                .logement(fakeLogement)
                .fileKey("logements/photo-abc.jpg")
                .ordre(0)
                .build();

        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.findByLogementIdOrderByOrdreAsc(fakeLogement.getId())).thenReturn(List.of(photo));
        when(minioService.generatePresignedUrl("logements/photo-abc.jpg")).thenReturn("https://minio/photo-abc.jpg");

        LogementResponse response = logementService.getLogement(fakeLogement.getId());

        assertThat(response.adresse()).isEqualTo("12 rue de la Paix");
        assertThat(response.ville()).isEqualTo("Paris");
        assertThat(response.photoUrls()).hasSize(1);
        assertThat(response.photoUrls().get(0)).isEqualTo("https://minio/photo-abc.jpg");
    }

    @Test
    void shouldThrowWhenLogementDetailNotFound() {
        when(logementRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logementService.getLogement(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Logement introuvable");
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
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(0);
        // FileValidationService lève l'exception — c'est son rôle, testé dans FileValidationServiceTest
        doThrow(new IllegalArgumentException("Le fichier dépasse la taille maximale autorisée de 5 Mo"))
                .when(fileValidationService).validateImage(bigFile);

        assertThatThrownBy(() -> logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(bigFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 Mo");
    }

    @Test
    void shouldRejectInvalidMimeType() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "virus.jpg", "image/jpeg", new byte[100]
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(0);
        doThrow(new IllegalArgumentException("Format non supporté"))
                .when(fileValidationService).validateImage(exeFile);

        assertThatThrownBy(() -> logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(exeFile)))
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
    void shouldUploadWebpWithoutCompressionInsteadOfFailing() {
        // WEBP n'est pas décodable par ImageIO : la compression doit échouer
        // silencieusement et l'original être uploadé (pas de 500). Ici les octets
        // ne sont pas un vrai WEBP → Thumbnails lève → on retombe sur l'original.
        MockMultipartFile webp = new MockMultipartFile(
                "file", "photo.webp", "image/webp", new byte[]{1, 2, 3, 4, 5}
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(photoRepository.countByLogementId(fakeLogement.getId())).thenReturn(0);
        when(photoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(minioService.generatePresignedUrl(any())).thenReturn("https://minio/signed-url");

        List<String> urls = logementService.addPhotos(
                "pierre@studup.fr", fakeLogement.getId(), List.of(webp));

        assertThat(urls).containsExactly("https://minio/signed-url");
    }

    // ─── Modification ─────────────────────────────────────────────────────────

    @Test
    void shouldUpdateOwnLogement() {
        CreateLogementRequest request = new CreateLogementRequest(
                "2 avenue Neuve", "Lyon", "69002", LogementType.T2,
                new BigDecimal("40.0"), 2, new BigDecimal("750.0"),
                new BigDecimal("60.0"), "Rénové", new String[]{"wifi"}, true);

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(accordRepository.existsByLogementAIdOrLogementBId(
                fakeLogement.getId(), fakeLogement.getId())).thenReturn(false);
        when(geocodingService.geocode(any(), any(), any())).thenReturn(null);
        when(photoRepository.findByLogementIdOrderByOrdreAsc(fakeLogement.getId()))
                .thenReturn(List.of());
        when(logementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LogementResponse response = logementService.updateLogement(
                "pierre@studup.fr", fakeLogement.getId(), request);

        assertThat(response.ville()).isEqualTo("Lyon");
        assertThat(response.loyer()).isEqualByComparingTo("750.0");
    }

    @Test
    void shouldRejectUpdateWhenLinkedToAccord() {
        CreateLogementRequest request = new CreateLogementRequest(
                "2 avenue Neuve", "Lyon", "69002", LogementType.T2,
                new BigDecimal("40.0"), 2, new BigDecimal("750.0"),
                new BigDecimal("60.0"), null, null, true);

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(accordRepository.existsByLogementAIdOrLogementBId(
                fakeLogement.getId(), fakeLogement.getId())).thenReturn(true);

        assertThatThrownBy(() -> logementService.updateLogement(
                "pierre@studup.fr", fakeLogement.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accord");

        verify(logementRepository, never()).save(any());
    }

    // ─── Suppression ──────────────────────────────────────────────────────────

    @Test
    void shouldDeleteOwnLogement() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(accordRepository.existsByLogementAIdOrLogementBId(
                fakeLogement.getId(), fakeLogement.getId())).thenReturn(false);
        when(photoRepository.findFileKeysByLogementId(fakeLogement.getId()))
                .thenReturn(List.of());

        logementService.deleteLogement("pierre@studup.fr", fakeLogement.getId());

        verify(logementRepository).deleteById(fakeLogement.getId());
    }

    @Test
    void shouldRejectDeleteWhenLinkedToAccord() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(accordRepository.existsByLogementAIdOrLogementBId(
                fakeLogement.getId(), fakeLogement.getId())).thenReturn(true);

        assertThatThrownBy(() -> logementService.deleteLogement(
                "pierre@studup.fr", fakeLogement.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accord");

        verify(logementRepository, never()).deleteById(any());
    }

    @Test
    void shouldRejectDeleteByNonOwner() {
        User autre = User.builder().id(UUID.randomUUID()).email("autre@studup.fr").build();
        when(userRepository.findByEmail("autre@studup.fr")).thenReturn(Optional.of(autre));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));

        assertThatThrownBy(() -> logementService.deleteLogement(
                "autre@studup.fr", fakeLogement.getId()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldThrowWhenLogementNotFound() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logementService.publishLogement("pierre@studup.fr", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Association ville ────────────────────────────────────────────────────

    @Test
    void shouldAssociateLogementToVilleA() {
        AlternantProfile profile = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(fakeOwner)
                .villeA("Paris")
                .villeB("Lyon")
                .build();

        // Le logement est à Paris → correspond à VILLE_A
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(alternantProfileRepository.findByUserId(fakeOwner.getId())).thenReturn(Optional.of(profile));
        // APP-91 : check anti-doublon désormais en mémoire via findByOwnerId
        when(logementRepository.findByOwnerId(fakeOwner.getId()))
                .thenReturn(List.of(fakeLogement));
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(photoRepository.findByLogementIdOrderByOrdreAsc(any())).thenReturn(List.of());

        LogementResponse response = logementService.associerVille(
                "pierre@studup.fr", fakeLogement.getId(),
                new AssocierVilleRequest(VilleAssociee.VILLE_A));

        assertThat(response.villeAssociee()).isEqualTo(VilleAssociee.VILLE_A);
    }

    @Test
    void shouldRejectWhenVilleMismatch() {
        AlternantProfile profile = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(fakeOwner)
                .villeA("Lyon")   // VILLE_A = Lyon
                .villeB("Paris")
                .build();

        // Le logement est à Paris mais l'alternant demande VILLE_A (Lyon) → erreur
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(alternantProfileRepository.findByUserId(fakeOwner.getId())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> logementService.associerVille(
                "pierre@studup.fr", fakeLogement.getId(),
                new AssocierVilleRequest(VilleAssociee.VILLE_A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    void shouldRejectWhenVilleAlreadyAssigned() {
        AlternantProfile profile = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(fakeOwner)
                .villeA("Paris")
                .villeB("Lyon")
                .build();

        // Un autre logement est déjà associé à VILLE_A pour cet owner
        Logement autreLogement = Logement.builder()
                .id(UUID.randomUUID())
                .owner(fakeOwner)
                .ville("Paris")
                .villeAssociee(VilleAssociee.VILLE_A)
                .statut(LogementStatut.ACTIF)
                .isVerified(false)
                .isMeuble(true)
                .build();

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(alternantProfileRepository.findByUserId(fakeOwner.getId())).thenReturn(Optional.of(profile));
        // APP-91 : un autre logement occupe déjà VILLE_A (filtrage en mémoire)
        when(logementRepository.findByOwnerId(fakeOwner.getId()))
                .thenReturn(List.of(fakeLogement, autreLogement));

        assertThatThrownBy(() -> logementService.associerVille(
                "pierre@studup.fr", fakeLogement.getId(),
                new AssocierVilleRequest(VilleAssociee.VILLE_A)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà un logement associé");
    }

    @Test
    void shouldRejectWhenNoAlternantProfile() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(alternantProfileRepository.findByUserId(fakeOwner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logementService.associerVille(
                "pierre@studup.fr", fakeLogement.getId(),
                new AssocierVilleRequest(VilleAssociee.VILLE_A)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profil alternant");
    }

    // ─── Recherche ────────────────────────────────────────────────────────────

    @Test
    void shouldFilterByVilleAndLoyer() {
        // Le logement de référence est à Paris avec loyer 800€
        fakeLogement.setStatut(LogementStatut.ACTIF);

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fakeLogement)));

        LogementSearchRequest request = new LogementSearchRequest(
                "Paris", new BigDecimal("900"), null, null, null, null, 0);

        PageResponse<LogementResponse> result = logementService.search(request, "pierre@studup.fr");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).ville()).isEqualTo("Paris");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyPageWhenNoResults() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        LogementSearchRequest request = new LogementSearchRequest(
                "Marseille", null, null, null, null, null, 0);

        PageResponse<LogementResponse> result = logementService.search(request, "pierre@studup.fr");

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void shouldSearchWithNoFilters() {
        fakeLogement.setStatut(LogementStatut.ACTIF);

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fakeLogement)));

        // Aucun filtre — on cherche tous les logements actifs
        LogementSearchRequest request = new LogementSearchRequest(
                null, null, null, null, null, null, 0);

        PageResponse<LogementResponse> result = logementService.search(request, "pierre@studup.fr");

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void shouldRespectPageNumber() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        LogementSearchRequest request = new LogementSearchRequest(
                null, null, null, null, null, null, 2);

        PageResponse<LogementResponse> result = logementService.search(request, "pierre@studup.fr");

        assertThat(result.page()).isEqualTo(2);
    }

    @Test
    void shouldResolveConnectedUserToExcludeOwnLogements() {
        // APP-117 (A-03) : la recherche doit identifier l'utilisateur connecté pour
        // pouvoir exclure ses propres logements (filtre proprietaireDifferent).
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeOwner));
        when(logementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        LogementSearchRequest request = new LogementSearchRequest(
                null, null, null, null, null, null, 0);

        logementService.search(request, "pierre@studup.fr");

        // L'utilisateur connecté est bien résolu (base du filtre d'exclusion)
        verify(userRepository).findByEmail("pierre@studup.fr");
    }

    @Test
    void shouldRejectSearchWhenUserNotFound() {
        when(userRepository.findByEmail("inconnu@studup.fr")).thenReturn(Optional.empty());

        LogementSearchRequest request = new LogementSearchRequest(
                null, null, null, null, null, null, 0);

        assertThatThrownBy(() -> logementService.search(request, "inconnu@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
