package com.studup.backend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Active la planification Spring et ShedLock.
 * Sans @EnableScheduling, les @Scheduled ne s'exécutent jamais.
 * Sans @EnableSchedulerLock, ShedLock ne pose aucun verrou.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    /**
     * LockProvider stocke les verrous dans la table shedlock (créée par V14).
     * On utilise JDBC (PostgreSQL) — pas Redis — pour que le verrou survive
     * même si Redis est temporairement indisponible.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
