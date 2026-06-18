package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.VilleAssociee;
import jakarta.validation.constraints.NotNull;

public record AssocierVilleRequest(

        @NotNull(message = "La ville associée est obligatoire (VILLE_A ou VILLE_B)")
        VilleAssociee villeAssociee
) {}
