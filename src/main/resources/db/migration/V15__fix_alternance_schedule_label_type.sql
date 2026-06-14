-- Conversion de CHAR(1) vers VARCHAR(1) pour la colonne label de alternance_schedules.
-- CHAR(1) (bpchar) n'est pas reconnu par Hibernate 6 comme VARCHAR — cela provoque
-- une erreur de validation de schéma au démarrage. VARCHAR(1) est le type natif
-- que Hibernate mappe pour un champ String avec length=1.
ALTER TABLE alternance_schedules
    ALTER COLUMN label TYPE VARCHAR(1);
