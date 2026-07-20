package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Changement de mode du compte (APP-117). Seuls ETUDIANT et ALTERNANT sont des
 * modes valides ici — la règle métier est vérifiée dans UserService.changeMode.
 */
public record ChangeModeRequest(

        @NotNull(message = "Le mode est obligatoire (ETUDIANT ou ALTERNANT)")
        UserRole role
) {}
