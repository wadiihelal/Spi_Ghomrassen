# SP Immobilière GHOMRASSEN Backend

Backend Spring Boot pour une application de gestion de dépenses et achats clients pour promoteur immobilier.

## Modules inclus
- Clients
- Projets
- Fournisseurs
- Catégories de dépense
- Dépenses
- Achats clients
- Acomptes clients
- Retenues à la source
- Dashboard
- Rapports

## Stack
- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Validation
- PostgreSQL 16 + Flyway
- H2 (tests uniquement)
- Lombok

## Base de données

Le backend s'appuie sur PostgreSQL 16. Le schéma est géré exclusivement par Flyway
(`src/main/resources/db/migration`) ; Hibernate est en `ddl-auto=validate` et ne crée ni ne
modifie jamais une table. Les données survivent donc aux redémarrages.

### Démarrer la base

Depuis la racine du projet :

```bash
docker compose up -d
```

Le service `postgres` expose `localhost:5432`, base `spi_ghomrassen`, utilisateur `spi`,
mot de passe `spi`, avec un volume nommé `spi-postgres-data` pour la persistance.

### Lancement du backend

```bash
mvn spring-boot:run
```

Au premier démarrage, Flyway applique `V1__baseline.sql` et crée les 11 tables.

## Profils

| Profil | Base | Usage |
|---|---|---|
| `dev` (défaut) | PostgreSQL `localhost:5432/spi_ghomrassen` | développement local, `show-sql=true` |
| `prod` | PostgreSQL via `${DATABASE_URL}` | production, aucune valeur par défaut |
| `test` | H2 en mémoire, mêmes migrations Flyway | suite de tests |

### Variables d'environnement

| Variable | Profils | Défaut |
|---|---|---|
| `DB_USER` | dev, prod | `spi` en dev, obligatoire en prod |
| `DB_PASSWORD` | dev, prod | `spi` en dev, obligatoire en prod |
| `DATABASE_URL` | prod | obligatoire |
| `APP_CORS_ALLOWED_ORIGINS` | prod | obligatoire |

## Données de démonstration — profil `demo`

Les données fictives ne sont plus chargées automatiquement.

`ReferenceDataInitializer` tourne dans tous les profils et ne crée que les données de
référence manquantes : catégories de dépense et types de fournisseur. Sur une base vide, un
démarrage en `dev` crée donc 6 catégories et 5 types, et rien d'autre : zéro projet, zéro
client, zéro appartement.

`DemoDataInitializer` est annoté `@Profile("demo")` et porte tout le reste : 5 « Résidence
Démo », 120 appartements, ~120 clients synthétiques en `@demo-spi.tn`, leurs achats et leurs
acomptes. Il journalise un avertissement au démarrage.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

**Ne jamais activer le profil `demo` en production** : il injecte des acquéreurs fictifs dans
la comptabilité réelle.

## Console H2

Désactivée dans tous les profils (`spring.h2.console.enabled=false`).

## Endpoints principaux
### CRUD de base
- `/api/clients`
- `/api/projects`
- `/api/suppliers`
- `/api/expense-categories`
- `/api/expenses`
- `/api/client-purchases`
- `/api/client-advances`
- `/api/withholding-taxes`

### Métier / filtres
- `GET /api/expenses/by-category/{categoryId}`
- `GET /api/expenses/by-project/{projectId}`
- `GET /api/client-purchases/by-client/{clientId}`
- `GET /api/client-purchases/by-project/{projectId}`
- `GET /api/client-advances/by-client/{clientId}`
- `GET /api/client-advances/by-project/{projectId}`

### Dashboard / rapports
- `GET /api/dashboard/summary`
- `GET /api/reports/expenses/by-category`
- `GET /api/reports/expenses/by-project`
- `GET /api/reports/clients/statements`
- `GET /api/reports/clients/{clientId}/statement`

## Exemples JSON

### Créer un client
```json
{
  "fullName": "Ahmed Ben Salah",
  "phone": "22111222",
  "email": "ahmed@test.com",
  "address": "Tunis",
  "cinOrFiscalId": "12345678",
  "notes": "Client prioritaire",
  "active": true
}
```

### Créer un projet
```json
{
  "code": "PRJ-001",
  "name": "Résidence Les Jardins",
  "location": "Ariana",
  "description": "Projet immobilier R+4",
  "startDate": "2026-04-01",
  "expectedEndDate": "2027-06-30",
  "budget": 2500000,
  "status": "IN_PROGRESS"
}
```

### Créer un fournisseur
```json
{
  "name": "Bureau Etudes Alpha",
  "fiscalId": "MF123",
  "phone": "55111222",
  "email": "alpha@test.com",
  "address": "Sfax",
  "type": "ENGINEER",
  "withholdingApplicable": true,
  "active": true
}
```

### Créer une dépense
```json
{
  "reference": "EXP-001",
  "expenseDate": "2026-04-10",
  "description": "Frais de dossier baladiya",
  "amountHt": 1000,
  "vatAmount": 190,
  "amountTtc": 1190,
  "paymentMethod": "BANK_TRANSFER",
  "documentNumber": "FAC-001",
  "notes": "Paiement mairie",
  "categoryId": 1,
  "projectId": 1,
  "supplierId": 1
}
```

### Créer un achat client
```json
{
  "reference": "PUR-001",
  "purchaseDate": "2026-04-12",
  "contractDate": "2026-04-15",
  "assetDescription": "Appartement A12",
  "totalAmount": 185000,
  "notes": "Réservation confirmée",
  "clientId": 1,
  "projectId": 1
}
```

### Créer un acompte client
```json
{
  "reference": "ADV-001",
  "advanceDate": "2026-04-13",
  "amount": 25000,
  "paymentMethod": "CHECK",
  "notes": "Premier acompte",
  "clientId": 1,
  "projectId": 1
}
```

### Créer une retenue à la source
```json
{
  "reference": "WHT-001",
  "calculationDate": "2026-04-13",
  "baseAmount": 1000,
  "rate": 1.5,
  "expenseId": 1,
  "supplierId": 1,
  "status": "CALCULATED"
}
```

## Remarque
Le projet est prêt structurellement. Je n'ai pas pu exécuter Maven ici car `mvn` n'est pas disponible dans l'environnement de génération du ZIP.


Architecture:
- controllers -> services -> serviceImpl -> repositories
- serviceImpl disponibles pour Client, Project, Supplier, ExpenseCategory, Expense, ClientPurchase, ClientAdvance, WithholdingTax, Report
