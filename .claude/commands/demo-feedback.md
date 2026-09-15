---
description: Livre un lot des retours de la demo client du 15/09/2026 (lot1 a lot4)
argument-hint: lot1 | lot1bis | lot2 | lot3 | lot4 | status
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(mvn *), Bash(npm *), Bash(npx *), Bash(git *), Bash(gh *), Bash(java *), Bash(curl *), Bash(find *), Bash(ls *), Bash(unzip *)
---

# Retours de la demonstration client — livraison d'un lot

La spec complete est dans `@DEMO_FEEDBACK_SPEC.md`. Les conventions du depot sont dans
`@CLAUDE.md`. **Lis les deux avant d'ecrire la moindre ligne.**

Lot demande : **$1**

## Ce que tu dois faire

1. **Lis `DEMO_FEEDBACK_SPEC.md` en entier.** Les sections « Decisions prises avec Wadii » et
   « Etat du depot » s'appliquent a tous les lots.

2. **Si `$1` vaut `status`** : n'ecris aucun code. Verifie `git remote -v`, l'existence des
   fichiers attendus par chaque lot (`docker-compose.demo.yml`, `docs/SERVEUR-DE-TEST.md`, profil `laptop`, `release.yml`, `docs/`), et rends un
   tableau : par lot, ce qui est fait, ce qui reste, la prochaine action. Arrete-toi la.

3. **Sinon, livre le lot `$1` et lui seul**, dans l'ordre lot1 → lot1bis → lot2 → lot3 → lot4. Si le lot
   demande depend d'un lot non livre, dis-le et propose de commencer par celui-la.

## Points d'arret obligatoires

Tu **t'arretes et attends la confirmation de Wadii** avant :

- **tout `git push`** vers GitHub au Lot 1 — le rapport d'hygiene (secrets, donnees du client,
  fichiers internes `CLAUDE_CODE_PROMPT*.md`, licence) doit avoir ete presente et valide ;
- **toute reecriture d'historique** (`git filter-repo`, `git push --force`) ;
- **la creation du depot GitHub** : confirme le nom `spi-ghomrassen` et le compte affiche par
  `gh auth status` ;
- **le premier tag `v*`** au Lot 4 : il declenche une Release publique ;
- **tout deploiement sur le VPS de test** au Lot 1 bis : tu prepares fichiers et guide, c'est
  Wadii qui execute les commandes sur la machine — tu n'as ni acces SSH ni identifiants.

## Regles de travail

- **Le backend est organise par fonctionnalite** (`advance/`, `attachment/`, `document/`,
  `shared/`…) depuis le commit `fbdc33a`. Ne recree pas de paquets `service/impl/` ou
  `controller/`. Si `CLAUDE.md` ou `TEST_HARDENING_SPEC.md` citent d'anciens chemins, mets-les
  a jour dans le rapport final.
- **`server.address=127.0.0.1` dans le profil `laptop` n'est pas negociable** : l'application
  n'a pas d'authentification (SEC-01).
- **Aucune donnee dans `Program Files`.** Base, pieces jointes, sauvegardes et logs vont sous
  `%LOCALAPPDATA%\SPI-Ghomrassen`.
- **Tu ne modifies jamais une migration deja livree** (`V1` a `V12`).
- **Le build Angular ne va pas dans `src/main/resources`** : il est copie dans
  `target/classes/static` par Maven au moment du build, jamais versionne.
- **Un recu ne doit jamais echouer a cause d'une piece jointe** : fichier absent → ligne
  « introuvable » + `WARN`, et le recu sort.
- **Argent** : `BigDecimal` uniquement ; `isEqualByComparingTo`, jamais `isEqualTo`.
- **`@DisplayName` en anglais**, chaines utilisateur en francais accentue via
  `messages_fr.properties` — sauf les libelles des PDF, qui sont deja des litteraux dans
  `DocumentServiceImpl` : suis le style en place la.
- **Tu ne neutralises pas un test rouge.** Tu le signales.
- **Les guides de `docs/` sont en francais.** `INSTALLATION-WINDOWS.md` ne contient ni
  « JVM », ni « port », ni « H2 », ni « profil ».

## Avant de dire que c'est fini

```bash
cd backend && mvn -q verify          # vert, sans Node installe
cd frontend && npx ng build          # vert
```

Lot 3 en plus : `mvn -q package -Pembed-frontend` puis
`java -jar target/*.jar --spring.profiles.active=laptop` sur un `APP_DATA` vide → le tableau
de bord repond sur `http://localhost:8080`, `backups/` contient une paire de zips.

Lot 1 bis en plus : `docker compose -f docker-compose.yml -f docker-compose.demo.yml config`
valide, le test PostgreSQL du semis `demo` passe, le guide est relu.

Lot 4 en plus : un tag `v0.9.0-rc1` pousse produit une Release avec un `.msi`.

Puis la liste « Criteres d'acceptation » du lot, point par point. Un critere manquant = lot non
livre : dis lequel et pourquoi.

## Ce que tu rends a la fin

Un compte rendu court, en francais :

1. Fichiers crees et modifies.
2. Ce qui a ete presente a Wadii aux points d'arret, et sa reponse.
3. **Ce que tu n'as pas pu verifier toi-meme** (installation du `.msi` sous Windows, ouverture
   du navigateur) et qui attend un test manuel.
4. Ce qui reste du lot, s'il reste quelque chose.
5. Les lignes de `CLAUDE.md` a mettre a jour.
