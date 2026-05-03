-- V11__add_ical_export_tokens.sql
-- Tokens iCal pour export calendrier alternance (Apple Calendar, Google Calendar)

-- ============================================================
-- TABLE: ical_tokens
-- Token opaque, signé côté serveur (HMAC-SHA256 + user_id)
-- URL publique : GET /api/v1/calendar/{token}/alternance.ics
-- ============================================================

CREATE TABLE ical_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(128) NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMPTZ,             -- NULL = pas d'expiration
    last_used   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ical_tokens_token UNIQUE (token),
    CONSTRAINT uq_ical_tokens_user  UNIQUE (user_id)   -- 1 token par utilisateur
);

COMMENT ON TABLE  ical_tokens IS 'Tokens d''accès au flux iCal privé. Régénérable à tout moment.';
COMMENT ON COLUMN ical_tokens.token IS 'Token opaque 64 chars hex. Jamais en clair dans les logs.';
COMMENT ON COLUMN ical_tokens.expires_at IS 'NULL = token permanent jusqu''à révocation manuelle';

CREATE INDEX idx_ical_tokens_user_id   ON ical_tokens(user_id);
CREATE INDEX idx_ical_tokens_token_val ON ical_tokens(token) WHERE is_active = TRUE;
