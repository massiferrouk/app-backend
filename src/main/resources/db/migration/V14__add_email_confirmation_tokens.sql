-- V14__add_email_confirmation_tokens.sql
-- Tokens de confirmation d'email : validité 24h, usage unique

CREATE TABLE email_confirmation_tokens (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_email_confirmation_token UNIQUE (token)
);

CREATE INDEX idx_email_confirmation_tokens_user_id ON email_confirmation_tokens(user_id);
CREATE INDEX idx_email_confirmation_tokens_token   ON email_confirmation_tokens(token);
