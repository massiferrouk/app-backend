package com.studup.backend.service;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.logements}")
    private String bucketLogements;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
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
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketLogements)
                    .object(fileKey)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
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
