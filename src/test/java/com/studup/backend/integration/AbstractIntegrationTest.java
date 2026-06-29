package com.studup.backend.integration;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe de base pour tous les tests d'intégration.
 *
 * Problème avec @Testcontainers + @Container static :
 * JUnit 5 arrête les containers après chaque classe de test, puis les redémarre
 * sur de NOUVEAUX ports pour la classe suivante. Mais le contexte Spring est caché
 * avec les ANCIENS ports → "Connection refused" sur les classes suivantes.
 *
 * Solution : démarrer les containers dans un bloc static{} sans @Testcontainers.
 * Ils démarrent une seule fois au chargement de la classe abstraite et restent
 * vivants jusqu'à la fin de la JVM (Ryuk les nettoie à la fin du run).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("yunivdb_test")
            .withUsername("yuniv")
            .withPassword("yuniv123");

    static final RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    // Démarrage manuel unique — ports stables pour tout le run
    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Base de données — Flyway et JPA utilisent ces propriétés
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis — blacklist JWT + cache matching
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Désactive MinIO pour les tests d'intégration — pas besoin de fichiers réels
        registry.add("minio.endpoint", () -> "http://localhost:9000");
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket.logements", () -> "logements-test");

        // Désactive Firebase — pas de vraies notifications push en test
        registry.add("firebase.enabled", () -> "false");

        // Email SMTP désactivé en test
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "3025");
    }
}
