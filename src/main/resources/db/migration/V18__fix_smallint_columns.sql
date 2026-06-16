-- Hibernate 6 attend INTEGER pour les champs Java Integer, mais la V2 utilisait SMALLINT.
-- On corrige les deux colonnes concernées.
ALTER TABLE logements ALTER COLUMN nb_pieces TYPE INTEGER;
ALTER TABLE photos_logements ALTER COLUMN ordre TYPE INTEGER;
