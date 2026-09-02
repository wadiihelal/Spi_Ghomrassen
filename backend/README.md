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
- H2 Database
- Lombok

## Lancement
```bash
mvn spring-boot:run
```

## Données de démonstration au démarrage
Au lancement, si la base est vide, le backend charge automatiquement des données cohérentes de démonstration :
- 3 projets
- 4 clients
- 5 fournisseurs
- catégories de dépense
- dépenses liées aux projets et fournisseurs
- acomptes clients
- achats clients
- retenues à la source générées automatiquement selon les fournisseurs soumis à retenue

Comme la base H2 est en mémoire (`jdbc:h2:mem:promoteurdb`), ces données sont rechargées à chaque démarrage du backend.

## H2 Console
- URL: `/h2-console`
- JDBC URL: `jdbc:h2:mem:promoteurdb`
- User: `sa`
- Password: vide

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
