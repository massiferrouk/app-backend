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
import com.studup.backend.repository.AccordRepository;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LogementService.class);

    private static final int MAX_PHOTOS = 10;

    private final LogementRepository logementRepository;
    private final PhotoLogementRepository photoRepository;
    private final UserRepository userRepository;
    private final AlternantProfileRepository alternantProfileRepository;
    private final AccordRepository accordRepository;
    private final MinioService minioService;
    private final GeocodingService geocodingService;
    private final FileValidationService fileValidationService;

    public LogementService(LogementRepository logementRepository,
                           PhotoLogementRepository photoRepository,
                           UserRepository userRepository,
                           AlternantProfileRepository alternantProfileRepository,
                           AccordRepository accordRepository,
                           MinioService minioService,
                           GeocodingService geocodingService,
                           FileValidationService fileValidationService) {
        this.logementRepository = logementRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.alternantProfileRepository = alternantProfileRepository;
        this.accordRepository = accordRepository;
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

    /**
     * Met à jour un logement appartenant à l'utilisateur (brouillon ou publié).
     * Interdit si le logement est engagé dans un accord : on ne modifie pas une
     * annonce sur laquelle quelqu'un s'est déjà engagé (cohérent avec la
     * suppression).
     */
    @Transactional
    public LogementResponse updateLogement(String email, UUID logementId, CreateLogementRequest request) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        if (!logement.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement");
        }

        // APP-117 (A-06) : on ne bloque que si un accord VIVANT engage le logement.
        // Un accord refusé/annulé/terminé le re-libère à la modification.
        if (accordRepository.existsLivingAccordForLogement(logementId)) {
            throw new IllegalStateException(
                    "Ce logement est engagé dans un accord en cours et ne peut pas être modifié.");
        }

        logement.setAdresse(request.adresse());
        logement.setVille(request.ville());
        logement.setCodePostal(request.codePostal());
        logement.setType(request.type());
        logement.setSurface(request.surface());
        logement.setNbPieces(request.nbPieces() != null ? request.nbPieces() : 1);
        logement.setLoyer(request.loyer());
        logement.setCharges(request.charges());
        logement.setDescription(request.description());
        logement.setEquipements(request.equipements());
        logement.setIsMeuble(request.isMeuble() != null ? request.isMeuble() : true);

        // Re-géocodage si l'adresse a changé — non bloquant
        GeocodingService.Coordinates coords = geocodingService.geocode(
                request.adresse(), request.ville(), request.codePostal());
        if (coords != null) {
            logement.setLat(coords.lat());
            logement.setLng(coords.lng());
        }

        Logement saved = logementRepository.save(logement);
        return LogementResponse.from(saved, getPhotoUrls(logementId));
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
        // APP-120 : la publication est le moment où le logement entre dans le
        // matching — on déduit donc sa ville associée ici. Avant, elle restait
        // nulle tant que l'utilisateur n'avait pas cliqué « Associer », et le
        // matching l'ignorait silencieusement (MatchingService filtre sur ce
        // champ) : l'alternant restait en « match potentiel » sans savoir
        // pourquoi, alors qu'il avait bien publié son logement.
        deduireVilleAssociee(logement);
        logement = logementRepository.save(logement);

        List<String> photoUrls = getPhotoUrls(logementId);
        return LogementResponse.from(logement, photoUrls);
    }

    /**
     * Déduit la ville associée à partir de la ville du logement et du profil
     * alternant (APP-120).
     *
     * Le choix manuel n'en était pas un : le backend connaissait déjà la seule
     * réponse valide et se contentait de vérifier que l'utilisateur l'avait
     * devinée. On la calcule donc directement.
     *
     * Ne fait rien si :
     * - le propriétaire n'est pas alternant (un bailleur n'a pas de villes) ;
     * - le logement n'est dans aucune des deux villes du profil — il n'entre
     *   alors pas dans le matching, et l'app doit le dire à l'utilisateur ;
     * - la ville visée est déjà prise par un autre logement actif.
     */
    private void deduireVilleAssociee(Logement logement) {
        AlternantProfile profile = alternantProfileRepository
                .findByUserId(logement.getOwner().getId())
                .orElse(null);
        if (profile == null) return;

        VilleAssociee cible;
        if (logement.getVille().equalsIgnoreCase(profile.getVilleA())) {
            cible = VilleAssociee.VILLE_A;
        } else if (logement.getVille().equalsIgnoreCase(profile.getVilleB())) {
            cible = VilleAssociee.VILLE_B;
        } else {
            return; // hors des deux villes : pas de matching possible
        }

        // Un seul logement par ville (même règle que l'association manuelle)
        boolean dejaPrise = logementRepository.findByOwnerId(logement.getOwner().getId())
                .stream()
                .anyMatch(l -> !l.getId().equals(logement.getId())
                        && l.getStatut() != LogementStatut.ARCHIVE
                        && cible.equals(l.getVilleAssociee()));
        if (dejaPrise) return;

        logement.setVilleAssociee(cible);
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

    /**
     * Supprime un logement appartenant à l'utilisateur connecté (brouillon ou
     * publié, quel que soit son rôle). Interdit si le logement est engagé dans
     * un accord (contrainte d'intégrité + logique métier).
     */
    @Transactional
    public void deleteLogement(String email, UUID logementId) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        if (!logement.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement");
        }

        // APP-117 (A-06) : accord VIVANT → suppression interdite (négociation ou contrat
        // en cours). L'utilisateur doit d'abord annuler/refuser l'accord.
        if (accordRepository.existsLivingAccordForLogement(logementId)) {
            throw new IllegalStateException(
                    "Ce logement est engagé dans un accord en cours et ne peut pas être supprimé.");
        }

        // APP-117 (A-06) : le logement a un historique d'accords MORTS. Les FK des accords
        // sont en RESTRICT et gardent une référence vers lui pour l'audit → on ne peut pas
        // l'effacer physiquement sans orpheliner un accord passé. On le passe donc en
        // ARCHIVE (suppression logique) : il disparaît des listes et des recherches mais
        // la trace de l'historique reste intacte. Les photos sont conservées.
        if (accordRepository.existsByLogementAIdOrLogementBId(logementId, logementId)) {
            logement.setStatut(LogementStatut.ARCHIVE);
            logementRepository.save(logement);
            return;
        }

        // Aucun accord n'a jamais engagé ce logement → suppression physique réelle.
        // On récupère uniquement les clés MinIO (projection String, pas d'entités
        // managées) AVANT la suppression — charger les entités PhotoLogement puis
        // supprimer le logement (cascade DB) fait échouer le flush Hibernate.
        List<String> fileKeys = photoRepository.findFileKeysByLogementId(logementId);

        logementRepository.deleteById(logementId);

        // Nettoyage MinIO best-effort APRÈS la suppression en base : un échec de
        // stockage ne doit pas empêcher la suppression logique.
        fileKeys.forEach(key -> {
            try {
                minioService.deleteFile(key);
            } catch (Exception e) {
                log.warn("Suppression MinIO échouée pour {} : {}", key, e.getMessage());
            }
        });
    }

    /**
     * Tous les logements de l'utilisateur connecté, quel que soit leur
     * statut (BROUILLON inclus) — pour l'écran "Mes logements" (APP-70).
     */
    @Transactional(readOnly = true)
    public List<LogementResponse> getMesLogements(String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        // APP-117 (A-06) : les logements archivés (supprimés logiquement car engagés dans
        // un accord passé) ne doivent plus apparaître dans "Mes logements".
        return logementRepository.findByOwnerId(owner.getId()).stream()
                .filter(l -> l.getStatut() != LogementStatut.ARCHIVE)
                .map(l -> LogementResponse.from(l, getPhotoUrls(l.getId())))
                .toList();
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

        // APP-117 (A-08) : sécurité — on ne modifie que SON propre logement.
        // Sans ce contrôle, n'importe quel alternant authentifié pouvait réassocier
        // la ville du logement d'un autre utilisateur (faille IDOR).
        if (!logement.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement");
        }

        // Récupère le profil alternant pour vérifier les villes autorisées
        AlternantProfile profile = alternantProfileRepository.findByUserId(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable — créez votre profil avant d'associer un logement"));

        // APP-117 (A-06) : ré-associer la ville d'un logement engagé dans un accord
        // vivant changerait la base du matching en cours de négociation → interdit.
        if (accordRepository.existsLivingAccordForLogement(logementId)) {
            throw new IllegalStateException(
                    "Ce logement est engagé dans un accord en cours : sa ville ne peut pas être réassociée.");
        }

        // Vérifie que la ville du logement correspond à la ville demandée dans le profil
        String villeAttendue = request.villeAssociee() == VilleAssociee.VILLE_A
                ? profile.getVilleA()
                : profile.getVilleB();

        if (!logement.getVille().equalsIgnoreCase(villeAttendue)) {
            throw new IllegalArgumentException(
                    "La ville du logement (" + logement.getVille() + ") ne correspond pas à "
                    + request.villeAssociee() + " (" + villeAttendue + ") dans votre profil");
        }

        // Vérifie qu'il n'existe pas déjà un logement associé à cette ville pour cet alternant.
        // APP-91 : filtrage en mémoire — la colonne ville_associee est un ENUM natif
        // PostgreSQL, une comparaison SQL "WHERE ville_associee = ?" (string) échoue
        // (operator does not exist). La lecture de la colonne, elle, fonctionne.
        boolean villeDejaOccupee = logementRepository.findByOwnerId(owner.getId()).stream()
                .anyMatch(l -> !l.getId().equals(logementId)
                        && l.getStatut() != LogementStatut.ARCHIVE
                        && request.villeAssociee().equals(l.getVilleAssociee()));
        if (villeDejaOccupee) {
            // IllegalStateException → 409 CONFLICT via le GlobalExceptionHandler
            throw new IllegalStateException(
                    "Vous avez déjà un logement associé à " + request.villeAssociee());
        }

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

    /**
     * URL signée de la photo de couverture (première photo) d'un logement,
     * pour les listes/recherche. Retourne une liste de 0 ou 1 élément.
     * On ne charge que la 1re clé (projection) et on génère une seule URL —
     * la signature est un calcul local, pas d'appel réseau à MinIO.
     */
    private List<String> getCoverPhotoUrl(UUID logementId) {
        return photoRepository.findFileKeysByLogementId(logementId).stream()
                .findFirst()
                .map(key -> List.of(minioService.generatePresignedUrl(key)))
                .orElse(List.of());
    }

    // Compresse l'image à 80% de qualité via Thumbnailator
    private byte[] compressImage(MultipartFile file) {
        final byte[] original;
        try {
            original = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lecture de l'image : " + e.getMessage(), e);
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(original))
                    .scale(1.0)
                    .outputQuality(0.80)
                    .toOutputStream(out);
            byte[] compressed = out.toByteArray();
            // Certains formats produisent un flux vide : on garde alors l'original
            return compressed.length > 0 ? compressed : original;
        } catch (Exception e) {
            // Format non décodable par ImageIO (ex: WEBP sans plugin natif) :
            // la compression est une optimisation, pas une obligation → on
            // uploade l'original tel quel plutôt que d'échouer en 500.
            log.warn("Compression impossible pour {} ({}) — upload de l'original",
                    file.getContentType(), e.getMessage());
            return original;
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

    public PageResponse<LogementResponse> search(LogementSearchRequest request, String email) {
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        // On commence toujours par filtrer les logements ACTIF uniquement,
        // en excluant ceux de l'utilisateur connecté (APP-117 · A-03)
        Specification<Logement> spec = LogementSpecification.estActif()
                .and(LogementSpecification.proprietaireDifferent(currentUser.getId()));

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
                .map(l -> LogementResponse.from(l, getCoverPhotoUrl(l.getId())))
                .toList();

        return new PageResponse<>(content, pageNumber, 20, page.getTotalElements(), page.hasNext());
    }
}
