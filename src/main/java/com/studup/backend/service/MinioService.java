package com.studup.backend.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket.logements}")
    private String bucketLogements;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Crée le bucket au démarrage s'il n'existe pas encore. Sans ça, un
     * MinIO fraîchement démarré (nouveau conteneur) n'a aucun bucket et le
     * premier upload échoue en « bucket does not exist » (→ 500).
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketLogements).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketLogements).build());
                log.info("Bucket MinIO '{}' créé au démarrage", bucketLogements);
            }
        } catch (Exception e) {
            // Non bloquant : on log, l'upload remontera une erreur claire si besoin
            log.warn("Impossible de vérifier/créer le bucket MinIO '{}' : {}",
                    bucketLogements, e.getMessage());
        }
    }

    /**
     * Upload un fichier dans le bucket logements.
     *
     * @param fileKey     clé unique du fichier (UUID généré côté service)
     * @param inputStream contenu du fichier (déjà compressé si image)
     * @param contentType type MIME : image/jpeg, image/png, image/webp
     * @param size        taille en bytes
     */
    public void uploadFile(String fileKey, InputStream inputStream, String contentType, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketLogements)
                    .object(fileKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'upload vers MinIO : " + e.getMessage(), e);
        }
    }

    /**
     * Génère une URL signée valable 1 heure pour accéder à un fichier.
     * Le client Flutter appelle directement cette URL — le fichier ne transite pas par le backend.
     */
    public String generatePresignedUrl(String fileKey) {
        return generatePresignedUrl(fileKey, 1, TimeUnit.HOURS);
    }

    /**
     * Génère une URL signée avec un TTL personnalisé.
     * Utilisé pour les photos de messages (24h) vs photos logements (1h).
     */
    public String generatePresignedUrl(String fileKey, int duration, TimeUnit unit) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketLogements)
                    .object(fileKey)
                    .method(Method.GET)
                    .expiry(duration, unit)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de l'URL signée : " + e.getMessage(), e);
        }
    }

    /**
     * Supprime un fichier du bucket logements.
     */
    public void deleteFile(String fileKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketLogements)
                    .object(fileKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la suppression du fichier MinIO : " + e.getMessage(), e);
        }
    }
}
