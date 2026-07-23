-- V29__add_logement_moderation_note.sql
-- Modération des annonces (APP-121).
--
-- Un administrateur peut retirer une annonce de la plateforme. Le statut
-- SUSPENDU existait déjà dans le type logement_statut depuis V2, mais aucun
-- code ne le posait : la valeur était morte. C'est exactement ce qu'il faut
-- ici — les logements SUSPENDU sont déjà exclus de la recherche
-- (LogementService), du matching (MatchingService) et du calendrier de
-- compatibilité (CalendrierService), sans une ligne de code supplémentaire.
--
-- Reste à dire au propriétaire POURQUOI son annonce a été retirée : d'où
-- cette colonne. Elle est aussi la trace de la décision côté modération.
--
-- Nullable et sans valeur par défaut : une annonce jamais modérée n'a pas de
-- note, et le champ se vide quand l'annonce est republiée.

ALTER TABLE logements
    ADD COLUMN IF NOT EXISTS moderation_note TEXT;

COMMENT ON COLUMN logements.moderation_note IS
    'Motif de la suspension par un administrateur (APP-121). NULL si jamais modérée ou republiée.';
