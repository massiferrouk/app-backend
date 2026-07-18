# Sécurité StudUp — Couverture OWASP Top 10 (2021)

Document de synthèse pour la compétence RNCP C2.2.3.
Chaque faille du Top 10 OWASP est mise en regard des mesures concrètes du projet.

| # | Faille OWASP 2021 | Mesures mises en œuvre dans StudUp |
|---|---|---|
| A01 | Broken Access Control | Ownership checks avant chaque modification de ressource ; `.anyRequest().authenticated()` ; endpoints Actuator réservés au rôle ADMIN ; JWT stateless |
| A02 | Cryptographic Failures | Mots de passe hashés BCrypt ; refresh tokens stockés hashés (SHA-256) ; JWT signés ; tokens côté mobile dans Keychain/Keystore (flutter_secure_storage) |
| A03 | Injection | Spring Data JPA / Hibernate (requêtes paramétrées, aucune concaténation SQL) ; Bean Validation sur toutes les entrées ; DTOs séparés des entités |
| A04 | Insecure Design | Architecture en couches (controller/service/repository) ; rate limiting Bucket4j ; expiration des accords ; séparation stricte DTO / entité |
| A05 | Security Misconfiguration | GlobalExceptionHandler (aucune stacktrace exposée) ; CORS explicite ; session STATELESS ; secrets en variables d'environnement (jamais commités) |
| A06 | Vulnerable & Outdated Components | **Scan de dépendances (APP-113)** : OWASP Dependency-Check côté Maven (`mvn verify -Psecurity`, échec si CVSS >= 7) ; `flutter pub outdated` côté Flutter |
| A07 | Identification & Auth Failures | JWT court (15 min) + refresh avec rotation + blacklist Redis ; rate limiting login (5/min) et register (3/min) ; BCrypt |
| A08 | Software & Data Integrity Failures | Validation du vrai type MIME des fichiers (Apache Tika, pas l'extension) ; webhooks idempotents |
| A09 | Security Logging & Monitoring Failures | Logback JSON structuré + MDC ; aucune donnée personnelle ni token dans les logs ; Sentry |
| A10 | Server-Side Request Forgery (SSRF) | Surface quasi nulle : le seul appel sortant (géocodage Nominatim) n'utilise pas d'URL fournie par l'utilisateur |

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
- Le scan A06 **détecte** les composants vulnérables/obsolètes ; leur mise à jour est
  une décision distincte (risque de régression), pilotée ticket par ticket.
