package com.studup.backend.model.enums;

public enum CompatibiliteType {
    ECHANGE("#27AE60", "Échange"),
    COLOCATION("#3498DB", "Coloc possible"),
    CHEVAUCHEMENT("#F39C12", "Chevauchement"),
    // Semaine neutre : chacun est dans sa ville, rien à organiser (APP-108).
    // Ce n'est pas un échec, juste une semaine sans arrangement possible.
    INCOMPATIBLE("#ECF0F1", "Chacun chez soi");

    public final String couleurHex;
    public final String label;

    CompatibiliteType(String couleurHex, String label) {
        this.couleurHex = couleurHex;
        this.label = label;
    }
}
