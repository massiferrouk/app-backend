# CLAUDE.md — StudUp
> Fichier lu automatiquement par Claude Code à chaque session.
> Contient le contexte complet du projet, les décisions d'architecture et le backlog complet.

---

## ÉTAT ACTUEL DU PROJET

- [x] Projet Spring Boot créé (Java 21, Spring Boot 3.5.14)
- [x] Flyway V1→V13 migrés avec succès sur Docker local (PostgreSQL 15, port 5433)
- [x] CI/CD GitHub Actions opérationnel (Build & Test automatique sur chaque PR)
- [x] Docker PostgreSQL local configuré (port 5433, user: yuniv, db: yunivdb)
- [x] application.properties configuré avec variables d'environnement
- [x] APP-48 (US-038) Rate limiting Bucket4j terminé et mergé (5 req/min login, 3 req/min register)
- [x] APP-039 (US-039) Configuration CORS Flutter terminé
- [ ] Sprint 1 en cours — prochain ticket : **US-004 Création profil alternant + rythme**

> 💡 À mettre à jour après chaque ticket terminé.

---

## ⛔ FONCTIONNALITÉS EXCLUES DE CETTE VERSION — NE PAS IMPLÉMENTER

Les paiements sont hors périmètre de cette version de l'application. Les tickets suivants sont DÉFINITIVEMENT exclus — ne jamais les coder, ni créer de tests, ni de notifications liées :

- US-025 Paiement loyer Stripe Connect
- US-026 Caution sécurisée séquestrée
- US-027 Abonnement Premium Stripe Billing
- US-048 Webhooks Stripe idempotents

Les notifications liées aux paiements sont aussi exclues :
- PAIEMENT_RECU, PAIEMENT_ECHOUE, CAUTION_RESTITUEE dans NotificationType

Ne jamais proposer, coder ou tester ces fonctionnalités sans que le développeur le demande explicitement.

---

## ⚠️ MODE PÉDAGOGIQUE — LIS CECI EN PREMIER

**Ce projet est un projet d'apprentissage autant qu'un projet professionnel réel.**
Le développeur est en phase de montée en compétences. Ton rôle n'est pas de générer l'application en entier — ton rôle est d'enseigner tout en construisant.

### Règles absolues de collaboration

**1. Un ticket à la fois — jamais d'avance**
- Attends toujours que le développeur valide et comprend la partie en cours avant de passer à la suivante
- Ne génère JAMAIS plusieurs tickets en une seule réponse sauf si explicitement demandé
- Si un ticket est long, découpe-le en étapes numérotées et attends confirmation à chaque étape
- Termine chaque réponse par : "Est-ce que tu veux qu'on passe à l'étape suivante ?" ou "Est-ce que tu as des questions sur ce qu'on vient de faire ?"

**2. Explique tout ce que tu fais — sans exception**
Pour chaque fichier créé ou modifié, explique :
- Ce que fait ce code (en français, clairement)
- Pourquoi on a fait ce choix et pas un autre
- Ce qui se passerait si on ne l'avait pas fait
- Les erreurs fréquentes à éviter sur ce type de code

**3. Distingue le générique du spécifique**
À chaque fois que tu écris du code, indique clairement :
- 🔵 **UNIVERSEL** — ce pattern s'applique dans TOUS les projets Spring Boot / Java. Apprends-le par cœur, tu le retrouveras partout.
- 🟣 **SPÉCIFIQUE AU PROJET** — ce code est adapté aux besoins de cette plateforme. Il suit la même structure universelle mais avec notre logique métier.

Exemple de formulation attendue :
> "🔵 UNIVERSEL — La classe @RestControllerAdvice est le pattern standard pour gérer les erreurs dans tous les projets Spring Boot. Tu en auras une dans chaque projet que tu feras.
> 🟣 SPÉCIFIQUE — Les types d'erreurs qu'on y gère ici (DuplicateEmailException, ResourceNotFoundException) sont propres à notre plateforme."

**4. Donne le contexte avant le code**
Avant d'écrire du code, explique toujours :
- Où ce fichier se place dans l'architecture (quel package, quel rôle)
- De quoi il dépend (quels autres fichiers il utilise)
- Ce qu'il va permettre de faire dans l'application

**5. Après chaque bloc de code — vérifie la compréhension**
Après avoir écrit un fichier ou une méthode, pose systématiquement une question de compréhension comme :
- "Est-ce que tu comprends pourquoi on a utilisé @Transactional ici ?"
- "Tu vois pourquoi on retourne un DTO et pas directement l'entité JPA ?"
- "Est-ce que cette annotation te parle ou tu veux que je l'explique plus en détail ?"

**6. Explique les erreurs et les corrections**
Si le code produit une erreur :
- Ne te contente pas de corriger — explique POURQUOI ça a planté
- Explique comment reconnaître ce type d'erreur à l'avenir
- Donne le pattern mental pour éviter cette erreur dans d'autres contextes

**7. Rappelle les bonnes pratiques de façon pédagogique**
Quand tu appliques une bonne pratique (sécurité, performance, maintenabilité), dis-le explicitement :
> "💡 Bonne pratique — On ne stocke jamais le mot de passe en clair. On utilise BCrypt qui hash le mot de passe de façon irréversible. C'est une règle de sécurité fondamentale dans TOUS les projets qui gèrent des comptes utilisateurs."

**8. Génère une fiche de synthèse après chaque ticket terminé — obligatoire**

Quand un ticket est entièrement terminé (code + tests + PR mergée), génère obligatoirement une fiche de synthèse structurée comme suit. Cette fiche sert à la fois de document d'apprentissage personnel et de support de soutenance RNCP.

> ⚠️ FORMAT WORD OBLIGATOIRE — Le développeur copie ces fiches dans un fichier Word.
> Ne jamais utiliser : bordures Unicode (━━━), blocs de code avec backticks, caractères spéciaux de mise en forme.
> Toujours utiliser : texte pur, tirets simples pour les puces, sections séparées par une ligne vide, emojis simples autorisés pour les titres de section.
> Objectif : collé dans Word sans aucune retouche manuelle.

Structure à respecter :

FICHE DE SYNTHÈSE — [APP-XXX] Nom du ticket

CE QU'ON A CONSTRUIT

[2-3 phrases simples décrivant ce que ce ticket apporte à l'application du point de vue utilisateur et technique]

FICHIERS CRÉÉS / MODIFIÉS

- NomFichier.java — [rôle en une ligne]
- NomFichier.java — [rôle en une ligne]

PATTERNS UNIVERSELS APPRIS

- [Pattern 1] : [explication courte pourquoi c'est universel]
- [Pattern 2] : [explication courte]
- [Pattern 3] : [explication courte]

DÉCISIONS SPÉCIFIQUES AU PROJET

- [Décision 1] : [pourquoi on a fait ça pour Yuniv]
- [Décision 2] : [contexte métier ou technique spécifique]

POINTS CLÉS À RETENIR POUR LA SOUTENANCE

1. [Point technique défendable]
2. [Choix d'architecture ou de sécurité]
3. [Bonne pratique appliquée]

QUESTIONS DE JURY — COMMENT RÉPONDRE

Question : "[Question directe que le jury pourrait poser]"
Réponse : "[Réponse courte, claire, défendable]"

Question : "[Deuxième question possible]"
Réponse : "[Réponse]"

Question : "[Troisième question possible]"
Réponse : "[Réponse]"

ERREURS FRÉQUENTES SUR CE TYPE DE CODE

- [Erreur 1] : [comment l'éviter]
- [Erreur 2] : [comment l'éviter]

CE QUE CE TICKET DÉBLOQUE

- [APP-XXX] peut maintenant être démarré
- [APP-XXX] dépendait de ce ticket

> Le développeur peut copier cette fiche dans un document Word personnel pour constituer sa base de connaissances au fil des sprints. En fin de projet, il aura une doc complète de tout ce qu'il a appris et construit — idéale pour la soutenance RNCP.

**9. Guide Git à chaque étape — sans exception**
Git fait partie intégrante du workflow. À chaque moment clé, indique exactement les commandes Git à exécuter et explique pourquoi.

Moments où tu dois donner les commandes Git :

- **Début de chaque ticket** → créer une branche feature
  ```bash
  git checkout -b feature/US-001-inscription-email
  ```
  > 🔵 UNIVERSEL — On ne code jamais directement sur main. Chaque ticket = une branche. C'est la règle dans tous les projets professionnels.

- **Après chaque étape significative** (un fichier terminé et testé) → commit intermédiaire
  ```bash
  git add src/main/java/com/.../AuthService.java
  git commit -m "feat(APP-11): ajout AuthService avec inscription et validation email"
  #                  ^^^^^^ clé Jira dans le message = lien automatique dans Jira
  ```
  > 💡 Convention des commits : feat / fix / test / refactor / docs / chore + (clé Jira) + message clair
  > Inclure la clé Jira dans le message de commit permet à Jira d'afficher les commits directement sur le ticket.

- **Fin de ticket** (code + tests + vérification) → push + pull request
  ```bash
  git push origin feature/US-001-inscription-email
  # Puis créer une Pull Request sur GitHub vers main
  ```

- **Merge validé** → revenir sur main et mettre à jour
  ```bash
  git checkout main
  git pull origin main
  ```

- **Si conflit** → expliquer comment le résoudre étape par étape, ne pas juste donner la commande

Convention de nommage des branches à respecter :
- `feature/APP-XX-nom-court` — nouvelles fonctionnalités (APP-XX = clé Jira réelle, demandée au développeur)
- `fix/APP-XX-description` — corrections de bugs liés à un ticket Jira
- `hotfix/APP-XX-description` — corrections urgentes en production
- `refactor/nom-court` — refactorisations sans ticket Jira associé

> ⚠️ La clé Jira (APP-XX) est générée automatiquement par Jira. Elle est différente de l'ID interne (US-001).
> Ne jamais inventer ni supposer cette clé — toujours la demander au développeur avant de créer la branche.

Convention des messages de commit (Conventional Commits) :
- `feat(scope): description` — nouvelle fonctionnalité
- `fix(scope): description` — correction de bug
- `test(scope): description` — ajout ou modification de tests
- `refactor(scope): description` — refactoring sans changement de comportement
- `docs(scope): description` — documentation
- `chore(scope): description` — tâche technique (config, deps...)

> 🔵 UNIVERSEL — Ces conventions s'appliquent dans tous les projets professionnels. Le journal des versions se génère automatiquement à partir de ces messages dans beaucoup d'équipes.

**10. Protège les secrets et vérifie le .gitignore — règle absolue**
Avant le premier commit de chaque session, vérifie systématiquement que les fichiers sensibles ne sont pas trackés par Git.

Fichiers qui ne doivent JAMAIS être commités :
```
.env
.env.local
.env.staging
.env.production
application-local.properties
application-secret.properties
src/main/resources/firebase-service-account.json
*.key
*.pem
```

À faire une seule fois au démarrage du projet :
```bash
# Vérifier que .gitignore couvre bien ces fichiers
cat .gitignore

# Vérifier qu'aucun secret n'est déjà tracké par erreur
git status
git ls-files | grep -E "\.env|secret|key|credentials"
```

Si un secret a été commité par erreur :
```bash
# Ne JAMAIS faire git push dans ce cas
# Supprimer le fichier du tracking sans le supprimer du disque
git rm --cached nom-du-fichier
git commit -m "chore: remove accidentally tracked secret file"
# Ensuite révoquer immédiatement la clé compromise (Stripe, Firebase, etc.)
```

> 🔵 UNIVERSEL — Un secret commité sur GitHub, même supprimé ensuite, peut avoir été scanné par des bots en quelques secondes. C'est l'une des erreurs les plus fréquentes et les plus dangereuses des développeurs débutants. Les clés Stripe ou Firebase compromises peuvent entraîner des pertes financières réelles.

> 💡 Bonne pratique — Installe `git-secrets` ou configure GitHub Secret Scanning sur ton repo pour être alerté automatiquement si un secret est détecté dans un commit.

---

### Format de réponse attendu

Pour chaque étape, structure ta réponse ainsi :

```
📍 OÙ ON EN EST
[Sprint X — Ticket APP-XXX — Étape N/total]

🎯 CE QU'ON VA FAIRE
[Explication claire en 2-3 phrases de ce qu'on va coder]

🔵 UNIVERSEL / 🟣 SPÉCIFIQUE
[Contextualisation du pattern]

💻 LE CODE
[Code avec commentaires en français]

💡 POURQUOI CE CHOIX
[Explication des décisions]

✅ POUR VÉRIFIER QUE ÇA MARCHE
[Commande ou test à lancer]

🗂️ GIT
[Commandes Git à exécuter à ce stade avec explication]

➡️ PROCHAINE ÉTAPE
[Ce qu'on fera ensuite — mais on attend la validation]
```

Quand le ticket est **entièrement terminé** (dernière étape validée + PR mergée) :
ajoute automatiquement la fiche de synthèse complète définie dans la règle 8.

---

## 0. COMMENT DÉMARRER UNE SESSION

Quand le développeur ouvre Claude Code, commence TOUJOURS par ces 3 étapes avant d'écrire la moindre ligne de code :

**Étape 1 — Identifier le ticket du jour**
Le développeur te dira sur quel ticket on travaille (ex : "on attaque APP-001").
Si ce n'est pas précisé, demande-lui : "Sur quel ticket on travaille aujourd'hui ?"
Ne commence jamais de toi-même sans confirmation.

**Étape 2 — Vérifier l'environnement**
Avant de coder, vérifie que l'environnement est prêt :
```bash
java -version          # doit afficher Java 21
mvn -version           # doit afficher Maven 3.x
docker info            # doit être lancé (pour Testcontainers)
git status             # doit être sur main, working tree clean
```

**Étape 3 — Récupérer la clé Jira et créer la branche Git**
Avant la première ligne de code, demande TOUJOURS :
> "Quelle est la clé Jira de ce ticket ? (ex : APP-11 — visible dans l'URL du ticket sur Jira)"

Ensuite crée la branche avec cette clé :
```bash
git checkout -b feature/APP-XX-nom-court-du-ticket
```
Ne commence jamais à coder sans avoir créé la branche avec la bonne clé Jira.

---

### Ordre des tickets par sprint

L'ordre d'implémentation à l'intérieur de chaque sprint est imposé par les dépendances techniques.
Respecte cet ordre strictement — ne saute pas d'étape.

#### Sprint 1 — dans cet ordre obligatoire
```
1.  US-039  Configuration CORS Flutter           (2 SP)  → prérequis de tout appel HTTP
2.  US-037  GlobalExceptionHandler                (3 SP)  → prérequis de tous les controllers
3.  US-044  Migrations Flyway + seed data         (5 SP)  → prérequis de tout accès BDD
4.  US-045  Logging structuré JSON Logback        (3 SP)  → prérequis du monitoring
5.  US-001  Inscription email/password            (5 SP)  → dépend de 037 + 044
6.  US-002  Connexion JWT + refresh token         (5 SP)  → dépend de 001
7.  US-038  Rate limiting Bucket4j                (5 SP)  → dépend de 002 (protège /auth/login)
8.  US-003  Upload vérification document identité (8 SP)  → dépend de 001 + 002
9.  US-004  Création profil alternant + rythme    (8 SP)  → dépend de 001 + 002
10. US-005  Création profil propriétaire          (5 SP)  → dépend de 001 + 002
11. US-017  Génération calendrier alternance      (8 SP)  → dépend de 004
12. US-007  Publication logement avec photos      (8 SP)  → dépend de 001 + 002
13. US-011  Association logement aux deux villes  (3 SP)  → dépend de 004 + 007
14. US-047  Pipeline CI/CD GitHub Actions         (5 SP)  → peut se faire en parallèle
```

#### Sprint 2 — dans cet ordre obligatoire
```
1.  US-040  Ownership checks @PreAuthorize        (5 SP)  → prérequis sécurité de tout Sprint 2
2.  US-041  Validation fichiers Apache Tika        (5 SP)  → améliore US-003 du Sprint 1
3.  US-042  JWT blacklist via Redis                (3 SP)  → renforce US-002 du Sprint 1
4.  US-043  Spring Actuator health checks          (3 SP)  → monitoring opérationnel
5.  US-008  Recherche logements avec filtres       (5 SP)  → dépend de US-007
6.  US-009  Détail d'un logement                  (3 SP)  → dépend de US-008
7.  US-010  Gestion des disponibilités             (5 SP)  → dépend de US-007
8.  US-017  (si pas terminé Sprint 1)
9.  US-012  Algorithme de compatibilité           (13 SP) → dépend de US-004 + US-017
10. US-013  Suggestions de matching               (8 SP)  → dépend de US-012
11. US-014  Échange partiel optimisé              (13 SP) → dépend de US-012
12. US-015  Colocation tournante                  (8 SP)  → dépend de US-012
13. US-018  Calendrier compatibilité colorisé     (8 SP)  → dépend de US-012
14. US-006  Dashboard admin utilisateurs          (8 SP)  → dépend de US-001
15. US-046  Jobs planifiés + ShedLock             (5 SP)  → dépend de US-002 + BDD opérationnelle
```

#### Sprint 3 — dans cet ordre obligatoire
```
1.  US-016  Envoi et gestion d'un accord         (5 SP)  → dépend de US-012 + US-013
2.  US-019  Override manuel du calendrier         (5 SP)  → dépend de US-017 + US-012
3.  US-021  Messagerie WebSocket STOMP            (8 SP)  → dépend de US-001 + US-016
4.  US-022  Notifications push FCM               (5 SP)  → dépend de US-001
5.  US-033  Notifications push personnalisées     (8 SP)  → dépend de US-022
6.  US-025  Paiement loyer Stripe Connect        (13 SP) → dépend de US-016 (accord signé)
7.  US-026  Caution sécurisée séquestrée         (8 SP)  → dépend de US-025
8.  US-027  Abonnement Premium Stripe Billing     (5 SP)  → dépend de US-025
9.  US-048  Webhooks Stripe idempotents           (5 SP)  → dépend de US-025 + US-026 + US-027
```

#### Sprint 4 — dans cet ordre recommandé
```
1.  US-030  Dépôt d'avis post-échange            (5 SP)  → dépend de US-016 (accord terminé)
2.  US-031  Score de réputation utilisateur       (5 SP)  → dépend de US-030
3.  US-032  Modération des avis                   (3 SP)  → dépend de US-030
4.  US-034  Dashboard propriétaire               (5 SP)  → dépend de US-025 + US-030
5.  US-035  Dashboard alternant                  (5 SP)  → dépend de US-012 + US-016
6.  US-023  Modération messagerie                (5 SP)  → dépend de US-021
7.  US-028  Dashboard revenus admin              (5 SP)  → dépend de US-025
8.  US-049  Suite tests intégration Testcontainers (8 SP) → couvre tout ce qui précède
9.  US-024  Photos dans les messages             (5 SP)  → dépend de US-021
10. US-029  API B2B CFA et écoles                (8 SP)  → indépendant
11. US-020  Export calendrier iCal               (3 SP)  → dépend de US-017
12. US-036  Email résumé hebdomadaire            (3 SP)  → dépend de US-046
```

> 💡 Si on finit un sprint en avance, on ne commence pas le sprint suivant — on améliore la couverture de tests et on optimise les performances des fonctionnalités déjà livrées.

---

## INSTRUCTION GÉNÉRALE

Tu travailles sur **StudUp**, une plateforme mobile de logement dédiée aux étudiants et alternants en France. Traite ce projet comme un projet professionnel réel. Chaque décision technique a déjà été prise et documentée — ne les remet pas en question sauf si tu identifies un problème technique bloquant.

**Règles de travail :**
- Toujours écrire des tests unitaires avec chaque service (JUnit 5 + Mockito)
- Toujours écrire des tests controller avec chaque controller (@WebMvcTest + MockMvc) — SANS EXCEPTION, même si le développeur ne le demande pas
- Les tests sont à produire en deux couches systématiques pour chaque ticket :
  1. XxxServiceTest — logique métier, ownership, exceptions, cas nominaux et cas d'erreur
  2. XxxControllerTest — codes HTTP, sérialisation JSON, sécurité (401 sans auth, 400 validation)
  Si d'autres couches sont nécessaires (algorithm, scheduler, config...) elles s'ajoutent en plus.
  Ne jamais livrer un ticket sans ces deux fichiers de test.
- Toujours valider les entrées avec Bean Validation (@NotNull, @Size, @Email...)
- Toujours utiliser des DTOs séparés des entités JPA
- Jamais de stacktrace exposée dans les réponses API (GlobalExceptionHandler)
- Jamais de données personnelles dans les logs
- Toujours vérifier l'ownership avant toute modification de ressource
- Les commentaires de code sont en français, le code en anglais

---

## 1. DESCRIPTION DU PROJET

**Nom de l'application : StudUp**

**Problème résolu :** Les alternants partagent leur temps entre deux villes selon un rythme fixe. Ils paient souvent deux loyers. La plateforme permet à deux alternants aux rythmes inverses d'échanger leurs logements gratuitement.

**4 mécanismes :**
1. **Échange total** — rythmes inverses, échange gratuit
2. **Échange partiel** — algorithme identifie les semaines sans chevauchement
3. **Colocation tournante** — même rythme, loyer divisé par deux
4. **Location classique** — propriétaires vers étudiants vérifiés

**3 profils :** ALTERNANT / ETUDIANT / PROPRIETAIRE (+ ADMIN)

**Business model :** Commission 8% locations | Premium 4,99€/mois | B2B CFA 2k-10k€/an | Échange gratuit

---

## 1.1 ALGORITHME DE MATCHING — LOGIQUE COMPLÈTE

### Philosophie fondamentale

L'algorithme ne décide pas. Il analyse, calcule et affiche.
L'utilisateur choisit toujours.

L'algo tourne au moment de la recherche.
Il calcule les compatibilités pour tous les profils dont les villes ont au moins une ville en commun
avec l'utilisateur connecté, les affiche classés par score décroissant, et l'utilisateur choisit.

Après la signature de l'accord, l'app n'interfère plus dans l'organisation des deux utilisateurs.

---

### Deux niveaux de match

MATCH ACTIF
Les deux alternants ont les logements qu'il faut. Un accord peut être signé immédiatement.
Exemple : A a un logement à Paris, B a un logement à Lyon, rythmes inverses → échange possible maintenant.

MATCH POTENTIEL
Les profils sont compatibles mais il manque un ou plusieurs logements.
L'app affiche quand même le profil avec un message du type :
- "Si tu trouves un logement à Lyon, vous pourriez faire un échange avec cet alternant."
- "Vous avez le même rythme. Si l'un de vous lâche son logement à Paris, vous pourriez
  partager un logement à Paris et un à Lyon."

L'app est là pour mettre les gens en contact même avant qu'ils aient résolu leur problème de logement.
Sans l'app, chacun galère dans son coin sans savoir qu'un autre alternant est dans la situation complémentaire.

---

### Condition préalable au matching

Deux profils sont comparés par l'algo UNIQUEMENT s'ils ont au moins une ville en commun.

Exemple de profils NON comparés :
- A : villeA=Paris, villeB=Lyon
- B : villeA=Bordeaux, villeB=Marseille
→ Aucune ville en commun → pas de match, B n'apparaît jamais dans les suggestions de A.

Exemple de profils comparés :
- A : villeA=Paris, villeB=Lyon
- B : villeA=Lyon, villeB=Paris
→ Villes en commun → l'algo calcule la compatibilité.

---

### Inputs de l'algorithme

Pour chaque alternant, l'algorithme lit :
- villeA : ville école
- villeB : ville entreprise
- List<AlternanceSchedule> : liste de semaines avec label 'A' (à l'école) ou 'B' (en entreprise)
- dateDebut et dateFin de l'alternance
- logements publiés et leurs champs villeAssociee (peut être vide)

---

### Traitement semaine par semaine

Pour chaque semaine de la période commune entre l'alternant A et l'alternant B,
l'algorithme compare leurs positions et produit un des 4 outputs :

OUTPUT 1 - ECHANGE
Condition : A et B sont dans des villes différentes ET les villes sont inversées.
A est dans la villeA de B, B est dans la villeA de A.
Leurs logements sont libres exactement quand l'autre en a besoin.
Couleur calendrier : VERT FONCE #27AE60
Label affiché : "Échange"

OUTPUT 2 - COLOCATION
Condition : A et B sont dans la MEME ville en même temps de façon récurrente sur toute la période.
Ils peuvent partager un seul logement dans cette ville et diviser le loyer par deux.
Couleur calendrier : BLEU CLAIR #3498DB
Label affiché : "Coloc possible"

OUTPUT 3 - CHEVAUCHEMENT
Condition : A et B sont dans la MEME ville en même temps mais de façon ponctuelle.
Ni échange ni colocation structurelle possible cette semaine.
L'app l'affiche dans le calendrier. C'est à eux de gérer entre eux si accord signé.
Couleur calendrier : ORANGE #F39C12
Label affiché : "Chevauchement"

OUTPUT 4 - INCOMPATIBLE
Ne se produit pas au niveau d'une semaine individuelle si les profils ont été présélectionnés
avec au moins une ville en commun. La condition INCOMPATIBLE s'applique au niveau du profil entier :
aucune ville en commun → ces profils ne se voient jamais dans les suggestions.
Couleur calendrier : GRIS #ECF0F1
Label affiché : ""

---

### Les 3 cas principaux

CAS 1 - MEME RYTHME, VILLES INVERSEES
AccordType : ECHANGE_TOTAL
Score : >= 0.90

Exemple :
  A rythme 3S Paris / 1S Lyon
  B rythme 3S Lyon  / 1S Paris

  Semaine 1 : A à Paris, B à Lyon  → ECHANGE vert
  Semaine 2 : A à Paris, B à Lyon  → ECHANGE vert
  Semaine 3 : A à Paris, B à Lyon  → ECHANGE vert
  Semaine 4 : A à Lyon,  B à Paris → ECHANGE vert

Score : 4/4 = 1.00

Résumé affiché : "4 sem d'échange - 0 sem coloc - 0 sem chevauchement"
Message : "Vos rythmes sont parfaitement complémentaires. Vous pouvez échanger vos logements
sur toutes les semaines."

Ce que ça veut dire concrètement :
Quand A est à Paris il loge dans le logement de B.
Quand B est à Paris il loge dans le logement de A.
Chacun paie zéro loyer supplémentaire.

---

CAS 2 - MEME RYTHME, MEMES VILLES
AccordType : COLOCATION_TOURNANTE
Score : 0 (pas de semaines d'échange)

Exemple :
  A rythme 3S Paris / 1S Lyon
  B rythme 3S Paris / 1S Lyon (identique)

  Semaine 1 : A à Paris, B à Paris → COLOCATION bleu
  Semaine 2 : A à Paris, B à Paris → COLOCATION bleu
  Semaine 3 : A à Paris, B à Paris → COLOCATION bleu
  Semaine 4 : A à Lyon,  B à Lyon  → COLOCATION bleu

Résumé affiché : "0 sem d'échange - 4 sem coloc - 0 sem chevauchement"
Message : "Vous avez exactement le même rythme. Vous pouvez partager un logement à Paris et
un logement à Lyon."

Ce que ça veut dire concrètement :
Ils gardent un seul logement à Paris pour les deux et un seul logement à Lyon pour les deux.
L'un des deux lâche son logement dans une ville, l'autre dans l'autre ville.
Chacun paie la moitié du loyer de chaque ville au lieu de payer deux loyers pleins.

Calcul économie affiché :
  Sans colocation : loyerVilleA + loyerVilleB = X euros
  Avec colocation : (loyerVilleA / 2) + (loyerVilleB / 2) = Y euros
  Économie : X - Y = Z euros/mois chacun

---

CAS 3 - RYTHMES DIFFERENTS
AccordType : ECHANGE_PARTIEL
Score : 0.60 à 0.89

Exemple :
  A rythme 3S Paris / 1S Lyon
  B rythme 1S Paris / 1S Lyon alterné (SEMAINE_1_1)

  Semaine 1 : A à Paris, B à Lyon  → ECHANGE vert
  Semaine 2 : A à Paris, B à Paris → CHEVAUCHEMENT orange
  Semaine 3 : A à Paris, B à Lyon  → ECHANGE vert
  Semaine 4 : A à Lyon,  B à Paris → ECHANGE vert

Score : 3/4 = 0.75

Résumé affiché : "3 sem d'échange - 0 sem coloc - 1 sem chevauchement"
Message : "Vos rythmes sont compatibles à 75%. 3 semaines d'échange possibles.
1 semaine de chevauchement : vous gérez ça entre vous."

---

### Colocation — ce que ça veut dire vraiment

La colocation tournante ne signifie pas juste "ils sont dans la même ville en même temps".

Ça veut dire qu'ils vont partager les logements :
- Un seul logement à Paris pour les deux
- Un seul logement à Lyon pour les deux

Concrètement l'un des deux lâche son logement dans une ville, l'autre lâche le sien dans l'autre ville.
Ensemble ils n'ont plus que deux logements au lieu de quatre et chacun paie moitié moins.

L'app les met en contact et leur montre le calcul d'économie. C'est eux qui décident comment s'organiser.

---

### Gestion des semaines de chevauchement ORANGE

Les semaines orange sont affichées dans le calendrier de compatibilité au moment de la recherche uniquement.

L'app informe l'utilisateur via le résumé :
"3 sem d'échange - 1 sem coloc - 2 sem à gérer entre vous"

Après la signature de l'accord, l'organisation des semaines de chevauchement est laissée entièrement
aux deux utilisateurs. L'app n'interfère pas.

Pas de notification proactive sur les semaines orange.
Pas de demande de confirmation semaine par semaine.
L'accord est signé une fois pour toute la période.

---

### Calcul du score

score = nbSemainesEchange / nbSemainesTotalesPeriodeCommune

Résultat : DECIMAL(5,4) entre 0.0000 et 1.0000
Affiché en % côté Flutter : score x 100

Mapping score vers AccordType :
  score >= 0.90 → ECHANGE_TOTAL
  score >= 0.60 → ECHANGE_PARTIEL
  score = 0.00 et nbSemainesColocation > 0 → COLOCATION_TOURNANTE
  score = 0.00 et nbSemainesColocation = 0 → pas de match

Note : la colocation n'entre pas dans le score.
Elle est détectée séparément et affichée comme un type d'accord distinct.

---

### Objet retourné par l'algorithme

MatchingResult {
  double score                         // 0.0000 à 1.0000
  AccordType typePropose               // ECHANGE_TOTAL, ECHANGE_PARTIEL, COLOCATION_TOURNANTE
  boolean isMatchActif                 // true si les logements nécessaires sont publiés
  String messageMatchPotentiel         // null si match actif, sinon message expliquant ce qu'il manque
  List<SemaineCompatibilite> semaines
  int nbSemainesEchange
  int nbSemainesColocation
  int nbSemainesChevauchement
  BigDecimal economieEstimeeMin
  BigDecimal economieEstimeeMax
  String messageResume
}

SemaineCompatibilite {
  LocalDate semaine
  String villeAlternantA
  String villeAlternantB
  CompatibiliteType type    // ECHANGE / COLOCATION / CHEVAUCHEMENT / INCOMPATIBLE
  String couleurHex
  String label
}

---

### Classes Java concernées

algorithm/CompatibilityCalculator.java
Méthode principale :
  MatchingResult calculate(
    AlternantProfile profileA,
    AlternantProfile profileB,
    List<AlternanceSchedule> schedulesA,
    List<AlternanceSchedule> schedulesB
  )

algorithm/PartialExchangeOptimizer.java
Optimise les semaines d'échange partiel.

algorithm/ColocationMatcher.java
Détecte les cas de colocation tournante.
Calcule l'économie estimée selon les loyers déclarés.

algorithm/ScheduleGenerator.java
Génère automatiquement les AlternanceSchedule depuis le RythmeAlternance + dateDebut + dateFin.

---

### Cache Redis

Clé : matching:{profileAId}:{profileBId}
profileAId toujours < profileBId (évite les doublons)
TTL : 1 heure

Invalidation si :
- Modification du rythme
- Modification des villes
- Override manuel d'une semaine

---

### Endpoints concernés

GET /api/v1/matching/suggestions
Retourne les 20 meilleurs matchs de l'utilisateur connecté. Trié par score décroissant.
Inclut matchs actifs ET potentiels.

GET /api/v1/matching/score?user1={id}&user2={id}
Score entre deux profils spécifiques.

GET /api/v1/matching/partial?user1={id}&user2={id}
Détail semaine par semaine pour deux profils.

GET /api/v1/calendrier/compatibilite?user1={id}&user2={id}
Retourne List<SemaineCompatibilite> avec couleurHex.
Utilisé par Flutter pour afficher le calendrier colorisé sur l'écran fiche profil match.

---

### Tests unitaires obligatoires — APP-12

CompatibilityCalculatorTest :
- shouldReturnEchangeTotalWhenRythmsAreInversed()
- shouldReturnEchangePartielWhenPartialOverlap()
- shouldReturnColocationWhenSameRythm()
- shouldReturnZeroScoreWhenNoCompatibility()
- shouldDetectMatchActifWhenBothLogementPublished()
- shouldDetectMatchPotentielWhenOneLogementMissing()
- shouldNeverExceedScoreOfOne()
- shouldHandleEmptySchedules()
- shouldReturnCorrectColorsForEachWeekType()
- shouldCalculateEconomieEstimeeCorrectly()

---

## 2. STACK TECHNIQUE

```
Backend    : Spring Boot 3.5.14 (Java 21)
Sécurité   : Spring Security 6.2.x + JWT (JJWT)
ORM        : Spring Data JPA 3.2.x + Hibernate
Migrations : Flyway 10.x
BDD        : PostgreSQL 15.x
Cache      : Redis 7.x (Lettuce client)
Fichiers   : MinIO (API S3-compatible)
Temps réel : Spring WebSocket + STOMP
Paiements  : Stripe Connect + Stripe Billing
Notifs push: Firebase Admin SDK 9.x (FCM)
Géocodage  : Nominatim (OpenStreetMap)
Monitoring : Spring Actuator + Sentry + Logback JSON
Tests      : JUnit 5 + Mockito + Testcontainers
CI/CD      : GitHub Actions → Docker → Railway
```

---

## 3. STRUCTURE DES PACKAGES

```
src/main/java/com/yuniv/
├── controller/
│   ├── AuthController.java
│   ├── LogementController.java
│   ├── MatchingController.java
│   ├── CalendrierController.java
│   ├── AccordController.java
│   ├── MessageController.java
│   ├── PaiementController.java
│   ├── ReviewController.java
│   ├── NotificationController.java
│   ├── DashboardController.java
│   ├── VerificationController.java
│   └── AdminController.java
│
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   ├── LogementService.java
│   ├── MatchingService.java
│   ├── CalendrierService.java
│   ├── AccordService.java
│   ├── MessageService.java
│   ├── PaymentService.java
│   ├── CautionService.java
│   ├── SubscriptionService.java
│   ├── ReviewService.java
│   ├── ReputationService.java
│   ├── NotificationService.java
│   ├── FCMService.java
│   ├── FileValidationService.java
│   ├── MinioService.java
│   ├── GeocodingService.java
│   └── EmailService.java
│
├── repository/
│   ├── UserRepository.java
│   ├── AlternantProfileRepository.java
│   ├── AlternanceScheduleRepository.java
│   ├── LogementRepository.java
│   ├── AccordRepository.java
│   ├── MessageRepository.java
│   ├── TransactionRepository.java
│   ├── CautionRepository.java
│   ├── ReviewRepository.java
│   ├── NotificationRepository.java
│   └── VerificationDocRepository.java
│
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   ├── AlternantProfile.java
│   │   ├── AlternanceSchedule.java
│   │   ├── Logement.java
│   │   ├── PhotoLogement.java
│   │   ├── Disponibilite.java
│   │   ├── Accord.java
│   │   ├── Message.java
│   │   ├── Conversation.java
│   │   ├── Transaction.java
│   │   ├── Caution.java
│   │   ├── Review.java
│   │   ├── ReputationScore.java
│   │   ├── Notification.java
│   │   ├── NotificationPreference.java
│   │   └── VerificationDoc.java
│   │
│   ├── dto/
│   │   ├── request/      # DTOs entrée (RegisterRequest, LoginRequest...)
│   │   └── response/     # DTOs sortie (UserResponse, MatchingResponse...)
│   │
│   └── enums/
│       ├── UserRole.java             # ALTERNANT, ETUDIANT, PROPRIETAIRE, ADMIN
│       ├── AccordType.java           # ECHANGE_TOTAL, ECHANGE_PARTIEL, COLOCATION_TOURNANTE, LOCATION_CLASSIQUE, HEBERGEMENT_PONCTUEL
│       ├── AccordStatut.java         # EN_ATTENTE, ACCEPTE, REFUSE, EN_COURS, TERMINE, ANNULE, LITIGE
│       ├── LogementType.java         # STUDIO, T1, T2, T3_PLUS, CHAMBRE_COLOC
│       ├── LogementStatut.java       # BROUILLON, ACTIF, SUSPENDU, ARCHIVE
│       ├── RythmeAlternance.java     # SEMAINE_1_1, SEMAINE_3_1, MOIS_1_1, AUTRE
│       ├── DisponibiliteType.java    # LIBRE, OCCUPE, BLOQUE
│       ├── TransactionType.java      # LOYER, COMMISSION, REMBOURSEMENT, ABONNEMENT
│       ├── TransactionStatut.java    # EN_ATTENTE, SUCCES, ECHEC, REMBOURSE
│       ├── CautionStatut.java        # EN_ATTENTE, VERSEE, RESTITUEE, RETENUE_PARTIELLE, RETENUE_TOTALE
│       ├── DocType.java              # CARTE_ETUDIANT, CONTRAT_ALTERNANCE, PIECE_IDENTITE, JUSTIFICATIF_DOMICILE, ACCORD_PROPRIETAIRE
│       ├── DocStatut.java            # EN_ATTENTE, VALIDE, REFUSE
│       ├── ReviewTargetType.java     # USER, LOGEMENT
│       ├── NotificationType.java     # NOUVEAU_MATCH, DEMANDE_ACCORD, ACCORD_ACCEPTE, ACCORD_REFUSE, NOUVEAU_MESSAGE, PAIEMENT_RECU, PAIEMENT_ECHOUE, CAUTION_RESTITUEE, AVIS_RECU, DOCUMENT_VALIDE, DOCUMENT_REFUSE, RAPPEL_DEPART, RAPPEL_ARRIVEE, SYSTEME
│       └── NotificationChannel.java  # PUSH, EMAIL, SMS
│
├── security/
│   ├── JwtAuthFilter.java
│   ├── JwtUtil.java
│   ├── JwtBlacklistService.java
│   ├── CustomUserDetailsService.java
│   └── SecurityConfig.java
│
├── config/
│   ├── WebSocketConfig.java
│   ├── MinioConfig.java
│   ├── StripeConfig.java
│   ├── FirebaseConfig.java
│   ├── RedisConfig.java
│   ├── RateLimitingConfig.java
│   └── WebMvcConfig.java       # CORS
│
├── algorithm/
│   ├── CompatibilityCalculator.java
│   ├── ScheduleGenerator.java
│   ├── PartialExchangeOptimizer.java
│   └── ColocationMatcher.java
│
├── scheduler/
│   ├── AccordExpirationJob.java
│   ├── ReputationCalculationJob.java
│   ├── EmailDigestJob.java
│   └── JwtCleanupJob.java
│
├── webhook/
│   └── StripeWebhookController.java
│
├── event/
│   ├── AccordCreatedEvent.java
│   └── MatchingCalculatedEvent.java
│
└── exception/
    ├── GlobalExceptionHandler.java
    ├── ResourceNotFoundException.java
    ├── UnauthorizedException.java
    ├── DuplicateEmailException.java
    └── PaymentException.java
```

---

## 4. ENTITÉS JPA — CHAMPS COMPLETS

```java
// ─── User (V1) ───────────────────────────────────────────────────────────────
UUID id;
String email  ;                // UNIQUE NOT NULL
String passwordHash           // bcrypt, jamais en clair
UserRole role                 // ENUM : ALTERNANT, ETUDIANT, PROPRIETAIRE, ADMIN
String firstName              // NOT NULL
String lastName               // NOT NULL
String phone
String avatarKey              // clé MinIO
Boolean isVerified            // DEFAULT false
Boolean isActive              // DEFAULT true
String fcmToken               // token Firebase pour push
OffsetDateTime createdAt
OffsetDateTime updatedAt      // mis à jour par trigger set_updated_at
OffsetDateTime deletedAt      // NULL = actif | non NULL = soft delete RGPD

// ─── RefreshToken (V1) ───────────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE
String tokenHash              // SHA-256 du token brut, UNIQUE
OffsetDateTime expiresAt
Boolean isRevoked             // DEFAULT false
OffsetDateTime createdAt

// ─── AlternantProfile (V1) ───────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE, UNIQUE (1 profil / user)
String villeA                 // ville école
String villeB                 // ville entreprise (≠ villeA)
String ecole
String entreprise
LocalDate dateDebut           // dateDebut < dateFin (CHECK)
LocalDate dateFin
RythmeAlternance rythme       // ENUM : SEMAINE_1_1, SEMAINE_3_1, MOIS_1_1, AUTRE
OffsetDateTime createdAt
OffsetDateTime updatedAt

// ─── AlternanceSchedule (V3) ─────────────────────────────────────────────────
UUID id
UUID profileId                // FK → alternant_profiles ON DELETE CASCADE
LocalDate semaine             // toujours un lundi (CHECK DOW=1), UNIQUE(profileId, semaine)
String label                  // 'A' = ville école | 'B' = ville entreprise (CHECK IN ('A','B'))
Boolean isOverridden          // DEFAULT false = généré auto | true = modifié manuellement
String overrideReason
OffsetDateTime createdAt

// ─── Logement (V2 + V13) ─────────────────────────────────────────────────────
UUID id
UUID ownerId                  // FK → users ON DELETE CASCADE
String adresse                // NOT NULL
String ville                  // NOT NULL
String codePostal             // NOT NULL
BigDecimal lat                // DECIMAL(9,6), nullable, rempli par Nominatim
BigDecimal lng                // DECIMAL(9,6), nullable
LogementType type             // ENUM : STUDIO, T1, T2, T3_PLUS, CHAMBRE_COLOC
BigDecimal surface            // DECIMAL(6,2), > 0
Integer nbPieces              // DEFAULT 1
BigDecimal loyer              // DECIMAL(8,2), >= 0
BigDecimal charges            // DECIMAL(8,2), DEFAULT 0
String description
String[] equipements          // TEXT[] PostgreSQL natif : {wifi, parking, lave-linge}
LogementStatut statut         // ENUM : BROUILLON, ACTIF, SUSPENDU, ARCHIVE
Boolean isVerified            // DEFAULT false, validé par admin
Boolean isMeuble              // DEFAULT true
Boolean isChambrePartagee     // DEFAULT false (V13 — hébergement ponctuel)
String accordProprioKey       // clé MinIO accord proprio (V13, requis si isChambrePartagee)
BigDecimal prixSemaine        // DECIMAL(8,2), requis si isChambrePartagee (V13)
OffsetDateTime createdAt
OffsetDateTime updatedAt

// ─── PhotoLogement (V2) ──────────────────────────────────────────────────────
UUID id
UUID logementId               // FK → logements ON DELETE CASCADE
String fileKey                // clé MinIO, UNIQUE
Integer ordre                 // DEFAULT 0, ordre d'affichage
OffsetDateTime createdAt

// ─── Disponibilite (V2) ──────────────────────────────────────────────────────
UUID id
UUID logementId               // FK → logements ON DELETE CASCADE
LocalDate dateDebut
LocalDate dateFin             // >= dateDebut (CHECK)
DisponibiliteType type        // ENUM : LIBRE, OCCUPE, BLOQUE

// ─── DisponibilitePonctuelle (V13) ───────────────────────────────────────────
UUID id
UUID logementId               // FK → logements ON DELETE CASCADE
LocalDate semaine             // lundi de la semaine (CHECK DOW=1), UNIQUE(logementId, semaine)
Boolean isDisponible          // DEFAULT true
OffsetDateTime createdAt

// ─── MatchingCache (V4) ──────────────────────────────────────────────────────
UUID id
UUID profileAId               // FK → alternant_profiles, toujours < profileBId (CHECK)
UUID profileBId               // FK → alternant_profiles, UNIQUE(profileAId, profileBId)
BigDecimal score              // DECIMAL(5,4), entre 0.0000 et 1.0000 (CHECK)
AccordType typeAccord         // ENUM : ECHANGE_TOTAL, ECHANGE_PARTIEL, COLOCATION_TOURNANTE...
String semainesCompatibles    // JSONB : [{semaine, occupant_a, occupant_b}]
Integer nbSemaines            // DEFAULT 0
OffsetDateTime expiresAt
OffsetDateTime createdAt

// ─── Accord (V5 + V13) ───────────────────────────────────────────────────────
UUID id
UUID initiatorId              // FK → users
UUID receiverId               // FK → users (≠ initiatorId CHECK)
UUID logementAId              // FK → logements, nullable (logement apporté par initiator)
UUID logementBId              // FK → logements, nullable (logement apporté par receiver)
AccordType type               // ENUM : ECHANGE_TOTAL, ECHANGE_PARTIEL, COLOCATION_TOURNANTE,
                              //        LOCATION_CLASSIQUE, HEBERGEMENT_PONCTUEL
AccordStatut statut           // ENUM : EN_ATTENTE, ACCEPTE, REFUSE, EN_COURS, TERMINE, ANNULE, LITIGE
LocalDate dateDebut           // dateDebut < dateFin (CHECK)
LocalDate dateFin
BigDecimal montantLoyer       // DECIMAL(8,2), nullable pour échanges gratuits
String conditions
String messageInitial
OffsetDateTime createdAt
OffsetDateTime updatedAt

// ─── AccordSemaine (V5) ──────────────────────────────────────────────────────
UUID id
UUID accordId                 // FK → accords ON DELETE CASCADE
LocalDate semaine             // lundi de la semaine (CHECK DOW=1), UNIQUE(accordId, semaine, logementId)
UUID occupantId               // FK → users
UUID logementId               // FK → logements

// ─── Conversation (V8) ───────────────────────────────────────────────────────
UUID id
UUID accordId                 // FK → accords ON DELETE SET NULL (nullable)
OffsetDateTime createdAt
OffsetDateTime lastMessageAt  // mis à jour par trigger après chaque message

// ─── ConversationParticipant (V8) ────────────────────────────────────────────
UUID id
UUID conversationId           // FK → conversations ON DELETE CASCADE
UUID userId                   // FK → users ON DELETE CASCADE
OffsetDateTime joinedAt       // UNIQUE(conversationId, userId)

// ─── Message (V8) ────────────────────────────────────────────────────────────
UUID id
UUID conversationId           // FK → conversations ON DELETE CASCADE
UUID senderId                 // FK → users
String content                // TEXT, TRIM non vide (CHECK)
Boolean isRead                // DEFAULT false
OffsetDateTime createdAt

// ─── Transaction (V6 + V7) ───────────────────────────────────────────────────
UUID id
UUID accordId                 // FK → accords, nullable
UUID payerId                  // FK → users (≠ payeeId CHECK)
UUID payeeId                  // FK → users
BigDecimal amount             // DECIMAL(10,2), > 0
BigDecimal commission         // DECIMAL(10,2), DEFAULT 0, <= amount
TransactionStatut statut      // ENUM : EN_ATTENTE, SUCCES, ECHEC, REMBOURSE
TransactionType type          // ENUM : LOYER, COMMISSION, REMBOURSEMENT, ABONNEMENT
String stripePaymentIntentId  // UNIQUE DEFERRABLE (V7)
String stripeTransferId
String stripeRefundId
String failureReason
OffsetDateTime createdAt
OffsetDateTime updatedAt

// ─── StripeWebhookEvent (V7) ─────────────────────────────────────────────────
UUID id
String stripeEventId          // UNIQUE — garantit l'idempotence
String eventType
String payload                // JSONB
OffsetDateTime processedAt

// ─── Caution (V6) ────────────────────────────────────────────────────────────
UUID id
UUID accordId                 // FK → accords ON DELETE CASCADE, UNIQUE(accordId, userId)
UUID userId                   // FK → users
BigDecimal amount             // DECIMAL(10,2), > 0
CautionStatut statut          // ENUM : EN_ATTENTE, VERSEE, RESTITUEE, RETENUE_PARTIELLE, RETENUE_TOTALE
String stripePaymentIntentId
String stripeRefundId
BigDecimal returnedAmount     // nullable, peut être < amount (retenue partielle)
String retentionReason
OffsetDateTime returnedAt
OffsetDateTime createdAt
OffsetDateTime updatedAt

// ─── Review (V8) ─────────────────────────────────────────────────────────────
UUID id
UUID authorId                 // FK → users
UUID targetUserId             // FK → users, nullable
UUID targetLogementId         // FK → logements, nullable
UUID accordId                 // FK → accords NOT NULL
ReviewTargetType targetType   // ENUM : USER, LOGEMENT
Integer rating                // SMALLINT, 1 à 5 (CHECK)
String comment                // TEXT, nullable
Boolean isModerated           // DEFAULT false
String moderationNote
OffsetDateTime createdAt
// Contrainte : si USER → targetUserId non null et targetLogementId null, et vice-versa
// UNIQUE(authorId, accordId, targetUserId) et UNIQUE(authorId, accordId, targetLogementId)

// ─── ReputationScore (V10) ───────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE, UNIQUE
BigDecimal avgRating          // DECIMAL(3,2), 0.00 à 5.00, mis à jour par trigger
Integer totalReviews          // DEFAULT 0
BigDecimal logementScore      // DECIMAL(3,2), 0.00 à 5.00
Integer nbAccords             // DEFAULT 0
OffsetDateTime updatedAt

// ─── ReputationHistory (V10) ─────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE
UUID reviewId                 // FK → reviews
BigDecimal avgRatingPrev      // DECIMAL(3,2)
BigDecimal avgRatingNew       // DECIMAL(3,2)
OffsetDateTime createdAt

// ─── Notification (V9) ───────────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE
NotificationType type         // ENUM : NOUVEAU_MATCH, DEMANDE_ACCORD, ACCORD_ACCEPTE,
                              //        ACCORD_REFUSE, NOUVEAU_MESSAGE, PAIEMENT_RECU,
                              //        PAIEMENT_ECHOUE, CAUTION_RESTITUEE, AVIS_RECU,
                              //        DOCUMENT_VALIDE, DOCUMENT_REFUSE, RAPPEL_DEPART,
                              //        RAPPEL_ARRIVEE, SYSTEME
String title                  // VARCHAR(200) NOT NULL
String body                   // TEXT NOT NULL
Boolean isRead                // DEFAULT false
String deepLink               // route Flutter ex: accord/123
String payload                // JSONB — données contextuelles pour Flutter
OffsetDateTime createdAt

// ─── NotificationPreference (V9) ─────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE
NotificationType notificationType
NotificationChannel channel   // ENUM : PUSH, EMAIL, SMS
Boolean isEnabled             // DEFAULT true
OffsetDateTime updatedAt      // UNIQUE(userId, notificationType, channel)

// ─── VerificationDoc (V9) ────────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE, UNIQUE(userId, type)
DocType type                  // ENUM : CARTE_ETUDIANT, CONTRAT_ALTERNANCE,
                              //        PIECE_IDENTITE, JUSTIFICATIF_DOMICILE, ACCORD_PROPRIETAIRE
String fileKey                // clé MinIO, chiffré AES-256 côté serveur
DocStatut statut              // ENUM : EN_ATTENTE, VALIDE, REFUSE
UUID reviewedBy               // FK → users (admin), nullable
OffsetDateTime reviewedAt     // non null si statut VALIDE ou REFUSE (CHECK)
String rejectionReason
OffsetDateTime createdAt

// ─── IcalToken (V11) ─────────────────────────────────────────────────────────
UUID id
UUID userId                   // FK → users ON DELETE CASCADE, UNIQUE (1 token / user)
String token                  // VARCHAR(128), UNIQUE, 64 chars hex opaque
Boolean isActive              // DEFAULT true
OffsetDateTime expiresAt      // nullable = pas d'expiration
OffsetDateTime lastUsed
OffsetDateTime createdAt

// ─── JourFerie (V3) ──────────────────────────────────────────────────────────
UUID id
LocalDate dateJour            // UNIQUE(dateJour, pays)
String libelle                // NOT NULL
String pays                   // CHAR(2), DEFAULT 'FR'
String region                 // nullable
```

---

## 5. MIGRATIONS FLYWAY (V1 → V12)

```sql
V1__init_schema.sql              -- users, refresh_tokens, alternant_profiles
V2__add_logements_table.sql      -- logements, photos_logements, disponibilites
V3__add_calendrier_alternance.sql -- alternance_schedules, jours_feries (seed 2024-2025)
V4__add_matching_cache_table.sql  -- matching_cache (score, type, semaines_compatibles JSONB)
V5__add_accords_table.sql         -- accords, accord_semaines
V6__add_transactions_cautions.sql -- transactions, cautions
V7__add_stripe_idempotency.sql    -- stripe_webhook_events + UNIQUE(stripe_payment_intent_id)
V8__add_reviews_messaging.sql     -- conversations, conversation_participants, messages, reviews
V9__add_notification_preferences.sql -- notifications, notification_preferences, verification_docs
V10__add_reputation_scores.sql    -- reputation_scores, reputation_history, vues matérialisées
V11__add_ical_export_tokens.sql   -- ical_tokens
V12__add_performance_indexes.sql  -- index de performance (sans NOW() — contrainte PostgreSQL)
V13__add_hebergement_ponctuel.sql -- HEBERGEMENT_PONCTUEL enum + disponibilites_ponctuelles
```

---

## ENVIRONNEMENT LOCAL DE DÉVELOPPEMENT

```
Base de données : Docker PostgreSQL 15 sur port 5433
URL             : jdbc:postgresql://localhost:5433/yunivdb
User            : yuniv
Password        : yuniv123
Démarrer Docker : docker start yuniv-postgres
Arrêter Docker  : docker stop yuniv-postgres
```

Railway est utilisé uniquement pour la production (soutenance septembre 2026).
Ne pas configurer de déploiement Railway pendant le développement.

---

## NOTES FLYWAY IMPORTANTES

- `spring.flyway.execute-in-transaction=false` est **obligatoire** dans application.properties
  → PostgreSQL exige que ALTER TYPE soit commité avant utilisation dans la même migration
- Les index partiels avec `NOW()` sont **interdits** dans PostgreSQL (fonction non IMMUTABLE)
  → Utiliser des index sans clause WHERE temporelle
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate ne touche jamais au schéma, Flyway seul gère
- Les IDs sont des **UUID** (pas Long) — utiliser `@GeneratedValue` avec `UuidGenerator`

---

| Décision | Choix | Ne pas changer car |
|---------|-------|-------------------|
| Architecture | Monolithe modulaire | MVP solo, microservices injustifiés |
| Auth | JWT stateless 15min + refresh 7j | Compatibilité Flutter mobile |
| JWT révocation | Blacklist Redis (JTI + TTL) | Révocation immédiate sans BDD |
| Fichiers | MinIO S3-compatible | Migration AWS transparente |
| Paiements | Stripe Connect | Multi-parties natif, agréments bancaires |
| Cache matching | Redis TTL 1h | Invalidation auto si rythme modifié |
| Algorithme | Java maison package algorithm/ | Contrôle total, testable |
| Migrations | Flyway SQL pur | Simplicité, traçabilité |
| Temps réel | WebSocket STOMP + fallback SSE | Compatibilité proxies Railway |
| Géocodage | Nominatim OSM | Gratuit, France excellente |
| Logs | Logback JSON (logstash-logback-encoder) | Indexation Railway, MDC |
| Monitoring erreurs | Sentry | Capture exceptions + contexte utilisateur |

---

## 7. SÉCURITÉ — RÈGLES NON NÉGOCIABLES

```java
// 1. Ownership check sur TOUTES les ressources modifiables
@PreAuthorize("@securityService.isOwner(#id, authentication)")

// 2. Rate limiting Bucket4j
// /auth/login     : 5 req/min par IP
// /auth/register  : 3 req/min par IP
// /matching/**    : 30 req/min par user

// 3. Validation fichiers uploadés — Apache Tika magic bytes
// Types autorisés : application/pdf, image/jpeg, image/png, image/webp
// Taille max : 5 Mo images, 10 Mo PDF
// Stockage : UUID renommage systématique (jamais le nom original)

// 4. JWT Blacklist
// Après logout : ajouter JTI dans Redis avec TTL = expiry du token
// Vérifier blacklist dans JwtAuthFilter AVANT toute requête

// 5. Logs — JAMAIS de PII
// Ne jamais logger : email, nom, IBAN, numéro de carte, contenu documents
// Toujours logger : userId (pas email), requestId, traceId

// 6. Réponses d'erreur
// 403 Forbidden (pas 404) si ressource existe mais non autorisé
// Jamais exposer la stacktrace en production
```

---

## 8. CONVENTIONS DE CODE

```java
// Nommage
// Controllers : @RestController, @RequestMapping("/api/v1/...")
// Services    : @Service, logique métier uniquement (pas de JPA direct)
// Repos       : @Repository, extends JpaRepository<Entity, UUID>
// DTOs        : record ou class immutable, jamais d'entité JPA exposée

// Réponses API standard
// Succès création   : HTTP 201 + body objet créé
// Succès lecture    : HTTP 200 + body objet ou liste
// Succès suppression: HTTP 204 no content
// Validation échoue : HTTP 400 + body {code, message, fields[]}
// Non authentifié   : HTTP 401
// Non autorisé      : HTTP 403
// Non trouvé        : HTTP 404
// Conflit           : HTTP 409 (ex: email déjà utilisé)
// Rate limit        : HTTP 429 + header Retry-After

// Tests — DEUX COUCHES OBLIGATOIRES PAR TICKET, TOUJOURS, SANS EXCEPTION
// 1. XxxServiceTest  : logique métier, ownership, exceptions (JUnit 5 + Mockito)
// 2. XxxControllerTest : codes HTTP, JSON, 401 sans auth, 400 si validation (MockMvc + @WebMvcTest)
// + couches additionnelles si nécessaire : AlgorithmTest, SchedulerTest, etc.
// Toujours un test par méthode de service
// Toujours un test du cas nominal + cas d'erreur
// Testcontainers pour les tests qui touchent BDD/Redis
```

---

## 9. BACKLOG COMPLET — PAR SPRINT

### SPRINT 1 — Fondations (~50 SP)
> Objectif : Auth complète, profils, logements, calendrier, infra de base

---

#### [US-001] Inscription email/password — 5 SP — Must Have
**Story :** En tant que visiteur, je veux m'inscrire avec email/mot de passe afin d'accéder à la plateforme.
**Critères d'acceptation :**
- POST /api/v1/auth/register → HTTP 201
- Validation email format + unicité (HTTP 409 si doublon)
- Mot de passe hashé bcrypt (jamais en clair)
- Email de confirmation envoyé (token valable 24h)
- Compte en statut PENDING_EMAIL jusqu'à confirmation
- JWT retourné uniquement après confirmation email
- Test : AuthServiceTest#shouldRegisterNewUser + shouldRejectDuplicateEmail

---

#### [US-002] Connexion JWT et refresh token — 5 SP — Must Have
**Story :** En tant qu'utilisateur, je veux me connecter avec JWT afin d'accéder à mes fonctionnalités.
**Critères d'acceptation :**
- POST /api/v1/auth/login → HTTP 200 + {accessToken, refreshToken}
- Access token expire 15 minutes (claims : userId, email, role)
- Refresh token expire 7 jours, stocké en base
- POST /api/v1/auth/refresh → nouveau pair de tokens
- POST /api/v1/auth/logout → JTI ajouté blacklist Redis
- Rate limiting : HTTP 429 après 5 tentatives/min par IP
- Test : AuthServiceTest#shouldLoginSuccessfully + shouldRefreshToken + shouldLogout

---

#### [US-003] Upload vérification document identité — 8 SP — Must Have
> ⚠️ REPORTÉ — Fonctionnalité complexe (Apache Tika, MinIO, chiffrement) reportée au Sprint 4 ou en V2 si le temps manque. Ne pas travailler sur ce ticket avant que tout le reste soit terminé.

**Story :** En tant qu'alternant, je veux soumettre mon contrat d'alternance afin de faire vérifier mon identité.
**Critères d'acceptation :**
- POST /api/v1/verification/upload (multipart/form-data)
- Validation Apache Tika : magic bytes + whitelist MIME
- Stockage MinIO avec UUID renommage + chiffrement
- Statut EN_ATTENTE créé en base
- PUT /api/v1/admin/verification/{id}/approve|reject (rôle ADMIN)
- Email notifié à l'utilisateur après décision
- Test : VerificationServiceTest#shouldAcceptValidPdf + shouldRejectExecutableRenamedAsPdf

---

#### [US-004] Création profil alternant avec rythme — 8 SP — Must Have
**Story :** En tant qu'alternant, je veux créer mon profil avec mon rythme d'alternance afin d'être matché.
**Critères d'acceptation :**
- POST /api/v1/profile/alternant → HTTP 201
- Champs obligatoires : villeA, villeB, ecole, entreprise, dateDebut, dateFin, rythme
- Génération automatique AlternanceSchedule sur 12 mois après création
- Validation : dateDebut < dateFin, villes différentes
- Si profil modifié → recalcul calendrier + invalidation cache matching
- Test : AlternantProfileServiceTest#shouldGenerateScheduleAfterProfileCreation

---

#### [US-005] Création profil propriétaire — 5 SP — Must Have
**Story :** En tant que propriétaire, je veux créer mon profil professionnel afin de gérer mes annonces.
**Critères d'acceptation :**
- POST /api/v1/profile/proprietaire → HTTP 201
- Champs : téléphone (vérifié format FR), adresse, SIRET optionnel
- Badge PROPRIETAIRE_VERIFIE après upload justificatif propriété
- Dashboard accessible dès création du profil
- Test : ProprietaireProfileServiceTest#shouldCreateProfile

---

#### [US-007] Publication logement avec photos — 8 SP — Must Have
**Story :** En tant qu'utilisateur, je veux publier mon logement avec photos et description afin de le proposer.
**Critères d'acceptation :**
- POST /api/v1/logements → HTTP 201 (statut BROUILLON par défaut)
- PUT /api/v1/logements/{id}/publish → passe en ACTIF (ownership check)
- Upload 1 à 10 photos (max 2 Mo chacune, JPEG/PNG/WEBP)
- Compression automatique à 80% qualité avant stockage MinIO
- Adresse géocodée via Nominatim → lat/lng stockés en base
- URLs signées MinIO pour accès aux photos (expiry 1h)
- Test : LogementServiceTest#shouldPublishLogement + shouldRejectOversizedPhoto

---

#### [US-011] Association logement aux deux villes alternant — 3 SP — Must Have
**Story :** En tant qu'alternant, je veux associer mon logement à mes deux villes afin d'activer le matching.
**Critères d'acceptation :**
- PATCH /api/v1/logements/{id}/ville → body {villeAssociee: "VILLE_A" | "VILLE_B"}
- Validation : la ville du logement doit correspondre à villeA ou villeB du profil
- Un alternant peut avoir 2 logements max (un par ville)
- HTTP 409 si logement pour cette ville déjà existant
- Test : LogementServiceTest#shouldAssociateLogementToVilleA

---

#### [US-017] Génération automatique calendrier alternance — 8 SP — Must Have
**Story :** En tant que système, je veux générer le calendrier d'alternance automatiquement afin d'alimenter le matching.
**Critères d'acceptation :**
- ScheduleGenerator.generateSchedule(profile) → List<AlternanceSchedule>
- Génère 52 semaines labellisées A ou B selon le rythme
- Gestion des jours fériés français (table statique 2025-2027)
- Recalcul automatique si profil modifié (event Spring)
- Stockage en base avec contrainte unicité (profileId, semaine)
- Test : ScheduleGeneratorTest#shouldGenerate52Weeks + shouldHandleHolidays

---

#### [US-037] GlobalExceptionHandler — 3 SP — Must Have
**Story :** En tant que développeur, je veux un gestionnaire d'erreurs global afin d'avoir des réponses API cohérentes.
**Critères d'acceptation :**
- @RestControllerAdvice catching toutes les exceptions
- Format réponse : {code, message, timestamp, path, details[]}
- Codes HTTP sémantiques (400/401/403/404/409/429/500)
- Jamais de stacktrace dans la réponse (mode prod)
- Log ERROR avec MDC (userId, requestId) sur chaque 5xx
- Format de réponse d'erreur standard à respecter partout :
```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "Un compte existe déjà avec cet email",
  "timestamp": "2026-05-09T10:30:00Z",
  "path": "/api/v1/auth/register",
  "details": []
}
```
- Test : GlobalExceptionHandlerTest#shouldReturn400OnValidationError

---

#### [US-038] Rate limiting Bucket4j — 5 SP — Must Have
**Story :** En tant que système, je veux un rate limiting sur les endpoints critiques afin d'éviter les abus.
**Critères d'acceptation :**
- Bucket4j configuré via Spring filter
- /auth/login : 5 req/min par IP → HTTP 429 + header Retry-After
- /auth/register : 3 req/min par IP
- /matching/suggestions : 30 req/min par userId authentifié
- Après 60 secondes : compteur reset automatique
- Test : RateLimitingTest#shouldReturn429AfterFiveLoginAttempts

---

#### [US-039] Configuration CORS Flutter — 2 SP — Must Have
**Story :** En tant que système, je veux configurer CORS pour Flutter afin de permettre les appels cross-origin.
**Critères d'acceptation :**
- WebMvcConfigurer bean avec origines par environnement
- Dev : * | Staging : staging.nomplateforme.fr | Prod : app.nomplateforme.fr
- Méthodes autorisées : GET, POST, PUT, PATCH, DELETE, OPTIONS
- Headers : Authorization, Content-Type, X-Request-ID
- Max-Age : 3600 secondes
- Test : CorsConfigTest#shouldAllowConfiguredOrigin

---

#### ~~[US-044] Migrations Flyway + seed data~~ — ✅ DÉJÀ FAIT
> **Statut :** V1 à V13 migrées avec succès sur Docker local et Railway (avant suppression Railway).
> Les 23 tables sont en place avec toutes les contraintes, indexes et triggers.
> Ne pas retravailler ce ticket.

---

#### [US-045] Logging structuré JSON Logback — 3 SP — Must Have
**Story :** En tant que développeur, je veux un logging structuré JSON en prod afin de faciliter le debug sur Railway.
**Critères d'acceptation :**
- Profil "prod" → format JSON (logstash-logback-encoder 7.4)
- MDCFilter injecte userId + requestId (UUID) dans chaque log
- Champs : timestamp, level, logger, message, traceId, userId, requestId, duration_ms
- Jamais de PII dans les logs (email, nom, IBAN...)
- Log level configurable via variable d'env LOG_LEVEL (défaut INFO)
- Test : MDCFilterTest#shouldInjectUserIdInLogs

---

#### ~~[US-047] Pipeline CI/CD GitHub Actions → Railway~~ — ✅ PARTIELLEMENT FAIT
> **Statut :** CI/CD GitHub Actions opérationnel (Build & Test automatique sur chaque PR).
> Le déploiement Railway est reporté à fin Sprint 4, avant la soutenance.
> Le job Build & Test tourne déjà sur chaque PR vers main.
> Ne pas travailler sur ce ticket pendant les sprints 1-3.

---

### SPRINT 2 — Matching & Recherche (~71 SP)
> Objectif : Algorithme matching complet, calendrier compatibilité, recherche logements

---

#### [US-008] Recherche logements avec filtres — 5 SP — Must Have
**Story :** En tant qu'étudiant, je veux rechercher des logements par ville et critères afin de trouver le bon.
**Critères d'acceptation :**
- GET /api/v1/logements?ville=&loyer_max=&surface_min=&meuble=&type=
- Pagination : 20 résultats/page, paramètre page + cursor
- Tri : pertinence (défaut) | prix_asc | prix_desc | surface_desc
- Recherche géographique si lat/lng fourni : distance calculée
- Résultats uniquement en statut ACTIF
- Test : LogementSearchServiceTest#shouldFilterByVilleAndLoyer

---

#### [US-009] Détail d'un logement — 3 SP — Must Have
**Story :** En tant qu'utilisateur, je veux voir le détail d'un logement afin d'évaluer s'il me convient.
**Critères d'acceptation :**
- GET /api/v1/logements/{id} → toutes infos + photos (URLs signées MinIO 1h)
- Disponibilités calculées dynamiquement (exclut dates accords en cours)
- Si appelant est alternant : score de compatibilité avec le propriétaire du logement inclus
- Avis des locataires précédents (3 derniers)
- Test : LogementControllerTest#shouldReturnLogementDetail

---

#### [US-010] Gestion des disponibilités — 5 SP — Should Have
**Story :** En tant que propriétaire, je veux gérer les disponibilités de mon logement afin d'éviter les conflits.
**Critères d'acceptation :**
- POST /api/v1/logements/{id}/disponibilites → créer plage (dateDebut, dateFin)
- GET /api/v1/logements/{id}/disponibilites → liste des plages
- Blocage automatique dates quand accord ACCEPTE créé sur ce logement
- Alerte si chevauchement détecté entre deux plages
- Test : DisponibiliteServiceTest#shouldBlockDatesOnAccordAccepted

---

#### [US-012] Algorithme de compatibilité des rythmes — 13 SP — Must Have
**Story :** En tant que système, je veux calculer la compatibilité entre deux alternants afin de proposer un échange.
**Critères d'acceptation :**
- CompatibilityCalculator.calculate(profileA, profileB, schedulesA, schedulesB) → MatchingResult
- MatchingResult : {score (0.0-1.0), typePropose, isMatchActif, messageMatchPotentiel, semaines[], nbSemainesEchange, nbSemainesColocation, nbSemainesChevauchement, economieEstimeeMin, economieEstimeeMax, messageResume}
- Score = nbSemainesEchange / nbSemainesTotalesPeriodeCommune (entre 0.0000 et 1.0000)
- Mapping : score >= 0.90 → ECHANGE_TOTAL | score >= 0.60 → ECHANGE_PARTIEL | score = 0 et coloc > 0 → COLOCATION_TOURNANTE
- Présélection : deux profils ne sont comparés QUE s'ils ont au moins une ville en commun
- Deux niveaux : isMatchActif=true si les logements nécessaires sont publiés, false = match potentiel avec message
- Résultat mis en cache Redis avec clé "matching:{profileAId}:{profileBId}" TTL 1h (profileAId toujours < profileBId)
- Performance : < 50ms par paire, < 500ms pour 500 profils
- Invalidation cache si un des deux profils modifie son rythme, ses villes, ou override une semaine
- Voir section 1.1 pour la logique complète semaine par semaine et les 3 cas principaux
- Test : CompatibilityCalculatorTest (10 cas — voir liste dans section 1.1)

---

#### [US-013] Suggestions de matching — 8 SP — Must Have
**Story :** En tant qu'alternant, je veux recevoir des suggestions de matching afin de trouver un accord.
**Critères d'acceptation :**
- GET /api/v1/matching/suggestions → top 20 matches triés par score décroissant
- Filtres optionnels : type_echange, ville, score_min
- Chaque suggestion : {userId, score, type, isMatchActif, messageMatchPotentiel, semainesCompatibles, logement}
- Inclut les matchs ACTIFS (logements publiés, accord signable immédiatement) ET les matchs POTENTIELS (profils compatibles, logements manquants)
- Les matchs actifs sont affichés en premier, puis les potentiels classés par score
- Pagination cursor (pas offset) pour éviter les doublons
- Calcul asynchrone (@Async) si > 100 candidats potentiels
- Test : MatchingServiceTest#shouldReturnSortedSuggestions + shouldIncludePotentialMatches

---

#### [US-014] Échange partiel optimisé — 13 SP — Must Have
**Story :** En tant que système, je veux proposer un échange partiel optimisé afin de maximiser les semaines utiles.
**Critères d'acceptation :**
- PartialExchangeOptimizer.optimize(scheduleA, scheduleB) → PartialExchangeProposal
- Proposal : {semainesProposees[], economieTotale (EUR), nbSemainesEchange}
- Semaines proposées = semaines VERT uniquement (aucun chevauchement)
- Économies = (nbSemainesEchange / 4.33) × loyerMensuel
- JSON formaté pour affichage Flutter CalendarWidget
- Test : PartialExchangeOptimizerTest#shouldMaximizeNonOverlappingWeeks

---

#### [US-015] Colocation tournante — 8 SP — Must Have
**Story :** En tant que système, je veux identifier les paires pour colocation tournante afin de diviser les loyers.
**Critères d'acceptation :**
- ColocationMatcher.findMatches(userId) → List<ColocationMatch>
- Critères : même rythme + mêmes villes (villeA et villeB identiques)
- Calcul économie : (loyerA + loyerB) / 2 vs loyers séparés
- Génère planning semaine par semaine sur 12 mois
- Accord de type COLOCATION distinct de l'accord ECHANGE
- Test : ColocationMatcherTest#shouldFindUsersWithSameRhythmAndCities

---

#### [US-018] Calendrier de compatibilité colorisé — 8 SP — Must Have
**Story :** En tant que système, je veux produire le calendrier de compatibilité colorisé afin de l'afficher dans l'app.
**Critères d'acceptation :**
- GET /api/v1/calendrier/compatibilite?user1={id}&user2={id}
- Réponse JSON : [{semaine: "2026-W03", couleur: "VERT|BLEU|ORANGE|GRIS", label: "..."}]
- VERT = semaines d'échange possible (labels différents, même ville cible)
- BLEU = semaines de colocation possible (même rythme)
- ORANGE = semaines de chevauchement (les deux présents au même endroit)
- GRIS = semaines incompatibles (villes différentes, pas de synergie)
- Test : CalendrierCompatibiliteServiceTest#shouldReturnColorCodedWeeks

---

#### [US-006] Dashboard admin utilisateurs — 8 SP — Should Have
**Story :** En tant qu'admin, je veux consulter et gérer tous les utilisateurs afin de modérer la plateforme.
**Critères d'acceptation :**
- GET /api/v1/admin/users?status=&role=&page= (rôle ADMIN requis)
- PUT /api/v1/admin/users/{id}/suspend → statut SUSPENDU + révocation JWT
- PUT /api/v1/admin/users/{id}/ban → statut BANNI + blacklist permanente
- GET /api/v1/admin/verifications/pending → liste docs en attente modération
- Toutes actions admin loggées avec adminId + action + cible
- Test : AdminControllerTest#shouldSuspendUser + shouldListPendingVerifications

---

#### [US-040] Ownership checks @PreAuthorize — 5 SP — Must Have
**Story :** En tant que développeur, je veux des ownership checks sur chaque ressource afin d'éviter l'accès croisé.
**Critères d'acceptation :**
- SecurityService.isOwner(resourceId, resourceType, authentication) → boolean
- @PreAuthorize sur tous les PUT/PATCH/DELETE de ressources utilisateur
- HTTP 403 Forbidden (jamais 404) si ressource existe mais non autorisée
- ADMIN bypass les ownership checks (peut tout modifier)
- Couverture : Logement, Accord, Message, Review, Profile
- Test : OwnershipCheckTest#shouldReturn403WhenNotOwner

---

#### [US-041] Validation fichiers Apache Tika — 5 SP — Must Have
**Story :** En tant que système, je veux valider les fichiers uploadés afin de bloquer les contenus malveillants.
**Critères d'acceptation :**
- FileValidationService.validate(MultipartFile) → ValidationResult
- Détection MIME par magic bytes via Apache Tika (pas l'extension)
- Whitelist : application/pdf, image/jpeg, image/png, image/webp
- Taille max : 5 Mo images, 10 Mo PDF → HTTP 400 si dépassé
- Renommage UUID systématique avant stockage
- Test : FileValidationServiceTest#shouldRejectExecutableRenamedAsPdf

---

#### [US-042] JWT blacklist via Redis — 3 SP — Should Have
**Story :** En tant que système, je veux une blacklist des JWT révoqués afin de sécuriser les déconnexions.
**Critères d'acceptation :**
- JwtBlacklistService.blacklist(jti, expiry) → ajoute dans Redis avec TTL
- JwtAuthFilter vérifie blacklist AVANT toute requête authentifiée
- Révocation auto si l'utilisateur change son mot de passe
- Admin peut révoquer tous les tokens d'un utilisateur suspendu
- Test : JwtBlacklistServiceTest#shouldRejectBlacklistedToken

---

#### [US-043] Spring Actuator health checks — 3 SP — Should Have
**Story :** En tant que développeur, je veux configurer Spring Actuator afin de monitorer l'application.
**Critères d'acceptation :**
- /actuator/health/liveness → UP/DOWN (public, utilisé par Railway)
- /actuator/health/readiness → UP/DOWN (public)
- /actuator/health → détail composants (BDD, Redis, MinIO) — rôle ADMIN
- /actuator/metrics/{name} — rôle ADMIN
- Métriques custom Micrometer : matchings.created, paiements.success, inscriptions.daily
- Test : ActuatorHealthTest#shouldReturnUpWhenAllComponentsHealthy

---

#### [US-046] Jobs planifiés + ShedLock — 5 SP — Should Have
**Story :** En tant que système, je veux des jobs planifiés Spring afin d'automatiser les tâches récurrentes.
**Critères d'acceptation :**
- AccordExpirationJob : toutes les heures, expire accords EN_ATTENTE > 72h
- ReputationCalculationJob : toutes les nuits à 2h, recalcule les scores
- JwtCleanupJob : toutes les heures, purge tokens expirés de Redis
- EmailDigestJob : lundi 8h, envoie le résumé hebdomadaire
- ShedLock sur chaque job (évite double exécution si scaling)
- Test : AccordExpirationJobTest#shouldExpireOldPendingAccords

---

### SPRINT 3 — Accords & Transactions (~57 SP)
> Objectif : Accords, messagerie WebSocket, Stripe Connect, caution, notifications

---

#### [US-016] Envoi et gestion d'un accord — 5 SP — Must Have
**Story :** En tant qu'alternant, je veux envoyer une demande d'accord à un match afin d'initier le processus.
**Critères d'acceptation :**
- POST /api/v1/accords → HTTP 201, statut EN_ATTENTE, expiresAt = now+72h
- Types acceptés : ECHANGE_TOTAL, PARTIEL, COLOCATION, LOCATION_CLASSIQUE
- Notification push FCM envoyée au destinataire
- PUT /api/v1/accords/{id}/accept|refuse|cancel (ownership check)
- GET /api/v1/accords/mes-accords → historique paginé
- Test : AccordServiceTest#shouldCreateAccordAndNotifyRecipient

---

#### [US-019] Override manuel du calendrier — 5 SP — Should Have
**Story :** En tant qu'alternant, je veux modifier mon calendrier manuellement afin de gérer les exceptions.
**Critères d'acceptation :**
- PATCH /api/v1/calendrier/{profileId}/semaines/{semaine}
- Body : {label: "A"|"B", reason: "rattrapage"|"conges"|"autre"}
- Validation : pas de modification des semaines passées
- Propagation : invalide cache matching pour tous les matches actifs de l'utilisateur
- Historique des overrides conservé en base
- Test : CalendrierOverrideServiceTest#shouldInvalidateCacheAfterOverride

---

#### [US-021] Messagerie WebSocket STOMP — 8 SP — Should Have
**Story :** En tant qu'utilisateur, je veux envoyer des messages à un autre utilisateur afin de discuter d'un accord.
**Critères d'acceptation :**
- WebSocket endpoint : ws://[host]/ws avec STOMP
- @MessageMapping("/chat/{conversationId}") → broadcast aux abonnés
- Messages texte max 2000 caractères, persistés en PostgreSQL
- GET /api/v1/messages/{conversationId} → historique paginé (50/page)
- Heartbeat toutes les 30 secondes pour maintenir connexion Railway
- Fallback SockJS si WebSocket bloqué par proxy
- Test : MessageServiceTest#shouldPersistMessageAndMarkAsUnread

---

#### [US-022] Notifications push FCM — 5 SP — Should Have
**Story :** En tant qu'utilisateur, je veux recevoir une notification quand j'ai un nouveau message afin de répondre vite.
**Critères d'acceptation :**
- FCMService.sendNotification(userId, title, body, data) via Firebase Admin SDK
- Types : NOUVEAU_MATCH, NOUVEAU_MESSAGE, ACCORD_RECU, PAIEMENT, AVIS
- Deep link dans le payload data : {screen: "accords", id: "123"}
- Notification in-app (SSE ou WebSocket) si application ouverte
- Préférences par type respectées (NotificationPreference en base)
- Test : FCMServiceTest#shouldSendPushNotification (Firebase Emulator)

---

#### [US-025] Paiement loyer via Stripe Connect — 13 SP — Must Have
**Story :** En tant que propriétaire, je veux recevoir le loyer sécurisé via la plateforme afin d'être protégé.
**Critères d'acceptation :**
- POST /api/v1/paiements/loyer → crée PaymentIntent Stripe
- Commission 8% prélevée automatiquement (application_fee_amount)
- Propriétaire reçoit 92% via Stripe Connect transfer
- Libération J+2 après confirmation entrée locataire
- Reçu PDF généré automatiquement (iTextPDF ou PDFBox)
- Test mode Stripe (4242 4242 4242 4242) utilisé en staging

---

#### [US-026] Caution sécurisée séquestrée — 8 SP — Must Have
**Story :** En tant qu'étudiant, je veux déposer une caution sécurisée afin de rassurer le propriétaire.
**Critères d'acceptation :**
- POST /api/v1/cautions → PaymentIntent avec capture_method=manual
- Statut BLOQUEE en base jusqu'à libération
- POST /api/v1/cautions/{id}/release (sans litige) → capture annulée, remboursement automatique
- POST /api/v1/cautions/{id}/dispute → statut EN_LITIGE, gel 30j supplémentaires
- Expiration @Scheduled : si non capturée après 30j → annulation auto
- Test : CautionServiceTest#shouldReleaseWithoutDispute + shouldHandleDispute

---

#### [US-027] Abonnement Premium Stripe Billing — 5 SP — Should Have
**Story :** En tant qu'étudiant, je veux souscrire à l'abonnement Premium afin d'accéder aux fonctionnalités avancées.
**Critères d'acceptation :**
- POST /api/v1/subscriptions/premium → crée Customer + Subscription Stripe
- 4,99€/mois, renouvellement automatique
- Webhook invoice.paid → isPremium=true sur User
- Webhook invoice.payment_failed → email + période de grâce 3j
- DELETE /api/v1/subscriptions/premium → annulation fin de période
- Test : SubscriptionServiceTest#shouldActivatePremiumOnWebhook

---

#### [US-033] Notifications push FCM personnalisées — 8 SP — Should Have
**Story :** En tant qu'utilisateur, je veux recevoir des notifications push personnalisées afin de ne rien manquer.
**Critères d'acceptation :**
- 5 types de notifications avec templates distincts
- Vérification NotificationPreference avant envoi (respect opt-out)
- Mise à jour fcmToken sur chaque login Flutter
- Nettoyage tokens invalides (InvalidRegistration Stripe FCM)
- Test : NotificationServiceTest#shouldRespectUserPreferences

---

#### [US-048] Webhooks Stripe idempotents — 5 SP — Must Have
**Story :** En tant que développeur, je veux gérer les webhooks Stripe avec idempotence afin d'éviter les doubles traitements.
**Critères d'acceptation :**
- POST /api/v1/webhooks/stripe vérifie signature HMAC-SHA256
- Avant traitement : INSERT stripe_event_id ON CONFLICT DO NOTHING (atomique)
- Si conflit → HTTP 200 silencieux (déjà traité)
- Events : payment_intent.succeeded, charge.refunded, invoice.paid, invoice.payment_failed
- Dead letter queue (table stripe_failed_events) pour events en erreur
- Test : StripeWebhookControllerTest#shouldIgnoreDuplicateWebhook

---

### SPRINT 4 — Finalisation MVP (~52 SP)
> Objectif : Avis, dashboards, modération, fonctionnalités Nice to Have

---

#### [US-023] Modération messagerie — 5 SP — Should Have
**Story :** En tant qu'admin, je veux modérer les messages afin de maintenir un environnement sûr.
**Critères d'acceptation :**
- POST /api/v1/messages/{id}/report → signalement avec motif
- GET /api/v1/admin/moderation/messages → queue signalements (rôle ADMIN)
- PUT /api/v1/admin/moderation/messages/{id}/hide → masque message
- Filtrage mots interdits configurable (table en base)
- Test : ModerationServiceTest#shouldHideReportedMessage

---

#### [US-024] Photos dans les messages — 5 SP — Nice to Have
**Story :** En tant qu'utilisateur, je veux partager des photos dans les messages afin de faciliter les échanges.
**Critères d'acceptation :**
- Upload image (max 5Mo, jpg/png/webp) dans le corps du message
- Compression automatique serveur (max 1024px large)
- URL signée MinIO 24h dans la réponse
- Max 5 images par message
- Test : MediaMessageServiceTest#shouldUploadAndCompressImage

---

#### [US-028] Dashboard revenus admin — 5 SP — Should Have
**Story :** En tant qu'admin, je veux voir les revenus et commissions afin de suivre la performance financière.
**Critères d'acceptation :**
- GET /api/v1/admin/dashboard/revenus?from=&to= (rôle ADMIN)
- KPIs : GMV total, commissions, MRR abonnements, nb accords
- GET /api/v1/admin/transactions/export → CSV (RFC 4180)
- Test : RevenueDashboardServiceTest#shouldCalculateGMV

---

#### [US-029] API B2B CFA et écoles — 8 SP — Nice to Have
**Story :** En tant que partenaire CFA/école, je veux accéder à l'API B2B afin de gérer mes étudiants.
**Critères d'acceptation :**
- Authentification par API Key (header X-API-Key)
- GET /api/v1/b2b/etudiants → liste étudiants de l'organisation
- POST /api/v1/b2b/invitations → invitation en masse par email
- Rate limiting par API Key : 1000 req/heure
- Test : B2BApiServiceTest#shouldAuthenticateWithApiKey

---

#### [US-030] Dépôt d'avis post-échange — 5 SP — Should Have
**Story :** En tant qu'utilisateur, je veux laisser un avis après un échange afin d'aider la communauté.
**Critères d'acceptation :**
- ReviewRequest créé automatiquement J+1 après fin accord
- POST /api/v1/reviews → note 1-5 + commentaire max 500 chars
- Avis réciproques : les deux parties notent (période 14j)
- Avis visible sur profil public après soumission des deux parties
- Test : ReviewServiceTest#shouldCreateReviewAndTriggerReputation

---

#### [US-031] Score de réputation utilisateur — 5 SP — Should Have
**Story :** En tant que système, je veux calculer le score de réputation de chaque utilisateur.
**Critères d'acceptation :**
- ReputationService.calculate(userId) → score 0.0-5.0
- Pondération : avis des 6 derniers mois × 1.5
- Badges automatiques : Nouveau (<5 avis), Fiable (≥5, score≥3.5), Expert (≥20, score≥4.2), Ambassadeur (≥50, score≥4.5)
- Recalcul automatique après chaque nouvel avis (@EventListener)
- Test : ReputationServiceTest#shouldCalculateWeightedScore

---

#### [US-032] Modération des avis — 3 SP — Should Have
**Critères d'acceptation :**
- POST /api/v1/reviews/{id}/report → signalement
- GET /api/v1/admin/moderation/reviews → queue admin
- PUT /api/v1/admin/moderation/reviews/{id}/hide → masquage + notification auteur
- Test : ReviewModerationServiceTest#shouldHideAndNotifyAuthor

---

#### [US-034] Dashboard propriétaire — 5 SP — Should Have
**Critères d'acceptation :**
- GET /api/v1/dashboard/proprietaire → KPIs + liste logements
- KPIs : taux occupation, revenus mois courant, nb locataires actifs
- Alertes : cautions en attente de libération, avis sans réponse
- GET /api/v1/dashboard/proprietaire/export → PDF bilan mensuel
- Test : ProprietaireDashboardServiceTest#shouldCalculateOccupancyRate

---

#### [US-035] Dashboard alternant — 5 SP — Should Have
**Critères d'acceptation :**
- GET /api/v1/dashboard/alternant → vue personnalisée
- Prochains échanges/colocs sur 8 semaines
- Économies réalisées vs loyer plein calculées
- Matches en attente de réponse (avec countdown expiration)
- Test : AlternantDashboardServiceTest#shouldCalculateSavings

---

#### [US-036] Email résumé hebdomadaire — 3 SP — Nice to Have
**Critères d'acceptation :**
- EmailDigestJob chaque lundi 8h via @Scheduled + ShedLock
- Template HTML Thymeleaf responsive : nouveaux matches, messages non lus, rappels
- Désabonnement en 1 clic (token unique dans URL)
- Envoi uniquement si activité dans la semaine
- Test : EmailDigestJobTest#shouldSendDigestToActiveUsers

---

#### [US-020] Export calendrier iCal — 3 SP — Nice to Have
**Critères d'acceptation :**
- GET /api/v1/calendrier/export → fichier .ics (RFC 5545)
- Contient semaines ville_A, ville_B et accords confirmés
- URL d'abonnement persistante : /calendrier/subscribe/{token}
- Token unique par utilisateur, régénérable
- Test : ICalExportServiceTest#shouldGenerateValidICalFormat

---

#### [US-049] Suite tests intégration Testcontainers — 8 SP — Should Have
**Critères d'acceptation :**
- @SpringBootTest avec @Testcontainers (PostgreSQL 15 + Redis 7)
- Flow E2E-01 : inscription → confirmation email → upload doc → vérification admin
- Flow E2E-02 : profil alternant → matching → calendrier → accord
- Flow E2E-03 : accord → paiement Stripe (mode test) → webhook → transaction
- Couverture JaCoCo ≥ 70% packages service/ et algorithm/
- Test de charge : /matching/suggestions P95 < 500ms pour 500 profils
- Test sécurité : ownership check sur tous les endpoints PUT/DELETE

---

## 10. VARIABLES D'ENVIRONNEMENT REQUISES

```bash
# Base de données
DATABASE_URL=postgresql://user:password@host:5432/dbname
REDIS_URL=redis://host:6379

# Sécurité
JWT_SECRET=<secret-256-bits-minimum>
JWT_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000

# MinIO
MINIO_ENDPOINT=https://...
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET_LOGEMENTS=logements
MINIO_BUCKET_DOCUMENTS=documents

# Stripe
STRIPE_SECRET_KEY=sk_live_... (sk_test_... en staging)
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PLATFORM_ACCOUNT_ID=...

# Firebase
FIREBASE_PROJECT_ID=...
FIREBASE_SERVICE_ACCOUNT_JSON=<base64 encoded>

# Monitoring
SENTRY_DSN=https://...
LOG_LEVEL=INFO

# Email
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USERNAME=apikey
SMTP_PASSWORD=<sendgrid-api-key>
FROM_EMAIL=noreply@nomplateforme.fr
```

---

## 11. ENDPOINTS API — RÉCAPITULATIF

```
Auth
POST   /api/v1/auth/register
POST   /api/v1/auth/confirm
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

Profils
POST   /api/v1/profile/alternant
PUT    /api/v1/profile/alternant
POST   /api/v1/profile/proprietaire
GET    /api/v1/profile/{userId}

Vérification
POST   /api/v1/verification/upload
PUT    /api/v1/admin/verification/{id}/approve
PUT    /api/v1/admin/verification/{id}/reject

Logements
GET    /api/v1/logements
POST   /api/v1/logements
GET    /api/v1/logements/{id}
PUT    /api/v1/logements/{id}
DELETE /api/v1/logements/{id}
PUT    /api/v1/logements/{id}/publish
PATCH  /api/v1/logements/{id}/ville

Matching
GET    /api/v1/matching/suggestions
GET    /api/v1/matching/score?user1=&user2=
GET    /api/v1/matching/partial?user1=&user2=

Calendrier
GET    /api/v1/calendrier/compatibilite?user1=&user2=
PATCH  /api/v1/calendrier/{profileId}/semaines/{semaine}
GET    /api/v1/calendrier/export
GET    /api/v1/calendrier/subscribe/{token}

Accords
GET    /api/v1/accords/mes-accords
POST   /api/v1/accords
PUT    /api/v1/accords/{id}/accept
PUT    /api/v1/accords/{id}/refuse
PUT    /api/v1/accords/{id}/cancel

Messages
GET    /api/v1/messages/{conversationId}
POST   /api/v1/messages/{id}/report
WS     /ws (STOMP)

Paiements
POST   /api/v1/paiements/loyer
POST   /api/v1/cautions
POST   /api/v1/cautions/{id}/release
POST   /api/v1/cautions/{id}/dispute
POST   /api/v1/subscriptions/premium
DELETE /api/v1/subscriptions/premium
GET    /api/v1/transactions/{id}/recu
POST   /api/v1/webhooks/stripe

Avis
POST   /api/v1/reviews
GET    /api/v1/reviews/user/{userId}
POST   /api/v1/reviews/{id}/report

Notifications
GET    /api/v1/notifications
PUT    /api/v1/notifications/{id}/read
GET    /api/v1/notifications/preferences
PUT    /api/v1/notifications/preferences

Dashboards
GET    /api/v1/dashboard/alternant
GET    /api/v1/dashboard/proprietaire
GET    /api/v1/dashboard/proprietaire/export

Admin
GET    /api/v1/admin/users
PUT    /api/v1/admin/users/{id}/suspend
PUT    /api/v1/admin/users/{id}/ban
GET    /api/v1/admin/verifications/pending
GET    /api/v1/admin/moderation/messages
PUT    /api/v1/admin/moderation/messages/{id}/hide
GET    /api/v1/admin/moderation/reviews
PUT    /api/v1/admin/moderation/reviews/{id}/hide
GET    /api/v1/admin/dashboard/revenus
GET    /api/v1/admin/transactions/export

B2B
GET    /api/v1/b2b/etudiants
POST   /api/v1/b2b/invitations

Actuator
GET    /actuator/health
GET    /actuator/health/liveness
GET    /actuator/health/readiness
GET    /actuator/metrics/{name}   (ADMIN)
GET    /actuator/info
```

---

*Dernière mise à jour : Juin 2026 — Sprint 1 en cours — prochain ticket : US-004 (Création profil alternant)*
*Application renommée Yuniv → StudUp (package Java com.yuniv.backend inchangé pour l'instant)*
*CI/CD GitHub Actions opérationnel — Docker local configuré — Flyway V1→V13 migrés*
*APP-048 (rate limiting Bucket4j) mergé et CI verte — APP-044 clôturé*
*Fichier à maintenir à jour après chaque ticket terminé*
