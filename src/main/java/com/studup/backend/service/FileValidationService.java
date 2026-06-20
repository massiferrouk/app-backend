package com.studup.backend.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Valide les fichiers uploadés en lisant leurs magic bytes via Apache Tika.
 * Ne fait jamais confiance au Content-Type déclaré par le client.
 */
@Service
public class FileValidationService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf"
    );

    private static final long MAX_IMAGE_SIZE_BYTES  = 5L  * 1024 * 1024; // 5 Mo
    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024; // 10 Mo

    private final Tika tika = new Tika();

    /**
     * Valide une photo de logement.
     * Types autorisés : JPEG, PNG, WEBP — taille max 5 Mo.
     */
    public void validateImage(MultipartFile file) {
        checkSize(file, MAX_IMAGE_SIZE_BYTES, "5 Mo");
        String detectedType = detectMimeType(file);
        if (!ALLOWED_IMAGE_TYPES.contains(detectedType)) {
            throw new IllegalArgumentException(
                    "Format non supporté : " + detectedType +
                    ". Formats acceptés : JPEG, PNG, WEBP"
            );
        }
    }

    /**
     * Valide un document (contrat d'alternance, pièce d'identité...).
     * Types autorisés : PDF — taille max 10 Mo.
     */
    public void validateDocument(MultipartFile file) {
        checkSize(file, MAX_DOCUMENT_SIZE_BYTES, "10 Mo");
        String detectedType = detectMimeType(file);
        if (!ALLOWED_DOCUMENT_TYPES.contains(detectedType)) {
            throw new IllegalArgumentException(
                    "Format non supporté : " + detectedType +
                    ". Seuls les PDF sont acceptés pour les documents"
            );
        }
    }

    // Lit les magic bytes du fichier via Tika — ignore totalement l'extension et le Content-Type
    private String detectMimeType(MultipartFile file) {
        try {
            return tika.detect(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier : " + e.getMessage());
        }
    }

    private void checkSize(MultipartFile file, long maxBytes, String maxLabel) {
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "Le fichier dépasse la taille maximale autorisée de " + maxLabel +
                    " : " + file.getOriginalFilename()
            );
        }
    }
}
