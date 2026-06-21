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
import com.studup.backend.model.enums.VilleAssociee;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.LogementSpecification;
import com.studup.backend.repository.PhotoLogementRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LogementService {

    private static final int MAX_PHOTOS = 10;

    private final LogementRepository logementRepository;
    private final PhotoLogementRepository photoRepository;
    private final UserRepository userRepository;
    private final AlternantProfileRepository alternantProfileRepository;
    private final MinioService minioService;
    private final GeocodingService geocodingService;
    private final FileValidationService fileValidationService;

    public LogementService(LogementRepository logementRepository,
                           PhotoLogementRepository photoRepository,
                           UserRepository userRepository,
                           AlternantProfileRepository alternantProfileRepository,
                           MinioService minioService,
                           GeocodingService geocodingService,
                           FileValidationService fileValidationService) {
        this.logementRepository = logementRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.alternantProfileRepository = alternantProfileRepository;
        this.minioService = minioService;
        this.geocodingService = geocodingService;
        this.fileValidationService = fileValidationService;
    }

    @Transactional
    public LogementResponse createLogement(String email, CreateLogementRequest request) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = Logement.builder()
                .owner(owner)
                .adresse(request.adresse())
                .ville(request.ville())
                .codePostal(request.codePostal())
                .type(request.type())
                .surface(request.surface())
                .nbPieces(request.nbPieces() != null ? request.nbPieces() : 1)
                .loyer(request.loyer())
                .charges(request.charges())
                .description(request.description())
                .equipements(request.equipements())
                .isMeuble(request.isMeuble() != null ? request.isMeuble() : true)
                .build();

        // Géocodage asynchrone — non bloquant : si Nominatim échoue, le logement est quand même créé
        GeocodingService.Coordinates coords = geocodingService.geocode(
                request.adresse(), request.ville(), request.codePostal()
        );
        if (coords != null) {
            logement.setLat(coords.lat());
            logement.setLng(coords.lng());
        }

        logement = logementRepository.save(logement);
        return LogementResponse.from(logement, List.of());
    }

    @Transactional
    public LogementResponse publishLogement(String email, UUID logementId) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        if (!logement.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement");
        }

        logement.setStatut(LogementStatut.ACTIF);
        logement = logementRepository.save(logement);

        List<String> photoUrls = getPhotoUrls(logementId);
        return LogementResponse.from(logement, photoUrls);
    }

    @Transactional
    public List<String> addPhotos(String email, UUID logementId, List<MultipartFile> files) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        if (!logement.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement");
        }

        int existingCount = photoRepository.countByLogementId(logementId);
        if (existingCount + files.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException(
                    "Un logement ne peut pas avoir plus de " + MAX_PHOTOS + " photos. " +
                    "Vous en avez déjà " + existingCount + "."
            );
        }

        List<String> uploadedUrls = new ArrayList<>();
        int ordre = existingCount;

        for (MultipartFile file : files) {
            // Validation par magic bytes — ignore le Content-Type déclaré par le client
            fileValidationService.validateImage(file);

            String fileKey = "logements/" + UUID.randomUUID() + getExtension(file.getContentType());
            byte[] compressed = compressImage(file);

            minioService.uploadFile(
                    fileKey,
                    new ByteArrayInputStream(compressed),
                    file.getContentType(),
                    compressed.length
            );

            PhotoLogement photo = PhotoLogement.builder()
                    .logement(logement)
                    .fileKey(fileKey)
                    .ordre(ordre++)
                    .build();
            photoRepository.save(photo);

            uploadedUrls.add(minioService.generatePresignedUrl(fileKey));
        }

        return uploadedUrls;
    }

    @Transactional(readOnly = true)
    public LogementResponse getLogement(UUID logementId) {
        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        List<String> photoUrls = getPhotoUrls(logementId);
        return LogementResponse.from(logement, photoUrls);
    }

    @Transactional
    public LogementResponse associerVille(String email, UUID logementId, AssocierVilleRequest request) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        // Récupère le profil alternant pour vérifier les villes autorisées
        AlternantProfile profile = alternantProfileRepository.findByUserId(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable — créez votre profil avant d'associer un logement"));

        // Vérifie que la ville du logement correspond à la ville demandée dans le profil
        String villeAttendue = request.villeAssociee() == VilleAssociee.VILLE_A
                ? profile.getVilleA()
                : profile.getVilleB();

        if (!logement.getVille().equalsIgnoreCase(villeAttendue)) {
            throw new IllegalArgumentException(
                    "La ville du logement (" + logement.getVille() + ") ne correspond pas à "
                    + request.villeAssociee() + " (" + villeAttendue + ") dans votre profil");
        }

        // Vérifie qu'il n'existe pas déjà un logement associé à cette ville pour cet alternant
        logementRepository.findByOwnerIdAndVilleAssociee(owner.getId(), request.villeAssociee())
                .filter(existant -> !existant.getId().equals(logementId))
                .ifPresent(existant -> {
                    throw new org.springframework.dao.DuplicateKeyException(
                            "Vous avez déjà un logement associé à " + request.villeAssociee());
                });

        logement.setVilleAssociee(request.villeAssociee());
        logement = logementRepository.save(logement);

        List<String> photoUrls = getPhotoUrls(logementId);
        return LogementResponse.from(logement, photoUrls);
    }

    private List<String> getPhotoUrls(UUID logementId) {
        return photoRepository.findByLogementIdOrderByOrdreAsc(logementId).stream()
                .map(p -> minioService.generatePresignedUrl(p.getFileKey()))
                .toList();
    }

    // Compresse l'image à 80% de qualité via Thumbnailator
    private byte[] compressImage(MultipartFile file) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .scale(1.0)
                    .outputQuality(0.80)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la compression de l'image : " + e.getMessage(), e);
        }
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    // ─── Recherche ────────────────────────────────────────────────────────────

    public PageResponse<LogementResponse> search(LogementSearchRequest request) {
        // On commence toujours par filtrer les logements ACTIF uniquement
        Specification<Logement> spec = LogementSpecification.estActif();

        // On ajoute les filtres optionnels un par un — ceux qui sont null sont ignorés
        if (request.ville() != null && !request.ville().isBlank()) {
            spec = spec.and(LogementSpecification.villeEgale(request.ville()));
        }
        if (request.loyerMax() != null) {
            spec = spec.and(LogementSpecification.loyerMaxInferieurOuEgal(request.loyerMax()));
        }
        if (request.surfaceMin() != null) {
            spec = spec.and(LogementSpecification.surfaceMinSuperieurOuEgal(request.surfaceMin()));
        }
        if (request.meuble() != null) {
            spec = spec.and(LogementSpecification.estMeuble(request.meuble()));
        }
        if (request.type() != null) {
            spec = spec.and(LogementSpecification.typeEgal(request.type()));
        }

        // Tri : prix_asc | prix_desc | surface_desc | pertinence (par défaut = date création desc)
        Sort sort = switch (request.tri() != null ? request.tri() : "pertinence") {
            case "prix_asc" -> Sort.by("loyer").ascending();
            case "prix_desc" -> Sort.by("loyer").descending();
            case "surface_desc" -> Sort.by("surface").descending();
            default -> Sort.by("createdAt").descending();
        };

        int pageNumber = request.page() != null ? request.page() : 0;
        PageRequest pageable = PageRequest.of(pageNumber, 20, sort);
        Page<Logement> page = logementRepository.findAll(spec, pageable);

        // Dans la liste de résultats, les photos ne sont pas chargées (perf)
        // Les URLs signées MinIO sont chargées uniquement sur le détail d'un logement
        List<LogementResponse> content = page.getContent().stream()
                .map(l -> LogementResponse.from(l, List.of()))
                .toList();

        return new PageResponse<>(content, pageNumber, 20, page.getTotalElements(), page.hasNext());
    }
}
