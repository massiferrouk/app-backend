-- Champ de masquage sur les messages existants
ALTER TABLE messages ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS moderation_note TEXT;

-- Signalements de messages (un utilisateur signale un message avec un motif)
CREATE TABLE IF NOT EXISTS message_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    reporter_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    motif         TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Un utilisateur ne peut signaler le même message qu'une seule fois
    CONSTRAINT uq_message_report UNIQUE (message_id, reporter_id)
);

-- Mots interdits configurables par l'admin (sans redéploiement)
CREATE TABLE IF NOT EXISTS mots_interdits (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mot        VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index pour accélérer la récupération des messages signalés non traités
CREATE INDEX IF NOT EXISTS idx_message_reports_message_id ON message_reports(message_id);
