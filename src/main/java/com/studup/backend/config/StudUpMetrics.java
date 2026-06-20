package com.studup.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métriques custom Micrometer exposées via /actuator/metrics/{name}.
 * Chaque counter est incrémenté depuis le service métier correspondant.
 */
@Component
public class StudUpMetrics {

    private final Counter matchingsCreated;
    private final Counter inscriptionsDaily;

    public StudUpMetrics(MeterRegistry registry) {
        // Counter.builder crée un compteur nommé, accessible via /actuator/metrics/matchings.created
        this.matchingsCreated = Counter.builder("matchings.created")
                .description("Nombre total de calculs de matching effectués")
                .register(registry);

        this.inscriptionsDaily = Counter.builder("inscriptions.daily")
                .description("Nombre total d'inscriptions")
                .register(registry);
    }

    public void incrementMatchingsCreated() {
        matchingsCreated.increment();
    }

    public void incrementInscriptionsDaily() {
        inscriptionsDaily.increment();
    }
}
