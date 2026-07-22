-- V28__backfill_ville_associee.sql
-- Rattrapage de la ville associée des logements déjà publiés (APP-120).
--
-- Jusqu'ici, ville_associee n'était renseignée QUE par l'action manuelle
-- « Associer à une ville ». Or le matching filtre sur ce champ
-- (MatchingService.findLogementPublieAssocie) : tout alternant ayant publié
-- son logement sans cliquer sur ce bouton restait invisible au matching, et
-- l'app continuait de lui répondre « publie ton logement ».
--
-- La valeur est entièrement déductible : on compare la ville du logement aux
-- deux villes du profil de son propriétaire — c'est exactement le calcul que
-- le backend faisait déjà pour VALIDER le choix de l'utilisateur.
--
-- Restent volontairement à NULL :
-- - les logements des propriétaires (aucun profil alternant, donc aucune ville
--   de référence) ;
-- - les logements situés hors des deux villes du profil : ils n'entrent pas
--   dans le matching, et l'app l'indique désormais sur leur fiche.

UPDATE logements l
SET ville_associee = CASE
        WHEN lower(l.ville) = lower(p.ville_a) THEN 'VILLE_A'
        ELSE 'VILLE_B'
    END::ville_associee
FROM alternant_profiles p
WHERE p.user_id = l.owner_id
  AND l.ville_associee IS NULL
  AND l.statut <> 'ARCHIVE'
  AND (lower(l.ville) = lower(p.ville_a) OR lower(l.ville) = lower(p.ville_b))
  -- Un seul logement par ville : on ne rattrape que s'il n'y a pas déjà
  -- un logement de ce propriétaire associé à la même ville.
  AND NOT EXISTS (
      SELECT 1 FROM logements autre
      WHERE autre.owner_id = l.owner_id
        AND autre.id <> l.id
        AND autre.statut <> 'ARCHIVE'
        AND autre.ville_associee = CASE
                WHEN lower(l.ville) = lower(p.ville_a) THEN 'VILLE_A'
                ELSE 'VILLE_B'
            END::ville_associee
  );
