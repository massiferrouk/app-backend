-- APP-122 : suppression des champs "nom de l'école" et "nom de l'entreprise".
-- Ces deux champs étaient saisis à la création du profil alternant mais
-- n'étaient jamais affichés ni utilisés (le matching ne se sert que des deux
-- VILLES, du rythme, des dates et du calendrier). Donnée collectée sans usage :
-- on l'enlève (minimisation des données).
ALTER TABLE alternant_profiles DROP COLUMN IF EXISTS ecole;
ALTER TABLE alternant_profiles DROP COLUMN IF EXISTS entreprise;
