package com.studup.backend.model.dto.response;

import com.studup.backend.algorithm.Scenario;

import java.math.BigDecimal;

/**
 * Un scénario d'arrangement exposé au frontend (APP-109).
 * type/action en String : Flutter les mappe sur ses propres enums.
 */
public record ScenarioResponse(
        String type,
        String message,
        BigDecimal economieMensuelle,
        String action
) {
    public static ScenarioResponse from(Scenario scenario) {
        return new ScenarioResponse(
                scenario.type().name(),
                scenario.message(),
                scenario.economieMensuelle(),
                scenario.action().name());
    }
}
