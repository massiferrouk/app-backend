-- V23__add_match_notifications.sql
-- Déduplication des notifications « nouveau match » (APP-98).

-- ============================================================
-- TABLE: match_notifications
-- Une ligne par PAIRE d'utilisateurs déjà notifiée d'un match.
-- La paire est stockée de façon canonique (user_a_id < user_b_id)
-- pour dédupliquer les deux sens en une seule ligne : on ne renotifie
-- jamais la même paire, quel que soit celui qui a déclenché le calcul.
-- ============================================================

CREATE TABLE match_notifications (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_a_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_match_notif_order CHECK (user_a_id < user_b_id),
    CONSTRAINT uq_match_notif_pair   UNIQUE (user_a_id, user_b_id)
);

COMMENT ON TABLE match_notifications IS 'Paires d''utilisateurs déjà notifiées d''un nouveau match (anti-spam).';

CREATE INDEX idx_match_notif_user_a ON match_notifications(user_a_id);
CREATE INDEX idx_match_notif_user_b ON match_notifications(user_b_id);
