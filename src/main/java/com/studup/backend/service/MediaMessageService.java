package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessagePhotoResponse;
import com.studup.backend.repository.MessageRepository;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MediaMessageService {

    private static final Logger log = LoggerFactory.getLogger(MediaMessageService.class);

    private static final int MAX_PHOTOS = 5;
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024L; // 5 Mo
    private static final int MAX_WIDTH_PX = 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MessageRepository messageRepository;
    private final MinioService minioService;

    public MediaMessageService(MessageRepository messageRepository, MinioService minioService) {
        this.messageRepository = messageRepository;
        this.minioService = minioService;
    }

    /**
     * Upload jusqu'à 5 photos pour un message existant.
     * Chaque image est compressée à max 1024px de large avant stockage MinIO.
     * Retourne les URLs signées (valables 24h) pour accès direct depuis Flutter.
     */
    public MessagePhotoResponse uploadPhotos(UUID messageId, List<MultipartFile> files) {
        // Vérifie que le message existe
        messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable : " + messageId));

        // Validation : max 5 photos par message
        if (files.size() > MAX_PHOTOS) {
            throw new IllegalArgumentException("Maximum " + MAX_PHOTOS + " photos par message");
        }

        List<String> signedUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            // Validation : type MIME autorisé
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                throw new IllegalArgumentException(
                        "Type de fichier non autorisé : " + contentType + ". Acceptés : JPEG, PNG, WEBP");
            }

            // Validation : taille max 5 Mo
            if (file.getSize() > MAX_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        "Fichier trop volumineux : " + file.getOriginalFilename() + " (max 5 Mo)");
            }

            // Compression et upload
            String fileKey = "messages/" + UUID.randomUUID();
            byte[] compressed = compressImage(file, contentType);

            minioService.uploadFile(fileKey,
                    new ByteArrayInputStream(compressed),
                    contentType,
                    compressed.length);

            // URL signée valable 24h (plus long que les logements car moins consultées)
            String signedUrl = minioService.generatePresignedUrl(fileKey, 24, TimeUnit.HOURS);
            signedUrls.add(signedUrl);

            log.info("Photo uploadée pour messageId={} clé={} taille={}Ko",
                    messageId, fileKey, compressed.length / 1024);
        }

        return new MessagePhotoResponse(messageId.toString(), signedUrls);
    }

    // Redimensionne l'image à max 1024px de large (garde les proportions)
    // Si l'image est déjà <= 1024px, Thumbnailator la retourne telle quelle
    private byte[] compressImage(MultipartFile file, String contentType) {
        try {
            String format = contentType.equals("image/png") ? "png"
                    : contentType.equals("image/webp") ? "webp"
                    : "jpg";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .width(MAX_WIDTH_PX)
                    .outputFormat(format)
                    .toOutputStream(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la compression de l'image : " + e.getMessage(), e);
        }
    }
}
