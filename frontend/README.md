# SP Immobilière GHOMRASSEN — Frontend

Console Angular 19 (composants standalone, PrimeNG 17) de gestion pour un promoteur
immobilier. Interface en français, montants en dinars tunisiens.

## Démarrer

```bash
npm ci
npm start          # http://localhost:4200, API attendue sur http://localhost:8080/api
```

`npm ci` plutôt que `npm install` : le `package-lock.json` est la référence.

```bash
npx ng build       # build de production, sortie dans dist/real-estate-frontend
```

## Fichiers d'environnement

| Fichier | Rôle |
|---|---|
| `src/environments/environment.ts` | développement : `apiUrl = http://localhost:8080/api` |
| `src/environments/environment.prod.ts` | production : `apiUrl = /api`, l'API est relayée par nginx (voir `nginx.conf`) ; substitué au build via `fileReplacements` dans `angular.json` |
| `src/environments/environment.local.ts` | surcharge locale personnelle, ignorée par git |

Les taux de TVA ne sont **pas** de la configuration : ils viennent de l'API (`GET /api/vat-rates`).

## Le projet actif

Toute la console est rattachée à un projet. `ProjectContextService` (`core/services`) expose le
projet sélectionné sous forme de signal (`selectedProjectId`), le charge au démarrage depuis
`GET /api/projects/active-context` et le change via `PUT /api/projects/active-context/{id}` —
le choix est donc persisté côté serveur, pas dans le navigateur. Le sélecteur est dans l'en-tête
(`core/layout`). Les écrans réagissent au changement en relançant leurs requêtes avec
`projectId`.

## Écrans

| Route | Composant | Contenu |
|---|---|---|
| `/dashboard` | `features/dashboard` | agrégats du projet actif (`/api/dashboard/summary`, `/api/reports/*`) et cinq dépenses récentes |
| `/apartments` | `features/apartments` | stock d'appartements, génération par bloc, contrat/encaissé/reste par ligne (calculés par le serveur) |
| `/supplier-invoices` | `features/supplier-invoices` | factures fournisseurs, TVA par taux |
| `/expenses` | `features/expenses` | dépenses, TVA par taux, catégories |
| `/purchases` | `features/purchases` | contrats de vente : un par appartement, statut de paiement dérivé |
| `/advances` | `features/advances` | acomptes, plafonnés par le contrat ou le prix de vente |
| `/reports` | `features/reports` | rapports par projet et période, exports Excel / PDF |
| `/projects`, `/projects/:id`, `/clients`, `/suppliers` | paramétrage | référentiels |

Toutes les routes sont chargées à la demande (`loadComponent`).

## Tables paginées côté serveur

Les cinq tables métier (dépenses, ventes, acomptes, appartements, factures) sont en mode
`lazy` PrimeNG. `core/services/lazy-table.ts` porte l'état d'une page — lignes, total réel,
chargement — et le filtre débouncé (300 ms). Les filtres partent au serveur en paramètres de
requête (`projectId`, `clientId`, `supplierId`, `categoryId`, `paymentStatus`, `dateFrom`,
`dateTo`, `search`). Le navigateur ne charge jamais une liste complète pour la filtrer
lui-même. Les listes de référence (projets, clients, catégories, taux) restent une seule
requête bornée.

## Pièces jointes

`shared/attachments/AttachmentsPanelComponent` remplace les anciens champs texte dans les quatre
formulaires : téléversement (PDF, JPEG, PNG, 10 Mo), ouverture, suppression. Un fichier ne se
rattache qu'à un document déjà enregistré.

## Erreurs

`core/interceptors/http-error.interceptor.ts` affiche le message renvoyé par le backend
(`error`, `details`) dans un toast. Les composants ne portent plus de message d'erreur fixe.

## Modèle de données côté client

`shared/models/models.ts` reflète les DTO de réponse du backend : identifiants à plat et libellés
(`projectId` + `projectName`), jamais d'objet imbriqué. `Client` porte un seul `fullName`.

## Conventions

- Composants `standalone`, `ChangeDetectionStrategy.OnPush`, état asynchrone dans des signaux.
- Ce qui reste en RxJS est libéré par `takeUntilDestroyed`.
- Chaînes utilisateur en français accentué.
