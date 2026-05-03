-- V7__add_stripe_idempotency.sql
-- Idempotence des webhooks Stripe : évite le traitement double

-- ============================================================
-- TABLE: stripe_webhook_events
-- Reçu et traité une seule fois grâce à l'UNIQUE sur stripe_event_id
-- ============================================================

CREATE TABLE stripe_webhook_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stripe_event_id VARCHAR(100)  NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSONB         NOT NULL,
    processed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stripe_event_id UNIQUE (stripe_event_id)
);

COMMENT ON TABLE stripe_webhook_events IS
    'Log des webhooks Stripe reçus. L''UNIQUE sur stripe_event_id garantit l''idempotence.';

CREATE INDEX idx_stripe_events_type       ON stripe_webhook_events(event_type);
CREATE INDEX idx_stripe_events_processed  ON stripe_webhook_events(processed_at);

-- ============================================================
-- Ajout contrainte unique sur stripe_payment_intent_id
-- pour les transactions (double protection)
-- ============================================================

ALTER TABLE transactions
    ADD CONSTRAINT uq_transactions_stripe_pi
        UNIQUE (stripe_payment_intent_id)
    DEFERRABLE INITIALLY DEFERRED;

-- Note : DEFERRABLE permet l'insert puis update dans la même transaction
-- sans violer la contrainte avant le COMMIT.
