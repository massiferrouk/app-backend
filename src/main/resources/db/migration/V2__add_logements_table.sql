-- V2__add_logements_table.sql
-- Logements, photos et disponibilités

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE logement_type   AS ENUM ('STUDIO', 'T1', 'T2', 'T3_PLUS', 'CHAMBRE_COLOC');
CREATE TYPE logement_statut AS ENUM ('BROUILLON', 'ACTIF', 'SUSPENDU', 'ARCHIVE');

-- ============================================================
-- TABLE: logements
-- ============================================================

CREATE TABLE logements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    adresse         VARCHAR(500)     NOT NULL,
    ville           VARCHAR(100)     NOT NULL,
    code_postal     VARCHAR(10)      NOT NULL,
    lat             DECIMAL(9, 6),
    lng             DECIMAL(9, 6),
    type            logement_type    NOT NULL,
    surface         DECIMAL(6, 2),
    nb_pieces       SMALLINT         NOT NULL DEFAULT 1,
    loyer           DECIMAL(8, 2),
    charges         DECIMAL(8, 2)    DEFAULT 0,
    description     TEXT,
    equipements     TEXT[],
    statut          logement_statut  NOT NULL DEFAULT 'BROUILLON',
    is_verified     BOOLEAN          NOT NULL DEFAULT FALSE,
    is_meuble       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_logement_loyer   CHECK (loyer IS NULL OR loyer >= 0),
    CONSTRAINT chk_logement_surface CHECK (surface IS NULL OR surface > 0)
);

COMMENT ON TABLE  logements IS 'Annonces de logements proposés sur la plateforme';
COMMENT ON COLUMN logements.equipements IS 'Array PostgreSQL : wifi, parking, lave-linge, etc.';
COMMENT ON COLUMN logements.is_verified IS 'Modération manuelle validée par un admin';

CREATE INDEX idx_logements_owner_id ON logements(owner_id);
CREATE INDEX idx_logements_statut   ON logements(statut) WHERE statut = 'ACTIF';
CREATE INDEX idx_logements_ville    ON logements(ville) WHERE statut = 'ACTIF';
CREATE INDEX idx_logements_coords   ON logements(lat, lng) WHERE lat IS NOT NULL;

-- ============================================================
-- TABLE: photos_logements
-- ============================================================

CREATE TABLE photos_logements (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logement_id UUID         NOT NULL REFERENCES logements(id) ON DELETE CASCADE,
    file_key    VARCHAR(500) NOT NULL,
    ordre       SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_photos_logements_key UNIQUE (file_key)
);

CREATE INDEX idx_photos_logement_id ON photos_logements(logement_id);

-- ============================================================
-- TABLE: disponibilites
-- ============================================================

CREATE TYPE disponibilite_type AS ENUM ('LIBRE', 'OCCUPE', 'BLOQUE');

CREATE TABLE disponibilites (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logement_id UUID                 NOT NULL REFERENCES logements(id) ON DELETE CASCADE,
    date_debut  DATE                 NOT NULL,
    date_fin    DATE                 NOT NULL,
    type        disponibilite_type   NOT NULL DEFAULT 'LIBRE',
    CONSTRAINT chk_dispo_dates CHECK (date_fin >= date_debut)
);

CREATE INDEX idx_disponibilites_logement_id ON disponibilites(logement_id);
CREATE INDEX idx_disponibilites_dates       ON disponibilites(date_debut, date_fin);

-- ============================================================
-- TRIGGERS updated_at
-- ============================================================

CREATE TRIGGER trg_logements_updated_at
    BEFORE UPDATE ON logements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
