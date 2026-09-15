# SPI Ghomrassen

[![CI](https://github.com/wadiihelal/Spi_Ghomrassen/actions/workflows/ci.yml/badge.svg)](https://github.com/wadiihelal/Spi_Ghomrassen/actions/workflows/ci.yml)

Application interne de gestion pour **SP Immobilière GHOMRASSEN**, promoteur immobilier tunisien.
Elle suit la vie d'un programme immobilier de bout en bout : projets et résidences, lots
(appartements, villas, locaux), acquéreurs, contrats de vente, encaissements et échéanciers,
factures fournisseurs et leurs règlements, dépenses de chantier, pièces jointes et rapports.

Interface en **français**, montants en **dinars tunisiens à trois décimales** (millimes). Tous les
calculs — TVA, plafonds d'acomptes, totaux, situations client — sont faits par le serveur ; le
navigateur se contente d'afficher.

## Ce que l'application produit

| Document | Contenu |
|---|---|
| Reçu de paiement | justificatif remis à l'acquéreur pour un encaissement |
| Situation client | état d'un contrat : total dû, encaissé, reste à payer, échéances |
| Récapitulatif TVA | TVA déductible sur les dépenses et factures fournisseurs |
| Rapports | dépenses, ventes, encaissements, filtrés par projet et par période |

## Pile technique

| Dossier | Contenu | Documentation |
|---|---|---|
| `backend/` | Spring Boot 3.3 · Java 17 · PostgreSQL 16 · Flyway · OpenPDF · Apache POI | [backend/README.md](backend/README.md) |
| `frontend/` | Angular 19 · PrimeNG 17 · TypeScript strict | [frontend/README.md](frontend/README.md) |
| `docs/` | manuel d'utilisation et guides de déploiement | [docs/](docs/) |
| `CLAUDE.md` | conventions à respecter par tout contributeur, humain ou agent | |

## Lancer en local

```bash
docker compose up --build      # PostgreSQL + API + interface sur http://localhost
```

En développement, les deux moitiés séparément :

```bash
docker compose up -d postgres            # la base seule
cd backend  && mvn spring-boot:run       # API sur http://localhost:8080
cd frontend && npm ci && npm start       # interface sur http://localhost:4200
```

Sans PostgreSQL du tout — H2 en mémoire et jeu de données fictif :

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=test,demo \
                                  -Dspring-boot.run.useTestClasspath=true
```

Le profil `demo` charge des acquéreurs fictifs : **jamais en production**.

## Déploiement

Deux modes sont prévus, selon que le client dispose d'un serveur ou non.

| Mode | État | Principe |
|---|---|---|
| **Serveur** (`prod`) | disponible | PostgreSQL + API + nginx via `docker-compose.yml`, sur un serveur ou un VPS |
| **Poste isolé** (`laptop`) | *en préparation* | un exécutable unique installé sur le portable du client, base en mode fichier, aucun serveur |

Le mode « poste isolé » et son installateur Windows sont en cours de réalisation ; les guides
correspondants arriveront dans [`docs/`](docs/).

## Intégration continue

`.github/workflows/ci.yml` exécute à chaque push et pull request `mvn -B verify` (compilation et
suite de tests sur H2 avec les migrations Flyway) puis `npm ci && npx ng build`.

## À lire avant de déployer

L'application **n'a pas d'authentification** — décision métier assumée. La protection se fait au
niveau réseau : voir « Sécurité — décision métier » dans [backend/README.md](backend/README.md).
Les sauvegardes ne sont pas automatisées en mode serveur : voir « Sauvegardes — lacune assumée »,
même document.

## Licence

[MIT](LICENSE).
