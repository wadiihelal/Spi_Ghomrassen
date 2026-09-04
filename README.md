# SPI Ghomrassen

[![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)

<!-- Le dépôt n'a pas encore de remote : remplacer OWNER/REPO par le chemin GitHub réel une fois le
     dépôt publié, le badge pointera alors sur .github/workflows/ci.yml. -->

Application interne de gestion pour SP Immobilière GHOMRASSEN, promoteur immobilier tunisien :
projets, appartements, clients, contrats de vente, acomptes, factures fournisseurs, dépenses,
justificatifs et rapports. Interface en français, montants en dinars tunisiens (3 décimales).

| Dossier | Contenu | Documentation |
|---|---|---|
| `backend/` | Spring Boot 3.3 · Java 17 · PostgreSQL 16 · Flyway | [backend/README.md](backend/README.md) |
| `frontend/` | Angular 19 · PrimeNG 17 | [frontend/README.md](frontend/README.md) |
| `CLAUDE.md` | conventions à respecter par tout contributeur, humain ou agent | |

## Démarrage rapide

```bash
docker compose up --build      # PostgreSQL + API + console sur http://localhost
```

En développement : `docker compose up -d postgres`, puis `cd backend && mvn spring-boot:run` et
`cd frontend && npm ci && npm start`.

## Intégration continue

`.github/workflows/ci.yml` exécute à chaque push et pull request `mvn -B verify` (compilation,
suite de tests sur H2 avec les migrations Flyway) et `npm ci && npx ng build`.

## À lire avant de déployer

L'application n'a **pas d'authentification** (décision métier). La protection se fait au niveau
réseau — voir la section « Exposition réseau » du README backend. Les sauvegardes ne sont pas
encore automatisées : voir « Sauvegardes — lacune assumée », même document.
