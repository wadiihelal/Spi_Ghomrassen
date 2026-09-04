# CLAUDE.md — backend

Ce fichier guide Claude Code (claude.ai/code) dans `backend/`. Il complète le
[CLAUDE.md racine](../CLAUDE.md), qui reste la référence sur ce qui ne se négocie pas, et le
[README](README.md), qui documente l'API route par route.

## Le module

API REST d'une application interne de promoteur immobilier tunisien : projets, appartements,
clients, contrats de vente, encaissements, échéanciers, factures et règlements fournisseurs,
dépenses, pièces jointes, journal d'audit, documents imprimés et rapports Excel / PDF.

Spring Boot 3.3.5, Java 17, Spring Web + Data JPA + Validation, PostgreSQL 16 (H2 en test),
Flyway, MapStruct, Lombok, Apache POI (Excel), OpenPDF (PDF), springdoc-openapi. Racine de
package `com.promoteur.app`. Pas de Spring Security : la dépendance n'est pas là et ne doit pas
y entrer sans décision explicite (voir « Sécurité » plus bas).

Les javadoc et commentaires sont en **anglais**, les chaînes destinées à l'utilisateur en
**français accentué** dans `messages_fr.properties`. Les libellés OpenAPI (`@Tag`,
`@Operation`) sont en français : ils s'affichent dans Swagger UI.

## Commandes

```bash
# Compilation + toute la suite de tests (H2 + mêmes migrations Flyway qu'en production)
mvn -q verify

# Une seule classe de test
mvn test -Dtest=VatCalculationTest

# Une seule méthode
mvn test -Dtest=AdvanceCeilingTest#anAdvanceAboveTheContractTotalIsRefused

# Démarrage sur PostgreSQL local (profil dev par défaut) — nécessite docker compose up -d postgres
mvn spring-boot:run

# Démarrage sans PostgreSQL : H2 + jeu de démonstration
mvn spring-boot:run -Dspring-boot.run.profiles=test,demo -Dspring-boot.run.useTestClasspath=true
```

`useTestClasspath` est indispensable dans la dernière commande : le pilote H2 est en
`scope=test`, sans lui le contexte ne démarre pas.

Aucun outil de formatage ni de lint n'est configuré. Le seul garde-fou automatique est
`mvn verify` (`.github/workflows/ci.yml` le lance sur chaque push).

## Pièges au démarrage et en test

- **`ddl-auto=validate` dans tous les profils.** Ajouter un champ à une entité sans écrire la
  migration correspondante ne casse pas la compilation : ça casse le démarrage *et les 19
  classes de test d'un coup*, avec un message Hibernate sur la colonne manquante. Entité et
  migration se livrent ensemble.
- **Les migrations doivent tourner sur PostgreSQL *et* H2.** La suite de tests reconstruit le
  schéma avec les mêmes fichiers que la production. Pas de `jsonb`, pas de `ON CONFLICT`, pas de
  type PostgreSQL exclusif dans `db/migration`. Une migration livrée ne se réécrit jamais —
  V7 existe précisément parce qu'on a corrigé des données au lieu de modifier V6.
- **Chaque classe de test a sa propre base H2**, nommée dans son `@TestPropertySource`
  (`jdbc:h2:mem:spi_ghomrassen_test_vat`, `..._queries`, `..._messages`…). Une nouvelle classe
  de test **doit** déclarer la sienne : réutiliser un nom existant fait hériter les lignes de
  l'autre classe, et les comptages de requêtes ou de totaux deviennent faux de façon aléatoire.
- **`@TestInstance(PER_CLASS)` + `@BeforeAll`** : les tests ne roulent pas en arrière entre les
  méthodes. Les fixtures sont créées une fois et les données s'accumulent, d'où les
  `AtomicInteger sequence` qui rendent uniques les numéros de lot et les références. Une méthode
  qui compte des lignes doit filtrer sur son propre projet, pas compter la table.
- **`application.properties` est lu en ISO-8859-1.** Les accents s'y écrivent en échappement
  unicode (`Immobilière`), sinon ils arrivent déformés sur les documents imprimés.
  `messages_fr.properties` est à l'inverse lu en UTF-8 (`MessageSourceConfig` fixe
  `defaultEncoding`) : les accents s'y écrivent normalement.
- **Apostrophes dans `messages_fr.properties`.** `MessageFormat` ne déduplique `''` que si le
  message a un placeholder. `n''a pas` dans un message sans `{0}` arrive tel quel à
  l'utilisateur. `MessageCatalogTest` refuse ce cas — ne pas contourner le test, corriger la
  chaîne.
- **Le pilote H2 est en `scope=test`** : rien dans `src/main` ne peut en dépendre.
- **La console H2 est désactivée dans tous les profils** (SEC-02) et doit le rester.

## Découpage

```
controller/            @RestController, un par ressource, /api/**
  ↓ DTO de requête (dto/) validés par Jakarta Validation
service/               interfaces — le contrat métier
service/impl/          implémentations @Service, où vivent les règles
  ↓ entités (entity/)
repository/            Spring Data JPA
repository/specification/  prédicats des listes filtrées
mapper/                MapStruct entité → dto/response
```

Règles structurantes :

- **Un nouveau service = une interface dans `service/` + une implémentation dans
  `service/impl/`.** Jamais de `@Service` sans interface, jamais de logique métier dans un
  contrôleur.
- **Les contrôleurs ne renvoient jamais d'entité JPA.** Les réponses sont des `record` de
  `dto/response`, avec les identifiants à plat et les libellés à côté (`projectId` +
  `projectName`), jamais d'objet imbriqué. Le mapping est fait par MapStruct.
- **`unmappedTargetPolicy=ERROR`** est passé au compilateur (`pom.xml`). Ajouter un champ à un
  `…Response` sans `@Mapping` correspondant fait **échouer la compilation** — c'est voulu.
- **L'ordre des annotation processors compte** : Lombok, puis `lombok-mapstruct-binding`, puis
  MapStruct. MapStruct lit les accesseurs générés par Lombok. Ne pas réordonner ce bloc.
- Les contrôleurs déclarent chaque filtre en `@RequestParam(required = false)` puis construisent
  un `ListFilter` — voir `ExpenseController.findAll` comme modèle.

## Conventions de code

- **Argent** : `BigDecimal`, `@Column(precision = 19, scale = 3)`, `RoundingMode.HALF_UP`. Le
  dinar se divise en 1000 millimes ; `scale = 2` perd de l'argent réel. Jamais `double` ni
  `float`. Comparaisons par `compareTo`, jamais `equals`.
- **Le serveur calcule, le navigateur affiche.** TVA, plafonds, totaux, statuts dérivés : si le
  client envoie une valeur calculée, elle est vérifiée et refusée en cas d'écart
  (`VatCalculationService.rejectInconsistentDeclaration`), pas acceptée.
- **`BaseEntity`** porte `id`, `createdAt`, `updatedAt` et `@Version`. Toute entité en hérite.
  Le `@Version` rend le conflit d'écriture visible : `OptimisticLockingFailureException` →
  `409`, traité une fois pour toutes dans `GlobalExceptionHandler`.
- **Lecture** : finders `@Transactional(readOnly = true)`, associations `LAZY`, `@EntityGraph`
  sur les listes, agrégats en JPQL. Jamais `findAll()` suivi d'un regroupement en Java —
  `QueryCountTest` compte les requêtes et échoue si le N+1 revient.
- **Chaînes utilisateur** : jamais concaténées en Java. Clé anglaise dans
  `messages_fr.properties`, valeur française, lue via `MessageService.get(key, args…)`.
- Champs et paramètres `final` dans les implémentations, accès par `this.`, `@RequiredArgsConstructor`
  pour l'injection. Suivre le style du fichier voisin plutôt que d'en introduire un autre.
- Les commentaires citent le code de l'exigence traitée (`PERF-02`, `CALC-01`, `UX-05`…). C'est
  la trace du travail de remédiation décrit dans `../CLAUDE_CODE_PROMPT.md` ; conserver ces
  références quand on touche au code concerné.

## Erreurs et codes HTTP

`GlobalExceptionHandler` est le seul endroit qui traduit une exception en réponse. Corps JSON
`{ timestamp, status, error, details? }`, message en français.

| Exception | Statut |
|---|---|
| `ResourceNotFoundException` | `404` |
| `MethodArgumentNotValidException` | `400` + `details` champ par champ |
| `IllegalArgumentException` | `400` — c'est le véhicule des refus métier |
| `OptimisticLockingFailureException` | `409` |
| `MaxUploadSizeExceededException` | `413` |
| tout le reste | `500`, journalisé |

Un refus métier se lève donc en `IllegalArgumentException` avec un message issu de
`MessageService` — pas d'exception maison par règle. Création → `201` + en-tête `Location`
(`locationOf`), suppression → `204`.

## Domaine

`Project` est le périmètre de tout. `Apartment` appartient à un projet ; `ClientPurchase` (le
contrat de vente) en lie un à un `Client`, **un seul par appartement** ; `ClientAdvance` est un
encaissement rattaché à l'appartement ; `PaymentInstallment` est une ligne d'échéancier portée
par le contrat. Côté sortant : `Supplier` → `SupplierInvoice` → `SupplierPayment`, et `Expense`
pour les frais. `FileAttachment` se rattache à l'un des quatre types de
`AttachmentOwnerType`. `AuditLog` enregistre tout.

**Statuts dérivés, jamais stockés** : `PurchasePaymentStatus` (UNPAID / PARTIALLY_PAID / PAID)
vient de ce qui a été encaissé, `InstallmentStatus` de la cascade d'imputation des encaissements
sur les échéances, `SettlementStatus` de la somme des règlements d'une facture. Il n'y a rien à
resynchroniser, et rien de tel ne doit être ajouté en colonne. L'unique exception est
`sales_status` (`AVAILABLE` / `RESERVED` / `SOLD` / `DELIVERED`), parce que « réservé » et
« livré » sont des décisions du promoteur que les données ne permettent pas de deviner.

**Règles financières à ne pas contourner** (chacune a son test) :

- Plafond d'acompte : total du contrat s'il existe, sinon prix de vente du lot, sinon refus.
  Le contrôle est sérialisé par un verrou pessimiste sur la ligne appartement
  (`ApartmentRepository`, `@Lock(PESSIMISTIC_WRITE)`) — deux encaissements simultanés ne peuvent
  pas passer le plafond ensemble.
- TVA : le client envoie `amountHt` + `vatRate`, le serveur produit `vatAmount` et `amountTtc`.
  Taux en base (`vat_rate_options`), pas en dur. Pas de retenue à la source (colonnes supprimées
  en V3).
- Un échéancier qui ne couvre pas le total du contrat est refusé.
- Un règlement fournisseur qui dépasserait le TTC de la facture est refusé. Sans `due_date`,
  une facture n'est jamais en retard.
- Un lot sous contrat ne peut pas repasser en stock ; un lot sans contrat ne peut pas être vendu.

## Références de documents

`ReferenceGeneratorServiceImpl` attribue `DEP-2026-00042` (dépense), `ACC-…` (encaissement),
`ACH-…` (contrat) **avant** le premier enregistrement, depuis une séquence SQL
(`expense_ref_seq`, `advance_ref_seq`, `purchase_ref_seq`). Deux points à connaître :

- la valeur suivante est lue via le `SequenceSupport` du dialecte Hibernate configuré, ce qui
  rend le même code portable PostgreSQL / H2 ;
- le compteur **n'est pas remis à zéro chaque année** (l'année fait partie de la référence), et
  chaque candidat est vérifié avant d'être attribué, parce que des références saisies à la main
  ou réparées par V7 échappent à la séquence.

Le reçu de paiement **ne consomme pas de séquence** : il porte la référence de l'encaissement,
pour qu'un reçu réimprimé reste le même document.

## Documents imprimés et rapports

`DocumentServiceImpl` produit trois PDF en lecture seule à partir des chiffres enregistrés :
reçu, situation de compte, récapitulatif de TVA. `PdfLetterhead` (package-private) tient
l'en-tête, l'échelle typographique et le format monétaire communs — les services décident *ce
que* dit un document, `PdfLetterhead` décide *à quoi il ressemble*. L'identité de la société
vient de `app.company.*` via `CompanyProperties`. `AmountInWordsServiceImpl` écrit le montant en
lettres.

Tout rapport passe par `ReportFilter` → `ReportScope` : `projectId` absent signifie « projet
actif », `projectId=ALL` signifie « tous les projets », et le périmètre résolu est **imprimé en
en-tête de l'export** (`ReportScope.describe()`). Ne pas produire un chiffre sans son périmètre.

La TVA collectée n'est pas suivie (les ventes sont enregistrées TTC) : le document de TVA ne
couvre que la TVA déductible et le dit explicitement.

## Profils et configuration

| Profil | Base | Notes |
|---|---|---|
| `dev` (défaut) | PostgreSQL `localhost:5432/spi_ghomrassen` | `show-sql=true`, OpenAPI ouvert, pièces jointes dans `~/.spi-ghomrassen/attachments` |
| `test` | H2 en mémoire, mêmes migrations | suite de tests, OpenAPI ouvert |
| `demo` | s'ajoute à un autre profil | acquéreurs, lots et paiements **fictifs** — jamais en production |
| `prod` | PostgreSQL via `${DATABASE_URL}` | aucune valeur par défaut, OpenAPI fermé |

Variables obligatoires en `prod` : `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`,
`APP_CORS_ALLOWED_ORIGINS`, `ATTACHMENTS_ROOT`.

`ReferenceDataInitializer` (`@Order(1)`) tourne dans **tous** les profils et crée uniquement ce
qui manque : catégories de dépense, types de fournisseur, taux de TVA. Un opérateur qui supprime
un libellé le retrouve au démarrage suivant, sans duplication du reste.
`DemoDataInitializer` est `@Profile("demo")` et passe par les services, pas par les repositories
— les données de démonstration respectent donc les mêmes règles que la saisie réelle.

## Sécurité — décision arrêtée

**Aucune authentification, aucun rôle** (décision du 02/09/2026). Toute personne capable
d'atteindre l'API lit les CIN, téléphones, adresses et montants des acquéreurs, et peut
supprimer n'importe quel enregistrement. La protection est réseau : le backend ne publie pas
8080, nginx est devant, l'accès est filtré par pare-feu ou VPN (voir [README](README.md)).

`audit_logs.actor` vaut `system`. **`AuditLogServiceImpl.resolveActor()` est le seul point à
changer** si un login est ajouté un jour — le reste du journal n'a pas besoin d'être touché.
L'écriture du journal se fait en `REQUIRES_NEW` : une transaction métier qui échoue laisse
quand même la trace de la tentative.

Ne pas ouvrir l'API, ne pas publier le port, ne pas introduire d'écran de connexion partiel sans
que la décision soit rouverte explicitement.

## Pièces jointes

`AttachmentServiceImpl` + `LocalFileSystemStorageService` : de vrais fichiers sur le système de
fichiers local sous `app.storage.root`, pas S3. PDF, JPEG, PNG, 10 Mo — la limite est déclarée
deux fois, dans `application.properties` (couche servlet, → `413`) et dans le service (règle
métier) ; les deux doivent rester d'accord. Un fichier ne se rattache qu'à un document déjà
enregistré. `StorageService` ne connaît qu'une clé opaque : c'est le point d'extension si le
stockage change un jour.

**Sauvegardes : lacune assumée.** Le dump PostgreSQL ne contient pas les fichiers ; une
sauvegarde complète couvre `spi-postgres-data` **et** `spi-attachments`.

## Tests

`src/test/java/com/promoteur/app/` — 19 classes, ~177 méthodes `@Test`, toutes `@SpringBootTest`
sur H2. Les compteurs cités dans `../CLAUDE.md` (140) et `README.md` (83) sont périmés ; ne pas
s'y fier, lancer `mvn -q verify`.

Une classe par règle, nommée d'après elle, et des `@DisplayName` qui énoncent la règle en clair
(« an advance above the contract total is refused »). Toute modification d'une règle financière
s'accompagne d'un test ; les classes existantes indiquent où l'ajouter :

| Classe | Ce qu'elle garde |
|---|---|
| `VatCalculationTest` | TVA calculée côté serveur, tout taux tunisien, scale 3 |
| `AdvanceCeilingTest` | plafond d'encaissement |
| `ClientPurchaseCalculationTest` | encaissé et statut dérivés d'un contrat |
| `PaymentScheduleTest` | échéanciers, cascade d'imputation, statuts |
| `SupplierPaymentTest` | règlements fournisseurs, dépassement, retard |
| `SalesBoardTest` | transitions de `sales_status` |
| `ClientStatementTest`, `DocumentTest`, `AmountInWordsTest` | situations de compte et documents |
| `ReferenceGenerationTest`, `PurchaseReferenceTest` | séquences de références |
| `ReportScopeTest` | périmètre projet/période des rapports |
| `QueryCountTest` | non-régression N+1 (compte les requêtes Hibernate) |
| `ServerSideFilteringTest` | filtres et pagination faits en SQL |
| `MessageCatalogTest` | catalogue français (apostrophes `MessageFormat`) |
| `AttachmentTest`, `AuditTrailTest`, `SearchServiceTest` | pièces jointes, journal, recherche globale |
| `StartupSeedTest`, `DemoProfileSeedTest` | idempotence des seeds |

## Où sont les choses

| Besoin | Emplacement |
|---|---|
| Règles d'encaissement | `service/impl/ClientAdvanceServiceImpl`, `ClientPurchaseCalculationServiceImpl` |
| Échéanciers | `service/impl/PaymentScheduleServiceImpl`, table `payment_installments` (V9) |
| Règlements fournisseurs | `service/impl/SupplierInvoiceServiceImpl`, table `supplier_payments` (V10) |
| Statut commercial des lots | `service/impl/ApartmentServiceImpl` (`salesBoard`, `changeSalesStatus`), V11 |
| Documents imprimés | `service/impl/DocumentServiceImpl`, `PdfLetterhead`, `AmountInWordsServiceImpl` |
| En-tête des documents | `app.company.*` dans `application.properties`, `config/CompanyProperties` |
| TVA | `service/impl/VatCalculationServiceImpl`, table `vat_rate_options` |
| Rapports et périmètre | `dto/report/ReportFilter`, `ReportScope`, `service/impl/ReportServiceImpl` |
| Filtres des listes | `dto/ListFilter`, `repository/specification/*` |
| Références de documents | `service/impl/ReferenceGeneratorServiceImpl`, séquences V6 et V12 |
| Recherche globale | `service/impl/SearchServiceImpl`, `GET /api/search?q=` |
| Pièces jointes | `service/impl/AttachmentServiceImpl`, `LocalFileSystemStorageService` |
| Messages français | `src/main/resources/messages_fr.properties` |
| Traduction des exceptions | `exception/GlobalExceptionHandler` |
| OpenAPI | `/swagger-ui.html`, profils `dev` et `test` uniquement |
