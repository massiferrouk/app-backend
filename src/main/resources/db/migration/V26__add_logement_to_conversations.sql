-- V26__add_logement_to_conversations.sql
-- Une conversation par annonce (APP-119).
--
-- Anomalie de recette : la conversation était retrouvée uniquement par la paire
-- d'utilisateurs. Un propriétaire possédant plusieurs logements n'avait donc
-- qu'un seul fil : contacter sa deuxième annonce rouvrait la discussion de la
-- première. Incohérent avec les candidatures, qui sont bien par annonce
-- (UNIQUE(user_id, logement_id) en V25) — le statut « contacté » devenait faux.
--
-- logement_id NULL = discussion de personne à personne, sans annonce : c'est le
-- cas des mises en relation alternant ↔ alternant issues du matching, où le fil
-- porte bien sur la personne et non sur un logement. Ce comportement reste le bon.
--
-- Les conversations existantes restent en NULL : elles continuent de fonctionner
-- comme avant, aucune donnée n'est perdue.

ALTER TABLE conversations
    ADD COLUMN logement_id UUID REFERENCES logements(id) ON DELETE CASCADE;

COMMENT ON COLUMN conversations.logement_id IS
    'Annonce sur laquelle porte la discussion. NULL = discussion de personne a personne (match alternant).';

CREATE INDEX idx_conversations_logement ON conversations(logement_id);
