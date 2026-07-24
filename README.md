# StudUp — API

API REST de **StudUp**, une plateforme de logement pour alternants et étudiants.

Un alternant partage son année entre deux villes selon un rythme fixe : trois semaines
en entreprise à Paris, une semaine à l'école à Lyon, et ainsi de suite. Beaucoup paient
deux loyers pour des logements qu'ils occupent à moitié. StudUp met en relation deux
alternants dont les rythmes se complètent, pour qu'ils échangent leurs logements ou en
partagent un seul.

Ce dépôt contient le backend. L'application mobile Flutter est dans un dépôt séparé.

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
| Chacun est dans la ville de l'autre | **Échange** — les logements sont libres exactement quand l'autre en a besoin |
| Les deux sont dans la même ville, de façon récurrente | **Colocation** — un seul logement partagé, loyer divisé |
| Les deux sont dans la même ville, ponctuellement | **Chevauchement** — à eux de s'organiser |
| Aucune synergie cette semaine | **Neutre** |

Le score est le rapport des semaines exploitables sur la période commune, et détermine
le type proposé : échange total, échange partiel, ou colocation tournante.

Deux principes de conception :

- **L'algorithme n'invente rien.** Une économie n'est chiffrée que si les loyers
  concernés sont réellement connus. Sinon, l'app dit ce qu'il manque plutôt que
  d'afficher un montant rassurant mais faux.
- **L'algorithme n'a pas le dernier mot.** Il analyse et affiche ; les deux personnes
  décident. Il ne connaît que les calendriers — savoir si les logements nécessaires
  sont publiés est le travail de `MatchingService`, pas du calculateur.

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
  par défaut valables uniquement en développement local.

---

## Démarrer en local

**Prérequis** : Java 21, Docker.

```bash
# Base de données (le conteneur porte l'ancien nom du projet)
docker run --name yuniv-postgres \
  -e POSTGRES_DB=yunivdb -e POSTGRES_USER=yuniv -e POSTGRES_PASSWORD=yuniv123 \
  -p 5433:5432 -d postgres:15

# API — Flyway applique les migrations au démarrage
./mvnw spring-boot:run
```

L'API écoute sur `http://localhost:8080`, préfixe `/api/v1`.

**Redis est obligatoire**, même en développement : le filtre JWT interroge la blacklist
et la liste des comptes révoqués à *chaque* requête authentifiée. Sans lui, l'application
démarre mais toute requête authentifiée échoue.

MinIO est optionnel : sans lui, seul l'upload de photos ne fonctionne pas.

```bash
docker run --name studup-redis -p 6379:6379 -d redis:7
docker run --name studup-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  -d minio/minio server /data --console-address ":9001"
```

### Variables d'environnement

| Variable | Défaut (dev) | Rôle |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/yunivdb` | Connexion PostgreSQL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `yuniv` / `yuniv123` | Identifiants base |
| `JWT_SECRET` | valeur de développement | **À remplacer en production** (256 bits minimum) |
| `JWT_EXPIRATION_MS` | `900000` | Durée de l'access token (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Durée du refresh token (7 jours) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Cache et révocation |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `localhost:9000` / `minioadmin` | Stockage des fichiers |
| `CORS_ALLOWED_ORIGINS` | `*` | **À restreindre en production** |
| `SMTP_PASSWORD` / `FROM_EMAIL` | vide / `noreply@studup.fr` | Envoi d'e-mails |
| `APP_BASE_URL` | `http://localhost:8080` | Liens dans les e-mails |

---

## Tests

```bash
./mvnw test
```

La suite complète est exécutée sur chaque pull request. La convention est de deux
couches systématiques par fonctionnalité :

- `XxxServiceTest` — logique métier, contrôles de propriété, cas d'erreur (Mockito) ;
- `XxxControllerTest` — codes HTTP, JSON, `401` sans authentification, `400` en cas de
  validation invalide (MockMvc, `@WebMvcTest`).

S'y ajoutent les tests d'algorithme, qui rejouent des grilles de cas complètes : tous
les rythmes, croisés avec toutes les combinaisons de villes et de première semaine.

Un test de démarrage du contexte Spring complet existe mais reste désactivé : il exige
Redis et MinIO. Le passer sous Testcontainers est la prochaine étape prévue.

---

## Base de données

Les migrations Flyway sont appliquées automatiquement au démarrage. Deux contraintes
apprises en cours de route et valables pour toute nouvelle migration :

- `spring.flyway.execute-in-transaction=false` est **obligatoire** : PostgreSQL exige
  qu'un `ALTER TYPE ... ADD VALUE` soit commité avant d'être utilisé.
- Les types ENUM natifs PostgreSQL imposent un `CAST(? AS mon_type)` dans les requêtes
  natives — sinon Hibernate envoie un `varchar` et la requête échoue silencieusement.

---

## Déploiement

`.github/workflows/ci.yml` compile et exécute la suite complète sur chaque pull request,
avec un PostgreSQL 15 en service. Un push sur `main` déclenche le déploiement Railway,
qui construit l'image à partir du `Dockerfile` multi-stage (compilation avec le JDK,
image finale sur le JRE seul).

---

## Périmètre

Les paiements (loyer, caution, abonnement) sont **hors périmètre** de cette version :
ils supposent un agrément et une gestion de fonds tiers qui dépassent le cadre du projet.
Le modèle économique repose dessus, mais la démonstration porte sur la mise en relation.

Certains endpoints existent côté API sans être appelés par l'application mobile — les
accords formels notamment, l'organisation se réglant dans la messagerie. Ils sont
conservés en l'état pour une version ultérieure.
