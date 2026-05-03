-- V13__add_hebergement_ponctuel.sql
-- Nouveau mécanisme : hébergement ponctuel entre un locataire/colocataire
-- et un alternant ayant des besoins ponctuels et récurrents dans une ville.
--
-- Contexte légal : la plateforme exige un accord écrit du propriétaire
-- (uploadé dans verification_docs) avant activation de l'offre.
-- La responsabilité légale repose sur l'utilisateur, pas sur la plateforme.

-- ============================================================
-- 1. Nouveau type d'accord
-- ============================================================

-- PostgreSQL ne permet pas de modifier un enum dans une transaction.
-- On utilise ALTER TYPE ... ADD VALUE qui est idempotent depuis PG 9.1.
ALTER TYPE accord_type ADD VALUE IF NOT EXISTS 'HEBERGEMENT_PONCTUEL';

-- ============================================================
-- 2. Évolution de la table logements
-- ============================================================

-- Indique que ce logement propose une chambre partagée ponctuellement.
-- Uniquement activable par un ETUDIANT (pas un PROPRIETAIRE).
ALTER TABLE logements
    ADD COLUMN IF NOT EXISTS is_chambre_partagee BOOLEAN NOT NULL DEFAULT FALSE;

-- Clé MinIO du document d'accord propriétaire uploadé par le locataire.
-- Obligatoire si is_chambre_partagee = TRUE (contrôlé côté service Java).
ALTER TABLE logements
    ADD COLUMN IF NOT EXISTS accord_proprio_key VARCHAR(500);

-- Prix par semaine pour l'hébergement ponctuel (différent du loyer mensuel).
ALTER TABLE logements
    ADD COLUMN IF NOT EXISTS prix_semaine DECIMAL(8, 2);

-- Contrainte : si chambre partagée activée, prix_semaine doit être renseigné.
ALTER TABLE logements
    ADD CONSTRAINT chk_chambre_partagee_prix CHECK (
        is_chambre_partagee = FALSE
        OR (is_chambre_partagee = TRUE AND prix_semaine IS NOT NULL AND prix_semaine > 0)
    );

COMMENT ON COLUMN logements.is_chambre_partagee IS
    'TRUE = locataire propose une chambre ponctuellement. Accord proprio obligatoire.';
COMMENT ON COLUMN logements.accord_proprio_key IS
    'Clé MinIO du document d''accord écrit du propriétaire. Requis si is_chambre_partagee = TRUE.';
COMMENT ON COLUMN logements.prix_semaine IS
    'Participation aux frais par semaine pour l''hébergement ponctuel (ex: 100€).';

-- ============================================================
-- 3. Nouveau type de document de vérification
-- ============================================================

-- Ajout de ACCORD_PROPRIETAIRE dans l'enum doc_type existant.
ALTER TYPE doc_type ADD VALUE IF NOT EXISTS 'ACCORD_PROPRIETAIRE';

-- ============================================================
-- 4. Table de disponibilités ponctuelles
-- ============================================================

-- L'étudiant déclare les semaines où sa chambre est disponible.
-- Distinct de la table disponibilites (qui gère les logements entiers).
CREATE TABLE IF NOT EXISTS disponibilites_ponctuelles (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    logement_id UUID        NOT NULL REFERENCES logements(id) ON DELETE CASCADE,
    semaine     DATE        NOT NULL,   -- lundi de la semaine
    is_disponible BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dispo_ponctuelle UNIQUE (logement_id, semaine),
    CONSTRAINT chk_dispo_ponctuelle_lundi
        CHECK (EXTRACT(DOW FROM semaine) = 1)
);

COMMENT ON TABLE disponibilites_ponctuelles IS
    'Semaines disponibles pour l''hébergement ponctuel. Une ligne par semaine par logement.';

CREATE INDEX idx_dispo_ponctuelles_logement_id
    ON disponibilites_ponctuelles(logement_id);
CREATE INDEX idx_dispo_ponctuelles_semaine
    ON disponibilites_ponctuelles(semaine)
    WHERE is_disponible = TRUE;

-- ============================================================
-- 5. Contrainte métier sur les accords HEBERGEMENT_PONCTUEL
-- ============================================================

-- Pour ce type d'accord :
-- - logement_b_id = logement de l'étudiant classique (celui qui héberge)
-- - logement_a_id = NULL (l'alternant n'apporte pas de logement dans cette ville)
-- - montant_loyer = participation aux frais par semaine
--
-- La contrainte existante chk_accord_echange_logements en V5 est déjà
-- compatible : elle ne s'applique qu'aux types ECHANGE_TOTAL et ECHANGE_PARTIEL.
-- Aucune modification nécessaire sur la table accords.

-- On ajoute une contrainte spécifique pour HEBERGEMENT_PONCTUEL :
-- logement_b_id obligatoire, montant_loyer obligatoire.
ALTER TABLE accords
    ADD CONSTRAINT chk_accord_hebergement_ponctuel CHECK (
        type <> 'HEBERGEMENT_PONCTUEL'
        OR (
            logement_b_id IS NOT NULL
            AND montant_loyer IS NOT NULL
            AND montant_loyer > 0
        )
    );

-- ============================================================
-- 6. Index de recherche pour l'hébergement ponctuel
-- ============================================================

-- Recherche : "chambres ponctuelles disponibles à Lyon"
CREATE INDEX idx_logements_chambre_partagee
    ON logements(ville, prix_semaine)
    WHERE is_chambre_partagee = TRUE
      AND statut = 'ACTIF'
      AND is_verified = TRUE;
