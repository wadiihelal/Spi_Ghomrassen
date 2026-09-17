# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SPI Ghomrassen — conventions du dépôt

Application interne d'un promoteur immobilier tunisien. `backend/` Spring Boot 3.3 / Java 17 /
PostgreSQL 16 / Flyway, `frontend/` Angular 19 / PrimeNG 17. Interface en français, montants en
dinars à trois décimales (millimes). Détails par module : `backend/README.md`, `frontend/README.md`.

## Commandes

```bash
# Backend (depuis backend/)
mvn -q verify                                    # suite H2 + Flyway ; ce que lance la CI
mvn -q verify -Ppostgres                         # + tests @Tag("postgres") sur PostgreSQL 16 réel (Docker)
mvn -q -Dtest=SalesBoardTest test                # une classe
mvn -q -Dtest='SalesBoardTest#aNewApartmentIsInStock' test   # une méthode
mvn spring-boot:run                              # sur PostgreSQL (docker compose up -d postgres)
mvn spring-boot:run -Dspring-boot.run.profiles=test,demo -Dspring-boot.run.useTestClasspath=true
                                                 # sans PostgreSQL : H2 en mémoire + données de démo

# Frontend (depuis frontend/)
npm start                                        # ng serve, http://localhost:4200, API sur :8080
npx ng build                                     # TypeScript strict + strictTemplates ; pas de tests unitaires côté front

# Tout
docker compose up --build                        # PostgreSQL + API + console sur http://localhost
```

`spring-boot:run` et `mvn verify` se disputent `target/` : arrêter l'application avant de lancer
la suite. Le profil `demo` charge des acquéreurs fictifs : jamais en production.
Sur ce poste, si `mvn` n'est pas dans le PATH : `~/.m2/wrapper/dists/apache-maven-3.9.9/*/bin/mvn`.

## Ce qui ne se négocie pas

- **Argent** : `BigDecimal`, `precision = 19, scale = 3`, `RoundingMode.HALF_UP`. Jamais
  `double` ni `float`. Le serveur calcule (TVA, plafonds, totaux) ; le navigateur affiche.
- **Découpage backend** : **un paquet par feature** (`expense/`, `client/`, `apartment/`…) qui
  contient entité, dépôt, spécifications, requête, réponse, mapper, service, implémentation et
  contrôleur. Les dépendances restent en couches : `Controller → XService (interface) →
  XServiceImpl → Repository`. `shared/` est le bas du graphe : une feature l'utilise, il
  n'utilise aucune feature. Ces règles sont **vérifiées par ArchUnit** (`ArchitectureTest`), qui
  reconnaît les couches par nom de classe et annotation, pas par paquet.
- **DTO à la frontière** : un contrôleur ne renvoie jamais d'entité JPA. Réponses = records
  (ids à plat + libellés, jamais d'objet imbriqué), mappées par MapStruct
  (`unmappedTargetPolicy = ERROR`). Une réponse a **une seule forme** partout où elle apparaît.
- **Schéma** : Flyway uniquement (`db/migration/V<n>__*.sql`), SQL portable PostgreSQL / H2,
  `ddl-auto=validate`. On n'édite jamais une migration livrée : on en ajoute une.
- **Chaînes utilisateur** : français accentué, jamais concaténées en Java ; clés anglaises dans
  `messages_fr.properties`, lues via `MessageService`. Un message **sans** `{0}` s'écrit avec une
  apostrophe simple (`MessageFormat` ne tourne pas dessus) ; un message **avec** paramètre la
  double. `MessageCatalogTest` le vérifie. Les accents d'`application.properties` s'écrivent en
  `è` : le fichier est lu en ISO-8859-1.
- **Tests** : toute règle financière (plafond, TVA, statut, cascade d'échéances, situation client)
  s'accompagne d'un test JUnit nommé d'après la règle en clair. Voir « Écrire un test ».
- **Lecture** : finders `@Transactional(readOnly = true)`, associations `LAZY`, `@EntityGraph` sur
  les listes (y compris `findAll(Specification, Pageable)` redéclaré), agrégats en JPQL avec
  `countQuery` — jamais `findAll()` puis regroupement en Java. Une page se résout avec un nombre
  de requêtes indépendant du nombre de lignes (`QueryCountTest`).
- **Écriture** : `@Version` partout ; `@Lock(PESSIMISTIC_WRITE)` là où un plafond se vérifie
  puis s'écrit (acomptes, CONC-01).
- **Frontend** : composants `standalone`, `ChangeDetectionStrategy.OnPush`, état asynchrone dans
  des signaux, RxJS libéré par `takeUntilDestroyed`. Les tables métier paginent côté serveur via
  `core/services/lazy-table.ts`. Les montants passent par `DinarPipe` (`full` par défaut,
  `'short'` pour les indicateurs, `'bare'` quand l'unité est dans le texte).
- **Périmètre projet** : un écran charge ses données sur `ProjectContextService.scope$`, jamais
  sur `selectedProjectId` directement. Le signal vaut `null` avant résolution ; une requête lancée
  sur ce `null` revient « tous projets » après la bonne et l'écrase.
- **Statuts dérivés** : l'état d'un encaissement, d'une échéance, d'une facture fournisseur se
  calcule à la lecture, jamais stocké. Seules les décisions que les données ne permettent pas de
  deviner sont stockées (`sales_status` : réservé, livré).

## Décisions métier à ne pas rouvrir

- **Pas d'authentification, pas de rôles** (02/09/2026) : protection au niveau réseau, voir
  `backend/README.md`. `audit_logs.actor` vaut `system`.
- **Serveur de test = compose de production + profil `demo` + mot de passe nginx** (15/09/2026) :
  `docker-compose.demo.yml` surcharge le compose, ne le remplace pas ; jamais de données réelles
  dessus ; PostgreSQL lié à `127.0.0.1`. Pas à pas dans `docs/SERVEUR-DE-TEST.md`.
- **Pas de retenue à la source** (CALC-02) : colonnes supprimées en V3.
- **Pièces jointes sur le système de fichiers local** (`app.storage.root`), pas de S3.
- **Sauvegardes : lacune assumée**, documentée dans `backend/README.md`.
- **La TVA collectée n'est pas suivie** : les ventes sont enregistrées TTC. Le document de TVA ne
  couvre que la TVA déductible et le dit.
- **Références par séquence** (`DEP-`, `ACC-`, `ACH-`, `PRJ-` + année + numéro), attribuées avant
  le premier enregistrement ; une référence saisie est conservée (reprise d'un contrat antérieur).
  Le numéro d'une facture fournisseur vient du fournisseur et reste saisi.
- **Le reçu ne consomme pas de séquence** : il porte la référence de l'encaissement, pour qu'un
  reçu réimprimé reste le même document.
- **Le compteur n'est pas remis à zéro chaque année** : l'année fait partie de la référence.

## Architecture en trois lectures

**Backend — une feature, un paquet.** `com.promoteur.app.<feature>` pour `advance`, `apartment`,
`attachment`, `audit`, `client`, `dashboard`, `document`, `expense`, `invoice` (factures
fournisseurs et leurs règlements), `project`, `purchase` (contrats de vente), `report`,
`schedule` (échéanciers), `search`, `supplier`, `vat`. Transverses : `shared/` (BaseEntity,
ListFilter, MessageService, ReferenceGeneratorService, PdfLetterhead, SpecificationSupport),
`config/` (CORS, `CompanyProperties` pour l'en-tête des documents, initialiseurs de données),
`exception/` (`GlobalExceptionHandler`, 11 familles d'erreurs en JSON français).

**Le motif « dérivé, pas stocké »** revient trois fois et il faut le connaître avant de toucher
aux totaux : `ClientPurchaseCalculationServiceImpl` (encaissé = paiement direct + acomptes,
statut qui en découle), `PaymentScheduleServiceImpl.describe` (l'argent d'un contrat se répartit
en cascade sur les échéances dans l'ordre), `SupplierInvoiceServiceImpl.settlementOf` (payé =
somme des règlements, retard = reste dû et échéance passée). Aucun de ces états n'a de colonne ;
un filtre sur un tel état se fait donc **après** mapping, pas en SQL.

**Frontend.** `core/` porte ce qui est unique : `api.service.ts` (tous les appels HTTP),
`project-context.service.ts` (le projet de travail, persisté côté serveur), `ui.service.ts`
(toasts, confirmation de suppression), `layout/` (barre latérale, en-tête, recherche globale).
`features/<écran>` = un composant chargé par `loadComponent`. `shared/` = modèles TypeScript,
pipes, et les panneaux réutilisés dans plusieurs écrans (pièces jointes, éditeur d'échéancier,
règlements fournisseurs). Le design system est en quatre couches CSS dans `src/styles/`
(`tokens.css` → `base.css` → `components.css` (surcharges PrimeNG) → `app.css`) ; un écran ne
définit que ce qui lui est propre. Les documents imprimables sont de simples liens vers
`/api/documents/...` : le navigateur ouvre le PDF.

## Écrire un test

Quatre couches, chacune avec sa classe de base ; choisir la plus basse qui prouve la règle.

| Couche | Base | Quand |
|---|---|---|
| Unitaire pur | aucune | logique sans base (`AmountInWordsTest`) |
| Service sur H2 | `AbstractIntegrationTest` | règle métier, statut dérivé, cascade — **le cas courant** |
| Web slice | `@WebMvcTest` + Mockito (`web/`) | codes HTTP, JSON d'erreur, validation ; Mockito n'est autorisé qu'ici |
| Persistance | `AbstractPersistenceTest` (`persistence/`) | spécifications, graphe de chargement |
| PostgreSQL réel | `AbstractPostgresTest` + `@Tag("postgres")` (`postgres/`) | dialecte, verrous, séquences sous concurrence |

`AbstractIntegrationTest` partage **un** contexte Spring et une base H2 nettoyée **avant** chaque
classe par `DatabaseCleaner` : ne pas ajouter de `@TestPropertySource` propre (cela casserait le
cache de contexte) et semer les fixtures dans le `@BeforeAll` de la sous-classe. Deux classes
restent isolées à dessein car elles testent l'amorçage : `StartupSeedTest`, `DemoProfileSeedTest`.
Les tests PostgreSQL sont exclus par défaut et ne tournent qu'avec `-Ppostgres` ; leur pool est
élargi à 25 parce que **toute écriture consomme deux connexions** (journal d'audit en
`REQUIRES_NEW`) — constat de production non tranché, voir `backend/README.md`.

`TEST_HARDENING_SPEC.md` décrit les cinq lots qui ont construit cette suite ; tous sont livrés.
`/test-hardening status` (commande dans `.claude/commands/`) fait le point sans rien écrire.

## Chantier en cours : retours de la démonstration client (15/09/2026)

`DEMO_FEEDBACK_SPEC.md` découpe les demandes du client en cinq lots : publication sur GitHub
(livré le 15/09/2026 : `github.com/wadiihelal/Spi_Ghomrassen`), serveur de test sur un VPS avec
le jeu `demo` pour une démonstration supplémentaire, manuel d'utilisation illustré de captures d'écran,
profil `laptop` (un seul exécutable, H2 en mode fichier, port lié à `127.0.0.1`) et installateur
Windows produit par GitHub Actions avec ses deux guides. Séquence réelle chez le client : serveur
de test d'abord, portable ensuite et **sans urgence** — le client acquiert le PC après validation.

```
/demo-feedback status   # où en est-on, sans rien écrire
/demo-feedback lot1bis  # puis lot2, lot3, lot4 — dans cet ordre (lot1 est livré)
```

## Où sont les choses

| Besoin | Emplacement (`backend/src/main/java/com/promoteur/app/`) |
|---|---|
| Plafond des acomptes, verrou, statut de paiement | `advance/ClientAdvanceServiceImpl`, `purchase/ClientPurchaseCalculationServiceImpl` |
| Échéanciers et cascade | `schedule/PaymentScheduleServiceImpl`, table `payment_installments` (V9) |
| Règlements fournisseurs, retards | `invoice/SupplierInvoiceServiceImpl`, table `supplier_payments` (V10) |
| Statut commercial des lots, plan de vente | `apartment/ApartmentServiceImpl` (`salesBoard`, `changeSalesStatus`), V11 |
| Documents PDF (reçu, situation, TVA) | `document/DocumentServiceImpl`, `shared/PdfLetterhead`, `document/AmountInWordsServiceImpl` |
| En-tête des documents | `config/CompanyProperties` ← `app.company.*` |
| TVA | `vat/VatCalculationServiceImpl`, table `vat_rate_options` |
| Rapports et périmètre | `report/ReportFilter`, `report/ReportServiceImpl` |
| Filtres des listes | `shared/ListFilter`, `<feature>/XSpecifications`, `shared/SpecificationSupport` |
| Références | `shared/ReferenceGeneratorServiceImpl`, séquences V6 (DEP, ACC) et V12 (ACH) |
| Recherche globale | `search/SearchServiceImpl`, méthodes `search(...)` des dépôts (sensible aux accents, constat ouvert) |
| Pièces jointes | `attachment/AttachmentServiceImpl`, `attachment/LocalFileSystemStorageService` |
| Journal d'audit | `audit/`, écrit en `REQUIRES_NEW` par chaque `*ServiceImpl` |
| Messages français | `src/main/resources/messages_fr.properties`, `ValidationMessages.properties` |
| Données de démo | `config/DemoDataInitializer` (profil `demo`), `config/ReferenceDataInitializer` |
| OpenAPI | `/swagger-ui.html`, profils `dev` et `test` uniquement |

Dettes connues et nommées :

- `shared/ReferenceGeneratorServiceImpl` lit trois dépôts de features pour vérifier qu'une
  référence tirée est libre — seule exclusion de `sharedDoesNotDependOnAFeature`, à corriger en
  inversant le contrôle.
- `shared/ReferenceGeneratorServiceImpl.nextProjectCode` prend le contrôle « déjà pris ? » de
  son appelant (V13, 15/09/2026) : c'est l'inversion que la règle ArchUnit réclame, appliquée à la
  seule méthode neuve. Les trois anciennes la doivent encore.
- `frontend/.npmrc` force `legacy-peer-deps=true` : PrimeNG 17 ne déclare qu'Angular 17/18 en
  peer dependency alors que le projet est sur Angular 19, et `npm ci` refuserait sinon toute
  installation sur machine neuve (CI, `frontend-maven-plugin`, release). La sortie est la
  migration vers PrimeNG 19 (nouveau système de thèmes, `components.css` à reprendre) — un
  chantier à part, pas un correctif.
