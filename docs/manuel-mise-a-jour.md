# Manuel de mise à jour — StudUp

Ce document décrit comment faire évoluer StudUp après sa mise en service : livrer une
modification de code, faire évoluer le schéma de base, mettre à jour les dépendances,
changer la configuration, publier une version et revenir en arrière si nécessaire.

Il complète les deux README (installation et démarrage) et le `CHANGELOG.md` de chaque
dépôt (historique des évolutions).

---

## 1. Livrer une modification de code

Le cycle est identique sur les deux dépôts.

```bash
# 1. Partir d'une base à jour
git checkout main
git pull origin main

# 2. Une branche par ticket — la clé Jira est réelle, jamais inventée
git checkout -b feature/APP-XX-description-courte

# 3. Développer avec les tests. Deux couches obligatoires par ticket :
#    backend  : XxxServiceTest + XxxControllerTest
#    frontend : xxx_viewmodel_test.dart

# 4. Vérifier localement AVANT de pousser
./mvnw test                # backend
flutter analyze && flutter test   # frontend

# 5. Pousser et ouvrir une pull request vers main
git push origin feature/APP-XX-description-courte
```

La pull request déclenche le pipeline d'intégration. **Une pull request rouge ne se
fusionne pas.** Après fusion, un push sur `main` déclenche le déploiement Railway.

Conventions de message de commit : `feat`, `fix`, `test`, `refactor`, `docs`, `chore`,
suivis de la portée et de la clé Jira — par exemple
`fix(logement): corrige le verrou sur accord mort (APP-117)`.

---

## 2. Faire évoluer le schéma de base

**Le schéma appartient à Flyway.** Hibernate est en `ddl-auto=validate` : il vérifie que
les entités correspondent à la base, il ne la modifie jamais. Toute évolution passe donc
par une migration.

```
src/main/resources/db/migration/V29__description_courte.sql
```

Règles à respecter :

1. **Ne jamais modifier une migration déjà appliquée.** Flyway conserve une empreinte de
   chaque fichier ; le modifier fait échouer le démarrage. Pour corriger, créer une
   nouvelle migration.
2. **Numéroter à la suite** — la dernière migration en date est `V28`.
3. **Adapter l'entité JPA dans le même ticket**, sinon `validate` bloque le démarrage.
4. Deux contraintes PostgreSQL apprises en cours de projet :
   - `spring.flyway.execute-in-transaction=false` est obligatoire — un
     `ALTER TYPE ... ADD VALUE` doit être commité avant d'être utilisé ;
   - les index partiels utilisant `NOW()` sont interdits (fonction non immuable).

Les migrations sont appliquées **automatiquement au démarrage** de l'application : aucune
action manuelle n'est requise en production.

Pour une migration destructrice (suppression de colonne ou de table), prévoir une
libération en deux temps : d'abord cesser d'utiliser la colonne dans le code et déployer,
ensuite supprimer la colonne dans une version suivante. Cela garantit qu'un retour en
arrière reste possible entre les deux.

---

## 3. Mettre à jour les dépendances

### Backend

```bash
./mvnw versions:display-dependency-updates   # ce qui est disponible
./mvnw verify -Psecurity                     # scan des vulnérabilités connues
```

Le scan croise chaque dépendance avec la base NVD et **échoue si une faille de gravité
haute (CVSS ≥ 7) est présente**. Le rapport détaillé est produit dans
`target/dependency-check-report.html`.

Procédure recommandée :

1. Lancer le scan pour établir l'état courant.
2. Monter les versions par petits lots — de préférence des correctifs, en traitant les
   montées de version majeure une par une.
3. Relancer **toute** la suite de tests après chaque lot.
4. Relancer le scan pour mesurer le gain.
5. Consigner le résultat dans `CHANGELOG.md` et dans `docs/securite-owasp.md`.

Certaines vulnérabilités ne sont pas corrigeables directement : elles proviennent de
dépendances transitives dont la version est imposée par un dépôt amont (BOM Spring Boot,
SDK Firebase). Elles se traitent en attendant le correctif amont, en documentant l'analyse
plutôt qu'en forçant une version incompatible.

### Frontend

```bash
flutter pub outdated     # versions courante / résoluble / dernière, paquets abandonnés
flutter pub upgrade
dart run build_runner build --delete-conflicting-outputs
flutter analyze && flutter test
```

La régénération du code est indispensable après toute mise à jour touchant Stacked ou
`build_runner` : le routeur et le conteneur d'injection ne sont pas versionnés.

---

## 4. Modifier la configuration

Aucun secret ne figure dans le dépôt. Chaque paramètre sensible est lu depuis une
variable d'environnement, avec une valeur par défaut valable **uniquement en
développement local**.

Pour modifier un paramètre en production, changer la variable dans l'interface Railway du
service concerné, puis redémarrer le service. Ne jamais écrire une valeur de production
dans `application.properties`.

Variables à ne jamais laisser à leur valeur par défaut en production : `JWT_SECRET`
(256 bits minimum) et `CORS_ALLOWED_ORIGINS` (à restreindre aux origines réelles).

Côté mobile, l'URL de l'API est injectée à la compilation :

```bash
flutter build apk --release --dart-define=API_URL=https://<hote>/api/v1
```

---

## 5. Publier une version

1. Vérifier que `main` est verte.
2. Incrémenter la version — `pom.xml` côté backend, `pubspec.yaml` côté mobile (où
   `1.2.0+5` signifie version affichée `1.2.0`, build `5`).
   Versionnement sémantique : **MAJEUR** pour une rupture de compatibilité, **MINEUR**
   pour une fonctionnalité rétrocompatible, **CORRECTIF** pour une correction.
3. Déplacer le contenu de la section `[Non publié]` du `CHANGELOG.md` sous un nouveau
   numéro de version daté, et mettre à jour les liens de comparaison en bas de fichier.
4. Créer le tag et la release GitHub correspondante.

---

## 6. Revenir en arrière

| Situation | Action |
|---|---|
| Régression détectée après déploiement | Redéployer le déploiement précédent depuis l'historique Railway — le retour est immédiat |
| Correctif à livrer en urgence | Branche `hotfix/APP-XX-…` depuis `main`, même cycle de vérification, aucun raccourci sur les tests |
| Migration de schéma en cause | **Ne pas supprimer la migration.** Écrire une migration compensatoire qui rétablit l'état attendu |

Le retour arrière du code est toujours possible ; celui du schéma ne l'est pas
automatiquement. C'est la raison pour laquelle les migrations destructrices se font en
deux temps (voir § 2).

---

## 7. Surveiller après mise à jour

| Point de contrôle | Où |
|---|---|
| Le service répond | `/actuator/health/liveness` et `/actuator/health/readiness` |
| État des composants (base, cache, stockage) | `/actuator/health` — réservé au rôle ADMIN |
| Métriques applicatives | `/actuator/metrics/matchings.created`, `/actuator/metrics/inscriptions.daily` |
| Erreurs | Journaux Railway au format JSON, corrélables par identifiant de requête et d'utilisateur |

Après une mise à jour, vérifier en priorité que les migrations se sont appliquées (visible
au démarrage dans les journaux) et que la sonde de disponibilité repasse au vert.
