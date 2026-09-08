# SPI Ghomrassen — conventions du dépôt

Application interne d'un promoteur immobilier tunisien. `backend/` Spring Boot 3.3 / Java 17,
`frontend/` Angular 19 / PrimeNG 17. Interface en français.

## Ce qui ne se négocie pas

- **Argent** : `BigDecimal`, `precision = 19, scale = 3` (millimes), `RoundingMode.HALF_UP`.
  Jamais `double` ni `float`. Le serveur calcule (TVA, plafonds, totaux) ; le navigateur affiche.
- **Découpage** : `controller → service (interface) → service/impl → repository`. Un nouveau
  service = une interface dans `service/` + une implémentation dans `service/impl/`.
- **DTO à la frontière** : les contrôleurs ne renvoient jamais d'entité JPA. Réponses dans
  `dto/response` (records, ids à plat + libellés), mappées par MapStruct dans `mapper/`
  (`unmappedTargetPolicy = ERROR`).
- **Schéma** : Flyway uniquement (`db/migration/V<n>__*.sql`), portable PostgreSQL / H2.
  `ddl-auto=validate`. On n'édite jamais une migration déjà livrée.
- **Chaînes utilisateur** : français accentué, jamais concaténées en Java ; clés anglaises dans
  `messages_fr.properties`, lues via `MessageService`.
- **Tests** : toute règle financière (plafond, TVA, statut, situation client) s'accompagne d'un
  test JUnit dans `backend/src/test`, nommé d'après la règle en clair. La suite tourne sur H2
  avec les mêmes migrations que la production.
- **Lecture** : finders `@Transactional(readOnly = true)`, associations `LAZY`, `@EntityGraph`
  sur les listes, agrégats en JPQL — jamais `findAll()` puis regroupement en Java.
- **Frontend** : `standalone`, `OnPush`, état asynchrone dans des signaux, RxJS restant libéré
  par `takeUntilDestroyed`. Les tables métier paginent côté serveur (`LazyTable`).
- **Périmètre projet** : un écran charge ses données sur `ProjectContextService.scope$`, jamais
  sur `selectedProjectId` directement. Le signal vaut `null` avant résolution, et une requête
  lancée sur ce `null` revient « tous projets » après la bonne : les chiffres d'un projet se
  retrouvent affichés sous le nom d'un autre.
- **Statuts dérivés** : l'état d'un encaissement, d'une échéance, d'une facture fournisseur se
  calcule à la lecture. Rien de tel n'est stocké — il n'y a donc rien à resynchroniser. Seules
  les décisions que les données ne permettent pas de deviner sont stockées (`sales_status`).

## Décisions métier à ne pas rouvrir

- **Pas d'authentification, pas de rôles** (décision du 02/09/2026) : l'API est protégée au
  niveau réseau, voir `backend/README.md`. `audit_logs.actor` vaut `system`.
- **Pas de retenue à la source** (CALC-02, même date) : colonnes supprimées en V3.
- **Pièces jointes sur le système de fichiers local** (`app.storage.root`), pas de S3.
- **Sauvegardes : lacune assumée** pour l'instant, documentée dans `backend/README.md`.
- **La TVA collectée n'est pas suivie** : les ventes sont enregistrées TTC. Le document de TVA
  ne couvre donc que la TVA déductible et le dit noir sur blanc.
- **Une référence de contrat de vente est attribuée par séquence** (`ACH-2026-00042`), comme
  celles des dépenses et des acomptes. Saisie à la main, elle est conservée : c'est la reprise
  d'un contrat antérieur à l'application. Le numéro d'une facture fournisseur, lui, vient du
  fournisseur et reste saisi.
- **Le reçu ne consomme pas de séquence** : il porte la référence de l'encaissement, pour qu'un
  reçu réimprimé reste le même document.

## Vérifier avant de livrer

```bash
cd backend && mvn -q verify             # 274 tests, H2 + Flyway, un contexte Spring partage
cd backend && mvn -q verify -Ppostgres  # 291 tests : + PostgreSQL 16 reel, demande Docker
cd frontend && npx ng build             # TypeScript strict + strictTemplates
```

Les tests marques `@Tag("postgres")` sont exclus par defaut : `mvn verify` reste utilisable
sans demon Docker, et c'est ce que lance la CI. Voir `backend/README.md`, section
« Tests sur PostgreSQL ».

Sans PostgreSQL local, le backend se lance sur H2 avec les données de démonstration :

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=test,demo -Dspring-boot.run.useTestClasspath=true
```

Le profil `demo` charge des acquéreurs fictifs : jamais en production.

## Chantier en cours : durcissement des tests

`TEST_HARDENING_SPEC.md` decoupe le travail en cinq lots (WP0 a WP4) : mutualisation du
contexte Spring, tests PostgreSQL via Testcontainers, couche web (`@WebMvcTest`), couche
persistance (`@DataJpaTest` sur les specifications), et regles d'architecture (ArchUnit).

Un lot se livre avec la commande `/test-hardening` :

```
/test-hardening status   # ou en est-on, sans rien ecrire
/test-hardening wp0      # puis wp1, wp2, wp3, wp4 — dans cet ordre
```

Etat de depart : 20 classes, 152 executions, dont **un seul test unitaire pur**
(`AmountInWordsTest`). Les 19 autres classes sont des `@SpringBootTest` sur H2, alors que
la production tourne sur PostgreSQL 16. Aucun test ne couvre les 19 controleurs ni le
`GlobalExceptionHandler`.

**WP0 livre** (08/09/2026) : les 17 classes d'integration heritent de
`AbstractIntegrationTest` et partagent un seul contexte Spring et une seule base H2, nettoyee
entre les classes par `DatabaseCleaner`. 19 demarrages de contexte -> 3. Phase de test 18,3 s
-> 9,3 s. `StartupSeedTest` et `DemoProfileSeedTest` restent isoles, `AmountInWordsTest` reste
un test unitaire pur.

**WP4 livre** (08/09/2026) — dernier lot. `ArchitectureTest` (ArchUnit) rend verifiables seize
regles de ce fichier : le decoupage en couches, la frontiere DTO, « l'argent est un BigDecimal
a l'echelle 3 », les finders en lecture seule, l'hygiene generale. Une vraie violation trouvee
et corrigee : `AuditLogServiceImpl.search` tournait **sans transaction**. Trois exclusions,
chacune commentee dans la classe : la marge de page de `DocumentServiceImpl` (geometrie, pas un
montant), `MessageServiceImpl.get` (lit un bundle, aucune base), et les cycles de paquets
`config` et `exception` — structurels, corrigeables en deplacant `CompanyProperties` et
`GlobalExceptionHandler`, decision non prise.

**WP3 livre** (08/09/2026) : les cinq specifications de `repository/specification` ont un test
direct (`@DataJpaTest`, paquet `persistence`), plus le graphe de chargement de la liste des
depenses et un garde-fou de serialisation des DTO. **Les jointures derapaient** : les chemins de
recherche etaient construits dans un `List.of(...)` evalue avant l'appel, donc joints meme sans
terme de recherche, et `root.join` en ajoutait une a chaque appel — six jointures pour trois
associations une fois tous les filtres poses. Corrige par un `Supplier` et la reutilisation des
jointures. Aucune ligne ne change : les associations sont toutes `@ManyToOne`, donc `totalElements`
n'etait pas fausse.

**WP2 livre** (08/09/2026) : la couche web est couverte — `GlobalExceptionHandlerTest` plus
quatre slices `@WebMvcTest` (`ExpenseController`, `AttachmentController`, `ReportController`,
`SearchController`) et un test de configuration CORS. `GlobalExceptionHandler` traite
desormais 11 familles d'exceptions au lieu de 6 : **cinq requetes malformees repondaient 500**
(violation d'integrite, valeur d'enum inconnue, identifiant non numerique, JSON illisible,
parametre ou part multipart manquant). Les messages de validation sont francais de facon
deterministe via `ValidationMessages.properties`. Mockito n'est autorise que dans ces slices.

**WP1 livre** (08/09/2026) : quatre classes dans `com.promoteur.app.postgres` tournent contre
un PostgreSQL 16 reel (Testcontainers, `AbstractPostgresTest`). Les 12 migrations et
`ddl-auto=validate` sont confirmes sur le dialecte de production, et CONC-01 tient sous
concurrence. Deux constats a trancher, documentes dans `backend/README.md` : la recherche
globale est **sensible aux accents** (« bechir » ne trouve pas « Béchir »), et toute ecriture
consomme **deux** connexions simultanement a cause du journal d'audit en `REQUIRES_NEW`, alors
que le pool Hikari de production reste au defaut de 10.

## Où sont les choses

| Besoin | Emplacement |
|---|---|
| Règles d'encaissement | `service/impl/ClientAdvanceServiceImpl`, `ClientPurchaseCalculationServiceImpl` |
| Échéanciers | `service/impl/PaymentScheduleServiceImpl`, table `payment_installments` (V9) |
| Règlements fournisseurs | `service/impl/SupplierInvoiceServiceImpl`, table `supplier_payments` (V10) |
| Statut commercial des lots | `service/impl/ApartmentServiceImpl` (`salesBoard`, `changeSalesStatus`), V11 |
| Documents imprimés | `service/impl/DocumentServiceImpl`, `PdfLetterhead`, `AmountInWordsServiceImpl` |
| En-tête des documents | `app.company.*` dans `application.properties` |
| TVA | `service/impl/VatCalculationServiceImpl`, table `vat_rate_options` |
| Rapports et périmètre | `dto/report/ReportFilter`, `service/impl/ReportServiceImpl` |
| Filtres des listes | `dto/ListFilter`, `repository/specification/*` |
| Références de documents | `service/impl/ReferenceGeneratorServiceImpl`, séquences V6 (DEP, ACC) et V12 (ACH) |
| Recherche globale | `service/impl/SearchServiceImpl`, requêtes `search(...)` des dépôts |
| Pièces jointes | `service/impl/AttachmentServiceImpl`, `LocalFileSystemStorageService` |
| Messages français | `src/main/resources/messages_fr.properties` |
| OpenAPI | `/swagger-ui.html` en dev et test uniquement |
