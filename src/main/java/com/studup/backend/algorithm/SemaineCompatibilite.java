package com.studup.backend.algorithm;

import com.studup.backend.model.enums.CompatibiliteType;

import java.time.LocalDate;

public record SemaineCompatibilite(
        LocalDate semaine,
        String villeAlternantA,
        String villeAlternantB,
        CompatibiliteType type,
        String couleurHex,
        String label
) {
    public static SemaineCompatibilite of(LocalDate semaine,
                                          String villeA,
                                          String villeB,
                                          CompatibiliteType type) {
        return new SemaineCompatibilite(semaine, villeA, villeB, type, type.couleurHex, type.label);
    }
}
