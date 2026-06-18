CREATE TYPE ville_associee AS ENUM ('VILLE_A', 'VILLE_B');

ALTER TABLE logements
    ADD COLUMN ville_associee ville_associee NULL;

-- Un alternant ne peut avoir qu'un logement par ville associée
-- La contrainte est partielle : elle ne s'applique que si ville_associee est renseignée
CREATE UNIQUE INDEX uq_logement_owner_ville
    ON logements (owner_id, ville_associee)
    WHERE ville_associee IS NOT NULL;
