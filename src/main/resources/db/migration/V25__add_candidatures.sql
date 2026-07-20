-- V25__add_candidatures.sql
-- Suivi personnel des candidatures logement d'un étudiant (APP-117).
--
-- Besoin métier : quand on cherche un logement, on postule à beaucoup d'annonces
-- et on oublie lesquelles. Les étudiants gèrent ça avec un Trello à côté de
-- l'app. Cette table intègre ce suivi : une ligne = une annonce suivie par un
-- utilisateur, avec un statut qu'il fait évoluer À LA MAIN (décision produit :
-- on ne devine jamais l'intention de l'utilisateur).

CREATE TYPE candidature_statut AS ENUM (
    'A_CONTACTER',
    'CONTACTE',
    'VISITE_PREVUE',
    'VISITEE',
    'SANS_SUITE',
    'ACCEPTEE'
);

CREATE TABLE candidatures (
    id          UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID               NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    logement_id UUID               NOT NULL REFERENCES logements(id) ON DELETE CASCADE,
    statut      candidature_statut NOT NULL DEFAULT 'A_CONTACTER',
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    -- Une seule candidature par (utilisateur, annonce) : suivre deux fois la
    -- même annonce n'a pas de sens, et ça rend l'ajout idempotent.
    CONSTRAINT uq_candidature_user_logement UNIQUE (user_id, logement_id)
);

COMMENT ON TABLE candidatures IS
    'Suivi personnel des annonces auxquelles un utilisateur a postulé (remplace le Trello).';
COMMENT ON COLUMN candidatures.statut IS
    'Statut géré manuellement par l''utilisateur, jamais déduit automatiquement.';

CREATE INDEX idx_candidatures_user     ON candidatures(user_id);
CREATE INDEX idx_candidatures_logement ON candidatures(logement_id);

CREATE TRIGGER trg_candidatures_updated_at
    BEFORE UPDATE ON candidatures
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
