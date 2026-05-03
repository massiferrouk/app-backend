-- V8__add_reviews_messaging.sql
-- Messagerie WebSocket STOMP + avis/notation

-- ============================================================
-- TABLE: conversations
-- ============================================================

CREATE TABLE conversations (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    accord_id       UUID        REFERENCES accords(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

CREATE INDEX idx_conversations_accord_id    ON conversations(accord_id);
CREATE INDEX idx_conversations_last_message ON conversations(last_message_at DESC NULLS LAST);

-- ============================================================
-- TABLE: conversation_participants
-- ============================================================

CREATE TABLE conversation_participants (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conv_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conv_participants_conv_id   ON conversation_participants(conversation_id);
CREATE INDEX idx_conv_participants_user_id   ON conversation_participants(user_id);

-- ============================================================
-- TABLE: messages
-- ============================================================

CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID        NOT NULL REFERENCES users(id),
    content         TEXT        NOT NULL,
    is_read         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_message_content CHECK (LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_messages_conversation_id   ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender_id         ON messages(sender_id);
CREATE INDEX idx_messages_unread            ON messages(conversation_id) WHERE is_read = FALSE;

-- Trigger : mettre à jour last_message_at sur chaque nouveau message
CREATE OR REPLACE FUNCTION update_conversation_last_message()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE conversations
    SET last_message_at = NEW.created_at
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_update_conversation
    AFTER INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION update_conversation_last_message();

-- ============================================================
-- TABLE: reviews
-- ============================================================

CREATE TYPE review_target_type AS ENUM ('USER', 'LOGEMENT');

CREATE TABLE reviews (
    id              UUID             PRIMARY KEY DEFAULT uuid_generate_v4(),
    author_id       UUID             NOT NULL REFERENCES users(id),
    target_user_id  UUID             REFERENCES users(id),
    target_logement_id UUID          REFERENCES logements(id),
    accord_id       UUID             NOT NULL REFERENCES accords(id),
    target_type     review_target_type NOT NULL,
    rating          SMALLINT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    is_moderated    BOOLEAN          NOT NULL DEFAULT FALSE,
    moderation_note TEXT,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    -- Un auteur peut laisser un seul avis par accord par cible
    CONSTRAINT uq_review_author_accord_user     UNIQUE (author_id, accord_id, target_user_id),
    CONSTRAINT uq_review_author_accord_logement UNIQUE (author_id, accord_id, target_logement_id),
    CONSTRAINT chk_review_target CHECK (
        (target_type = 'USER'     AND target_user_id IS NOT NULL     AND target_logement_id IS NULL)
        OR
        (target_type = 'LOGEMENT' AND target_logement_id IS NOT NULL AND target_user_id IS NULL)
    )
);

CREATE INDEX idx_reviews_author_id        ON reviews(author_id);
CREATE INDEX idx_reviews_target_user_id   ON reviews(target_user_id);
CREATE INDEX idx_reviews_target_logement  ON reviews(target_logement_id);
CREATE INDEX idx_reviews_accord_id        ON reviews(accord_id);
CREATE INDEX idx_reviews_not_moderated    ON reviews(created_at) WHERE is_moderated = FALSE;
