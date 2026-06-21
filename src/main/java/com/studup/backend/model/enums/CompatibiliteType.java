package com.studup.backend.model.enums;

public enum CompatibiliteType {
    ECHANGE("#27AE60", "Échange"),
    COLOCATION("#3498DB", "Coloc possible"),
    CHEVAUCHEMENT("#F39C12", "Chevauchement"),
    INCOMPATIBLE("#ECF0F1", "");

    public final String couleurHex;
    public final String label;

    CompatibiliteType(String couleurHex, String label) {
        this.couleurHex = couleurHex;
        this.label = label;
    }
}
