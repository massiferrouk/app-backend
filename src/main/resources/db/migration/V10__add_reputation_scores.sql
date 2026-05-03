-- V10__add_reputation_scores.sql
-- Scores de réputation calculés + vue matérialisée

-- ============================================================
-- TABLE: reputation_scores
-- Score agrégé par utilisateur (recalculé après chaque avis)
-- ============================================================

CREATE TABLE reputation_scores (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    avg_rating      DECIMAL(3, 2) NOT NULL DEFAULT 0.00 CHECK (avg_rating BETWEEN 0 AND 5),
    total_reviews   INTEGER       NOT NULL DEFAULT 0 CHECK (total_reviews >= 0),
    logement_score  DECIMAL(3, 2) NOT NULL DEFAULT 0.00 CHECK (logement_score BETWEEN 0 AND 5),
    nb_accords      INTEGER       NOT NULL DEFAULT 0 CHECK (nb_accords >= 0),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reputation_user UNIQUE (user_id)
);

COMMENT ON TABLE reputation_scores IS 'Score de réputation agrégé. Mis à jour via Spring @EventListener(ReviewCreatedEvent)';

CREATE INDEX idx_reputation_scores_user_id    ON reputation_scores(user_id);
CREATE INDEX idx_reputation_scores_avg_rating ON reputation_scores(avg_rating DESC);

-- ============================================================
-- TABLE: reputation_history
-- Historique des variations pour audit
-- ============================================================

CREATE TABLE reputation_history (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    review_id       UUID          NOT NULL REFERENCES reviews(id),
    avg_rating_prev DECIMAL(3, 2) NOT NULL,
    avg_rating_new  DECIMAL(3, 2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reputation_history_user_id ON reputation_history(user_id);

-- ============================================================
-- VUE MATÉRIALISÉE : tableau de bord admin
-- Rafraîchie par @Scheduled(cron = "0 0 3 * * *") Spring Boot
-- ============================================================

CREATE MATERIALIZED VIEW mv_dashboard_stats AS
SELECT
    DATE_TRUNC('day', created_at)          AS jour,
    COUNT(*)                                AS nb_inscriptions,
    COUNT(*) FILTER (WHERE role = 'ALTERNANT')    AS nb_alternants,
    COUNT(*) FILTER (WHERE role = 'ETUDIANT')     AS nb_etudiants,
    COUNT(*) FILTER (WHERE role = 'PROPRIETAIRE') AS nb_proprietaires
FROM users
WHERE deleted_at IS NULL
GROUP BY DATE_TRUNC('day', created_at)
ORDER BY jour DESC;

CREATE UNIQUE INDEX mv_dashboard_stats_jour ON mv_dashboard_stats(jour);

-- Vue des accords par statut (pour monitoring)
CREATE MATERIALIZED VIEW mv_accords_stats AS
SELECT
    statut,
    type,
    COUNT(*)       AS nb_accords,
    AVG(EXTRACT(EPOCH FROM (date_fin::timestamp - date_debut::timestamp)) / 86400) AS duree_moyenne_jours
FROM accords
GROUP BY statut, type;

CREATE UNIQUE INDEX mv_accords_stats_idx ON mv_accords_stats(statut, type);

-- Trigger pour mise à jour du score de réputation après un avis
CREATE OR REPLACE FUNCTION update_reputation_score()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id  UUID;
    v_new_avg  DECIMAL(3,2);
    v_total    INTEGER;
    v_prev_avg DECIMAL(3,2);
BEGIN
    -- Détermine l'utilisateur cible
    v_user_id := COALESCE(NEW.target_user_id, NULL);
    IF v_user_id IS NULL THEN RETURN NEW; END IF;

    -- Calcule le nouveau score
    SELECT
        ROUND(AVG(rating)::numeric, 2),
        COUNT(*)
    INTO v_new_avg, v_total
    FROM reviews
    WHERE target_user_id = v_user_id AND is_moderated = FALSE;

    -- Récupère l'ancien score pour l'historique
    SELECT avg_rating INTO v_prev_avg
    FROM reputation_scores WHERE user_id = v_user_id;

    -- Upsert du score
    INSERT INTO reputation_scores (user_id, avg_rating, total_reviews, updated_at)
    VALUES (v_user_id, COALESCE(v_new_avg, 0), COALESCE(v_total, 0), NOW())
    ON CONFLICT (user_id)
    DO UPDATE SET
        avg_rating    = EXCLUDED.avg_rating,
        total_reviews = EXCLUDED.total_reviews,
        updated_at    = NOW();

    -- Historique
    IF v_prev_avg IS NOT NULL THEN
        INSERT INTO reputation_history (user_id, review_id, avg_rating_prev, avg_rating_new)
        VALUES (v_user_id, NEW.id, v_prev_avg, COALESCE(v_new_avg, 0));
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_update_reputation
    AFTER INSERT ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_reputation_score();
