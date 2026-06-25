-- Ajout du champ is_reported sur la table reviews
-- Permet de distinguer les avis signalés (en attente de modération) des avis masqués
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS is_reported BOOLEAN NOT NULL DEFAULT false;
