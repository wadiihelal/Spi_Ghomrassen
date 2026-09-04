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
- **Le reçu ne consomme pas de séquence** : il porte la référence de l'encaissement, pour qu'un
  reçu réimprimé reste le même document.

## Vérifier avant de livrer

```bash
cd backend && mvn -q verify        # 140 tests, H2 + Flyway
cd frontend && npx ng build        # TypeScript strict + strictTemplates
```

Sans PostgreSQL local, le backend se lance sur H2 avec les données de démonstration :

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=test,demo -Dspring-boot.run.useTestClasspath=true
```

Le profil `demo` charge des acquéreurs fictifs : jamais en production.

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
| Références de documents | `service/impl/ReferenceGeneratorServiceImpl`, séquences V6 |
| Pièces jointes | `service/impl/AttachmentServiceImpl`, `LocalFileSystemStorageService` |
| Messages français | `src/main/resources/messages_fr.properties` |
| OpenAPI | `/swagger-ui.html` en dev et test uniquement |
