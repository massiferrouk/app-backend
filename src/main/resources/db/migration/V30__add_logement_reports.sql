-- V30__add_logement_reports.sql
-- Signalement d'annonces par les utilisateurs (APP-121).
--
-- Sans ce mécanisme, un administrateur ne pouvait repérer une annonce
-- frauduleuse qu'en parcourant la liste complète à la main — ce qui ne passe
-- pas l'échelle et n'arrive jamais en pratique.
--
-- Structure calquée sur message_reports (V21) : même contrainte d'unicité,
-- pour qu'un même utilisateur ne puisse pas signaler deux fois la même annonce
-- et gonfler artificiellement la file de modération.

CREATE TABLE IF NOT EXISTS logement_reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    logement_id  UUID NOT NULL REFERENCES logements(id) ON DELETE CASCADE,
    reporter_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    motif        TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_logement_report UNIQUE (logement_id, reporter_id)
);

-- La file de modération lit les signalements par annonce : l'index évite un
-- parcours complet de la table à chaque ouverture de l'écran.
CREATE INDEX IF NOT EXISTS idx_logement_reports_logement_id
    ON logement_reports(logement_id);
