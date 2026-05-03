-- V1__init_schema.sql
-- Schéma initial : utilisateurs, profils alternants, tokens

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE user_role AS ENUM ('ALTERNANT', 'ETUDIANT', 'PROPRIETAIRE', 'ADMIN');
CREATE TYPE rythme_alternance AS ENUM (
    'SEMAINE_1_1',   -- 1 semaine école / 1 semaine entreprise
    'SEMAINE_3_1',   -- 3 semaines entreprise / 1 semaine école
    'MOIS_1_1',      -- 1 mois école / 1 mois entreprise
    'AUTRE'
);

-- ============================================================
-- TABLE: users
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            user_role    NOT NULL DEFAULT 'ETUDIANT',
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    avatar_key      VARCHAR(500),
    is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    fcm_token       VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email)
);

COMMENT ON TABLE  users IS 'Table principale des utilisateurs de la plateforme';
COMMENT ON COLUMN users.role IS 'Rôle principal : ALTERNANT, ETUDIANT, PROPRIETAIRE ou ADMIN';
COMMENT ON COLUMN users.avatar_key IS 'Clé MinIO du fichier avatar';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete RGPD — non NULL = compte supprimé';

-- ============================================================
-- TABLE: refresh_tokens
-- ============================================================

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id  ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires  ON refresh_tokens(expires_at) WHERE is_revoked = FALSE;

-- ============================================================
-- TABLE: alternant_profiles
-- ============================================================

CREATE TABLE alternant_profiles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ville_a     VARCHAR(100)        NOT NULL,
    ville_b     VARCHAR(100)        NOT NULL,
    ecole       VARCHAR(200),
    entreprise  VARCHAR(200),
    date_debut  DATE                NOT NULL,
    date_fin    DATE                NOT NULL,
    rythme      rythme_alternance   NOT NULL DEFAULT 'SEMAINE_1_1',
    created_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alternant_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_alternant_dates CHECK (date_fin > date_debut),
    CONSTRAINT chk_alternant_villes CHECK (ville_a <> ville_b)
);

CREATE INDEX idx_alternant_profiles_user_id ON alternant_profiles(user_id);

-- ============================================================
-- TRIGGER: updated_at automatique
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_alternant_profiles_updated_at
    BEFORE UPDATE ON alternant_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
