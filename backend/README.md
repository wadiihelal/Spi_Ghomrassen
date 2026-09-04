# SP Immobilière GHOMRASSEN — Backend

API Spring Boot d'une application de gestion pour promoteur immobilier : projets, appartements,
clients, contrats de vente, acomptes, factures fournisseurs, dépenses, justificatifs, journal
d'audit et rapports Excel / PDF.

## Stack

- Java 17, Spring Boot 3.3.5, Spring Web, Spring Data JPA, Validation
- PostgreSQL 16, schéma géré par Flyway (`ddl-auto=validate`)
- MapStruct (DTO de réponse), Lombok, Apache POI (Excel), OpenPDF (PDF), springdoc-openapi
- H2 en mémoire pour la suite de tests uniquement

Découpage : `controller → service (interface) → service/impl → repository`. Les contrôleurs
renvoient des records de `dto/response`, jamais d'entité. Tous les montants sont des
`BigDecimal` `precision = 19, scale = 3` (millimes), arrondis `HALF_UP`.

## Base de données

Le schéma est géré exclusivement par Flyway (`src/main/resources/db/migration`, V1 à V8) ;
Hibernate ne fait que le vérifier. Les données survivent aux redémarrages.

```bash
docker compose up -d postgres      # depuis la racine du projet
mvn spring-boot:run                # profil dev par défaut
```

Le service `postgres` expose `localhost:5432`, base `spi_ghomrassen`, utilisateur `spi`, mot de
passe `spi`, volume `spi-postgres-data`.

Sans Docker, l'API se lance sur H2 avec les données de démonstration :

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test,demo -Dspring-boot.run.useTestClasspath=true
```

## Profils

| Profil | Base | Usage |
|---|---|---|
| `dev` (défaut) | PostgreSQL `localhost:5432/spi_ghomrassen` | développement, `show-sql=true`, OpenAPI actif |
| `demo` | s'ajoute à un autre profil | charge des projets, appartements, acquéreurs et paiements **fictifs** — jamais en production |
| `prod` | PostgreSQL via `${DATABASE_URL}` | production, aucune valeur par défaut, OpenAPI désactivé |
| `test` | H2 en mémoire, mêmes migrations Flyway | suite de tests, OpenAPI actif |

`ReferenceDataInitializer` tourne dans tous les profils et crée uniquement ce qui manque :
catégories de dépense, types de fournisseur, taux de TVA (0, 7, 13, 19 %).

### Variables d'environnement

| Variable | Profils | Défaut |
|---|---|---|
| `DB_USER`, `DB_PASSWORD` | dev, prod | `spi` / `spi` en dev, obligatoires en prod |
| `DATABASE_URL` | prod | obligatoire, ex. `jdbc:postgresql://postgres:5432/spi_ghomrassen` |
| `APP_CORS_ALLOWED_ORIGINS` | prod | obligatoire, origine du frontend |
| `ATTACHMENTS_ROOT` | prod | obligatoire — dossier des pièces jointes, hors du dossier de l'application |

## Sécurité — décision métier

L'application **n'a aucune authentification ni rôle** (décision du 2 septembre 2026). Toute
personne capable d'atteindre l'API peut lire les CIN, téléphones, adresses et montants des
acquéreurs, et supprimer n'importe quel enregistrement. La protection est donc réseau :

- ne jamais publier le port 8080 : `docker-compose.yml` place le backend derrière nginx ;
- restreindre le port 80 par pare-feu aux adresses du bureau (`ufw allow from <ip> to any port 80`)
  ou placer le tout derrière un VPN ;
- à défaut, `auth_basic` dans nginx protège l'API sans écran de connexion.

Le journal d'audit (`audit_logs`) enregistre `actor = system` tant qu'il n'y a pas de connexion.
`AuditLogServiceImpl.resolveActor()` est le seul point à changer si un login est ajouté.

## Règles métier codées

- **TVA** : le client envoie `amountHt` + `vatRate` ; le serveur calcule `vatAmount` et
  `amountTtc` (`VatCalculationService`). Les taux sont dans la table `vat_rate_options`.
  Pas de retenue à la source (décision du 2 septembre 2026).
- **Acomptes** : plafonnés par le total du contrat de vente, sinon par le prix de vente de
  l'appartement ; refusés si aucun des deux n'existe. Le contrôle est sérialisé par un verrou
  pessimiste sur la ligne appartement ; toutes les entités portent un `@Version` (409 en cas de
  conflit).
- **Contrats** : un seul par appartement ; encaissé = paiement direct + acomptes ; statut
  `UNPAID` / `PARTIALLY_PAID` / `PAID` dérivé, jamais stocké.
- **Références** : `DEP-2026-00042`, `ACC-2026-00042`, attribuées par séquence avant le premier
  enregistrement.
- **Rapports** : tout rapport est filtré par `projectId` (absent = projet actif, `ALL` = tous) et
  par `year` / `month`. Les exports indiquent leur périmètre en en-tête.

## Pièces jointes

Bordereaux de virement, scans de chèque et pages de contrat sont de vrais fichiers
(`file_attachments`, `POST /api/attachments` en multipart, `GET /api/attachments/{id}` en flux).
PDF, JPEG, PNG ; 10 Mo maximum. Stockage sur le système de fichiers local sous `app.storage.root` :

| Profil | Racine |
|---|---|
| `dev` | `~/.spi-ghomrassen/attachments` |
| `prod` | `${ATTACHMENTS_ROOT}` — volume `spi-attachments` dans `docker-compose.yml` |
| `test` | dossier temporaire |

Les anciennes colonnes `attachment_name` / `attachment_url` restent lisibles pour l'historique
mais ne sont plus alimentées.

### Sauvegardes — lacune assumée

**Aucune sauvegarde automatisée n'est en place** pour le volume PostgreSQL ni pour celui des
pièces jointes (décision du 4 septembre 2026 : à traiter plus tard). Le dump de la base ne
contient pas les fichiers : une sauvegarde complète doit couvrir `spi-postgres-data` **et**
`spi-attachments`. Tant que ce n'est pas fait, la perte du serveur emporte la comptabilité et ses
justificatifs.

## API

Documentation OpenAPI : `/v3/api-docs` et `/swagger-ui.html`, profils `dev` et `test` uniquement.

Conventions : création → `201` avec en-tête `Location` ; suppression → `204` ; erreurs en JSON
`{ timestamp, status, error, details? }` avec un message en français ; `400` saisie refusée,
`404` introuvable, `409` modification concurrente, `413` fichier trop volumineux.

Toutes les listes sont paginées (`page`, `size`, `sort`) et acceptent des filtres en paramètres
de requête : `projectId`, `clientId`, `supplierId`, `categoryId`, `apartmentId`,
`paymentStatus`, `paymentMethod`, `dateFrom`, `dateTo`, `search`. Les routes `/by-project/{id}`,
`/by-client/{id}`, `/by-category/{id}`, `/by-supplier/{id}` sont dépréciées et retirées à la
prochaine version.

| Ressource | Routes |
|---|---|
| Projets | `/api/projects`, `/api/projects/active-context` (`GET`, `PUT /{id}`, `DELETE`) |
| Appartements | `/api/apartments` |
| Clients | `/api/clients` |
| Fournisseurs, types | `/api/suppliers`, `/api/supplier-types` |
| Catégories, taux de TVA | `/api/expense-categories`, `/api/vat-rates` |
| Dépenses | `/api/expenses` |
| Factures fournisseurs | `/api/supplier-invoices` |
| Contrats de vente | `/api/client-purchases` |
| Acomptes | `/api/client-advances` |
| Pièces jointes | `/api/attachments?ownerType&ownerId`, `/api/attachments/{id}` |
| Journal d'audit | `/api/audit-logs?entityType&actor&dateFrom&dateTo`, `/api/audit-logs/by-entity/{type}/{id}` |
| Tableau de bord | `/api/dashboard/summary?projectId` |
| Rapports | `/api/reports/expenses/by-category`, `/by-project`, `/by-month`, `/api/reports/purchases/by-project`, `/api/reports/advances/by-payment-method`, `/api/reports/clients/statements`, `/api/reports/clients/{id}/statement`, `/api/reports/export/excel`, `/export/pdf` |

### Exemple — créer une dépense

```json
POST /api/expenses
{
  "expenseDate": "2026-04-10",
  "description": "Frais de dossier baladiya",
  "amountHt": 1000.000,
  "vatRate": 0.0700,
  "paymentMethod": "BANK_TRANSFER",
  "documentNumber": "FAC-001",
  "categoryId": 1,
  "projectId": 1,
  "supplierId": 1
}
```

Réponse `201`, `Location: /api/expenses/42`, corps avec `reference: "DEP-2026-00042"`,
`vatAmount: 70.000`, `amountTtc: 1070.000`.

## Déploiement

```bash
docker compose up --build
```

Trois services : `postgres`, `backend` (image multi-étapes, JRE 17, utilisateur non root, aucun
port publié) et `frontend` (build Node puis nginx sur le port 80, qui sert la console et relaie
`/api/`). Voir « Sécurité » ci-dessus avant d'exposer le port 80.

## Vérifier

```bash
mvn -q verify        # compilation + 83 tests sur H2 avec les migrations Flyway
```

Chaque règle financière a son test dans `src/test/java/com/promoteur/app/service`, nommé
d'après la règle en clair.
