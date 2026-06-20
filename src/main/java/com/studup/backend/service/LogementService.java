package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.AssocierVilleRequest;
import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.PhotoLogement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.VilleAssociee;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.PhotoLogementRepository;
import com.studup.backend.repository.UserRepository;
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
}
