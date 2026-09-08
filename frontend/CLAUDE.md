# CLAUDE.md — frontend

Ce fichier guide Claude Code (claude.ai/code) dans `frontend/`. Il complète le
[CLAUDE.md racine](../CLAUDE.md), qui reste la référence sur ce qui ne se négocie pas, et le
[README](README.md), qui décrit les écrans un par un.

## Le module

Console Angular 19 de gestion pour un promoteur immobilier tunisien : plan de vente, stock
d'appartements, contrats, encaissements, échéanciers, factures et règlements fournisseurs,
dépenses, rapports et documents imprimables.

Angular 19 en composants **standalone** (pas un seul `NgModule` applicatif), PrimeNG 17 sur le
thème `lara-light-blue` retouché, RxJS 7.8, TypeScript 5.6 en `strict` + `strictTemplates`.
Aucune bibliothèque d'état, aucun utilitaire CSS : les signaux et le fichier de tokens
suffisent. Ne pas ajouter de dépendance sans nécessité démontrée.

**Interface entièrement en français accentué**, locale `fr-TN` (virgule décimale, espace comme
séparateur de milliers, dates jour d'abord). Les commentaires et la javadoc du code sont en
anglais, comme côté backend.

## Commandes

```bash
npm ci                 # et non npm install : package-lock.json est la référence
npm start              # http://localhost:4200, API attendue sur http://localhost:8080/api
npx ng build           # build de production (TypeScript strict + strictTemplates)
npm run watch          # rebuild continu en configuration development
```

`npx ng build` est le seul garde-fou automatique du module : **il n'y a aucun test frontend**
— pas de cible `test` dans `angular.json`, ni Karma, ni Jasmine, ni Jest, et aucun ESLint ou
Prettier configuré. Une régression de typage ou de template est détectée par le build, le reste
se vérifie à l'écran. `.github/workflows/ci.yml` lance `npm ci` puis `npx ng build`.

Le build par défaut est `production` (`defaultConfiguration`), donc un `npx ng build` nu
substitue `environment.prod.ts` et fait respecter les budgets de taille.

## Le piège principal : le périmètre projet

Toute la console est rattachée à un projet, choisi dans l'en-tête et **persisté côté serveur**
(`GET`/`PUT /api/projects/active-context`), pas dans le navigateur.

**Un écran charge ses données sur `ProjectContextService.scope$`, jamais sur
`selectedProjectId` directement.** Le signal vaut `null` jusqu'à ce que le contexte enregistré
revienne du serveur ; une requête lancée sur ce `null` porte sur *tous* les projets et peut
répondre **après** la bonne — les chiffres de tous les projets s'affichent alors sous le nom
d'un seul. `scope$` n'émet qu'une fois le contexte résolu, puis à chaque changement, et sa
valeur est boxée pour que re-sélectionner le même projet émette quand même.

Le motif à recopier, dans `ngOnInit` :

```ts
this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
  this.selectedProjectId.set(projectId);
  this.table.onFilterChange();   // la table suit l'en-tête
});
```

Un seul abonnement à `scope$` par écran. Deux abonnements, ou un abonnement doublé d'une lecture
directe du signal, ramènent exactement le bug que `scope$` existe pour empêcher.

## Découpage

```
src/app/
  app.config.ts          providers racine : locale fr-TN, router, HttpClient + intercepteur,
                         MessageService / ConfirmationService de PrimeNG, DecimalPipe
  app.routes.ts          toutes les routes en loadComponent (chargement à la demande)
  core/
    layout/              coquille : en-tête, navigation, horloge, sélecteur de projet,
                         recherche globale
    services/            ApiService, ProjectContextService, LazyTable, UiService
    interceptors/        http-error.interceptor.ts
  features/<écran>/      un dossier par écran : .ts + .html + .css
  shared/
    models/models.ts     miroir des DTO de réponse du backend
    pipes/               dinar, percentShare
    attachments/         panneau de pièces jointes réutilisable
    schedule/            éditeur d'échéancier
    supplier-payments/   règlements d'une facture
  styles.css             n'importe que les quatre couches de styles/
  styles/                tokens.css, base.css, components.css, app.css
```

Toute route passe par `loadComponent` : importer statiquement les quatorze composants et leurs
modules PrimeNG remettrait la console entière dans le bundle initial (FE-04).

## Conventions de composant

- `standalone: true`, `changeDetection: ChangeDetectionStrategy.OnPush`, **sans exception**.
- Les modules PrimeNG s'importent composant par composant, dans `imports:` — pas de baril
  d'imports partagé.
- **État asynchrone dans des signaux** (`signal`, `computed`) : un template qui lit un signal
  se rafraîchit sous `OnPush` sans coup de pouce manuel. Les propriétés simples de formulaire
  (`editingId`, `dialogVisible`) peuvent rester des champs ordinaires.
- **Tout ce qui reste en RxJS est libéré par `takeUntilDestroyed(this.destroyRef)`.** Pas de
  `Subscription` gardée à la main, pas de `ngOnDestroy` pour désabonner. Un `setInterval` en
  revanche se nettoie bien dans `ngOnDestroy` (voir `LayoutComponent`).
- Injection par `inject()`, champs `private readonly`.
- Formulaires en `ReactiveFormsModule` via `FormBuilder`, avec `Validators`. Les filtres de
  liste, eux, sont un simple objet `filters` lié en `ngModel` et poussé au serveur.
- Confirmations et toasts passent par `UiService` (`success`, `error`, `info`,
  `confirmDelete`) — pas d'appel direct à `MessageService` ni `ConfirmationService` dans un
  écran.
- Les commentaires citent le code de l'exigence traitée (`PERF-02`, `FE-03`, `UX-05`…) ;
  conserver ces références quand on touche au code concerné.

Deux syntaxes cohabitent, et c'est assumé : le code récent utilise le **flux de contrôle
intégré** (`@if`, `@for` avec `track`) et les **entrées/sorties en signaux**
(`input()`, `input.required()`, `output()`), quatre templates plus anciens sont encore en
`*ngIf` / `*ngFor`. Écrire du neuf dans la forme moderne, mais **ne pas migrer en masse** les
écrans qu'on ne touche pas : le dépôt refuse les changements opportunistes hors du périmètre
demandé.

## Le serveur calcule, le navigateur affiche

Aucun montant n'est calculé ici. TVA, plafonds, encaissé, reste à payer, statuts de paiement,
totaux de rapport : tout arrive déjà calculé par l'API. Un total recalculé en TypeScript
finirait par contredire le PDF que le backend imprime à partir des mêmes lignes.

Les montants s'affichent avec `| dinar` : **trois décimales toujours**, parce que le dinar se
divise en 1000 millimes et que le backend stocke en `scale = 3` — afficher deux décimales perd
de l'argent réel. `| dinar:'short'` pour les tuiles de KPI où les millimes sont du bruit,
`| dinar:'bare'` quand l'unité est déjà dans le texte alentour. Les parts en pourcentage
passent par `| percentShare` (« 18,5 % », espace avant le signe).

Les taux de TVA ne sont **pas** de la configuration front : ils viennent de
`GET /api/vat-rates`.

## Tables paginées côté serveur

Les tables métier (dépenses, ventes, encaissements, appartements, factures) sont en mode `lazy`
PrimeNG et passent par `core/services/lazy-table.ts` :

```ts
readonly table = new LazyTable<Expense>(
  (query) => this.api.getExpenses(this.serverFilter, query),
  this.destroyRef
);
```

`LazyTable` tient les lignes, le **vrai** `totalElements` et l'état de chargement dans des
signaux, débounce les filtres à 300 ms et revient à la première page à chaque changement de
filtre. `onLazyLoad` reçoit pagination et tri, `reload()` rafraîchit après un enregistrement ou
une suppression.

**Le navigateur ne charge jamais une liste complète pour la filtrer lui-même.** Les filtres
partent au serveur en paramètres de requête (`projectId`, `clientId`, `supplierId`,
`categoryId`, `apartmentId`, `paymentStatus`, `settlement`, `dateFrom`, `dateTo`, `search`).
Avant ce travail, chaque écran demandait la page 0 de 1000 lignes et filtrait en TypeScript, en
perdant silencieusement tout ce qui dépassait la millième ligne parce que personne ne lisait
`totalElements`.

Les listes de **référence** (projets, clients, catégories, types de fournisseur, taux de TVA)
sont l'exception assumée : petites et bornées, elles passent par `ApiService.getReferenceList`,
une requête unique plafonnée à 500 éléments, taillée pour un menu déroulant. Une table métier
ne doit jamais l'emprunter.

## ApiService

Point d'entrée unique vers l'API (`core/services/api.service.ts`, ~500 lignes) : aucun
composant n'injecte `HttpClient` directement. Trois formes privées structurent le reste —
`getReferenceList` (liste bornée), `page` (une page filtrée avec ses totaux) et les appels
directs. Ajouter un endpoint = ajouter une méthode ici, et le type correspondant dans
`shared/models/models.ts`.

Les documents imprimables ne sont **pas** téléchargés en blob : `receiptUrl`,
`clientStatementUrl`, `vatSummaryUrl` et `attachmentUrl` renvoient une URL que le lecteur PDF du
navigateur ouvre, et qui reste ouvrable dans un nouvel onglet. Ne pas remplacer ça par un
`HttpClient.get(..., { responseType: 'blob' })`.

## Modèle de données côté client

`shared/models/models.ts` reflète les `record` de `dto/response` du backend : **identifiants à
plat et libellés à côté** (`projectId` + `projectName`), jamais d'objet imbriqué. `Client` porte
un seul `fullName`. Modifier un DTO de réponse côté backend implique de modifier ce fichier dans
la même livraison — `strictTemplates` fait échouer le build si un template lit un champ qui
n'existe plus, ce qui est le comportement voulu.

## Erreurs

`core/interceptors/http-error.interceptor.ts` affiche ce que le backend a réellement dit :
`details` champ par champ s'ils sont présents, sinon `error`, avec un résumé français par
statut (`400` Saisie refusée, `404` Introuvable, `409` Modification concurrente, `0`/`504`
Serveur injoignable). **Un composant ne pose pas de message d'erreur fixe** : dans un
`subscribe`, la branche `error` sert au plus à retomber sur un état propre
(`this.loading.set(false)`), l'intercepteur a déjà parlé. Un message précis — le numéro de lot
et les montants d'un acompte refusé — ne doit pas être écrasé par un « Une erreur est
survenue ».

Deux choses ont changé côté serveur (WP2 du durcissement des tests, voir
`../backend/CLAUDE.md`) : les valeurs de `details` arrivent maintenant **en français** — elles
sortaient en anglais (« must not be blank ») selon la locale du serveur — et cinq familles de
requêtes malformées répondent `400` ou `409` au lieu de `500`. Le résumé par statut de
l'intercepteur couvre déjà ces cas ; il n'y a rien à changer ici, mais un toast qui affichait
« Erreur serveur » sur une saisie refusée doit désormais dire « Saisie refusée ».

## Styles

`src/styles.css` n'importe que quatre couches, dans cet ordre :

| Fichier | Rôle |
|---|---|
| `styles/tokens.css` | couleur, type, espacement, rayons, élévation — **source unique** |
| `styles/base.css` | reset, typographie, focus visible, barres de défilement, utilitaires |
| `styles/components.css` | reprise des composants PrimeNG sur le système de tokens |
| `styles/app.css` | mobilier de page partagé (`.section-header`, `.page-title`, grilles…) |

**Aucune valeur hexadécimale brute, aucune taille magique** en dehors de `tokens.css`. La
direction est « institutionnel calme » : navy pour la structure, laiton pour l'accent, élévation
sobre. Les chiffres sont en `tabular-nums` et alignés à droite (`.align-end`) — l'argent ne se
lit en colonne qu'à cette condition.

Un `.css` de composant ne contient que ce qui est propre à cet écran ; ce qui se répète monte
dans `app.css`. Les surcharges PrimeNG sont préfixées `body .p-…` pour l'emporter sur le thème.

## Environnements

| Fichier | Rôle |
|---|---|
| `src/environments/environment.ts` | dev : `apiUrl = http://localhost:8080/api` |
| `src/environments/environment.prod.ts` | prod : `apiUrl = /api`, relayé par nginx ; substitué au build par `fileReplacements` |
| `src/environments/environment.local.ts` | surcharge personnelle, ignorée par git |

En production, `nginx.conf` sert la SPA (`try_files … /index.html`) et relaie `/api/` vers
`backend:8080` : un seul port publié, aucun problème de CORS. Le build sort dans
`dist/real-estate-frontend/browser` — c'est ce sous-dossier `browser/` que le `Dockerfile`
copie, pas le parent.

Les assets viennent de `public/` (`favicon.svg`). `index.html` charge Inter depuis Google
Fonts, avec repli sur les polices système dans les tokens : une machine hors ligne reste
lisible.

## Recherche globale

`core/layout/global-search.component.ts` : la boîte de recherche de la barre supérieure
(`GET /api/search?q=`). Débounce 250 ms, deux caractères minimum, résultats groupés par type,
navigation au clavier. Choisir un résultat ouvre la liste correspondante **déjà filtrée** via
`queryParams: { search: hit.label }` — un écran cible doit donc savoir lire `search` dans ses
paramètres de requête.

## Écrans

| Route | Composant | Contenu |
|---|---|---|
| `/dashboard` | `features/dashboard` | situation du projet actif : encaissé, décaissé, retards, échéances du mois, reste à vendre |
| `/sales-board` | `features/sales-board` | plan de commercialisation par bloc et par étage, statut modifiable |
| `/apartments` | `features/apartments` | stock, génération par bloc, contrat / encaissé / reste par ligne |
| `/supplier-invoices` | `features/supplier-invoices` | factures, TVA par taux, règlements, impayés |
| `/expenses` | `features/expenses` | dépenses, TVA par taux, catégories |
| `/purchases` | `features/purchases` | contrats de vente, un par appartement, statut dérivé |
| `/advances` | `features/advances` | encaissements, plafonnés par le contrat ou le prix de vente |
| `/schedules` | `features/schedules` | échéanciers et échéances à relancer |
| `/reports` | `features/reports` | rapports par projet et période, exports Excel / PDF |
| `/audit` | `features/audit` | journal des opérations |
| `/projects`, `/projects/:id`, `/clients`, `/suppliers` | paramétrage | référentiels |

La navigation est scindée en deux groupes dans `LayoutComponent` : `dailyNavItems` (le travail
quotidien) et `settingsNavItems` (le paramétrage). Ajouter un écran = une route dans
`app.routes.ts` **et** une entrée dans le bon groupe.

## Pièces jointes

`shared/attachments/AttachmentsPanelComponent` est réutilisé par les quatre formulaires
concernés : téléversement (PDF, JPEG, PNG, 10 Mo), ouverture, suppression. Un fichier ne se
rattache qu'à un document **déjà enregistré** : tant que `ownerId` est `null`, le panneau
n'affiche pas le champ de téléversement mais la phrase « Enregistrez d'abord le document ».
Les deux autres composants partagés (`shared/schedule`, `shared/supplier-payments`) suivent le
même contrat : un identifiant d'entité en entrée, un `output<void>()` `changed` que l'écran
hôte utilise pour recharger sa table.

## Ce qu'il ne faut pas faire

- Calculer un montant, une TVA ou un statut de paiement en TypeScript.
- Charger une liste métier complète pour la filtrer ou la trier dans le navigateur.
- Lire `selectedProjectId` pour déclencher un chargement, au lieu de `scope$`.
- Afficher un montant sur deux décimales, ou sans `| dinar`.
- Écrire un message d'erreur fixe dans un `subscribe`.
- Introduire une couleur en dur hors de `tokens.css`.
- Ajouter un composant sans `standalone` ou sans `OnPush`.
- Stocker le projet actif dans `localStorage` : il vit côté serveur.
