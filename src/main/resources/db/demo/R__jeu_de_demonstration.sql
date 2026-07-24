-- =====================================================================
-- Jeu de données de démonstration — StudUp
-- =====================================================================
-- Ce script n'est PAS joué en temps normal. Il n'est appliqué que si le
-- profil Spring « demo » est actif, qui ajoute classpath:db/demo aux
-- emplacements Flyway (voir application-demo.properties).
--
-- Objectif : qu'une personne qui découvre le projet (jury, nouveau
-- développeur) puisse se connecter et voir des écrans remplis en une
-- minute, sans créer de compte ni confirmer d'e-mail.
--
-- Migration « repeatable » (préfixe R__) et entièrement idempotente :
-- identifiants figés + ON CONFLICT DO NOTHING. La rejouer ne duplique
-- rien et n'écrase aucune donnée saisie à la main.
--
-- Mot de passe commun à tous les comptes : Demo1234!
-- (hash BCrypt ci-dessous — le mot de passe en clair n'existe nulle part
--  dans le code, il n'est écrit que dans le README.)
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Comptes
-- ---------------------------------------------------------------------
-- is_verified = true : le compte est déjà confirmé. Sans ça la connexion
-- serait refusée (EmailNotConfirmedException), et l'e-mail de
-- confirmation n'est pas envoyé en local faute de clé SendGrid.
INSERT INTO users (id, email, password_hash, role, first_name, last_name, phone, is_verified, is_active)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'alternant1@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'ALTERNANT', 'Ludovic', 'Martin', '0600000001', true, true),

    ('22222222-2222-2222-2222-222222222222', 'alternant2@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'ALTERNANT', 'Ines', 'Rousseau', '0600000002', true, true),

    ('33333333-3333-3333-3333-333333333333', 'alternant3@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'ALTERNANT', 'Karim', 'Benali', '0600000003', true, true),

    ('44444444-4444-4444-4444-444444444444', 'etudiant@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'ETUDIANT', 'Lea', 'Dubois', '0600000004', true, true),

    ('55555555-5555-5555-5555-555555555555', 'proprietaire@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'PROPRIETAIRE', 'Marc', 'Lefevre', '0600000005', true, true),

    ('66666666-6666-6666-6666-666666666666', 'admin@studup.demo',
     '$2a$10$MJrX7W2/UB1wl1l9QEK32OKjtT3PYQFOuaAHFTzuFp9WHcM0YiOEW',
     'ADMIN', 'Admin', 'StudUp', NULL, true, true)
ON CONFLICT (email) DO NOTHING;


-- ---------------------------------------------------------------------
-- 2. Profils d'alternance
-- ---------------------------------------------------------------------
-- Les dates démarrent au lundi de la semaine courante : le jeu de données
-- reste pertinent quelle que soit la date à laquelle le projet est lancé.
-- date_trunc('week', ...) renvoie toujours un lundi, ce qu'exige la
-- contrainte chk_alternance_semaine_lundi sur le calendrier.
--
-- Ludovic et Ines ont des situations MIROIR : école et entreprise
-- inversées, même rythme. C'est le cas d'usage central de StudUp.
INSERT INTO alternant_profiles
    (id, user_id, ville_a, ville_b, ecole, entreprise, date_debut, date_fin, rythme, premiere_semaine)
VALUES
    -- Ludovic : école à Lyon, entreprise à Paris
    ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'Lyon', 'Paris', 'YNOV Lyon', 'Capgemini Paris',
     date_trunc('week', CURRENT_DATE)::date,
     date_trunc('week', CURRENT_DATE)::date + 364,
     'SEMAINE_3_1', 'ENTREPRISE'),

    -- Ines : école à Paris, entreprise à Lyon — exactement l'inverse
    ('aaaaaaaa-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
     'Paris', 'Lyon', 'YNOV Paris', 'Sopra Steria Lyon',
     date_trunc('week', CURRENT_DATE)::date,
     date_trunc('week', CURRENT_DATE)::date + 364,
     'SEMAINE_3_1', 'ENTREPRISE'),

    -- Karim : même situation que Ludovic, mais SANS logement publié.
    -- Sert à montrer le « match potentiel » et la colocation.
    ('aaaaaaaa-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333',
     'Lyon', 'Paris', 'YNOV Lyon', 'Orange Paris',
     date_trunc('week', CURRENT_DATE)::date,
     date_trunc('week', CURRENT_DATE)::date + 364,
     'SEMAINE_3_1', 'ENTREPRISE')
ON CONFLICT (user_id) DO NOTHING;


-- ---------------------------------------------------------------------
-- 3. Calendriers (52 semaines par profil)
-- ---------------------------------------------------------------------
-- Reproduit exactement ce que produit ScheduleGenerator pour un rythme
-- SEMAINE_3_1 démarrant par l'entreprise : 3 semaines 'B' (entreprise)
-- puis 1 semaine 'A' (école), en boucle.
INSERT INTO alternance_schedules (profile_id, semaine, label)
SELECT p.id,
       date_trunc('week', CURRENT_DATE)::date + (s.i * 7),
       CASE WHEN (s.i % 4) < 3 THEN 'B' ELSE 'A' END
FROM alternant_profiles p
CROSS JOIN generate_series(0, 51) AS s(i)
WHERE p.id IN ('aaaaaaaa-0000-0000-0000-000000000001',
               'aaaaaaaa-0000-0000-0000-000000000002',
               'aaaaaaaa-0000-0000-0000-000000000003')
ON CONFLICT (profile_id, semaine) DO NOTHING;


-- ---------------------------------------------------------------------
-- 4. Profil propriétaire
-- ---------------------------------------------------------------------
INSERT INTO proprietaire_profiles (id, user_id, phone, adresse, ville, code_postal, is_verified)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', '55555555-5555-5555-5555-555555555555',
        '0600000005', '12 rue de la Republique', 'Lyon', '69002', true)
ON CONFLICT (user_id) DO NOTHING;


-- ---------------------------------------------------------------------
-- 5. Logements
-- ---------------------------------------------------------------------
-- Ludovic possède à LYON (sa ville d'école) et passe 3 semaines sur 4 à
-- Paris. Ines possède à PARIS et passe 3 semaines sur 4 à Lyon. Chacun
-- loge donc chez l'autre 3 semaines par cycle : l'algorithme sort un
-- échange sur 75 % des semaines. C'est la démonstration du produit.
--
-- ville_associee = VILLE_A : le logement est rattaché à la ville d'école
-- du profil, ce qui active le matching (sans ce champ, pas d'échange).
INSERT INTO logements
    (id, owner_id, adresse, ville, code_postal, lat, lng, type, surface, nb_pieces,
     loyer, charges, description, equipements, statut, is_verified, is_meuble, ville_associee)
VALUES
    ('cccccccc-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     '18 rue Sainte-Catherine', 'Lyon', '69001', 45.767300, 4.834400,
     'STUDIO', 24.00, 1, 520.00, 45.00,
     'Studio meuble en plein Presqu''ile, a 10 min a pied de la gare Part-Dieu en metro. Libre 3 semaines sur 4.',
     ARRAY['wifi', 'lave-linge', 'cuisine equipee'], 'ACTIF', true, true, 'VILLE_A'),

    ('cccccccc-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
     '7 rue de Belleville', 'Paris', '75020', 48.872100, 2.383900,
     'T1', 28.00, 1, 780.00, 60.00,
     'T1 lumineux proche metro Belleville. Ideal pour un alternant en semaine entreprise.',
     ARRAY['wifi', 'ascenseur', 'lave-linge'], 'ACTIF', true, true, 'VILLE_A'),

    -- Logements du propriétaire : alimentent la recherche côté étudiant
    ('cccccccc-0000-0000-0000-000000000003', '55555555-5555-5555-5555-555555555555',
     '45 cours Gambetta', 'Lyon', '69003', 45.752000, 4.851000,
     'T2', 42.00, 2, 690.00, 70.00,
     'T2 renove, proche Jean Mace. Colocation possible entre deux alternants.',
     ARRAY['wifi', 'parking', 'lave-linge', 'balcon'], 'ACTIF', true, true, NULL),

    ('cccccccc-0000-0000-0000-000000000004', '55555555-5555-5555-5555-555555555555',
     '22 avenue Jean Jaures', 'Paris', '75019', 48.884000, 2.371000,
     'CHAMBRE_COLOC', 14.00, 1, 480.00, 40.00,
     'Chambre en colocation dans un T4, quartier calme, charges comprises.',
     ARRAY['wifi', 'lave-linge'], 'ACTIF', true, true, NULL)
ON CONFLICT (id) DO NOTHING;


-- ---------------------------------------------------------------------
-- 6. Une conversation déjà entamée
-- ---------------------------------------------------------------------
-- Évite l'écran « aucune conversation » et permet de tester la messagerie
-- temps réel à deux (une session Ludovic, une session Ines).
INSERT INTO conversations (id, logement_id)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO conversation_participants (conversation_id, user_id)
VALUES
    ('dddddddd-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111'),
    ('dddddddd-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222')
ON CONFLICT (conversation_id, user_id) DO NOTHING;

INSERT INTO messages (id, conversation_id, sender_id, content, is_read, created_at)
VALUES
    ('eeeeeeee-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Salut Ines ! Nos rythmes ont l''air parfaitement inverses, je suis a Paris pendant que tu es a Lyon.',
     true, NOW() - INTERVAL '2 days'),

    ('eeeeeeee-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001',
     '22222222-2222-2222-2222-222222222222',
     'Salut ! Oui j''ai vu le calendrier, 3 semaines sur 4 ca colle. Mon T1 est libre exactement sur ces semaines-la.',
     true, NOW() - INTERVAL '2 days' + INTERVAL '12 minutes'),

    ('eeeeeeee-0000-0000-0000-000000000003', 'dddddddd-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Parfait. On se cale un appel cette semaine pour les details ?',
     false, NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
