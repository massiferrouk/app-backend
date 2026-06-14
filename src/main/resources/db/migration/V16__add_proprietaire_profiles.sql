-- V16__add_proprietaire_profiles.sql
-- Table des profils propriétaires

CREATE TABLE proprietaire_profiles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone           VARCHAR(20)     NOT NULL,
    adresse         VARCHAR(255)    NOT NULL,
    ville           VARCHAR(100)    NOT NULL,
    code_postal     VARCHAR(10)     NOT NULL,
    siret           VARCHAR(14),
    is_verified     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_proprietaire_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_siret_length CHECK (siret IS NULL OR LENGTH(siret) = 14)
);

CREATE INDEX idx_proprietaire_profiles_user_id ON proprietaire_profiles(user_id);

CREATE TRIGGER trg_proprietaire_profiles_updated_at
    BEFORE UPDATE ON proprietaire_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE proprietaire_profiles IS 'Profils des propriétaires de logements';
COMMENT ON COLUMN proprietaire_profiles.siret IS 'SIRET optionnel — 14 chiffres exactement si renseigné';
COMMENT ON COLUMN proprietaire_profiles.is_verified IS 'True après upload justificatif propriété validé par admin';
