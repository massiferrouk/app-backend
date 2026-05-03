-- V4__add_matching_cache_table.sql
-- Cache des résultats de l'algorithme de matching (Redis + PostgreSQL)

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE accord_type AS ENUM (
    'ECHANGE_TOTAL',
    'ECHANGE_PARTIEL',
    'COLOCATION_TOURNANTE',
    'LOCATION_CLASSIQUE'
);

-- ============================================================
-- TABLE: matching_cache
-- Le cache primaire est Redis (TTL 1h). Cette table est un
-- cache secondaire persistant pour audit et analytics.
-- ============================================================

CREATE TABLE matching_cache (
    id                   UUID       PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_a_id         UUID       NOT NULL REFERENCES alternant_profiles(id) ON DELETE CASCADE,
    profile_b_id         UUID       NOT NULL REFERENCES alternant_profiles(id) ON DELETE CASCADE,
    score                DECIMAL(5, 4)  NOT NULL CHECK (score BETWEEN 0 AND 1),
    type_accord          accord_type    NOT NULL,
    semaines_compatibles JSONB      NOT NULL DEFAULT '[]',
    nb_semaines          SMALLINT   NOT NULL DEFAULT 0,
    expires_at           TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Symétrie : on ne stocke pas (B,A) si (A,B) existe déjà
    CONSTRAINT uq_matching_cache_pair  UNIQUE (profile_a_id, profile_b_id),
    -- On enforce que profile_a < profile_b pour garantir l'unicité symétrique
    CONSTRAINT chk_matching_order CHECK (profile_a_id < profile_b_id)
);

COMMENT ON TABLE  matching_cache IS 'Cache PostgreSQL des scores de compatibilité. Cache primaire = Redis.';
COMMENT ON COLUMN matching_cache.semaines_compatibles IS
    'JSON array de { "semaine": "2024-09-02", "occupant_a": "B", "occupant_b": "A" }';
COMMENT ON COLUMN matching_cache.score IS 'Score de compatibilité normalisé entre 0.0 et 1.0';

CREATE INDEX idx_matching_cache_profile_a  ON matching_cache(profile_a_id);
CREATE INDEX idx_matching_cache_profile_b  ON matching_cache(profile_b_id);
CREATE INDEX idx_matching_cache_expires    ON matching_cache(expires_at);
CREATE INDEX idx_matching_cache_score      ON matching_cache(score DESC);

-- Nettoyage automatique des entrées expirées (appelé par job Spring @Scheduled)
CREATE INDEX idx_matching_cache_cleanup    ON matching_cache(expires_at);
