-- V5__add_accords_table.sql
-- Accords de logement et planning semaine par semaine

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE accord_statut AS ENUM (
    'EN_ATTENTE',
    'ACCEPTE',
    'REFUSE',
    'EN_COURS',
    'TERMINE',
    'ANNULE',
    'LITIGE'
);

-- ============================================================
-- TABLE: accords
-- ============================================================

CREATE TABLE accords (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    initiator_id    UUID          NOT NULL REFERENCES users(id),
    receiver_id     UUID          NOT NULL REFERENCES users(id),
    logement_a_id   UUID          REFERENCES logements(id),  -- logement de l'initiateur
    logement_b_id   UUID          REFERENCES logements(id),  -- logement du receiver
    type            accord_type   NOT NULL,
    statut          accord_statut NOT NULL DEFAULT 'EN_ATTENTE',
    date_debut      DATE          NOT NULL,
    date_fin        DATE          NOT NULL,
    montant_loyer   DECIMAL(8, 2),
    conditions      TEXT,
    message_initial TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_accord_dates         CHECK (date_fin > date_debut),
    CONSTRAINT chk_accord_parties       CHECK (initiator_id <> receiver_id),
    CONSTRAINT chk_accord_loyer         CHECK (montant_loyer IS NULL OR montant_loyer >= 0),
    -- Pour un échange, les deux logements sont requis
    CONSTRAINT chk_accord_echange_logements CHECK (
        type NOT IN ('ECHANGE_TOTAL', 'ECHANGE_PARTIEL')
        OR (logement_a_id IS NOT NULL AND logement_b_id IS NOT NULL)
    )
);

COMMENT ON TABLE  accords IS 'Accord de logement entre deux utilisateurs (échange, coloc, location)';
COMMENT ON COLUMN accords.logement_a_id IS 'Logement apporté par l''initiateur (NULL pour location classique)';
COMMENT ON COLUMN accords.logement_b_id IS 'Logement apporté par le receiver (NULL pour location classique)';

CREATE INDEX idx_accords_initiator_id   ON accords(initiator_id);
CREATE INDEX idx_accords_receiver_id    ON accords(receiver_id);
CREATE INDEX idx_accords_statut         ON accords(statut) WHERE statut IN ('EN_ATTENTE', 'EN_COURS');
CREATE INDEX idx_accords_logement_a_id  ON accords(logement_a_id);
CREATE INDEX idx_accords_logement_b_id  ON accords(logement_b_id);
CREATE INDEX idx_accords_dates          ON accords(date_debut, date_fin);

-- ============================================================
-- TABLE: accord_semaines
-- Détail semaine par semaine de l'occupation pour les échanges
-- ============================================================

CREATE TABLE accord_semaines (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    accord_id    UUID NOT NULL REFERENCES accords(id) ON DELETE CASCADE,
    semaine      DATE NOT NULL,      -- lundi de la semaine
    occupant_id  UUID NOT NULL REFERENCES users(id),
    logement_id  UUID NOT NULL REFERENCES logements(id),
    CONSTRAINT uq_accord_semaines_week UNIQUE (accord_id, semaine, logement_id),
    CONSTRAINT chk_accord_semaine_lundi CHECK (EXTRACT(DOW FROM semaine) = 1)
);

CREATE INDEX idx_accord_semaines_accord_id  ON accord_semaines(accord_id);
CREATE INDEX idx_accord_semaines_semaine    ON accord_semaines(semaine);
CREATE INDEX idx_accord_semaines_occupant   ON accord_semaines(occupant_id);

-- ============================================================
-- TRIGGERS
-- ============================================================

CREATE TRIGGER trg_accords_updated_at
    BEFORE UPDATE ON accords
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
