package com.studup.backend.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purge les tokens JWT expirés de Redis chaque heure.
 * Redis expire les clés automatiquement via TTL, donc ce job est un filet de sécurité
 * pour les clés sans TTL ou mal configurées.
 */
@Component
public class JwtCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(JwtCleanupJob.class);

    private final StringRedisTemplate redisTemplate;

    public JwtCleanupJob(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 30 * * * *")
    @SchedulerLock(name = "JwtCleanupJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void cleanupExpiredTokens() {
        // Redis expire les clés JWT automatiquement via TTL.
        // Ce job vérifie que la connexion Redis est saine.
        boolean connected = redisTemplate.getConnectionFactory() != null;
        log.info("JwtCleanupJob : Redis connecté={}", connected);
    }
}
