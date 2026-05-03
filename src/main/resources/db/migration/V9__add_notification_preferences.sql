-- V9__add_notification_preferences.sql
-- Notifications push/email et préférences utilisateurs

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE notification_type AS ENUM (
    'NOUVEAU_MATCH',
    'DEMANDE_ACCORD',
    'ACCORD_ACCEPTE',
    'ACCORD_REFUSE',
    'NOUVEAU_MESSAGE',
    'PAIEMENT_RECU',
    'PAIEMENT_ECHOUE',
    'CAUTION_RESTITUEE',
    'AVIS_RECU',
    'DOCUMENT_VALIDE',
    'DOCUMENT_REFUSE',
    'RAPPEL_DEPART',
    'RAPPEL_ARRIVEE',
    'SYSTEME'
);

-- ============================================================
-- TABLE: notifications
-- ============================================================

CREATE TABLE notifications (
    id          UUID              PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        notification_type NOT NULL,
    title       VARCHAR(200)      NOT NULL,
    body        TEXT              NOT NULL,
    is_read     BOOLEAN           NOT NULL DEFAULT FALSE,
    deep_link   VARCHAR(500),
    payload     JSONB,
    created_at  TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN notifications.deep_link IS 'Route Flutter : accord/123, logement/456, messages/789';
COMMENT ON COLUMN notifications.payload   IS 'Données contextuelles JSON pour le deep link';

CREATE INDEX idx_notifications_user_id   ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread    ON notifications(user_id) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_type      ON notifications(type);

-- ============================================================
-- TABLE: notification_preferences
-- ============================================================

CREATE TYPE notification_channel AS ENUM ('PUSH', 'EMAIL', 'SMS');

CREATE TABLE notification_preferences (
    id               UUID                 PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID                 NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type notification_type   NOT NULL,
    channel          notification_channel NOT NULL,
    is_enabled       BOOLEAN              NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notif_pref UNIQUE (user_id, notification_type, channel)
);

CREATE INDEX idx_notif_prefs_user_id ON notification_preferences(user_id);

-- Seed : préférences par défaut pour les types critiques
-- (appelé via application Bootstrap, pas directement ici)
COMMENT ON TABLE notification_preferences IS
    'Préférences de notification par utilisateur, type et canal. Initialisées au signup.';

-- ============================================================
-- TABLE: verification_docs
-- Documents d'identité uploadés sur MinIO (AES-256)
-- ============================================================

CREATE TYPE doc_type    AS ENUM ('CARTE_ETUDIANT', 'CONTRAT_ALTERNANCE', 'PIECE_IDENTITE', 'JUSTIFICATIF_DOMICILE');
CREATE TYPE doc_statut  AS ENUM ('EN_ATTENTE', 'VALIDE', 'REFUSE');

CREATE TABLE verification_docs (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        doc_type    NOT NULL,
    file_key    VARCHAR(500) NOT NULL,   -- Clé MinIO (chiffré AES-256)
    statut      doc_statut  NOT NULL DEFAULT 'EN_ATTENTE',
    reviewed_by UUID        REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_verification_docs UNIQUE (user_id, type),
    CONSTRAINT chk_doc_review CHECK (
        statut = 'EN_ATTENTE'
        OR (statut IN ('VALIDE', 'REFUSE') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

COMMENT ON COLUMN verification_docs.file_key IS 'Clé objet MinIO. Fichier chiffré AES-256 côté serveur.';

CREATE INDEX idx_verification_docs_user_id ON verification_docs(user_id);
CREATE INDEX idx_verification_docs_statut  ON verification_docs(statut) WHERE statut = 'EN_ATTENTE';
