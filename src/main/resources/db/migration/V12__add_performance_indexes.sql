-- V12__add_performance_indexes.sql
-- Index de performance pour les requêtes fréquentes en production
-- SLA cible : /matching/suggestions P95 < 500ms pour 500 profils

-- ============================================================
-- MATCHING — Requêtes de compatibilité géographique
-- ============================================================

-- Recherche des profils compatibles par ville (clé du matching)
-- SELECT * FROM alternant_profiles
-- WHERE (ville_a = ? AND ville_b = ?) OR (ville_a = ? AND ville_b = ?)
CREATE INDEX idx_alternant_profiles_villes
    ON alternant_profiles(ville_a, ville_b);

CREATE INDEX idx_alternant_profiles_villes_inv
    ON alternant_profiles(ville_b, ville_a);

-- Recherche par rythme + dates actives (filtrage préalable au calcul de score)
CREATE INDEX idx_alternant_profiles_rythme_dates
    ON alternant_profiles(rythme, date_debut, date_fin);

-- ============================================================
-- LOGEMENTS — Recherche géospatiale (Nominatim + PostgreSQL)
-- ============================================================

-- Index pour la recherche par rayon (distance euclidienne approchée)
-- Pour la production : envisager PostGIS + GiST sur point géographique
CREATE INDEX idx_logements_geo
    ON logements(lat, lng, ville)
    WHERE statut = 'ACTIF' AND lat IS NOT NULL AND lng IS NOT NULL;

-- ============================================================
-- ACCORDS — Tableau de bord utilisateur
-- ============================================================

-- Requête : "mes accords actifs" (page d'accueil utilisateur)
CREATE INDEX idx_accords_user_actif
    ON accords(initiator_id, statut, date_fin)
    WHERE statut IN ('EN_ATTENTE', 'ACCEPTE', 'EN_COURS');

CREATE INDEX idx_accords_receiver_actif
    ON accords(receiver_id, statut, date_fin)
    WHERE statut IN ('EN_ATTENTE', 'ACCEPTE', 'EN_COURS');

-- ============================================================
-- MESSAGES — Pagination et WebSocket
-- ============================================================

-- Requête : "derniers messages d'une conversation" avec pagination
CREATE INDEX idx_messages_conv_pagination
    ON messages(conversation_id, created_at DESC)
    INCLUDE (sender_id, content, is_read);

-- Requête : "combien de messages non lus pour l'utilisateur ?"
CREATE INDEX idx_messages_unread_by_user
    ON messages(conversation_id)
    WHERE is_read = FALSE;

-- ============================================================
-- TRANSACTIONS — Comptabilité / Reporting
-- ============================================================

CREATE INDEX idx_transactions_reporting
    ON transactions(created_at DESC, statut, type)
    WHERE statut = 'SUCCES';

-- ============================================================
-- NOTIFICATIONS — Polling et push
-- ============================================================

CREATE INDEX idx_notifications_unread_count
    ON notifications(user_id, is_read, created_at DESC)
    WHERE is_read = FALSE;

-- ============================================================
-- USERS — Recherche admin et RGPD
-- ============================================================

-- Soft delete : toutes les requêtes métier filtrent sur deleted_at IS NULL
CREATE INDEX idx_users_active
    ON users(email, role)
    WHERE deleted_at IS NULL AND is_active = TRUE;

-- Requête admin : utilisateurs non vérifiés
CREATE INDEX idx_users_not_verified
    ON users(created_at)
    WHERE is_verified = FALSE AND deleted_at IS NULL;

-- ============================================================
-- ANALYTICS — Statistiques plateforme
-- ============================================================

-- Score de matching les plus élevés non expirés (suggestions homepage)
-- (déjà créé en V4, rappel documentaire)
-- CREATE INDEX idx_matching_cache_score ON matching_cache(score DESC) WHERE expires_at > NOW();

-- ============================================================
-- NOTE : Stratégie d'indexation
-- ============================================================
-- 1. Index partiels (WHERE ...) : évitent l'indexation des lignes jamais requêtées
-- 2. Index INCLUDE : évitent les heap fetches sur les requêtes de lecture fréquentes
-- 3. Index composites : colonnes les plus sélectives en premier
-- 4. Maintenance : REINDEX CONCURRENTLY à planifier mensuellement en production
-- 5. Monitoring : pg_stat_user_indexes pour détecter les index non utilisés
