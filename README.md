# StudUp — API

API REST de **StudUp**, une plateforme de logement pour alternants et étudiants.

Un alternant partage son année entre deux villes selon un rythme fixe : trois semaines
en entreprise à Paris, une semaine à l'école à Lyon, et ainsi de suite. Beaucoup paient
deux loyers pour des logements qu'ils occupent à moitié. StudUp met en relation deux
alternants dont les rythmes se complètent, pour qu'ils échangent leurs logements ou en
partagent un seul.

Ce dépôt contient le backend. L'application mobile Flutter est dans un dépôt séparé :
[app-frontend](https://github.com/massiferrouk/app-frontend).

---

## Démarrage rapide

> **Objectif : une API qui tourne avec des données dedans, en une commande.**
> Rien à installer à part Docker. Ni Java, ni Maven, ni PostgreSQL.

### Prérequis — un seul

**Docker Desktop**, installé et **lancé** (l'icône baleine doit être visible dans la
barre des tâches). Téléchargement : <https://www.docker.com/products/docker-desktop/>

Pour vérifier qu'il répond :

```bash
docker info
```

Si la commande affiche des informations, tout est prêt. Si elle affiche
`error during connect`, c'est que Docker Desktop n'est pas démarré.

### Lancer

Depuis ce dossier (celui qui contient `docker-compose.yml`) :

```bash
docker compose up -d --build
```

**La première exécution est longue : comptez 10 à 25 minutes selon la connexion.**
Docker télécharge les images, puis compile l'API en récupérant toutes ses dépendances
Maven. C'est normal, il faut laisser tourner — la commande rend la main quand tout est
prêt. Les lancements suivants prennent quelques secondes, tout étant mis en cache.

Pour suivre la progression pendant ce temps : `docker compose logs -f`.

Quatre conteneurs démarrent : la base PostgreSQL, Redis, MinIO et l'API. Les migrations
de schéma et le jeu de données de démonstration sont appliqués automatiquement.

### Vérifier que ça marche

```bash
curl http://localhost:8080/actuator/health/liveness
```

Réponse attendue :

```json
{"status":"UP"}
```

Puis une vraie connexion, avec un compte de démonstration. Sous **macOS, Linux ou Git
Bash** :

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"alternant1@studup.demo","password":"Demo1234!"}'
```

Sous **PowerShell**, les guillemets se comportent différemment — utiliser cette forme :

```bash
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType 'application/json' -Body '{"email":"alternant1@studup.demo","password":"Demo1234!"}'
```

La réponse contient `accessToken` et `refreshToken` : l'API est opérationnelle, la base
est peuplée, et l'authentification fonctionne.

Pour suivre le démarrage en direct : `docker compose logs -f api`.

### Arrêter

```bash
docker compose down
```

Les données sont conservées. Pour repartir d'une base totalement vide :
`docker compose down -v`.

---

## Comptes de démonstration

Ces comptes sont créés par le profil `demo`, actif par défaut dans `docker-compose.yml`.
Ils sont **déjà confirmés** : on se connecte directement, sans passer par l'e-mail de
confirmation.

**Mot de passe commun à tous les comptes : `Demo1234!`**

| E-mail | Rôle | Ce qu'il permet de voir |
|---|---|---|
| `alternant1@studup.demo` | Alternant | Ludovic — école à Lyon, entreprise à Paris, rythme 3/1. Possède un studio à Lyon. C'est le compte à utiliser en premier. |
| `alternant2@studup.demo` | Alternant | Inès — situation exactement inverse : école à Paris, entreprise à Lyon. Possède un T1 à Paris. **C'est le match de Ludovic.** |
| `alternant3@studup.demo` | Alternant | Karim — même rythme et mêmes villes que Ludovic, mais **sans logement publié** : illustre le « match potentiel » et la colocation. |
| `etudiant@studup.demo` | Étudiant | Léa — recherche de logements et suivi de candidatures. |
| `proprietaire@studup.demo` | Propriétaire | Marc — deux annonces publiées, une à Lyon et une à Paris. |
| `admin@studup.demo` | Admin | Modération des messages et des annonces. |

### Le scénario à suivre

Ludovic possède un studio à **Lyon**, sa ville d'école, mais passe **trois semaines sur
quatre à Paris**, en entreprise. Inès est dans la situation miroir : elle possède un T1
à **Paris** et passe trois semaines sur quatre à **Lyon**.

Chacun paie donc un loyer pour un logement qu'il n'occupe qu'une semaine sur quatre,
tout en cherchant à se loger dans la ville de l'autre. L'algorithme compare leurs deux
calendriers semaine par semaine et détecte que **trois semaines sur quatre sont
échangeables** — un score de 75 %, un échange partiel, et une économie chiffrée à partir
des loyers réellement déclarés.

La quatrième semaine, chacun est chez soi : l'algorithme ne la compte pas comme un
échange. C'est le principe tenu partout dans le projet — **ne jamais annoncer un gain
qui n'existe pas**.

À voir en priorité une fois connecté avec Ludovic :

1. **Mon calendrier** — les 52 semaines générées automatiquement à la création du profil.
2. **Matches** — Inès en match actif (les deux logements sont publiés), Karim en match
   potentiel.
3. **Le calendrier de compatibilité avec Inès** — les deux calendriers côte à côte,
   semaine par semaine, avec le code couleur et le score.
4. **Messages** — une conversation déjà entamée avec Inès. En ouvrant une seconde
   session sur le compte d'Inès, on voit les messages arriver en temps réel.

### Et si je veux tester la création de compte ?

C'est prévu. En profil `demo`, un compte créé depuis l'écran d'inscription est
**confirmé automatiquement** : on peut se connecter juste après, sans e-mail.

Ce n'est pas le comportement de production. Normalement, l'inscription envoie un e-mail
de confirmation, et la connexion reste refusée tant qu'on n'a pas cliqué le lien. Or
**aucun e-mail ne peut partir sur une machine locale** : l'envoi passe par un service
tiers (SendGrid) dont la clé est un secret de production, absent d'un poste de
développement — c'est un choix de sécurité standard, non une limite du projet. Sans
adaptation, un compte auto-créé resterait donc bloqué à la connexion.

Le drapeau `app.auto-confirm-accounts`, activé **uniquement** par le profil `demo`
(`application-demo.properties`), lève cette barrière le temps de la démonstration. En
production, il reste à `false` et la confirmation par e-mail garde toute sa valeur.

---

## Lancer pour développer

Le mode « tout en Docker » ci-dessus reconstruit l'image à chaque modification du code.
Pour développer, on ne lance que les dépendances dans Docker et on exécute l'API
directement.

**Prérequis** : Docker, et **Java 21** (`java -version` doit afficher `21`).
Maven n'est pas nécessaire — le dépôt contient son propre lanceur (`mvnw`).

```bash
# Les trois dépendances, sans l'API
docker compose up -d postgres redis minio

# L'API, avec le jeu de démonstration
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Sous Windows, en PowerShell ou `cmd`, le lanceur s'appelle `mvnw.cmd` :

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo
```

Sans `-Dspring-boot.run.profiles=demo`, l'API démarre sur une base vide, sans compte.

L'API écoute sur `http://localhost:8080`, préfixe `/api/v1`.

**Redis est obligatoire**, même en développement : le filtre JWT interroge la blacklist
et la liste des comptes révoqués à *chaque* requête authentifiée. Sans lui, l'application
démarre mais toute requête authentifiée échoue.

MinIO est optionnel : sans lui, seul l'envoi de photos ne fonctionne pas.

---

## En cas de problème

| Symptôme | Cause | Solution |
|---|---|---|
| `docker : command not found` ou `error during connect` | Docker Desktop n'est pas installé ou pas lancé | Ouvrir Docker Desktop et attendre que l'icône se stabilise |
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Un autre programme occupe le port 8080 | `docker compose down`, puis vérifier : `netstat -ano \| findstr :8080` (Windows) ou `lsof -i :8080` (macOS/Linux) |
| Même erreur sur 5433, 6379 ou 9000 | Un PostgreSQL, Redis ou MinIO tourne déjà sur la machine | L'arrêter, ou modifier la partie gauche du port dans `docker-compose.yml` (par exemple `5434:5432`) |
| `docker compose` inconnu, mais `docker-compose` fonctionne | Version ancienne de Docker | Utiliser `docker-compose up -d --build` (avec le tiret) |
| L'API redémarre en boucle | La base n'était pas prête, ou une migration a échoué | `docker compose logs api` pour lire l'erreur. En dernier recours : `docker compose down -v && docker compose up -d --build` (efface les données) |
| `Validate failed : migration checksum mismatch` | Base créée par une version antérieure du schéma | `docker compose down -v` puis relancer : la base est recréée de zéro |
| `401` sur **toutes** les requêtes authentifiées | Redis n'est pas joignable | Vérifier que le conteneur Redis tourne : `docker compose ps` |
| `401` avec le code `EMAIL_NOT_CONFIRMED` | Compte créé par inscription **sans** le profil `demo` : l'e-mail de confirmation ne part pas en local, le compte reste non confirmé | Lancer l'API avec le profil `demo` (cas par défaut de `docker compose`) — l'inscription y confirme automatiquement. Sinon, confirmer à la main : `UPDATE users SET is_verified = true WHERE email = '…';` |
| `429 Too Many Requests` sur `/auth/login` | Limitation à 5 tentatives par minute et par IP | Attendre une minute — c'est le comportement attendu |
| `./mvnw : command not found` sous Windows | `./mvnw` est la forme Unix | Utiliser `mvnw.cmd` |
| `UnsupportedClassVersionError` au build | Java antérieur à 21 | Installer un JDK 21 (Temurin) et vérifier `java -version` |
| Les photos ne s'affichent pas en mode tout-Docker | Les URL signées par MinIO pointent vers `http://minio:9000`, un nom qui n'existe que dans le réseau Docker | Sans conséquence pour la démonstration (aucune photo dans le jeu de données). Pour tester l'envoi de photos, lancer l'API depuis la machine (`./mvnw spring-boot:run`) plutôt que dans Docker |

Pour inspecter la base directement :

```bash
docker compose exec postgres psql -U yuniv -d yunivdb
```

---

## Ce que fait le backend

- **Comptes et sécurité** — inscription avec confirmation par e-mail, authentification
  JWT avec rotation des refresh tokens, révocation à la déconnexion et à la suspension.
- **Profils d'alternance** — deux villes, un rythme, des dates. Le calendrier
  semaine par semaine est généré automatiquement à la création du profil.
- **Algorithme de compatibilité** — compare deux calendriers semaine par semaine et
  produit un score, un type d'arrangement et un calendrier colorisé (voir plus bas).
- **Annonces** — publication avec photos (compression, stockage objet, URLs signées),
  géocodage des adresses, recherche multi-critères paginée.
- **Suivi de candidature** — un étudiant suit les annonces qui l'intéressent et fait
  évoluer lui-même le statut : à contacter, contacté, visite prévue, visitée, acceptée.
- **Messagerie temps réel** — WebSocket STOMP, un fil par annonce, historique persisté.
- **Notifications** — in-app et push (Firebase), avec préférences par type et par canal.
- **Administration** — modération des messages et des avis, suspension et bannissement.

---

## Stack

| Domaine | Choix |
|---|---|
| Langage / framework | Java 21, Spring Boot 3.5 |
| Sécurité | Spring Security 6, JWT (JJWT 0.12), BCrypt, Bucket4j |
| Persistance | Spring Data JPA / Hibernate, PostgreSQL 15 |
| Migrations | Flyway (SQL pur) |
| Cache et révocation | Redis |
| Stockage de fichiers | MinIO (API compatible S3) |
| Temps réel | Spring WebSocket + STOMP |
| Notifications push | Firebase Admin SDK |
| Géocodage | Nominatim / Base Adresse Nationale |
| Tâches planifiées | Spring Scheduling + ShedLock |
| Observabilité | Actuator, Logback JSON, métriques Micrometer |
| Tests | JUnit 5, Mockito, MockMvc |
| CI / déploiement | GitHub Actions, Docker multi-stage, Railway |

---

## Architecture

Monolithe modulaire en couches. Le choix est assumé : un projet mené seul, avec un
modèle de données fortement lié (un accord référence deux utilisateurs, deux logements
et deux calendriers), n'a rien à gagner à être découpé en services distants.

```
com.studup.backend
├── controller/   contrôleurs REST — routes, codes HTTP, sérialisation
├── service/      logique métier — aucune requête SQL écrite ici
├── repository/   accès aux données (Spring Data JPA)
├── algorithm/    calcul de compatibilité, génération de calendrier, scénarios
├── model/
│   ├── entity/   entités JPA
│   ├── dto/      request/ et response/ — jamais d'entité exposée à l'extérieur
│   └── enums/
├── security/     filtre JWT, blacklist, UserDetailsService, configuration
├── config/       CORS, Redis, MinIO, Firebase, WebSocket, rate limiting
├── scheduler/    jobs planifiés (expiration, réputation, purge des tokens)
└── exception/    GlobalExceptionHandler et exceptions métier
```

Trois règles tenues partout :

1. **Les entités JPA ne sortent jamais du backend.** Chaque réponse passe par un DTO,
   ce qui évite d'exposer accidentellement un champ interne et découple l'API du schéma.
2. **Aucun appel au repository depuis un contrôleur.** La logique vit dans les services.
3. **Le schéma appartient à Flyway.** Hibernate est en `ddl-auto=validate` : il vérifie
   que le code correspond à la base, il ne la modifie jamais.

---

## L'algorithme de compatibilité

C'est le cœur du produit. Il compare les calendriers de deux alternants **semaine par
semaine** sur leur période commune, et classe chaque semaine :

| Situation | Résultat |
|---|---|
| Chacun passe la semaine dans la ville où l'autre a un logement publié | **Échange** — les logements sont libres exactement quand l'autre en a besoin |
| Les deux sont dans la même ville | **Colocation** — un seul logement partagé, loyer divisé |
| Chacun est chez soi, ou aucune synergie | **Neutre** — rien n'est promis |

Le score est le rapport des semaines exploitables — échange ou colocation — sur la
période commune. Le type proposé découle du décompte des semaines : échange total si
toutes les semaines sont échangeables, échange partiel dès qu'il y en a au moins une,
colocation tournante s'il n'y a que de la colocation.

Deux principes de conception :

- **L'algorithme n'invente rien.** Une semaine n'est comptée comme un échange que si les
  deux logements sont réellement publiés, dans les bonnes villes. Une économie n'est
  chiffrée que si les loyers concernés sont connus. Sinon, l'app dit ce qu'il manque
  plutôt que d'afficher un montant rassurant mais faux.
- **L'algorithme n'a pas le dernier mot.** Il analyse et affiche ; les deux personnes
  décident.

Fichiers concernés : `algorithm/CompatibilityCalculator`, `ScheduleGenerator`,
`ColocationMatcher`, `PartialExchangeOptimizer`, `ScenarioAdvisor`.

---

## Sécurité

Les mesures sont mises en regard de l'OWASP Top 10 dans [docs/securite-owasp.md](docs/securite-owasp.md).
Les points structurants :

- **Mots de passe** hashés avec BCrypt. Les refresh tokens sont stockés hashés en SHA-256 :
  une fuite de la table ne donne aucun token utilisable.
- **Deux niveaux de révocation.** Le JTI d'un token est mis en blacklist Redis à la
  déconnexion ; une suspension révoque *tous* les tokens de l'utilisateur en bloc et
  invalide ses refresh tokens en base. Sans ce second niveau, une suspension n'aurait
  tenu que le temps de l'access token courant.
- **Vérification de propriété** avant toute modification de ressource, et `403` plutôt
  que `404` quand la ressource existe mais n'appartient pas à l'appelant.
- **Rate limiting** Bucket4j sur les routes sensibles (connexion, inscription, matching).
- **Validation des fichiers** par les magic bytes via Apache Tika, jamais par l'extension.
  Renommage systématique en UUID avant stockage.
- **Aucune stacktrace exposée** : toutes les erreurs passent par `GlobalExceptionHandler`
  et sortent au format `{code, message, timestamp, path, details[]}`.
- **Aucune donnée personnelle dans les logs** — on trace des identifiants, jamais des
  e-mails ni des noms.
- **Aucun secret dans le dépôt** : tout est en variable d'environnement, avec des valeurs
  par défaut valables uniquement en développement local. Le modèle est dans
  [.env.example](.env.example).

---

## Variables d'environnement

Aucune n'est requise en local : chacune a une valeur par défaut de développement dans
`application.properties`. Le fichier [.env.example](.env.example) sert de référence pour
un déploiement.

| Variable | Défaut (dev) | Rôle |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/yunivdb` | Connexion PostgreSQL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `yuniv` / `yuniv123` | Identifiants base |
| `JWT_SECRET` | valeur de développement | **À remplacer en production** (256 bits minimum) |
| `JWT_EXPIRATION_MS` | `900000` | Durée de l'access token (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Durée du refresh token (7 jours) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / vide | Cache et révocation |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `http://localhost:9000` / `minioadmin` | Stockage des fichiers |
| `CORS_ALLOWED_ORIGINS` | `*` | **À restreindre en production** |
| `SMTP_PASSWORD` / `FROM_EMAIL` | vide / `noreply@studup.fr` | Clé d'API SendGrid et expéditeur. Vide = envoi désactivé, sans erreur |
| `APP_BASE_URL` | `http://localhost:8080` | Liens dans les e-mails |
| `PORT` | `8080` | Port d'écoute (injecté par l'hébergeur) |

### Profils Spring

| Profil | Effet |
|---|---|
| *(aucun)* | Schéma seul, base vide. C'est le mode de production. |
| `demo` | Ajoute le jeu de données de démonstration (`db/demo`). Aucun changement de schéma. |

---

## Tests

```bash
./mvnw test
```

**Docker doit tourner** : les tests d'intégration démarrent eux-mêmes un PostgreSQL et
un Redis jetables via Testcontainers. Rien d'autre à installer ni à configurer — les
conteneurs sont créés, utilisés puis détruits automatiquement. Le reste de la suite ne
dépend de rien.

La convention est de deux couches systématiques par fonctionnalité :

- `XxxServiceTest` — logique métier, contrôles de propriété, cas d'erreur (Mockito) ;
- `XxxControllerTest` — codes HTTP, JSON, `401` sans authentification, `400` en cas de
  validation invalide (MockMvc, `@WebMvcTest`).

S'y ajoutent :

- les tests d'algorithme, qui rejouent des grilles de cas complètes — tous les rythmes,
  croisés avec toutes les combinaisons de villes et de première semaine ;
- quatre tests d'intégration sur Testcontainers (`src/test/.../integration/`), qui
  couvrent l'authentification de bout en bout, la mise à jour d'un profil d'alternance
  et les notifications de match.

Un seul test reste désactivé : `BackendApplicationTests`, qui charge le contexte Spring
complet et exige en plus MinIO. Le passer sous Testcontainers est la prochaine étape
prévue (US-049).

La suite complète est exécutée sur chaque pull request par GitHub Actions ; une pull
request rouge n'est jamais fusionnée.

---

## Base de données

Les migrations Flyway sont appliquées automatiquement au démarrage, depuis
`src/main/resources/db/migration`. Deux contraintes apprises en cours de route et
valables pour toute nouvelle migration :

- `spring.flyway.execute-in-transaction=false` est **obligatoire** : PostgreSQL exige
  qu'un `ALTER TYPE ... ADD VALUE` soit commité avant d'être utilisé.
- Les types ENUM natifs PostgreSQL imposent un `CAST(? AS mon_type)` dans les requêtes
  natives — sinon Hibernate envoie un `varchar` et la requête échoue silencieusement.

Le jeu de démonstration vit à part, dans `src/main/resources/db/demo`. C'est une
migration *repeatable* et idempotente : identifiants figés, `ON CONFLICT DO NOTHING`.
La rejouer ne duplique rien.

---

## Déploiement

`.github/workflows/ci.yml` compile et exécute la suite complète sur chaque pull request,
avec un PostgreSQL 15 en service. Un push sur `main` déclenche le déploiement Railway,
qui construit l'image à partir du `Dockerfile` multi-stage (compilation avec le JDK,
image finale sur le JRE seul).

L'API est déployée et accessible en ligne :

```
https://app-backend-production-219d.up.railway.app        (préfixe /api/v1)
https://app-backend-production-219d.up.railway.app/actuator/health/liveness
```

Pour lancer l'application mobile pointée sur ce backend en ligne plutôt qu'en local,
voir la section « Tester l'application déployée » du [README racine](../README.md).

La procédure de mise à jour après mise en service — livrer un correctif, faire évoluer
le schéma, revenir en arrière — est décrite dans
[docs/manuel-mise-a-jour.md](docs/manuel-mise-a-jour.md).

---

## Périmètre

Les paiements (loyer, caution, abonnement) sont **hors périmètre** de cette version :
ils supposent un agrément et une gestion de fonds tiers qui dépassent le cadre du projet.
Le modèle économique repose dessus, mais la démonstration porte sur la mise en relation.

Certains endpoints existent côté API sans être appelés par l'application mobile — les
accords formels notamment, l'organisation se réglant dans la messagerie. Ils sont
conservés en l'état pour une version ultérieure.
