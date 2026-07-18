# Changelog — StudUp API (backend)

Toutes les évolutions notables de l'API sont consignées ici.
Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/) ;
versions selon [SemVer](https://semver.org/lang/fr/) (MAJEUR.MINEUR.CORRECTIF).

Le détail fin de chaque évolution est traçable dans l'historique Git
(commits liés aux tickets Jira APP-XX) et les pull requests GitHub.

## [Non publié]

_Rien pour l'instant._

## [1.0.0] — 2026-07-18

Première version complète et fonctionnelle de l'API.

### Authentification & sécurité
- Inscription email/mot de passe, confirmation par email (US-001)
- Connexion JWT (access 15 min + refresh 7 j avec rotation), déconnexion + blacklist Redis (US-002, US-042)
- Rate limiting Bucket4j sur login et register (US-038)
- Contrôles d'ownership `@PreAuthorize`, endpoints admin protégés (US-040)
- Validation des fichiers par magic bytes Apache Tika (US-041)
- GlobalExceptionHandler, logs JSON structurés sans données personnelles (US-037, US-045)
- Scan de dépendances OWASP A06 + montée de versions sécurité (APP-113, APP-114)

### Profil alternant & calendrier
- Création/édition du profil (villes, école, entreprise, rythme, dates) (US-004)
- Rythme 2 semaines / 2 semaines et choix de la première semaine école/entreprise (APP-110)
- Génération automatique du calendrier d'alternance, jours fériés annotés (US-017)
- Override manuel d'une semaine (US-019)

### Moteur de matching
- Calcul de compatibilité semaine par semaine : échange, colocation, chevauchement (US-012)
- Score mixte échange + colocation (APP-108)
- Économies estimées à partir des loyers publiés (APP-103)
- Échange réel basé sur les logements publiés — règle §3 (APP-110)
- Moteur de scénarios : surplus même ville, logement manquant, relais (APP-109)
- Notification des deux alternants lors d'un nouveau match (APP-98)

### Logements & accords
- Publication de logements avec photos, association aux villes du profil (US-007, US-011)
- Recherche filtrée + pagination (US-008)
- Demande, acceptation, refus, annulation d'accords ; expiration automatique (US-016, US-046)

### Social
- Messagerie temps réel WebSocket STOMP + modération (US-021, US-023)
- Notifications in-app et préférences (US-022, US-033)
- Avis, score de réputation, modération (US-030, US-031, US-032)

### Qualité
- 429 tests automatisés (unitaires, controller, intégration Testcontainers)
- Couverture JaCoCo 85 % (99 % sur le moteur de matching)
- Pipeline CI GitHub Actions : build + tests sur chaque push et PR

[Non publié]: https://github.com/massiferrouk/app-backend/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/massiferrouk/app-backend/releases/tag/v1.0.0
