-- V6__add_transactions_cautions.sql
-- Transactions financières et cautions via Stripe Connect

-- ============================================================
-- TYPES ENUM
-- ============================================================

CREATE TYPE transaction_type   AS ENUM ('LOYER', 'COMMISSION', 'REMBOURSEMENT', 'ABONNEMENT');
CREATE TYPE transaction_statut AS ENUM ('EN_ATTENTE', 'SUCCES', 'ECHEC', 'REMBOURSE');
CREATE TYPE caution_statut     AS ENUM ('EN_ATTENTE', 'VERSEE', 'RESTITUEE', 'RETENUE_PARTIELLE', 'RETENUE_TOTALE');

-- ============================================================
-- TABLE: transactions
-- ============================================================

CREATE TABLE transactions (
    id                        UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
    accord_id                 UUID               REFERENCES accords(id),
    payer_id                  UUID               NOT NULL REFERENCES users(id),
    payee_id                  UUID               NOT NULL REFERENCES users(id),
    amount                    DECIMAL(10, 2)     NOT NULL CHECK (amount > 0),
    commission                DECIMAL(10, 2)     NOT NULL DEFAULT 0 CHECK (commission >= 0),
    statut                    transaction_statut NOT NULL DEFAULT 'EN_ATTENTE',
    type                      transaction_type   NOT NULL,
    stripe_payment_intent_id  VARCHAR(100),
    stripe_transfer_id        VARCHAR(100),
    stripe_refund_id          VARCHAR(100),
    failure_reason            TEXT,
    created_at                TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_parties CHECK (payer_id <> payee_id),
    CONSTRAINT chk_transaction_commission CHECK (commission <= amount)
);

COMMENT ON TABLE  transactions IS 'Transactions financières (loyers, commissions) via Stripe Connect';
COMMENT ON COLUMN transactions.commission IS 'Commission plateforme (8% du loyer)';

CREATE INDEX idx_transactions_accord_id  ON transactions(accord_id);
CREATE INDEX idx_transactions_payer_id   ON transactions(payer_id);
CREATE INDEX idx_transactions_payee_id   ON transactions(payee_id);
CREATE INDEX idx_transactions_statut     ON transactions(statut);
CREATE INDEX idx_transactions_created    ON transactions(created_at);

-- ============================================================
-- TABLE: cautions
-- ============================================================

CREATE TABLE cautions (
    id                        UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    accord_id                 UUID           NOT NULL REFERENCES accords(id) ON DELETE CASCADE,
    user_id                   UUID           NOT NULL REFERENCES users(id),
    amount                    DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    statut                    caution_statut NOT NULL DEFAULT 'EN_ATTENTE',
    stripe_payment_intent_id  VARCHAR(100),
    stripe_refund_id          VARCHAR(100),
    returned_amount           DECIMAL(10, 2),
    retention_reason          TEXT,
    returned_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_caution_accord_user UNIQUE (accord_id, user_id)
);

COMMENT ON TABLE cautions IS 'Cautions de garantie bloquées sur Stripe jusqu''à fin d''accord';

CREATE INDEX idx_cautions_accord_id ON cautions(accord_id);
CREATE INDEX idx_cautions_user_id   ON cautions(user_id);
CREATE INDEX idx_cautions_statut    ON cautions(statut);

-- ============================================================
-- TRIGGERS
-- ============================================================

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_cautions_updated_at
    BEFORE UPDATE ON cautions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
