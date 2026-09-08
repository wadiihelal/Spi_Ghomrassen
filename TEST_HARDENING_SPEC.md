# Spec — Durcissement de la suite de tests (backend)

> Document de travail destiné à Claude Code. Il décrit **cinq lots** (WP0–WP4) à livrer dans
> l'ordre. Chaque lot est autonome : `mvn -q verify` doit rester vert à la fin de chacun, sans
> attendre le suivant.

> **Révision du 08/09/2026** — relecture de la spec contre le code avant lancement du WP0.
> Corrigé : le nettoyage de base du WP0 détruisait les données de référence dont six classes
> dépendent (§0.2 bis, point bloquant) ; l'instruction de changer la portée du driver PostgreSQL
> était inutile et nuisible (WP1) ; la neutralisation du dialecte par une valeur vide était
> fragile (§1.1) ; le risque annoncé du double-join sur `totalElements` était faux et masquait un
> défaut plus large (WP3) ; `@DisplayName` ne s'applique pas aux règles ArchUnit (WP4). Ajouté :
> deux violations déjà repérées à la lecture, et les précisions surefire / `@MockBean` / CORS.

## Contexte

État actuel de `backend/src/test` : **20 classes, 146 méthodes de test, 152 exécutions**
(surefire, mesuré). Les 146 méthodes sont 144 `@Test` et 2 `@ParameterizedTest`, ces dernières
comptant pour 8 exécutions.

> ⚠️ Une version antérieure de cette spec annonçait 177 tests. Le chiffre venait d'un
> `grep -c "@Test"` qui comptait aussi les 18 `@TestPropertySource` et les 15 `@TestInstance`.
> La valeur `152` de `CLAUDE.md` était donc correcte. Compter avec `@Test\b`, ou lire le total
> de surefire, qui est la seule source de vérité.

| Type | Nombre | Détail |
|---|---|---|
| Tests unitaires purs | **1 classe / 11 tests** | `AmountInWordsTest` — `new AmountInWordsServiceImpl()`, sans contexte Spring |
| Tests d'intégration | **19 classes / 141 tests** | `@SpringBootTest` + `@ActiveProfiles("test")` + H2 |
| Tests de la couche web | **0** | aucun `MockMvc`, aucun `@WebMvcTest` |
| Tests sur PostgreSQL | **0** | la production tourne sur PostgreSQL 16 (`docker-compose.yml`) |
| Tests d'architecture | **0** | les règles de `CLAUDE.md` ne sont vérifiées par rien |
| Tests frontend | **0** | pas de script `test`, pas de karma/jest, aucun `.spec.ts` |

Aucun mock n'est utilisé nulle part dans la suite (`grep -rl "Mockito\|@Mock\|MockMvc" src/test` → vide).

### Les quatre trous que cette spec comble

1. **WP1** — 100 % des tests tournent sur H2, la production sur PostgreSQL 16.
2. **WP2** — 19 contrôleurs et le `GlobalExceptionHandler` ne sont exercés par aucun test.
3. **WP3** — les 5 spécifications de `repository/specification/` n'ont aucun test direct.
4. **WP4** — les règles d'architecture de `CLAUDE.md` sont tenues par la discipline seule.

---

## Conventions à respecter dans tout le code produit

Reprises de `CLAUDE.md` et du style existant de `src/test` :

- **JUnit 5 + AssertJ.** Pas de JUnit 4, pas de Hamcrest, pas de `org.junit.Assert`.
- **`@DisplayName` en anglais**, en minuscules, formulé comme une règle métier :
  `@DisplayName("a duplicate reference answers 409 rather than 500")`. Les 146 méthodes de test
  existantes portent toutes un `@DisplayName` — s'y conformer sans exception.
- **Nom de méthode = `@DisplayName` en camelCase.** `void aDuplicateReferenceAnswers409()`.
- **Javadoc de classe** expliquant *quelle règle* la classe protège, avec la référence du plan
  quand elle existe (`CALC-01`, `PERF-02`, `CONC-01`…), comme dans `VatCalculationTest`.
- **Chaînes utilisateur en français** uniquement lorsqu'on assère un message réellement produit
  par l'application (`messages_fr.properties`). Le reste du code de test est en anglais.
- **Argent** : `BigDecimal` seulement, jamais `double`. Comparaisons avec
  `assertThat(x).isEqualByComparingTo("1234.500")` — **jamais `isEqualTo`** sur un `BigDecimal`
  (`1.500` ≠ `1.5` pour `equals`).
- **Un `mvn -q verify` doit rester déterministe** : pas de `Thread.sleep` pour synchroniser,
  utiliser `CountDownLatch` comme le fait déjà `AdvanceCeilingTest`.

### Interdits

- ❌ **Ne jamais modifier une migration déjà livrée** (`V1`…`V12`). Toute correction de schéma
  passe par une nouvelle migration `V13__*.sql`.
- ❌ **Ne pas introduire Mockito dans les tests de service.** Le choix d'exercer les services
  contre une vraie base est délibéré ; les mocks ne sont autorisés qu'en `@WebMvcTest` (WP2),
  où la couche service est hors périmètre par construction.
- ❌ **Ne pas modifier le code de production pour faire passer un test**, sauf aux endroits
  explicitement listés dans la section « Corrections attendues » de chaque lot.
- ❌ **Ne pas toucher à `src/main/resources/application-test.properties`** sans le signaler :
  il est lu aussi par `mvn spring-boot:run -Dspring-boot.run.profiles=test,demo`.

---

# WP0 — Infrastructure de test partagée

À livrer avant WP1. Objectif : arrêter de payer 19 démarrages de contexte Spring.

## Problème mesuré

Chacune des 19 classes `@SpringBootTest` porte sa propre URL H2 :

```java
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_vat;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
```

L'URL fait partie de la clé du cache de contexte Spring. Résultat : **19 contextes distincts,
19 exécutions complètes de Flyway** sur les 12 migrations. C'est ce qui rend la suite trop lente
pour qu'on ose y ajouter les lots suivants.

## Travail attendu

### 0.1 — Classe de base

Créer `src/test/java/com/promoteur/app/AbstractIntegrationTest.java` :

```java
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {
    // aucune @TestPropertySource : l'URL vient de application-test.properties
}
```

### 0.2 — Isolation entre classes

Le partage d'une seule base H2 impose de nettoyer entre les classes. **Ne pas** utiliser
`@Transactional` sur les tests : `AdvanceCeilingTest` a besoin de commits réels pour observer
la concurrence, et `QueryCountTest` compte des requêtes que le rollback fausserait.

Créer à la place `src/test/java/com/promoteur/app/DatabaseCleaner.java` : un composant de test
qui tronque les tables applicatives et réinitialise les trois séquences
(`expense_ref_seq`, `advance_ref_seq`, `purchase_ref_seq`), appelé en `@AfterAll` par la classe
de base. Lire la liste des tables depuis les métadonnées JDBC plutôt que de la coder en dur —
une `V13` future ne doit pas casser le nettoyage.

⚠️ Attention : `ReferenceDataInitializer` et `DemoDataInitializer` (paquet `config/`) peuplent des
données au démarrage. `StartupSeedTest` et `DemoProfileSeedTest` en dépendent. Ces deux classes
**gardent leur `@TestPropertySource` isolée** et restent hors de la base commune.

### 0.2 bis — Les données de référence survivent au nettoyage (point bloquant)

**À trancher avant d'écrire une ligne de `DatabaseCleaner`.** `ReferenceDataInitializer` est un
`CommandLineRunner` : il ne tourne **qu'une fois**, au démarrage du contexte. Dès que les classes
partagent une base, la première qui se nettoie détruit les données de référence de toutes les
suivantes — et six classes en dépendent :

```
VatCalculationTest:197, ReportScopeTest:188, AttachmentTest:159,
ServerSideFilteringTest:223, AuditTrailTest:112, ReferenceGenerationTest:159
  → request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
```

`.get(0)` sur une liste vide lève `IndexOutOfBoundsException`. Et `VatCalculationTest:72` assère
`containsExactly` les quatre taux de TVA seedés : il casse aussi bien si on les supprime que si
une classe précédente en ajoute un.

Deux options, l'une ou l'autre, pas les deux :

1. **Exclure les trois tables de référence** de la troncature : `expense_categories`,
   `supplier_type_options`, `vat_rate_options`. Simple, mais impose une liste d'exclusion
   nommée dans `DatabaseCleaner` — ce qui contredit en partie « lire la liste depuis les
   métadonnées JDBC ». Assumer l'exception et la commenter.
2. **Rejouer le seed après nettoyage** : injecter `ReferenceDataInitializer` dans
   `DatabaseCleaner` et appeler son `run(...)` après les `TRUNCATE`. Plus fidèle à l'état de
   démarrage, mais fait dépendre l'infrastructure de test d'un composant de `config/`.

L'option 1 est recommandée. Dans les deux cas, ajouter un test qui **garde** le choix :
`@DisplayName("reference data survives the cleaner so a later class still finds a category")`.

⚠️ `VatCalculationTest:72` reste fragile même après correction, parce qu'il assère la liste
exacte des taux. Le passer en `contains(...)` plutôt que `containsExactly(...)`, ou le laisser
tel quel en documentant qu'aucune classe de la base commune ne doit créer de taux de TVA.

### 0.3 — Migration des classes existantes

Faire hériter les 17 autres classes `@SpringBootTest` de `AbstractIntegrationTest` et supprimer
leurs annotations `@SpringBootTest` / `@ActiveProfiles` / `@TestPropertySource`.

`AmountInWordsTest` **reste inchangée** : c'est le seul test unitaire pur, il ne doit pas gagner
de contexte Spring.

## Critères d'acceptation WP0

- [ ] `mvn -q verify` reste vert, les **152 exécutions** d'origine toujours au vert (plus celles
      des tests ajoutés par le lot).
- [ ] Le log de build ne montre plus que **3 démarrages de contexte** au maximum
      (commun, `StartupSeedTest`, `DemoProfileSeedTest`).
- [ ] Le temps de la **phase de test** est réduit d'au moins 50 % — noter l'avant/après dans le
      message de commit. Mesurer la phase de test (somme des `Time elapsed` de surefire) et non
      le `Total time` de Maven : ce dernier inclut ~3 s de compilation et d'empaquetage
      incompressibles, et `DemoProfileSeedTest` à lui seul pèse ~6 s qu'aucun partage de contexte
      ne réduit, puisque cette classe doit rester isolée.
- [ ] Mettre à jour la ligne `# 152 tests, H2 + Flyway` de `CLAUDE.md` avec le nouveau total.

---

# WP1 — Tests sur PostgreSQL (Testcontainers)

Le lot le plus important : il change ce qu'on **sait** de la production.

## Divergences H2 / PostgreSQL présentes dans ce code

| # | Endroit | Risque |
|---|---|---|
| 1 | `ClientRepository:72`, `SupplierRepository:22`, `SpecificationSupport:48` — `lower(x) like :pattern` | La collation H2 et la collation `fr_FR.UTF-8` ne traitent pas les accents pareil. `SearchServiceTest` est vert sur H2 et ne prouve rien pour « Béchir » vs « bechir ». |
| 2 | `AdvanceCeilingTest` — `LOCK_TIMEOUT=15000` sur l'URL H2, `@Lock(PESSIMISTIC_WRITE)` dans `ApartmentRepository:54` | CONC-01, la garantie phare, repose sur le verrouillage H2. PostgreSQL en READ COMMITTED avec `SELECT … FOR UPDATE` se comporte autrement. |
| 3 | Séquences `expense_ref_seq` / `advance_ref_seq` (V6), `purchase_ref_seq` (V12) | L'allocation *pooled* d'Hibernate et le comportement des trous après rollback diffèrent entre les deux moteurs. |
| 4 | Colonnes monétaires `NUMERIC(19,3)` | Arrondi et débordement diffèrent. Une valeur à 20 chiffres lève une erreur sur PostgreSQL, pas forcément sur H2. |
| 5 | Les 12 migrations | Écrites en SQL portable (`BIGINT GENERATED BY DEFAULT AS IDENTITY`) — mais **jamais exécutées sur PostgreSQL par un test**. `ddl-auto=validate` n'a jamais été confronté au dialecte réel. |

## Dépendances à ajouter (`backend/pom.xml`)

Spring Boot 3.3.5 gère déjà les versions Testcontainers via son BOM — ne pas fixer de version.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Le driver `org.postgresql:postgresql` est déjà présent en `runtime` : **ne pas y toucher.** La
portée `runtime` de Maven est déjà sur le classpath de test ; seul le classpath de compilation de
`src/main` l'exclut, ce qui est exactement la protection qu'on veut garder. Retirer
`<scope>runtime</scope>` exposerait le driver à la compilation de la production sans rien
apporter aux tests.

## Travail attendu

### 1.1 — Socle conteneur

`src/test/java/com/promoteur/app/AbstractPostgresTest.java` :

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractPostgresTest {

    /** Même image majeure que docker-compose.yml, pour tester ce qui tourne réellement. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("spi_ghomrassen")
                    .withReuse(true);
}
```

Points d'attention :

- ⚠️ **`@Testcontainers` + `@Container` ne marche pas ici.** `SpringExtension` et
  `TestcontainersExtension` accrochent tous deux `beforeAll`, et celui de Spring passe en
  premier : la datasource réclame le port avant que l'extension ait démarré le conteneur, et le
  contexte échoue sur `Mapped port can only be obtained after the container is started`.
  Démarrer le conteneur dans un **initialiseur statique** et retirer `@Testcontainers` : l'ordre
  n'est plus une question. `@ServiceConnection` sur le champ statique suffit au câblage.
- `@ServiceConnection` **écrase** `spring.datasource.*` d'`application-test.properties`. Vérifier
  qu'aucune `@TestPropertySource` résiduelle ne réimpose une URL H2 : elle gagnerait.
- Il faut neutraliser `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect` hérité du
  profil `test`. **Ne pas le faire par une valeur vide** (`spring.jpa.database-platform=`) :
  Hibernate reçoit alors `hibernate.dialect=""` et le comportement dépend de la version. Créer
  `src/test/resources/application-postgres.properties` qui pose le dialecte PostgreSQL
  explicitement, et activer les deux profils : `@ActiveProfiles({"test", "postgres"})`. Un
  `@DynamicPropertySource` alimenté par le conteneur fait aussi l'affaire ; dans les deux cas le
  résultat est déterministe et lisible.
- `withReuse(true)` n'a d'effet que si l'utilisateur a `testcontainers.reuse.enable=true` dans
  `~/.testcontainers.properties`. Le documenter dans `backend/README.md`, ne pas en dépendre.

### 1.2 — `PostgresMigrationTest`

Le test le plus rentable du lot. Il ne teste aucune règle métier : il vérifie que le schéma
existe et que les entités lui correspondent.

- `@DisplayName("the twelve migrations apply in order on a clean PostgreSQL database")`
  — le contexte démarre, donc Flyway a tourné et `ddl-auto=validate` a accepté le mapping.
  Asserter que `flyway_schema_history` contient 12 lignes toutes en `success = true`.
- `@DisplayName("every money column is numeric(19,3) and every rate column numeric(5,4)")` —
  parcourir `information_schema.columns`. ⚠️ **Toute colonne `numeric` n'est pas de l'argent** :
  `vat_rate`, `default_vat_rate` et `rate` sont des *taux*, en `numeric(5, 4)`. Asserter 19,3
  partout ferait échouer le test sur ces trois-là. Protège la règle « argent » au niveau du
  schéma, en distinguant les deux familles.
- `@DisplayName("the version column exists on every table an entity maps")` — ⚠️ **15 tables,
  pas les 12 de V5** : `V5__optimistic_locking.sql` couvre les tables qui existaient alors, et
  V8, V9, V10 déclarent la colonne à la création de `file_attachments`,
  `payment_installments` et `supplier_payments`. Lire la liste depuis le métamodèle JPA plutôt
  que depuis V5, pour qu'une seizième entité ne passe pas sans son verrou. Filtrer sur ces
  tables : `flyway_schema_history` a elle aussi une colonne `version`, en `varchar`.
- `@DisplayName("the three reference sequences exist and start at one")` — les trois séquences
  de V6 et V12 dans `information_schema.sequences`.

### 1.3 — `PostgresSearchTest`

Cible la divergence #1. Insérer via les services (pas de SQL brut) des clients aux noms
réellement accentués — « Béchir Ben Salah », « Amira Trabelsi », « Néjib Ferchichi » — puis :

- `@DisplayName("a lower-cased search finds a name written with capitals")`
- `@DisplayName("an unaccented search term does not silently miss an accented name")`
  — **ce test doit documenter le comportement réel, pas le comportement souhaité.** Si
  `lower(...) like '%bechir%'` ne trouve pas « Béchir » sur PostgreSQL, l'assertion est
  `isEmpty()` et le javadoc explique que la recherche est sensible aux accents ; ouvrir alors
  une note dans `backend/README.md`. Ne **pas** corriger le code de production dans ce lot :
  la correction (`unaccent`, ou une colonne normalisée) demande une migration et une décision.
- `@DisplayName("the search stays bounded to the project in scope")` — reprendre le cas déjà
  couvert par `SearchServiceTest` et vérifier qu'il tient aussi sur PostgreSQL.

### 1.4 — `PostgresAdvanceCeilingTest`

Cible la divergence #2 — la plus importante fonctionnellement.

Reprendre **exactement** le scénario concurrent d'`AdvanceCeilingTest` (deux `Callable`, un
`CountDownLatch`, un pool de 2) et l'exécuter contre PostgreSQL :

- `@DisplayName("exactly one of two simultaneous advances is accepted past the ceiling")`
- `@DisplayName("the refusal is the ceiling check, not a lock timeout")` — distinguer
  explicitement `IllegalArgumentException` (plafond) de
  `CannotAcquireLockException` / `OptimisticLockingFailureException`.
- `@DisplayName("no advance is lost when both transactions commit")` — après la course,
  la somme des acomptes en base est cohérente avec le nombre d'acceptations.

Si ce test échoue là où la version H2 passait, **c'est le résultat attendu du lot** : le
signaler dans le rapport final, ne pas le neutraliser avec un `@Disabled`.

### 1.5 — `PostgresReferenceSequenceTest`

Cible les divergences #3 et #4.

- `@DisplayName("references are allocated without gaps under sequential creation")`
- `@DisplayName("a rolled back creation may leave a gap but never reuses a number")` — le vrai
  contrat de DATA-03 : l'unicité, pas la contiguïté.
- `@DisplayName("a hundred concurrent expenses receive a hundred distinct references")` —
  pool de 10 threads, asserter `distinct().count() == 100`. ⚠️ Ce test échoue d'abord sur
  `HikariPool-1 - Connection is not available` et non sur la séquence : **toute écriture tient
  deux connexions à la fois**, la transaction métier plus celle que `AuditLogServiceImpl.create`
  ouvre en `REQUIRES_NEW`. Avec le défaut Hikari de 10, dix écritures concurrentes se bloquent
  mutuellement. Élargir le pool du profil de test pour que le test mesure la séquence, et
  **signaler le constat** : la production tourne sur ce défaut de 10.
- `@DisplayName("an amount beyond numeric(19,3) is refused by the database, not silently truncated")`

### 1.6 — Séparation CI / local

Marquer les classes PostgreSQL avec `@Tag("postgres")` et configurer `maven-surefire-plugin` pour
les **exclure par défaut** (`<excludedGroups>postgres</excludedGroups>`), avec un profil Maven
`-Ppostgres` qui les inclut. Raison : `mvn verify` doit rester utilisable sans démon Docker.

Le pom **n'a aujourd'hui aucune configuration `maven-surefire-plugin` ni aucun bloc
`<profiles>`** : les deux sont à créer de zéro, pas à modifier. La version du plugin vient du
parent Spring Boot — ne pas la fixer.

Documenter les deux commandes dans `CLAUDE.md`, section « Vérifier avant de livrer ».

## Critères d'acceptation WP1

- [ ] `mvn -q verify` (sans Docker) → vert, tests PostgreSQL ignorés.
- [ ] `mvn -q verify -Ppostgres` → démarre un conteneur `postgres:16-alpine`, applique les 12
      migrations, exécute les 4 nouvelles classes.
- [ ] Toute divergence H2/PostgreSQL découverte est **documentée**, pas masquée : un test rouge
      légitime est signalé dans le rapport final avec la correction proposée.
- [ ] `backend/README.md` gagne une section « Tests sur PostgreSQL » (prérequis Docker, `reuse`).

---

# WP2 — Couche web (`@WebMvcTest` + MockMvc)

19 contrôleurs, 0 test. C'est le lot qui va trouver des bugs immédiatement.

## Anomalies déjà identifiées — à confirmer par un test AVANT correction

### A. `DataIntegrityViolationException` → 500 au lieu de 409

`exception/GlobalExceptionHandler.java` traite `ResourceNotFoundException`,
`MethodArgumentNotValidException`, `IllegalArgumentException`, `OptimisticLockingFailureException`,
`MaxUploadSizeExceededException`, puis `Exception`. **Il ne traite pas
`DataIntegrityViolationException`.** Une référence en doublon ou une violation de clé étrangère
tombe dans `handleGeneric` et répond :

```json
{ "status": 500, "error": "Unexpected server error" }
```

Le client Angular ne peut rien en faire, et le message n'est même pas en français.

### B. Valeur d'énumération invalide → 500 au lieu de 400

`AttachmentController.upload` déclare `@RequestParam AttachmentOwnerType ownerType`. Un appel
avec `ownerType=BOGUS` lève `MethodArgumentTypeMismatchException`, non traitée → 500.
Même problème sur tout `@PathVariable Long id` recevant une valeur non numérique.

### C. Messages de validation dans une langue non déterministe

`handleValidation` renvoie `error.getDefaultMessage()`. Il n'existe **ni
`ValidationMessages_fr.properties`, ni configuration de locale** dans
`src/main/resources`. Les messages viennent donc du bundle par défaut d'Hibernate Validator,
résolu selon la locale de la JVM : « must not be blank » sur un serveur en `en_US`, « ne doit pas
être vide » ailleurs. L'application affiche du français partout sauf là.

### E. Requêtes malformées répondant 500 — deux cas de plus, trouvés à l'exécution

Au-delà des anomalies A à C, **deux autres familles** tombaient dans `handleGeneric` :

- `MissingServletRequestParameterException` — `/api/search` sans `q`, ou
  `/api/reports/export/excel` sans `year` (les deux sont obligatoires) ;
- `MissingServletRequestPartException` — un téléversement sans sa part `file`.

Même forme de défaut que l'anomalie B, donc à traiter dans la même famille : une requête
malformée doit répondre 400 en nommant ce qui manque, jamais 500.

### D. `@ResponseStatus(CREATED)` + `ResponseEntity.created(...)`

`ExpenseController.create` porte les deux. **Anomalie cosmétique, à couvrir sans corriger** :
quand une méthode renvoie un `ResponseEntity`, c'est son statut qui gagne et `@ResponseStatus`
est ignoré — et ici les deux valent 201. Un test qui atteste le 201 et l'en-tête `Location`
(API-02) suffit ; retirer l'annotation redondante serait un changement hors périmètre.

## Travail attendu

### 2.1 — `GlobalExceptionHandlerTest`

Le test le plus important du lot. Utiliser un `@RestController` de test dédié, déclaré en classe
interne, qui lève chaque exception à la demande — plus lisible que de piloter un vrai contrôleur.

⚠️ Deux pièges de mécanique, tous deux rencontrés :

- **Le contrôleur sonde doit être `@Import`é**, en plus d'être nommé dans
  `@WebMvcTest(controllers = ...)`. Une classe imbriquée dans un test n'est pas candidate au
  scan : sans l'import, chaque requête tombe sur le gestionnaire de ressources statiques
  (`NoResourceFoundException`) et **tous** les tests voient un 500, ce qui masque exactement ce
  qu'on cherche à mesurer. Importer aussi `GlobalExceptionHandler`, `MessageServiceImpl` et
  `MessageSourceConfig` : une slice ne charge pas les `@Configuration` du projet.
- **`getContentAsString()` sans charset** retombe en ISO-8859-1, alors que les matchers
  `jsonPath` décodent en UTF-8. Lire le corps à la main sans `StandardCharsets.UTF_8` fait
  passer un message français correct pour du mojibake — un faux positif côté test uniquement.

Un test par branche existante, **plus** un par branche manquante :

| Exception levée | Statut attendu | Corps attendu |
|---|---|---|
| `ResourceNotFoundException` | 404 | `error` = message du service |
| `MethodArgumentNotValidException` | 400 | `details` : un couple champ → message par champ invalide |
| `IllegalArgumentException` | 400 | `error` = message |
| `OptimisticLockingFailureException` | 409 | `error` = `error.optimisticLock` **en français accentué** |
| `MaxUploadSizeExceededException` | 413 | `error` = `error.uploadTooLarge` |
| `DataIntegrityViolationException` | **409** | ⚠️ échoue aujourd'hui (500) |
| `MethodArgumentTypeMismatchException` | **400** | ⚠️ échoue aujourd'hui (500) |
| `Exception` quelconque | 500 | `error` = `Unexpected server error`, **et rien de la stacktrace** |

Ajouter aussi :

- `@DisplayName("no error response ever leaks a stack trace or a SQL statement")` — asserter que
  le corps ne contient ni `at com.promoteur`, ni `SQL`, ni `constraint`.
- `@DisplayName("every error response carries timestamp, status and error")` — le contrat de
  forme sur lequel s'appuie le client Angular.

### 2.2 — Corrections attendues dans le code de production

Une fois les tests rouges écrits, corriger `GlobalExceptionHandler` :

1. Ajouter `@ExceptionHandler(DataIntegrityViolationException.class)` → **409 CONFLICT**, message
   depuis une nouvelle clé `error.dataIntegrity` de `messages_fr.properties`. Logger l'exception
   en `warn` avec la contrainte, ne pas l'exposer au client.
2. Ajouter `@ExceptionHandler({MethodArgumentTypeMismatchException.class,
   HttpMessageNotReadableException.class})` → **400 BAD REQUEST**, message générique en français
   nommant le paramètre fautif (`ex.getName()`), jamais la valeur reçue.
3. Rendre les messages de validation déterministes et français. **Ne pas** viser
   `ValidationMessages_fr.properties` : le suffixe de locale ne sert à rien ici, puisque le
   problème est justement que la locale résolue est imprévisible. Renseigner
   `ValidationMessages.properties`, le bundle **sans suffixe** : l'application étant francophone
   uniquement, le français y est le défaut et aucune locale de serveur ne peut plus le changer.
   Un `LocalValidatorFactoryBean` branché sur le `MessageSource` existant ne suffit pas non
   plus : avec `messages_fr.properties` seul et `fallbackToSystemLocale=false`, une locale `en`
   ne résout rien et Hibernate Validator repart sur son bundle anglais.
   ⚠️ Hibernate Validator charge ce bundle en **ISO-8859-1** : accents en échappement unicode,
   comme dans `application.properties`. Asserter le texte français exact, ce qui couvre
   l'encodage.
4. Traiter aussi les deux familles de l'anomalie E (paramètre obligatoire absent, part multipart
   absente), même correctif, même famille.

Toute nouvelle clé va dans `messages_fr.properties` en français accentué — jamais concaténée en
Java, conformément à `CLAUDE.md`.

### 2.3 — Slices par contrôleur

Ne pas écrire 19 classes identiques. Couvrir **quatre contrôleurs représentatifs**, choisis pour
ce qu'ils exercent de différent :

**`ExpenseControllerTest`** — le patron CRUD + filtres (`@WebMvcTest(ExpenseController.class)`,
`@MockBean ExpenseService`) :

> `@MockBean` est correct sur Spring Boot 3.3.5. Il est déprécié à partir de 3.4 au profit de
> `@MockitoBean` : si une montée de version est envisagée, le noter dans le rapport plutôt que
> d'anticiper ici.


- création valide → 201 + en-tête `Location` se terminant par `/api/expenses/{id}`
- `amountHt` absent → 400, `details` contient la clé `amountHt`
- `amountHt` négatif → 400 (`@Positive`)
- `description` vide → 400 (`@NotBlank`)
- les 10 paramètres de `ListFilter` sont transmis au service — capturer l'argument et asserter
  le record complet
- `dateFrom` mal formée (`2026-13-45`) → 400, pas 500
- `paymentStatus=BOGUS` → 400 : le constructeur compact de `ListFilter` lève
  `IllegalArgumentException`, vérifier que le handler la traduit bien
- `Pageable` par défaut, et `?page=2&size=50&sort=expenseDate,desc` transmis intact
- les deux routes `@Deprecated` (`/by-category/{id}`, `/by-project/{id}`) répondent encore

**`AttachmentControllerTest`** — le multipart et le streaming :

- upload PDF valide → 201 + `AttachmentResponse`
- `ownerType=BOGUS` → 400 (cf. anomalie B)
- absence de la part `file` → 400
- téléchargement → `Content-Type` issu des métadonnées, `Content-Length` correct, et
  `Content-Disposition: inline` avec un **nom de fichier accentué correctement encodé en UTF-8**
  (`Reçu février 2026.pdf` → vérifier la forme `filename*=UTF-8''…`)
- suppression → 204 sans corps

⚠️ Signaler dans le rapport, sans le corriger ici : `download` renvoie le `Content-Type` **déclaré
par le client à l'upload** avec `Content-Disposition: inline`, et aucun en-tête
`X-Content-Type-Options: nosniff` n'est posé. C'est une décision de sécurité à prendre, pas un
bug de test.

**`ReportControllerTest`** — le périmètre projet (RPT-01/RPT-02) : absence de `projectId`,
`projectId=ALL`, projet explicite ; et le type MIME des exports.

**`SearchControllerTest`** — le contrat de la recherche globale : terme trop court, limite du
nombre de résultats, périmètre projet.

### 2.4 — Test de configuration CORS

`config/CorsConfig` lit `app.cors.allowed-origins` et pose `setAllowCredentials(true)`.
Un test `@SpringBootTest` + `MockMvc` vérifiant qu'une origine hors liste est refusée, et que
`allowedOrigins` n'est jamais `*` en même temps que `allowCredentials` (combinaison rejetée par
les navigateurs, et par Spring depuis la 5.3).

⚠️ `CorsFilter` est un filtre servlet : il ne s'exécute que si le `MockMvc` enregistre les
filtres du contexte. Utiliser `@SpringBootTest` **+ `@AutoConfigureMockMvc`**, et non un
`MockMvc` construit à la main par `MockMvcBuilders.standaloneSetup(...)`, qui ne verrait jamais
le filtre et rendrait le test vert pour de mauvaises raisons.

## Critères d'acceptation WP2

- [ ] `GlobalExceptionHandler` traite au moins 8 familles d'exceptions — 11 après le lot, les
      anomalies A, B et E en ajoutant cinq — chacune couverte par un test.
- [ ] Les anomalies A, B et C sont corrigées, chacune avec le test rouge écrit **avant** la
      correction (le commit du test précède celui du correctif).
- [ ] Aucune réponse d'erreur ne contient de stacktrace ni de fragment SQL.
- [ ] Les 4 classes de slice passent, `@MockBean` uniquement sur les services.
- [ ] Les nouvelles clés de `messages_fr.properties` sont en français accentué et couvertes par
      `MessageCatalogTest` (qui vérifie déjà les apostrophes doublées).

---

# WP3 — Couche persistance (`@DataJpaTest`)

Les 5 spécifications de `repository/specification/` ne sont testées qu'indirectement, via
`ServerSideFilteringTest` et `QueryCountTest` au niveau service.

## Anomalie à vérifier en priorité

`SpecificationSupport` expose deux méthodes qui joignent la **même** association :

```java
static void whenId(...)   { predicates.add(builder.equal(root.join(association, JoinType.LEFT).get("id"), value)); }
static Path<String> joined(final Root<?> root, ...) { return root.join(association, JoinType.LEFT).get(attribute); }
```

Dans `ExpenseSpecifications.matching`, `whenId(… "project" …)` et `joined(root, "project",
"name")` appellent chacun `root.join("project", LEFT)` : chaque appel crée un nouveau join dans
la requête Criteria.

**Le défaut est plus large qu'un cumul de filtres.** Les chemins de recherche sont passés à
`whenSearch` dans un `List.of(...)` :

```java
SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), List.of(
        …, SpecificationSupport.joined(root, "project", "name"),
           SpecificationSupport.joined(root, "category", "name"),
           SpecificationSupport.joined(root, "supplier", "name")));
```

Java évalue les arguments avant l'appel. Les trois `joined(...)` créent donc leurs joins **même
quand `normalizedSearch()` vaut `null`** et que `whenSearch` sort immédiatement : trois
`LEFT JOIN` surnuméraires sur **chaque** `GET /api/expenses`, avec ou sans recherche.

**Ce qu'il ne faut PAS asserter** : `projects`, `category` et `supplier` sont toutes des
`@ManyToOne`. Dupliquer un `LEFT JOIN` vers un côté « un » ne peut pas multiplier les lignes,
donc `totalElements` **n'est pas faussé** et ne peut pas l'être. Un test qui cherche un
`totalElements` faux passera au vert et fera conclure à tort que tout va bien.

Ce qu'il faut asserter : le **nombre de joins dans le SQL généré**, sur deux cas — filtre projet
seul, et aucun filtre du tout. Le second est le plus parlant : une requête sans aucun critère
porte quand même trois joins.

Écrire le test qui l'expose avant toute correction.

## Travail attendu

### 3.1 — `ExpenseSpecificationTest` (`@DataJpaTest`)

`@DataJpaTest` remplace la datasource par défaut : ajouter
`@AutoConfigureTestDatabase(replace = NONE)` pour garder Flyway et le schéma réel.

- `@DisplayName("an empty filter restricts nothing")` — `ListFilter.none()` renvoie toutes les
  lignes ; c'est le cas qui casse silencieusement si un `if (value != null)` est mal écrit.
- `@DisplayName("a blank search term restricts nothing")` — `normalizedSearch()` renvoie `null`
  pour `""` et `"   "`.
- `@DisplayName("a blank payment method restricts nothing")` — `whenText` sur `""`.
- `@DisplayName("combining a project filter and a search term joins the project once")` —
  le test de l'anomalie ci-dessus. Asserter sur le SQL généré (Hibernate statistics ou
  `spring.jpa.show-sql` capturé) que `projects` n'apparaît qu'une fois.
- `@DisplayName("an unfiltered query joins nothing")` — le cas le plus révélateur de l'anomalie :
  `ListFilter.none()` produit aujourd'hui trois `LEFT JOIN` parce que les arguments de
  `whenSearch` sont évalués avant le contrôle de nullité.
- `@DisplayName("totalElements matches the number of rows the filter really selects")` —
  filtre projet + recherche, comparer `page.getTotalElements()` au comptage direct. Ce test doit
  **passer dès maintenant** : il documente que le double join est inoffensif pour le comptage
  (associations `@ManyToOne`), il n'expose pas l'anomalie.
- `@DisplayName("dateFrom and dateTo are inclusive on both ends")` — une dépense exactement à
  `dateFrom` et une exactement à `dateTo` sont retenues.
- `@DisplayName("a search term matches the supplier name through the join")`
- `@DisplayName("a search term containing a percent sign is not treated as a wildcard")` —
  `normalizedSearch()` entoure de `%` sans échapper `%` ni `_` saisis par l'utilisateur.
  Documenter le comportement réel ; si `%` agit bien comme joker, c'est un défaut à signaler.

### 3.2 — Les quatre autres spécifications

`repository/specification/` contient six fichiers, dont **cinq spécifications** et un utilitaire
package-private (`SpecificationSupport`, exercé à travers elles, sans test propre).
`ExpenseSpecifications` étant traitée en §3.1, il reste : `ApartmentSpecifications`,
`ClientAdvanceSpecifications`, `ClientPurchaseSpecifications`, `SupplierInvoiceSpecifications`.

Même canevas, allégé. Au minimum, pour chacune : filtre vide, chaque filtre isolé, deux filtres
combinés, et la cohérence de `totalElements`.

`ClientPurchaseSpecifications` mérite un cas de plus : `paymentStatus` est un **statut dérivé**,
résolu en SQL et non stocké (`CLAUDE.md`). Vérifier `UNPAID`, `PARTIALLY_PAID` et `PAID` contre
des contrats dont les encaissements sont connus.

### 3.3 — `@EntityGraph` et comptage de requêtes

`QueryCountTest` couvre déjà PERF-01/PERF-03 au niveau service. Compléter au niveau dépôt :

- `@DisplayName("listing expenses fetches project, category and supplier in one query")`
- `@DisplayName("no finder triggers a lazy load after the transaction closes")` — sérialiser
  chaque `*Response` avec Jackson hors transaction ; toute `LazyInitializationException` est un
  DTO qui expose une association non chargée.

## Critères d'acceptation WP3

- [ ] Les **5 spécifications** de `repository/specification/` ont un test direct
      (`SpecificationSupport` est un utilitaire package-private : il est couvert à travers
      elles, pas par une classe de test dédiée).
⚠️ Trois attentes de cette section se sont révélées fausses à l'exécution, corrigées ici :

- **La mesure ne peut pas porter sur le SQL émis.** La journalisation SQL est désactivée dans le
  profil `test`, et Hibernate 6 élague une jointure qu'il n'utilise pas : une assertion sur le
  texte du statement passe au vert alors que la requête construite contient bien la jointure en
  trop. Mesurer sur `root.getJoins()` après avoir appliqué la spécification.
- **Une page qui tient entièrement dans la première requête ne déclenche pas de comptage.**
  Spring Data élide la requête `count(*)` dans ce cas : un `@EntityGraph` correct donne **une**
  seule requête, pas deux. Forcer une page plus petite pour observer les deux.
- **`apartment_id` est `NOT NULL` sur `client_advances` et `client_purchases`**, et **unique**
  sur les contrats : un jeu d'essai doit créer un lot par contrat.

- [ ] Le double-join est corrigé (mise en cache du join dans un `Map<String, Join<?,?>>` au sein
      de la spécification, **et** évaluation paresseuse des chemins de recherche pour ne plus
      joindre en l'absence de terme), avec les tests qui l'attestent. Son innocuité pour
      `totalElements` est prouvée séparément, elle ne dispense pas de la correction.
- [ ] `totalElements` est asserté sur au moins un filtre combiné par spécification.

---

# WP4 — Règles d'architecture (ArchUnit)

`CLAUDE.md` énonce des règles non négociables qu'aucun outil ne vérifie. ~80 lignes suffisent à
les rendre permanentes.

## Dépendance

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

## Travail attendu

Une seule classe, `src/test/java/com/promoteur/app/ArchitectureTest.java`, annotée
`@AnalyzeClasses(packages = "com.promoteur.app", importOptions = DoNotIncludeTests.class)`.

⚠️ **Exception à la convention `@DisplayName` du haut de ce document.** Une règle ArchUnit est un
champ `static final ArchRule` annoté `@ArchTest`, pas une méthode : `@DisplayName` ne s'y
applique pas. Le libellé passe par `.as(...)` sur la règle elle-même, et c'est lui qui apparaît
dans le rapport :

```java
@ArchTest
static final ArchRule noControllerDependsOnARepository = noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAPackage("..repository..")
        .as("no controller depends on a repository");
```

Les libellés listés ci-dessous en `@DisplayName(...)` sont donc à lire comme des arguments de
`.as(...)`, et le nom du champ reprend la même formule en camelCase.

### 4.1 — Découpage en couches

```java
@ArchTest
static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Controller").definedBy("..controller..")
        .layer("Service").definedBy("..service..")
        .layer("Repository").definedBy("..repository..")
        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");
```

Plus, explicitement :

- `@DisplayName("no controller depends on a repository")`
- `@DisplayName("no controller depends on a service implementation")` — les contrôleurs
  dépendent de l'interface dans `service/`, jamais de `service/impl/`.
- `@DisplayName("every class in service.impl implements an interface from service")`

`consideringOnlyDependenciesInLayers()` est indispensable ici : `ReferenceDataInitializer` et
`DemoDataInitializer` vivent dans `config/`, qui n'est pas déclaré comme couche, et injectent
directement des dépôts. Sans cette option la règle « Repository accessible seulement depuis
Service » échouerait sur eux, alors que le seed de démarrage est un cas légitime.

### 4.2 — Frontière DTO

- `@DisplayName("no controller method returns a JPA entity")` — aucune méthode publique de
  `..controller..` n'a pour type de retour une classe de `..entity..`, ni directement, ni via
  `ResponseEntity<>`, `Page<>` ou `List<>` (utiliser `ArchCondition` sur les types génériques).
- `@DisplayName("no DTO exposes a JPA entity in a field or a record component")`
- `@DisplayName("every response DTO is a record")` — `dto/response` ne contient que des records,
  conformément à `CLAUDE.md`.

### 4.3 — La règle « argent »

La plus rentable des quatre, parce qu'elle protège la correction financière :

- `@DisplayName("no field anywhere is a double or a float")` — champs et composants de record
  dans `..entity..`, `..dto..`, `..service..`.
- `@DisplayName("no method anywhere returns a double or a float")`
- `@DisplayName("every BigDecimal column declares precision 19 and scale 3")` — parcourir les
  champs `@Column` de `..entity..` de type `BigDecimal` et asserter les attributs de
  l'annotation. Complète le contrôle côté schéma de WP1 §1.2.

### 4.4 — Transactions et lectures

- `@DisplayName("every read-only finder is annotated transactional read-only")` — méthodes de
  `..service.impl..` dont le nom commence par `find`, `get`, `list`, `search`.
  **Violation déjà repérée, à corriger dans ce lot** : `AuditLogServiceImpl.search` (ligne 56)
  n'a pas de `@Transactional(readOnly = true)`, alors que `findByEntity` juste au-dessus l'a.
  C'est un vrai écart à `CLAUDE.md`, pas un faux positif de la règle.
- `@DisplayName("no service calls findAll without arguments")` — la règle « jamais `findAll()`
  puis regroupement en Java » de `CLAUDE.md`. Interdire l'appel à `JpaRepository.findAll()`
  sans `Specification` ni `Pageable`.

### 4.5 — Hygiène générale

- `@DisplayName("no class uses java.util.Date or Calendar")` — `java.time` uniquement.
- `@DisplayName("nothing prints to System.out or System.err")`
- `@DisplayName("no field injection")` — `@Autowired` sur un champ interdit dans le code de
  production ; `@RequiredArgsConstructor` est le patron du dépôt. Deux exclusions à prévoir,
  chacune commentée : **`..config..`**, où `CorsConfig:15` utilise `@Value` sur un champ, et
  **`@PersistenceContext`** sur le champ `entityManager` de `ReferenceGeneratorServiceImpl:31`,
  qui est le patron d'injection normal d'un `EntityManager`. Il n'y a aucun `@Autowired` sur
  champ dans `src/main` aujourd'hui : la règle doit rester verte, pas devenir une liste
  d'exceptions.
- `@DisplayName("no cycles between packages")` — `slices().matching("com.promoteur.app.(*)..")
  .should().beFreeOfCycles()`.

⚠️ Ces règles vont probablement échouer sur du code existant. Pour chaque échec : soit corriger
le code (si c'est bien une violation de `CLAUDE.md`), soit ajouter un `.allowEmptyShould(false)`
ou une exclusion **commentée avec la raison**. Ne jamais désactiver une règle en silence.

## Critères d'acceptation WP4

⚠️ Trois précisions venues de la livraison :

- **`archunit-junit5` n'est qu'un agrégateur** de `archunit-junit5-api` et
  `archunit-junit5-engine` ; déclarer les deux directement évite un POM intermédiaire.
- **La règle des finders doit lire l'annotation de classe**, pas seulement celle de la méthode :
  Spring résout `@Transactional` sur la méthode puis sur la classe déclarante.
  `SearchServiceImpl.search` est couvert au niveau classe — le signaler serait un faux positif.
- **Il y a deux cycles de paquets, pas un.** Exclure `config` révèle
  `exception → service → exception` : les services lèvent `ResourceNotFoundException`, et
  `GlobalExceptionHandler`, dans le même paquet, injecte `MessageService`. Les deux sont
  structurels ; les corriger demande de déplacer `CompanyProperties` et
  `GlobalExceptionHandler`.

- [ ] `ArchitectureTest` couvre les 5 groupes ci-dessus.
- [ ] Toute exclusion porte un commentaire justifiant pourquoi le cas est légitime.
- [ ] Les violations réelles trouvées sont corrigées ou listées dans le rapport final.

---

# Livraison

## Ordre imposé

```
WP0  →  WP1  →  WP2  →  WP3  →  WP4
```

WP0 en premier : sans lui, chaque lot suivant alourdit une suite déjà lente.

**Ordre recommandé en pratique : WP0 → WP4 → WP2 → WP3 → WP1.** Le WP4 coûte ~80 lignes et a
déjà révélé une violation à la simple lecture (`AuditLogServiceImpl.search`) : le passer tôt
cadre le code avant que le WP2 et le WP3 y touchent, plutôt que de commenter après coup des
règles violées par du code tout neuf. Le WP1 vient en dernier parce qu'il dépend d'un démon
Docker et produira le résultat le plus inconfortable — le scénario concurrent CONC-01 sur
PostgreSQL. Cet ordre reste un conseil : l'ordre imposé ci-dessus est celui qui fait foi si on
ne tranche pas.

## Un commit par lot, au minimum

Format des messages, en français, comme le reste du dépôt :

```
tests(wp2): la couche web est couverte par des slices MockMvc

Le GlobalExceptionHandler ne traitait pas DataIntegrityViolationException :
une reference en doublon repondait 500 « Unexpected server error » au lieu
d'un 409 exploitable par la console. Le test precede le correctif.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Rapport final attendu

À la fin des quatre lots, produire une note contenant :

1. Le nombre de tests avant / après, et le temps de `mvn -q verify` avant / après.
2. **La liste des bugs réels trouvés**, avec pour chacun : le test qui l'expose, s'il a été
   corrigé, et sinon pourquoi.
3. Les divergences H2 / PostgreSQL constatées et ce qu'elles impliquent pour la production.
4. Les mises à jour à porter dans `CLAUDE.md` : le nombre de tests (la valeur `152` y est déjà
   fausse), les nouvelles commandes de vérification, et la section « Où sont les choses ».

## Hors périmètre de cette spec

Volontairement écartés, à traiter plus tard :

- Tests de mutation (PIT) sur `service.impl`.
- Tests par propriétés (jqwik) sur `AmountInWordsService`, la TVA et les échéanciers.
- Tests « golden file » sur les PDF (OpenPDF) et les exports Excel (POI) : aujourd'hui
  `DocumentTest` vérifie qu'un PDF valide sort, jamais ce qu'il contient.
- Test d'empreinte des migrations livrées (garde-fou sur « on n'édite jamais une migration
  déjà livrée »).
- **Toute l'infrastructure de test du frontend**, aujourd'hui inexistante — en particulier la
  course sur `ProjectContextService.scope$` que `CLAUDE.md` décrit en toutes lettres : une
  requête lancée sur `null` revient après la bonne et affiche les chiffres d'un projet sous le
  nom d'un autre. C'est le test frontend le plus rentable, et il n'existe pas.
- Un test de fumée bout en bout à travers `docker-compose`.
