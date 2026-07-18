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

## Notes de justification pour la soutenance

- **CSRF désactivé** : choix volontaire et correct pour une API REST stateless
  authentifiée par JWT (pas de cookie de session, donc pas de vecteur CSRF classique).
- Le scan A06 **détecte** les composants vulnérables/obsolètes ; leur mise à jour est
  une décision distincte (risque de régression), pilotée ticket par ticket.
