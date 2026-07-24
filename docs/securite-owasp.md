# Sécurité StudUp — Couverture OWASP Top 10 (2021)

Document de synthèse pour la compétence RNCP C2.2.3.
Chaque faille du Top 10 OWASP est mise en regard des mesures concrètes du projet.

| # | Faille OWASP 2021 | Mesures mises en œuvre dans StudUp |
|---|---|---|
| A01 | Broken Access Control | Ownership checks avant chaque modification de ressource ; `.anyRequest().authenticated()` ; `@PreAuthorize("hasRole('ADMIN')")` au niveau de la classe `AdminController` ; **rôle ADMIN refusé à l'inscription (APP-121)** ; élévation de privilèges impossible par le changement de mode ; JWT stateless |
| A02 | Cryptographic Failures | Mots de passe hashés BCrypt ; refresh tokens stockés hashés (SHA-256) ; JWT signés ; tokens côté mobile dans Keychain/Keystore (flutter_secure_storage) |
| A03 | Injection | Spring Data JPA / Hibernate (requêtes paramétrées, aucune concaténation SQL) ; Bean Validation sur toutes les entrées ; DTOs séparés des entités |
| A04 | Insecure Design | Architecture en couches (controller/service/repository) ; rate limiting Bucket4j ; expiration des accords ; séparation stricte DTO / entité |
| A05 | Security Misconfiguration | GlobalExceptionHandler (aucune stacktrace exposée) ; CORS explicite ; session STATELESS ; secrets en variables d'environnement (jamais commités) |
| A06 | Vulnerable & Outdated Components | **Scan de dépendances (APP-113)** : OWASP Dependency-Check côté Maven (`mvn verify -Psecurity`, échec si CVSS >= 7) ; `flutter pub outdated` côté Flutter |
| A07 | Identification & Auth Failures | JWT court (15 min) + refresh avec rotation + blacklist Redis ; rate limiting login (5/min) et register (3/min) ; BCrypt |
| A08 | Software & Data Integrity Failures | Validation du vrai type MIME des fichiers (Apache Tika, pas l'extension) ; webhooks idempotents |
| A09 | Security Logging & Monitoring Failures | Logback JSON structuré + MDC ; aucune donnée personnelle ni token dans les logs ; Sentry |
| A10 | Server-Side Request Forgery (SSRF) | Surface quasi nulle : le seul appel sortant (géocodage Nominatim) n'utilise pas d'URL fournie par l'utilisateur |

## Détail A01 — Élévation de privilèges vers ADMIN (APP-121)

### La faille trouvée

`RegisterRequest` acceptait un `UserRole` libre et `AuthService` le recopiait tel quel :
le formulaire d'inscription ne propose pas ADMIN, mais l'API l'acceptait.

    curl -X POST .../api/v1/auth/register       -d '{"email":"x@y.fr","password":"Password123!",
           "firstName":"A","lastName":"B","role":"ADMIN"}'

Le compte était créé avec **tous les droits d'administration** : liste et sanction des
comptes, modération, tableau de bord. Classée A01, la faille la plus fréquente du Top 10.

**Cause racine** : avoir pris un contrôle d'interface pour un contrôle de sécurité.
Masquer une option dans un formulaire n'empêche personne d'appeler l'API directement.
Toute donnée venant du client doit être validée côté serveur, sans exception.

### La correction

`AuthService.register` refuse explicitement le rôle ADMIN (HTTP 403) et journalise la
tentative. Les comptes administrateur s'attribuent **uniquement en base**, par une
personne disposant déjà d'un accès au serveur : le privilège ne peut pas s'auto-attribuer
par un parcours applicatif.

### Les quatre portes vérifiées

| Vecteur | Résultat |
|---|---|
| Inscription avec `role: ADMIN` | **403** + aucun compte créé (vérifié en base) |
| Routes `/admin/**` avec un compte étudiant | **403** sur les 6 routes |
| Routes `/admin/**` sans authentification | **401** |
| Changement de mode vers ADMIN | Refusé, rôle inchangé en base |

Tests : `AdminIntegrationTest` (Testcontainers, PostgreSQL réel) et
`AuthServiceTest#shouldRefuserUneInscriptionAvecLeRoleAdmin`.

Ces tests rejouent l'attaque plutôt que de vérifier une implémentation : ils resteront
valables même si le code change de forme.

### Défenses déjà en place, confirmées par l'audit

- **`@PreAuthorize` porté par la classe** `AdminController` : toute route ajoutée est
  protégée par construction — impossible d'oublier l'annotation sur un nouvel endpoint.
- **Le JWT ne fait pas autorité sur le rôle.** Il est signé (falsification impossible),
  et `JwtAuthFilter` recharge l'utilisateur depuis la base à chaque requête : c'est le
  rôle en base qui décide. Un rôle retiré prend effet immédiatement, sans attendre
  l'expiration du token.
- **Deux niveaux de révocation** : blacklist du JTI au logout, et révocation en bloc de
  tous les tokens d'un compte suspendu (clé Redis) + invalidation de ses refresh tokens.
  Sans le second niveau, une suspension n'aurait tenu que le temps de l'access token.
- **Un administrateur ne peut ni se sanctionner ni sanctionner un pair**
  (`AdminService.checkNotAdmin`) : un compte admin compromis ne peut pas neutraliser
  les autres administrateurs.
- **Traçabilité** : chaque sanction est journalisée avec l'identifiant de l'admin et
  celui de la cible — jamais l'adresse e-mail (règle « aucune PII dans les logs »).

### Limites assumées pour cette version

- **Pas de rate limiting sur `/admin/**`** : Bucket4j ne couvre que `/auth/login` et
  `/auth/register`. Un token administrateur volé permettrait d'énumérer la base sans
  limite. Exploitation conditionnée à la compromission préalable d'un admin.
- **Pas de second facteur pour les comptes administrateur.** Un back-office réel le
  justifierait ; hors périmètre de cette version.
- **`CORS_ALLOWED_ORIGINS=*` par défaut** : valable en développement, à restreindre
  impérativement en production.

## Détail A06 — Scan de dépendances (APP-113)

### Backend (Maven)
Plugin **OWASP Dependency-Check** configuré dans un profil dédié du `pom.xml` :

    mvn verify -Psecurity

- Croise chaque dépendance avec la base NVD (CVE connues).
- Produit `target/dependency-check-report.html` (+ JSON).
- `failBuildOnCVSS=7` : le build échoue si une faille de gravité haute est détectée.
- Remarque : le premier scan télécharge la base NVD (long). Une clé API NVD accélère
  fortement le scan — à fournir en variable d'environnement dans la CI, jamais en dur.

### Frontend (Flutter)

    flutter pub outdated

Liste les paquets obsolètes (versions courante / résoluble / dernière) et signale les
paquets abandonnés (« discontinued »). Sert de contrôle régulier des composants tiers.

### Intégration CI/CD
Le scan est prévu pour s'exécuter périodiquement dans la pipeline (plutôt qu'à chaque
push, à cause de la durée du téléchargement NVD), afin de détecter les nouvelles CVE
publiées sur des dépendances déjà présentes.

## Remédiation des dépendances (APP-114)

Après le premier scan (APP-113), une campagne de montée de versions a été menée puis
revalidée par la suite de tests complète (429 tests, 0 échec) et un nouveau scan.

Montées de version appliquées (patchs sûrs, sans changement de ligne majeure sauf Tika) :

    Spring Boot          3.5.14  -> 3.5.16
    firebase-admin       9.4.2   -> 9.10.0
    tika-core            2.9.2   -> 3.3.1   (majeure : API detect() inchangée)
    google-auth-library  (ajout explicite 1.49.0 : firebase 9.x ne l'expose plus
                          transitivement à la compilation)

Résultat mesuré :

    Dépendances vulnérables (CVE >= 7)   ~25  ->  11
    CVE >= 7 (dont deux 10.0 sur netty)  ~90  -> ~24

Clusters éliminés : netty (22 CVE), spring-core, spring-web, spring-security,
jackson-databind, grpc-core, grpc-protobuf, postgresql, tika-core.

### CVE résiduelles (11 dépendances) — analyse

Toutes les CVE restantes sont soit gérées par un dépôt amont (BOM Spring Boot,
Firebase Admin SDK), soit sans correctif publié à ce jour :

- **Gérées par le BOM Spring Boot** (tomcat-embed-core, log4j-api, httpcore,
  httpcore5, angus-activation) : versions imposées par Spring Boot 3.5.16 ; CVE
  publiées en 2026, pas encore corrigées dans la ligne 3.5.x. Résolution attendue
  au prochain patch Spring Boot — suivi actif.
- **Transitives Firebase/Google** (grpc-context, kotlin-stdlib, protobuf-java,
  opentelemetry-*-alpha) : tirées par firebase-admin, non exposées directement par
  l'application ; les composants opentelemetry sont en version alpha (incubator),
  utilisés en interne par le SDK Google.

Aucune de ces dépendances n'est appelée directement par le code applicatif ; la
seule dépendance directe traitant des données non fiables (Tika, validation des
fichiers uploadés) a été corrigée.

### Décision sur les résidus

Le seuil `failBuildOnCVSS=7` reste volontairement strict : le scan « échoue » tant
que des CVE hautes subsistent, ce qui maintient une pression de mise à jour. Le scan
étant exécuté périodiquement (et non à chaque push), cela ne bloque pas le
développement courant. Une montée de Spring Boot et de firebase-admin sera refaite
dès que les correctifs amont seront publiés.

## Notes de justification pour la soutenance

- **CSRF désactivé** : choix volontaire et correct pour une API REST stateless
  authentifiée par JWT (pas de cookie de session, donc pas de vecteur CSRF classique).
- **A01 (APP-121)** : la faille d'élévation de privilèges a été trouvée en auditant le
  code après coup, pas par la suite de tests — celle-ci mockait les couches basses et ne
  touchait jamais PostgreSQL. Enseignement retenu : les contrôles d'accès se testent en
  bout en bout, contre une vraie base, en rejouant l'attaque.
- Le scan A06 **détecte** les composants vulnérables/obsolètes ; leur mise à jour est
  une décision distincte (risque de régression), pilotée ticket par ticket.
