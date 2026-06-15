-- V17__fix_jours_feries_char_columns.sql
-- Hibernate 6 exige VARCHAR pour les colonnes mappées en String Java.
-- PostgreSQL CHAR(n) = bpchar, incompatible avec varchar(n) attendu par Hibernate.
-- Même correction que V15 pour alternance_schedules.label.

ALTER TABLE jours_feries ALTER COLUMN pays TYPE VARCHAR(2);
ALTER TABLE jours_feries ALTER COLUMN region TYPE VARCHAR(50);
