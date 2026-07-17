-- APP-110 : rythme 2 semaines / 2 semaines
-- IF NOT EXISTS rend la migration rejouable sur une base déjà à jour
ALTER TYPE rythme_alternance ADD VALUE IF NOT EXISTS 'SEMAINE_2_2';

-- APP-110 : ordre de départ du cycle d'alternance
-- Sans ce champ, le générateur code l'ordre en dur : les rythmes inversés
-- (ex. 3 semaines entreprise PUIS 1 école vs 1 école PUIS 3 entreprise)
-- produisent le même calendrier, faux pour l'un des deux.
CREATE TYPE premiere_semaine AS ENUM ('ECOLE', 'ENTREPRISE');

ALTER TABLE alternant_profiles
    ADD COLUMN premiere_semaine premiere_semaine;

-- Backfill aligné sur l'ancien comportement codé en dur du générateur,
-- pour que les calendriers existants restent identiques :
-- SEMAINE_3_1 commençait par l'entreprise, tous les autres par l'école.
UPDATE alternant_profiles
SET premiere_semaine = CASE
    WHEN rythme = 'SEMAINE_3_1' THEN 'ENTREPRISE'::premiere_semaine
    ELSE 'ECOLE'::premiere_semaine
END;

ALTER TABLE alternant_profiles
    ALTER COLUMN premiere_semaine SET NOT NULL;
