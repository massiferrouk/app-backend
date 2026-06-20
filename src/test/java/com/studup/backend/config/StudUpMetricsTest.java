package com.studup.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudUpMetricsTest {

    private SimpleMeterRegistry registry;
    private StudUpMetrics metrics;

    @BeforeEach
    void setUp() {
        // SimpleMeterRegistry : registry en mémoire fourni par Micrometer pour les tests
        registry = new SimpleMeterRegistry();
        metrics = new StudUpMetrics(registry);
    }

    @Test
    void shouldIncrementMatchingsCreated() {
        metrics.incrementMatchingsCreated();
        metrics.incrementMatchingsCreated();

        Counter counter = registry.find("matchings.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void shouldIncrementInscriptionsDaily() {
        metrics.incrementInscriptionsDaily();

        Counter counter = registry.find("inscriptions.daily").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldStartAtZero() {
        Counter matchings = registry.find("matchings.created").counter();
        Counter inscriptions = registry.find("inscriptions.daily").counter();

        assertThat(matchings).isNotNull();
        assertThat(inscriptions).isNotNull();
        assertThat(matchings.count()).isEqualTo(0.0);
        assertThat(inscriptions.count()).isEqualTo(0.0);
    }

    @Test
    void shouldRegisterCountersWithCorrectNames() {
        // Vérifie que les noms sont exacts — ce sont ces noms qui apparaissent dans /actuator/metrics
        assertThat(registry.find("matchings.created").counter()).isNotNull();
        assertThat(registry.find("inscriptions.daily").counter()).isNotNull();
    }
}
