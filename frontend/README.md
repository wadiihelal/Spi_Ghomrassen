# SP Immobilière GHOMRASSEN Frontend - Angular

Frontend Angular standalone, responsive, pour la gestion immobilière.

## Stack
- Angular 19 standalone
- PrimeNG
- HttpClient
- Responsive admin layout

## Pages incluses
- Dashboard
- Dépenses
- Achats clients
- Acomptes
- Rapports

## Configuration API
Modifier `src/environments/environment.ts` si nécessaire.

Par défaut:
```ts
apiUrl: 'http://localhost:8080/api'
```

## Installation
```bash
npm install
npm start
```

## Remarques
- Ce frontend attend que le backend expose les endpoints déjà préparés.
- Les listes Client/Project/Category sont récupérées depuis l'API.
- Pour les dropdowns clients, l'affichage utilise `firstName` en simple starter. Tu peux facilement le remplacer par `firstName + ' ' + lastName`.
