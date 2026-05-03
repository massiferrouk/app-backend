-- V3__add_calendrier_alternance.sql
-- Calendrier semaine par semaine et jours fériés

-- ============================================================
-- TABLE: alternance_schedules
-- ============================================================

-- label : 'A' = semaine école (ville A), 'B' = semaine entreprise (ville B)
CREATE TABLE alternance_schedules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id      UUID         NOT NULL REFERENCES alternant_profiles(id) ON DELETE CASCADE,
    semaine         DATE         NOT NULL,   -- Toujours le lundi de la semaine ISO
    label           CHAR(1)      NOT NULL,   -- 'A' ou 'B'
    is_overridden   BOOLEAN      NOT NULL DEFAULT FALSE,
    override_reason VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alternance_schedule      UNIQUE (profile_id, semaine),
    CONSTRAINT chk_alternance_label        CHECK (label IN ('A', 'B')),
    CONSTRAINT chk_alternance_semaine_lundi
        CHECK (EXTRACT(DOW FROM semaine) = 1)   -- 1 = lundi
);

COMMENT ON COLUMN alternance_schedules.semaine IS 'Date du lundi de la semaine (semaine ISO). Ex: 2024-09-02';
COMMENT ON COLUMN alternance_schedules.label IS '''A'' = ville école, ''B'' = ville entreprise';
COMMENT ON COLUMN alternance_schedules.is_overridden IS 'Semaine modifiée manuellement vs générée par algorithme';

CREATE INDEX idx_alternance_schedules_profile_id ON alternance_schedules(profile_id);
CREATE INDEX idx_alternance_schedules_semaine    ON alternance_schedules(semaine);
-- Index composite pour la recherche de compatibilité
CREATE INDEX idx_alternance_schedules_profile_semaine
    ON alternance_schedules(profile_id, semaine, label);

-- ============================================================
-- TABLE: jours_feries
-- ============================================================

CREATE TABLE jours_feries (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date_jour   DATE         NOT NULL,
    libelle     VARCHAR(100) NOT NULL,
    pays        CHAR(2)      NOT NULL DEFAULT 'FR',
    region      VARCHAR(50),
    CONSTRAINT uq_jours_feries UNIQUE (date_jour, pays)
);

COMMENT ON TABLE jours_feries IS 'Jours fériés FR pour correction du calendrier alternance';

-- Jours fériés France 2024-2025 (fixes)
INSERT INTO jours_feries (date_jour, libelle) VALUES
    ('2024-01-01', 'Jour de l''An'),
    ('2024-04-01', 'Lundi de Pâques'),
    ('2024-05-01', 'Fête du Travail'),
    ('2024-05-08', 'Victoire 1945'),
    ('2024-05-09', 'Ascension'),
    ('2024-05-20', 'Lundi de Pentecôte'),
    ('2024-07-14', 'Fête Nationale'),
    ('2024-08-15', 'Assomption'),
    ('2024-11-01', 'Toussaint'),
    ('2024-11-11', 'Armistice'),
    ('2024-12-25', 'Noël'),
    ('2025-01-01', 'Jour de l''An'),
    ('2025-04-21', 'Lundi de Pâques'),
    ('2025-05-01', 'Fête du Travail'),
    ('2025-05-08', 'Victoire 1945'),
    ('2025-05-29', 'Ascension'),
    ('2025-06-09', 'Lundi de Pentecôte'),
    ('2025-07-14', 'Fête Nationale'),
    ('2025-08-15', 'Assomption'),
    ('2025-11-01', 'Toussaint'),
    ('2025-11-11', 'Armistice'),
    ('2025-12-25', 'Noël');
