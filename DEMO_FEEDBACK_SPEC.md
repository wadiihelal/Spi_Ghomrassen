# Spec — Retours de la démonstration client (15/09/2026)

> Document de travail destiné à Claude Code, exécuté par la commande `/demo-feedback`.
> Cinq lots, **dans l'ordre** (le Lot 1 bis date du 15/09/2026). `mvn -q verify` et
> `npx ng build` restent verts à la fin de chacun. Les conventions de `CLAUDE.md` s'appliquent partout.

## Ce que le client a demandé

| # | Demande | Lot |
|---|---|---|
| 1 | Le **manuel d'utilisation PDF** doit contenir des captures d'écran de l'application | Lot 2 |
| 2 | Le code doit être publié sur le GitHub personnel de Wadii, avec un guide de déploiement | Lots 1 et 4 |
| 3 | L'application tourne **sur le portable du client** (Windows), sans serveur ni VPS | Lots 3 et 4 |

## Séquence de mise en service (corrigée le 15/09/2026)

La première version de cette spec traitait le serveur comme une hypothèse lointaine. La séquence
réelle chez le client est en deux étapes, avec le même code et deux profils Spring :

1. **Serveur de test** — un VPS Linux avec Docker, chez Wadii, qui fait tourner l'application
   avec le jeu de données **fictif** (`prod,demo`) pour une démonstration supplémentaire au
   client, depuis son navigateur, sans rien installer chez lui. C'est le **Lot 1 bis**.
2. **Portable du client** — installation sur son PC Windows, base **vide** (profil `laptop`,
   Lots 3 et 4). Le client acquiert le PC **après** avoir validé sur le serveur de test : cette
   étape n'est **pas urgente**.

Le profil `prod` n'est donc plus une cible hypothétique : il sert dès maintenant au serveur de
test.

## Décisions prises avec Wadii, à ne pas rouvrir

- **Dépôt GitHub public.** Conséquence : un passage d'hygiène est obligatoire avant le premier
  push (Lot 1).
- **Édition « poste isolé » : un seul exécutable, H2 en mode fichier, JRE embarqué.** Pas de
  Docker Desktop, pas de PostgreSQL à installer chez le client. L'application s'ouvre dans le
  navigateur par défaut. Le profil `prod` (PostgreSQL + docker-compose) est **conservé** tel
  quel : il fait tourner le serveur de test (Lot 1 bis) et resterait la cible si le client
  passait un jour sur un serveur.
- **Le portable du client est sous Windows.** L'installateur est un `.msi` produit par
  `jpackage`. Wadii travaille sur macOS : `jpackage` ne produit un installateur que pour l'OS
  qui l'exécute, donc **le `.msi` est construit par GitHub Actions sur `windows-latest`**,
  jamais sur le Mac.
- **Le manuel est généré, jamais édité à la main.** Sa source vit dans `docs/manuel/` ; les
  captures d'écran viennent du jeu `demo` et sont rejouées par script, pour que le manuel suive
  l'interface au lieu de la décrire de mémoire (Lot 2).

## État du dépôt au moment d'écrire cette spec

- Backend réorganisé **par fonctionnalité** (`advance/`, `attachment/`, `document/`,
  `shared/`…) depuis le commit `fbdc33a`. `CLAUDE.md` est à jour ; `TEST_HARDENING_SPEC.md`
  cite encore d'anciens chemins `service/impl/…` mais ses cinq lots sont livrés — ne pas le
  reprendre. Cette spec utilise les nouveaux chemins.
- `origin` pointe sur `github.com/wadiihelal/Spi_Ghomrassen` depuis le 15/09/2026 (Lot 1). Le nom
  `spi-ghomrassen` et l'usage de `gh` prévus au §1.2 sont périmés : `gh` n'est pas installé sur le Mac.
- `.github/workflows/ci.yml` existe déjà (Maven + Angular sur `ubuntu-latest`).
- `h2` est en portée `test` dans `backend/pom.xml`.
- `docs/Manuel-utilisation-SPI-Ghomrassen.pdf` (45 pages, producteur `pypdf`, aucune image) n'a
  **pas de source dans le dépôt** : ni script, ni Markdown, ni HTML. Voir Lot 2 §2.1.
- `frontend/src/environments/environment.prod.ts` pointe déjà sur `apiUrl: '/api'`.
- `frontend/nginx.conf` contient la règle SPA (`try_files … /index.html`) et le proxy `/api`
  qu'il faudra reproduire côté Spring Boot.
- `CLAUDE_CODE_PROMPT.md` et sa copie ont été **retirés de tout l'historique** (`git filter-repo`)
  avant le premier push, avec l'adresse professionnelle des commits (Lot 1, 15/09/2026).

---

# Lot 1 — Dépôt GitHub public

Objectif : le code est sauvegardé hors du Mac, et rien de confidentiel n'est publié.

> **État au 15/09/2026.** Rapport d'hygiène présenté et validé ; historique réécrit (prompts
> internes et adresse professionnelle retirés) ; poussé sur `wadiihelal/Spi_Ghomrassen` ;
> licence MIT ; manuel dans `docs/`. Le premier run de la CI a révélé que `npm ci` refusait le
> conflit de peer dependency PrimeNG 17 / Angular 19 sur toute machine neuve — réglé par
> `frontend/.npmrc` (`legacy-peer-deps=true`), dette nommée dans `CLAUDE.md`.

## 1.1 — Hygiène avant publication (bloquant)

Produire un **rapport, sans rien pousser**, puis attendre la confirmation de Wadii :

1. **Secrets.** Parcourir tout l'historique, pas seulement HEAD :
   `git log -p --all | grep -iE "password|secret|token|api[_-]?key|BEGIN (RSA|OPENSSH)"`.
   Les identifiants `spi`/`spi` de `docker-compose.yml` et `application-dev.properties` sont
   des valeurs de développement local : les signaler, ne pas les considérer comme des fuites.
2. **Données du client.** `app.company.*` dans `application.properties` contient le nom et
   l'adresse réels de la société. `tax-id`, `phone` et `email` sont vides aujourd'hui — vérifier
   qu'ils le sont aussi dans tout l'historique. Lister ce qui deviendra public.
3. **Fichiers internes.** `CLAUDE_CODE_PROMPT.md` et `CLAUDE_CODE_PROMPT copy.md` : lire leur
   contenu, dire ce qu'ils contiennent, et proposer soit de les retirer du dépôt
   (`git rm --cached`), soit de les garder. Le fichier `Manuel-utilisation-SPI-Ghomrassen.pdf`
   n'est pas suivi : proposer de le laisser hors du dépôt ou de le ranger dans `docs/`.
4. **Données de démonstration.** `DemoDataInitializer` charge des acquéreurs fictifs. Vérifier
   qu'aucun nom, CIN ou téléphone réel ne s'y est glissé.
5. **`.gitignore`.** Vérifier que `frontend/dist/`, `node_modules/`, `.idea/`, `.cursorj/`,
   `backend/target/` et `*.iml` sont bien ignorés — c'est déjà le cas, le confirmer.
6. **Licence.** Un dépôt public sans fichier `LICENSE` reste « tous droits réservés », mais
   les visiteurs ne le savent pas. Proposer un `LICENSE` explicite (« Tous droits réservés —
   usage interne SPI Ghomrassen ») ou une licence ouverte ; **Wadii tranche**.

Si l'historique contient un secret réel, **s'arrêter** et proposer une réécriture
(`git filter-repo`) avant tout push. Ne jamais pousser puis nettoyer.

## 1.2 — Création et premier push

Une fois la confirmation obtenue :

```bash
gh auth status                       # le compte authentifié est le compte cible
gh repo create spi-ghomrassen --public --source=. --remote=origin --push
```

Nom du dépôt : `spi-ghomrassen`. Branche `main`. *Réalisé autrement : `gh` absent du Mac, dépôt
`wadiihelal/Spi_Ghomrassen` créé par Wadii, `git remote add origin` puis `git push --force` sur le
stub initial de GitHub.* Vérifier ensuite que le workflow `CI` se
déclenche et passe sur GitHub (`gh run watch`). S'il échoue, corriger avant de continuer.

## 1.3 — README public

Compléter `README.md` à la racine pour un lecteur qui découvre le projet : ce que fait
l'application, la pile technique, les deux modes de déploiement (poste isolé / serveur),
comment lancer en local, et un renvoi vers `docs/`. En français.

## Critères d'acceptation Lot 1

- [x] Le rapport d'hygiène a été présenté et Wadii a confirmé avant le push.
- [x] `git remote -v` montre `origin` sur `github.com/wadiihelal/Spi_Ghomrassen`.
- [x] Le workflow `CI` est vert sur GitHub pour le commit poussé (run `34978124952` sur `03d38a0`,
      après le correctif `frontend/.npmrc`).
- [x] La question de la licence a une réponse (MIT), et le fichier correspondant est en place.

---

# Lot 1 bis — Serveur de test avec données de démonstration

Ajouté le 15/09/2026, voir « Séquence de mise en service ». Objectif : le client manipule
l'application, remplie du jeu `demo`, depuis son navigateur, sur un VPS Linux de Wadii — sans
rien installer chez lui. Ce n'est **pas** la production : les acquéreurs sont fictifs, et le
serveur se remet à zéro d'une commande.

## 1b.1 — Profils `prod,demo` sans toucher au compose de production

> **Trois pièges rencontrés au premier déploiement, tous corrigés.** Le fichier `htpasswd` créé en
> `600 root:root` était illisible par les ouvriers nginx, qui tournent sous l'utilisateur `nginx` :
> **500 sur toutes les pages**, sans que le mot de passe soit même vérifié (`chmod 644` ; le fichier
> ne porte qu'un hachage salé). Et surtout : `frontend/Dockerfile`
> copiait `package.json` et `package-lock.json` puis lançait `npm ci` : `.npmrc` — qui porte
> `legacy-peer-deps=true` — n'arrivait qu'après, donc l'image échouait là où la CI et le poste
> passaient. D'où le job CI « Images Docker », qui construit ce qui part réellement. Et le VPS
> étant partagé, le port 80 était déjà pris : voir `ports: !override` plus bas.

- `docker-compose.yml` fixe `SPRING_PROFILES_ACTIVE: prod` et reste tel quel. Ajouter un
  fichier d'*override* `docker-compose.demo.yml` qui passe `prod,demo`, monte la configuration
  nginx de démonstration (1b.2) et rien d'autre. Commande :
  `docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build`.
- `DemoDataInitializer` (`@Profile("demo")`) ne dépend pas de H2 et ne ressème pas si des
  clients, fournisseurs ou projets existent déjà. Prouver qu'il passe sur PostgreSQL 16 : un
  test `@Tag("postgres")` dans `postgres/` qui démarre le contexte en `demo` et compte les
  résidences « Démo » — la suite H2 (`DemoProfileSeedTest`) ne couvre pas le dialecte.
- Les variables obligatoires de `prod` (`DATABASE_URL`, `DB_USER`, `DB_PASSWORD`,
  `APP_CORS_ALLOWED_ORIGINS`, `ATTACHMENTS_ROOT`) restent fixées par le compose ; `.env` **non
  versionné** ne porte que `DB_PASSWORD` et `DEMO_HTTP_PORT` : fournir `.env.example` et ajouter
  `.env` au `.gitignore` (`htpasswd` y reste, au cas où le mot de passe serait rétabli).

## 1b.2 — Protection minimale (SEC-01 sur un serveur exposé)

Sans authentification, tout `DELETE` est ouvert : un serveur joignable depuis Internet se fait
vandaliser par le premier scanner venu, même avec des données fictives.

- `frontend/nginx.demo.conf` : copie de `nginx.conf`, montée par le compose de démo à la place de
  la configuration par défaut. **Accès libre, décidé le 15/09/2026** : le mot de passe partagé
  initialement prévu a été retiré à la demande de Wadii — une fenêtre d'identification avant
  d'entrer dans l'application dénature la démonstration. Les deux lignes `auth_basic` restent en
  commentaire, prêtes à rétablir. Contrepartie assumée : les données étant fictives et la remise
  à zéro immédiate, la démonstration **s'arrête entre deux séances** (`down`), ce que le guide dit.
- **Le VPS est partagé** (constaté au déploiement) : Apache y sert déjà Akaunting sur le port 80,
  Odoo répond sur 8069, Dockge sur 5001, `ufw` est inactif. Conséquences : pas de `ufw enable` à
  l'aveugle (il couperait ces services) ; la démonstration est publiée sur `DEMO_HTTP_PORT` (8088)
  grâce à `ports: !override` dans l'override, qui **remplace** la liste du compose de base au lieu
  de s'y ajouter (Compose ≥ 2.24) ; PostgreSQL n'est **pas publié du tout** sur l'hôte en démo
  (`ports: !override []`). Le compose de base, lui, le lie à `127.0.0.1:5432` pour le poste de
  développement au lieu de toutes les interfaces, et lit `DB_PASSWORD` dans `.env`.
- TLS non exigé pour une démonstration à données fictives ; le guide indique comment l'ajouter
  (Caddy devant nginx, ou certbot) si l'URL doit circuler plus largement.

## 1b.3 — Guide `docs/SERVEUR-DE-TEST.md`

Pour Wadii, pas à pas : Docker sur le VPS, clonage du dépôt, `.env`, premier
lancement, vérifications (`http://<ip>/` demande un identifiant ; `/api/projects` répond 401
sans, 200 avec), **mise à jour** après un push (`git pull` puis la commande compose avec
`--build`), **remise à zéro** du jeu de démonstration (`down -v` puis relance : `demo` ressème
sur base vide), lecture des logs (`docker compose logs -f backend`).

## Critères d'acceptation Lot 1 bis

- [x] `mvn -q verify` et `npx ng build` verts ; la CI construit les images Docker déployées et
      rejoue le déploiement complet. **Le tableau de bord a été ouvert sur le serveur** le
      15/09/2026 : `http://102.204.206.105:8088/` affiche directement le périmètre « Résidence Oasis
      Ghomrassen », ses encaissements, son avancement et son stock de lots — sans écran d'accueil.
- [x] `curl /api/projects` répond **200 sans rien demander**, l'interface et une route SPA aussi —
      prouvé sur machine vierge par le job CI « Démonstration de bout en bout », qui exécute les
      commandes du guide telles quelles. **Leçon conservée** : tant que le mot de passe existait, un
      `401` ne prouvait rien (nginx l'émet sans ouvrir `htpasswd` ni joindre le backend) — d'où la
      règle générale, n'attendre que le signal qui traverse toute la pile.
- [x] Ni 5432 ni 8080 ne sont joignables depuis l'extérieur du VPS — vérifié le 15/09/2026 depuis le
      Mac, et confirmé par `docker compose ps` : seul `frontend` publie (`0.0.0.0:8088->80/tcp`),
      `postgres` et `backend` n'exposent qu'au réseau Docker.
- [x] Le test PostgreSQL du semis `demo` passe avec `-Ppostgres` — `PostgresDemoSeedTest`, 2 tests, dans le
      job CI « Backend (PostgreSQL) » (run `34981710492`, 5 classes / 19 tests ; l'étape de contrôle
      échoue si moins de 5 classes ou 8 tests ont tourné).
- [x] `docs/SERVEUR-DE-TEST.md` existe et Wadii l'a suivi pour le premier déploiement, le
      15/09/2026 ; trois défauts rencontrés en chemin ont été corrigés à la source et sont désormais
      couverts par la CI. Claude Code n'a jamais eu d'accès au VPS : toutes les commandes ont été
      exécutées par Wadii.

---

# Lot 2 — Manuel d'utilisation illustré

## Contexte

- `docs/Manuel-utilisation-SPI-Ghomrassen.pdf` : 45 pages A4, 16 chapitres et 4 annexes,
  « expliqué écran par écran » — et **aucune image**. Le client veut voir les écrans dont le
  manuel parle.
- Producteur `pypdf`. Polices embarquées : **Bitter** (titres), **Source Sans 3** (texte),
  **JetBrains Mono** (références). Maquette : couverture bleu-vert foncé, bandeau de chapitre,
  encadrés `À RETENIR` / `ATTENTION` / `ASTUCE`, étapes numérotées à pastille, pied de page
  `SP Immobilière GHOMRASSEN — Manuel d'utilisation   n / N`.
- **Le script qui a produit ce PDF n'est pas dans le dépôt.** Aucun `.py`, `.md` ou `.html`
  ne le mentionne. Sans source, on ne peut ni insérer une image ni corriger une phrase.
- Défaut visible à corriger au passage : les étapes dont la description est vide affichent un
  « . » orphelin sur la ligne suivante (p. 25, étapes 1, 5, 6 et 8).
- Données pour les captures : **uniquement le jeu `demo`** (acquéreurs fictifs). En local :
  `docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build` puis
  `http://localhost` ; ou le serveur de test du Lot 1 bis. Jamais des données réelles.
- Le job `demo` de `.github/workflows/ci.yml` démarre déjà cette pile et attend que les
  résidences soient semées : les captures peuvent y être rejouées.

## 2.1 — Retrouver ou reconstruire la source (point d'arrêt)

**Demander à Wadii** s'il a encore le script et les fichiers qui ont produit le PDF (autre
dossier, autre session, autre outil). Deux issues :

- **Il les a** : les ranger dans `docs/manuel/`, les versionner, puis passer au §2.2 en
  conservant la chaîne existante si elle sait insérer des images ; sinon la remplacer.
- **Il ne les a pas** : reconstruire la source depuis le PDF.
  1. `pdftotext -layout docs/Manuel-utilisation-SPI-Ghomrassen.pdf` → un fichier par chapitre
     dans `docs/manuel/chapitres/NN-titre.md` (`01-prendre-en-main.md` … `16-que-faire-si.md`,
     `A-memo.md` … `D-glossaire.md`). Couverture et page « Comment lire ce manuel » dans
     `00-couverture.md`.
  2. Markdown avec trois extensions, et pas davantage : encadrés `:::retenir` /
     `:::attention` / `:::astuce`, chemin de menu `{menu}Travail quotidien › Paiements clients`,
     référence en `code`. Texte de bouton ou de champ en **gras** — c'est la convention du
     manuel, elle sert au contrôle du §2.6.
  3. Vérifier la parité : `check_parity.py` compare le texte du PDF d'origine et celui du PDF
     reconstruit, normalisés (espaces, césures, numéros de page). **Aucun paragraphe perdu.**

Dans les deux cas, le PDF actuel reste dans l'historique git ; pas de copie d'archive.

## 2.2 — Chaîne de fabrication (`docs/manuel/`)

```
docs/manuel/
├── README.md            comment régénérer captures et PDF, règle « demo uniquement »
├── requirements.txt     weasyprint, markdown-it-py, pypdf, playwright, pillow, pyyaml
├── build.py             Markdown → HTML → PDF, échoue si une image manque
├── captures.py          Playwright : rejoue captures.yaml contre une instance demo
├── captures.yaml        la liste déclarative des figures (§2.4)
├── check_parity.py      texte ancien PDF ≡ texte nouveau PDF
├── check_labels.py      chaque **libellé** du manuel existe dans frontend/src
├── template.html + style.css
├── fonts/               Bitter, Source Sans 3, JetBrains Mono (licence OFL, versionnables)
├── chapitres/           la source
└── captures/            les PNG produits, versionnés
```

- **WeasyPrint** pour le PDF, pas Chromium : le sommaire a besoin de `target-counter()` pour
  les numéros de page, le pied de page de `counter(page) / counter(pages)`, la couverture d'une
  page nommée. Chromium (`page.pdf()`) ne sait faire aucun des trois proprement.
- **pypdf** pose les métadonnées, identiques à aujourd'hui (`Title`, `Subject`, `Author`) ;
  la couverture passe en **Version 1.1**, édition du mois de génération.
- `python build.py` écrit `docs/Manuel-utilisation-SPI-Ghomrassen.pdf` **au même chemin** :
  les liens existants restent valables. Le build **échoue** — pas un avertissement — si une
  image référencée manque ou si une capture listée dans `captures.yaml` n'a pas de fichier.
- Une figure = image + légende numérotée « Figure 9.2 — La fenêtre Nouvel acompte ».
  Largeur maximale = largeur du texte ; jamais d'agrandissement au-delà de la taille native.
- Polices : Bitter, Source Sans 3 et JetBrains Mono sont sous licence OFL, elles vont dans le
  dépôt public sans problème. Vérifier que WeasyPrint les charge via `@font-face`, pas depuis
  le système : la CI n'a pas les mêmes polices que le Mac.

## 2.3 — Captures d'écran automatisées

`captures.py` lit `captures.yaml` et rejoue chaque entrée avec Playwright (Chromium) :

```yaml
- id: 09-nouvel-acompte
  chapitre: 9
  route: /advances
  actions:
    - click: { role: button, name: "Nouvel acompte" }
    - wait: { role: dialog }
  cadrage: dialog                # page | dialog | selector
  pastilles:                     # dans l'ordre des étapes du §9.1
    - { role: combobox, name: "Appartement" }
    - { role: textbox,  name: "Référence" }
    - { role: textbox,  name: "Date acompte" }
    - { role: spinbutton, name: "Montant" }
    - { role: combobox, name: "Mode de paiement" }
  legende: "La fenêtre Nouvel acompte, avec le bloc de simulation"
```

Règles :

- Viewport **1440 × 900**, `device_scale_factor=2`, `locale="fr-FR"`,
  `timezone_id="Africa/Tunis"`, `color_scheme="light"`, `reduced_motion="reduce"`. Attendre la
  fin des requêtes réseau (`wait_for_load_state("networkidle")`) et le texte attendu, jamais un
  `sleep`.
- **Les éléments sont trouvés par leur libellé français exact**
  (`get_by_role("button", name="Nouvel acompte")`), les mêmes textes que le manuel cite en gras.
  Un libellé qui change dans l'interface casse la capture : c'est voulu — le manuel ne peut plus
  se désynchroniser silencieusement de l'écran. Pas de `data-testid` à ajouter au frontend.
- **Pastilles** : avant la capture, injecter dans le DOM un badge par champ cité (cercle de la
  couleur des titres, chiffre blanc, même style que les étapes du manuel), positionné en haut à
  gauche de l'élément. Le lecteur relie l'étape 3 du texte à la pastille 3 de l'image.
- **Cadrage** : `page` → plein écran ; `dialog` → `locator.screenshot()` du dialogue PrimeNG
  avec 24 px de marge ; `selector` → un composant précis (bloc de simulation, panneau des
  pièces jointes, sélecteur de projet).
- **Documents PDF de l'application** (reçu, situation client, récapitulatif TVA) : appeler
  l'API, puis `pdftoppm -r 150 -png -f 1 -l 1` sur la réponse → la première page devient une
  figure. Ce sont les seules captures qui ne passent pas par le navigateur.
- **Poids** : PNG optimisés par Pillow (`optimize=True`, palette quand l'image le permet).
  Cible ≤ 400 Ko par capture, **PDF final ≤ 10 Mo**.
- `python captures.py --base-url http://localhost` régénère tout ; `--only "09-*"` un
  sous-ensemble. `--base-url` est obligatoire : pas de valeur par défaut silencieuse.
- Les captures sont **versionnées** dans `docs/manuel/captures/` : `build.py` fonctionne sans
  application qui tourne. On les régénère quand l'interface change, pas à chaque build.

## 2.4 — Liste des figures

Trente-quatre captures, au moins une par chapitre. Les libellés viennent des gabarits Angular
(`frontend/src/app/features/**/*.html`) ; Claude Code les confirme un par un avant d'écrire
`captures.yaml`, et signale toute étape du manuel dont le libellé n'existe plus.

| Ch. | Id | Route | Cadrage | Contenu |
|---|---|---|---|---|
| 1 | `01-tableau-de-bord` | `/dashboard` | page | L'écran d'accueil complet |
| 1 | `01-menu` | `/dashboard` | selector | Le menu de gauche, toutes les entrées visibles |
| 1 | `01-selecteur-projet` | `/dashboard` | selector | Le sélecteur de projet en scope |
| 3 | `03-projets-liste` | `/projects` | page | La liste |
| 3 | `03-nouveau-projet` | `/projects` | dialog | **Nouveau projet**, pastilles sur les champs |
| 3 | `03-projet-detail` | `/projects/:id` | page | La fiche d'un projet demo |
| 4 | `04-appartements-liste` | `/apartments` | page | Le stock |
| 4 | `04-generer-bloc` | `/apartments` | dialog | La génération d'un bloc entier (§4.2) |
| 5 | `05-plan-de-vente` | `/sales-board` | page | Le plan par bloc et étage |
| 5 | `05-etat-commercial` | `/sales-board` | selector | Le menu des quatre états (§5.2) |
| 6 | `06-clients-liste` | `/clients` | page | La liste |
| 6 | `06-nouveau-client` | `/clients` | dialog | **Nouveau client** |
| 7 | `07-ventes-liste` | `/purchases` | page | La liste |
| 7 | `07-nouvelle-vente` | `/purchases` | dialog | **Nouvelle vente**, pastilles (§7.2) |
| 7 | `07-plafond` | `/purchases` | dialog | La règle du plafond visible (§7.5) |
| 8 | `08-echeanciers` | `/schedules` | page | L'écran Échéancier (§8.5) |
| 8 | `08-plan-de-paiement` | `/purchases` | dialog | **Échéancier de la vente** (§8.2) |
| 9 | `09-paiements-liste` | `/advances` | page | La liste, compteur **Virements** visible |
| 9 | `09-nouvel-acompte` | `/advances` | dialog | **Nouvel acompte**, pastilles 1 à 8 (§9.1) |
| 9 | `09-simulation` | `/advances` | selector | Le bloc de simulation, six cases (§9.3) |
| 9 | `09-plafond-bloquant` | `/advances` | dialog | Cases rouges, **Enregistrer** inactif (§9.4) |
| 9 | `09-recu` | API | pdf | Première page d'un reçu demo (§9.5) |
| 10 | `10-fournisseurs-liste` | `/suppliers` | page | La liste |
| 10 | `10-nouveau-fournisseur` | `/suppliers` | dialog | **Nouveau fournisseur** |
| 11 | `11-factures-liste` | `/supplier-invoices` | page | La liste, colonne **Retards** |
| 11 | `11-nouvelle-facture` | `/supplier-invoices` | dialog | **Nouvelle facture** |
| 11 | `11-reglements` | `/supplier-invoices` | dialog | **Règlements de la facture** (§11.3) |
| 12 | `12-nouvelle-depense` | `/expenses` | dialog | **Nouvelle dépense**, TVA calculée |
| 13 | `13-pieces-jointes` | `/expenses` | selector | Le panneau avec un fichier attaché |
| 14 | `14-rapports` | `/reports` | page | L'écran et ses boutons **PDF** |
| 14 | `14-situation-client` | API | pdf | Première page d'une situation (§14.5) |
| 15 | `15-recherche` | `/dashboard` | selector | La recherche globale avec résultats |
| 15 | `15-journal` | `/audit` | page | Le journal des opérations |
| 16 | `16-message-plafond` | `/advances` | dialog | Un message bloquant tel que le chapitre le décrit |

Le chapitre 2 (« L'ordre de travail ») et les annexes n'ont pas de capture : ce sont des
textes de méthode. Le chapitre 16 réutilise `09-plafond-bloquant` si aucun autre message n'est
reproductible sur le jeu `demo`.

## 2.5 — Intégration continue (souhaitable, pas bloquant)

Dans `ci.yml`, un job `manuel` après le job `demo`, qui a déjà la pile en marche :
`pip install -r docs/manuel/requirements.txt && playwright install chromium`, puis
`captures.py --base-url http://localhost`, puis `build.py`, et le PDF en artefact du run.
Le job **ne compare pas les pixels** (le rendu varie d'une machine à l'autre) : il échoue
seulement si une capture manque ou si `check_labels.py` trouve un libellé disparu.

## 2.6 — Contrôles

- `check_parity.py` : texte du PDF 1.0 ⊆ texte du PDF 1.1 (normalisés). Seuls les ajouts sont
  admis : légendes de figures, numéro de version, date d'édition.
- `check_labels.py` : chaque **libellé en gras** des chapitres existe dans
  `frontend/src/app/**/*.html` (ou dans `messages_fr.properties` pour les messages du ch. 16).
  Première passe en avertissement ; le rapport final liste les écarts.
- `build.py` depuis un clone propre, sans application qui tourne : PDF produit, 45 à 60 pages,
  chaque chapitre 1 et 3 à 16 a au moins une figure, poids ≤ 10 Mo, métadonnées conservées,
  couverture en version 1.1.
- `captures.py` contre la pile `demo` locale : les 34 fichiers listés existent, aucun n'est
  vide, aucun ne dépasse 400 Ko après optimisation.
- Le « . » orphelin des étapes a disparu (p. 25 et partout ailleurs — `grep` du rendu texte).
- **Relecture par Wadii** de cinq pages : couverture, sommaire, §9.1 avec pastilles, une figure
  de document PDF, glossaire. Claude Code ne peut pas juger la lisibilité d'une capture.

## Documentation

- `docs/manuel/README.md` : les deux commandes, la règle « demo uniquement », quand régénérer
  les captures (à chaque changement d'écran), comment ajouter une figure.
- `CLAUDE.md`, « Où sont les choses » : `Manuel d'utilisation | docs/manuel/ (source
  Markdown, captures.yaml, build.py) — le PDF dans docs/ est un artefact, jamais édité à la main`.
- `CLAUDE.md`, « Décisions » : *Le manuel est généré depuis `docs/manuel/` (15/09/2026). Les
  captures viennent du jeu `demo` et sont trouvées par les libellés que le manuel cite — un
  libellé qui change casse le build du manuel, c'est le but.*

## Critères d'acceptation Lot 2

- [ ] La question de la source (§2.1) a été posée à Wadii et tranchée avant toute reconstruction.
- [ ] `docs/manuel/` existe avec les dix fichiers du §2.2 ; `build.py` régénère le PDF au même
      chemin depuis un clone propre.
- [ ] Les 34 captures du §2.4 sont dans `docs/manuel/captures/`, produites par `captures.py`,
      pas à la main.
- [ ] `check_parity.py` passe : rien du texte 1.0 n'a été perdu.
- [ ] Le « . » orphelin a disparu.
- [ ] PDF ≤ 10 Mo, version 1.1, métadonnées identiques.
- [ ] Wadii a relu les cinq pages du §2.6.

---

# Lot 3 — Édition « poste isolé » (profil `laptop`)

Objectif : `java -jar app.jar --spring.profiles.active=laptop` sur un poste vierge démarre
l'application complète — API **et** interface — sans Docker, PostgreSQL, nginx ni Node.

## 3.1 — Frontend embarqué dans le jar

- Construire l'Angular en `--configuration production` et copier
  `frontend/dist/real-estate-frontend/browser/**` dans `backend/target/classes/static/` **au
  moment du build Maven**, via `frontend-maven-plugin` (`com.github.eirslett`) dans un profil
  Maven `embed-frontend` — actif par défaut dans la CI et la release, désactivable en local
  (`-P !embed-frontend`) pour que `mvn -q verify` ne demande pas Node.
- Ne **pas** copier le build dans `src/main/resources` : il resterait dans le dépôt.
- Reproduire la règle SPA de `nginx.conf` : une classe `shared/SpaForwardingConfig` (ou nom
  équivalent, dans le paquet `config/`) qui renvoie `index.html` pour toute route **GET** sans
  extension, hors `/api/**`, `/swagger-ui/**`, `/v3/api-docs/**`. Les fichiers statiques gardent
  leur `Cache-Control` long ; `index.html` doit être servi avec `no-cache`.
- `environment.prod.ts` pointe déjà sur `/api` : aucune modification du frontend.

## 3.2 — Profil Spring `laptop`

Nouveau fichier `backend/src/main/resources/application-laptop.properties` :

```properties
# Poste isole (15/09/2026) : un seul processus, H2 en mode fichier, pas de reseau.
# APP_DATA est fixe par le lanceur ; par defaut %LOCALAPPDATA%\SPI-Ghomrassen sous Windows.
app.data.root=${APP_DATA:${LOCALAPPDATA:${user.home}}/SPI-Ghomrassen}
spring.datasource.url=jdbc:h2:file:${app.data.root}/db/spi-ghomrassen;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
app.storage.root=${app.data.root}/attachments
# SEC-01 : pas d'authentification. Sur un portable, l'API n'ecoute que la boucle locale.
server.address=127.0.0.1
server.port=8080
app.cors.allowed-origins=http://localhost:8080,http://127.0.0.1:8080
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

Points d'attention :

- **`h2` passe de la portée `test` à la portée par défaut** dans `pom.xml`, sinon le driver
  n'est pas dans le jar. Les tests continuent de fonctionner à l'identique.
- Les 12 migrations sont déjà exécutées sur H2 par toute la suite de tests : le schéma est
  connu bon. Vérifier tout de même un démarrage à froid sur un répertoire vide.
- **`server.address=127.0.0.1` n'est pas négociable** : sans authentification, l'application
  ne doit être joignable que depuis le poste lui-même. Le documenter dans `backend/README.md`.
- Les chemins Windows contiennent des espaces (`C:\Users\Prénom Nom\AppData\…`) : tester
  l'URL H2 et `app.storage.root` avec un chemin contenant un espace et un accent.

## 3.3 — Lanceur

Classe `shared/LaptopLauncher` (ou dans `config/`), active uniquement sous le profil `laptop`
(`@Profile("laptop")`) :

1. **Avant** le démarrage de Spring (dans `main`, si le profil `laptop` est demandé) : tester si
   le port 8080 est déjà pris sur `127.0.0.1`. Si oui, l'application tourne déjà → ouvrir le
   navigateur sur `http://localhost:8080` et **quitter** sans démarrer un second processus (H2
   en mode fichier refuserait de toute façon le second verrou).
2. Sur `ApplicationReadyEvent` : ouvrir le navigateur par défaut. `java.awt.Desktop` n'existe
   pas dans un runtime `jlink` minimal : utiliser `ProcessBuilder` —
   `rundll32 url.dll,FileProtocolHandler http://localhost:8080` sous Windows, `open` sous macOS,
   `xdg-open` sous Linux. Ne jamais échouer si le navigateur ne s'ouvre pas : journaliser l'URL
   en `INFO`.
3. Journal : écrire dans `${app.data.root}/logs/spi-ghomrassen.log` avec rotation (Logback,
   fichier `logback-spring.xml` scoped au profil `laptop`), en plus de la console.

## 3.4 — Sauvegardes (la « lacune assumée » de `CLAUDE.md` cesse de l'être pour ce mode)

Sur un portable, perdre la machine = perdre les données. Composant `shared/LaptopBackupService`,
`@Profile("laptop")` :

- **Au démarrage** puis **toutes les 24 h** (`@Scheduled`, `@EnableScheduling` réservé au
  profil), écrire dans `${app.data.root}/backups/` :
  - `db-<yyyyMMdd-HHmmss>.zip` via la commande SQL H2 `BACKUP TO '<chemin>'` (sauvegarde en
    ligne, cohérente, sans arrêter l'application) ;
  - `attachments-<yyyyMMdd-HHmmss>.zip` : archive du dossier `attachments/`.
- **Rétention** : garder les 30 dernières paires, supprimer le reste.
- **Restauration** : documentée, pas automatisée. Arrêter l'application, dézipper `db-*.zip`
  dans `db/`, dézipper `attachments-*.zip` dans `attachments/`, redémarrer.
- Le dossier `backups/` est celui que le client copie sur une clé USB ou dans OneDrive : le
  guide client (Lot 4) le dit en une phrase.

## 3.5 — Tests

- `@SpringBootTest` sous profil `laptop` avec `app.data.root` pointé sur `@TempDir` :
  - `@DisplayName("a cold start on an empty folder creates the database and the attachment root")`
  - `@DisplayName("the API is reachable and answers JSON under /api")`
  - `@DisplayName("an unknown route without extension answers index.html")` — poser un
    `index.html` de fixture dans `src/test/resources/static/`.
  - `@DisplayName("a route under /api never falls back to index.html")` — `/api/nope` → 404 JSON.
  - `@DisplayName("a backup produces a database zip and an attachments zip")`
  - `@DisplayName("retention keeps the thirty most recent backups")` — créer 35 fichiers datés
    factices, exécuter la purge, en compter 30.
- Le test de port occupé du lanceur : ouvrir un `ServerSocket` sur un port libre, pointer le
  lanceur dessus, vérifier qu'il **n'appelle pas** `SpringApplication.run` (injecter une
  `Runnable` d'ouverture de navigateur factice).

## Documentation

- `backend/README.md` : section « Édition poste isolé » — profil, emplacement des données,
  port lié à la boucle locale, sauvegardes.
- `CLAUDE.md` : dans « Décisions métier à ne pas rouvrir », ajouter la décision du 15/09/2026
  (deux modes de production : `laptop` H2 fichier / `prod` PostgreSQL) ; remplacer la ligne
  « Sauvegardes : lacune assumée » par le renvoi vers `LaptopBackupService` pour le mode
  `laptop`, lacune maintenue pour le mode `prod`. Ajouter les nouvelles classes (`LaptopLauncher`,
  `LaptopBackupService`, config SPA) à la table « Où sont les choses ».

## Critères d'acceptation Lot 3

- [ ] `mvn -q verify` vert sans Node installé (`embed-frontend` inactif par défaut en local).
- [ ] `mvn -q package -Pembed-frontend` produit un jar ; `java -jar … --spring.profiles.active=laptop`
      sur un dossier vide ouvre le navigateur et affiche le tableau de bord.
- [ ] `curl http://<IP-LAN-du-Mac>:8080/api/projects` échoue (connexion refusée) ; la même URL
      en `localhost` répond.
- [ ] Deux lancements successifs n'ouvrent qu'un seul processus.
- [ ] `backups/` contient une paire de zips après le premier démarrage.

---

# Lot 4 — Installateur Windows et guide de déploiement

## 4.1 — `jpackage` sur GitHub Actions

Nouveau workflow `.github/workflows/release.yml`, déclenché par un tag `v*` :

1. `ubuntu-latest` : `npm ci` + `ng build --configuration production`, puis
   `mvn -B -Pembed-frontend -DskipTests package` — ou laisser `frontend-maven-plugin` faire
   les deux. Artefact : le jar.
2. `windows-latest` (WiX 3 est préinstallé sur cette image) :
   ```
   jpackage --type msi --input target --main-jar <jar> --name "SPI Ghomrassen"
            --app-version <version sans le v> --vendor "SPI Ghomrassen"
            --win-menu --win-shortcut --win-dir-chooser
            --java-options "-Dspring.profiles.active=laptop"
            --java-options "-Xmx512m"
            --icon <icone .ico>
            --dest dist
   ```
   Version : `jpackage` exige un numéro `x.y.z` — dériver du tag (`v1.0.0` → `1.0.0`).
   L'icône : générer un `.ico` depuis `frontend/public/favicon*` s'il en existe, sinon en créer
   un simple ; le versionner dans `backend/packaging/`.
3. Créer la **GitHub Release** du tag et y attacher le `.msi` (`softprops/action-gh-release`
   ou `gh release upload`).
4. Les tests **ne tournent pas** dans ce workflow : c'est le rôle de `ci.yml`, qui doit être
   vert sur le même commit. Le dire dans le workflow.

Vérifier en fin de lot en poussant un tag `v0.9.0-rc1` — la version `jpackage` devra être
`0.9.0` ; gérer le suffixe — et en téléchargeant le `.msi` produit.

## 4.2 — Comportement de l'installateur

- Programme dans `C:\Program Files\SPI Ghomrassen\` (non inscriptible : aucune donnée n'y va).
- Données dans `%LOCALAPPDATA%\SPI-Ghomrassen\` (`db/`, `attachments/`, `backups/`, `logs/`).
- Raccourci « SPI Ghomrassen » dans le menu Démarrer et sur le bureau.
- **Mise à jour** : installer le nouveau `.msi` par-dessus ; `--win-upgrade-uuid` fixe pour que
  Windows remplace au lieu de dupliquer. Les données ne bougent pas ; Flyway migre au premier
  démarrage. Le documenter.
- **Désinstallation** : retire le programme, **laisse les données** — le dire au client.

## 4.3 — Deux guides, dans `docs/`

**`docs/DEPLOIEMENT.md` — pour Wadii.** Comment publier une version :

```bash
git tag v1.0.0 && git push origin v1.0.0     # déclenche release.yml
gh release download v1.0.0 -p "*.msi"        # récupère l'installateur
```

Puis : apporter le `.msi` chez le client (clé USB ou lien vers la Release), l'installer, où
sont les données, comment restaurer une sauvegarde, comment mettre à jour, comment lire les
logs, et la liste de ce qu'il faut vérifier avant de tagger (CI verte, `CLAUDE.md` à jour,
numéro de version dans `pom.xml` cohérent avec le tag).

**`docs/INSTALLATION-WINDOWS.md` — pour le client.** Non technique, en français, une page :

1. Double-cliquer sur le fichier `.msi`, suivant, suivant, terminer.
2. Ouvrir « SPI Ghomrassen » depuis le menu Démarrer : le navigateur s'ouvre tout seul.
3. Si la fenêtre se ferme : rouvrir depuis le menu Démarrer, l'application est toujours là.
4. **Sauvegardes** : le dossier `%LOCALAPPDATA%\SPI-Ghomrassen\backups` — comment y aller
   (`Win + R`, coller le chemin) — et la consigne : le copier sur une clé USB **chaque semaine**.
5. Que faire si ça ne s'ouvre pas : attendre 30 secondes, réessayer, puis appeler Wadii en
   envoyant le fichier `logs\spi-ghomrassen.log`.

Pas de jargon : ni « JVM », ni « port », ni « H2 » dans ce document.

## Critères d'acceptation Lot 4

- [ ] Un tag `v*` poussé produit une Release GitHub avec un `.msi` téléchargeable.
- [ ] Le `.msi` installé sur une machine Windows vierge (ou une VM) ouvre le navigateur sur le
      tableau de bord ; Wadii le confirme, Claude Code ne peut pas le vérifier.
- [ ] Réinstaller une version supérieure conserve les données saisies.
- [ ] `docs/DEPLOIEMENT.md` et `docs/INSTALLATION-WINDOWS.md` existent et sont relus.
- [ ] `README.md` renvoie vers les deux guides.

---

# Livraison

## Ordre

```
Lot 1 (GitHub)  →  Lot 1 bis (serveur de test)  →  Lot 2 (reçu)  →  Lot 3 (profil laptop)  →  Lot 4 (installateur + guides)
```

Le Lot 1 en premier : le code est en sécurité hors du Mac avant qu'on n'y touche, et la CI
GitHub valide chaque lot suivant. Le Lot 1 bis suit immédiatement : c'est surtout de la
configuration, et c'est l'instrument de validation du client ; le Lot 2 s'y déploie ensuite par
simple mise à jour. Les Lots 3 et 4 ne sont **pas urgents** : le client acquiert le PC après
validation. Le Lot 4 dépend du Lot 3 (le jar à empaqueter) et du Lot 1 (le dépôt où publier la
Release).

## Commits

En français, un lot = un ou plusieurs commits, format du dépôt :

```
docs(manuel): le manuel d'utilisation est généré depuis docs/manuel et illustré

Trente-quatre captures d'écran rejouées par script sur le jeu demo, une par
écran décrit. Le PDF dans docs/ devient un artefact de build.py ; le texte de
la version 1.0 est conservé mot pour mot (check_parity.py).
```

## Rapport final

1. Fichiers créés et modifiés par lot.
2. Le rapport d'hygiène du Lot 1 et ce qui a été retiré ou conservé.
3. Les points que Claude Code **n'a pas pu vérifier** (installation réelle du `.msi`, ouverture
   du navigateur sous Windows) et qui attendent un test de Wadii.
4. Les lignes de `CLAUDE.md` et de `TEST_HARDENING_SPEC.md` mises à jour ou restant à mettre à
   jour après le passage par fonctionnalité.

## Hors périmètre

- Authentification : décision du 02/09/2026 maintenue. Le port lié à `127.0.0.1` en est la
  contrepartie sur poste isolé.
- Multi-postes ou accès depuis un second ordinateur : demanderait PostgreSQL et un serveur,
  c'est le profil `prod` existant.
- Sauvegarde vers le cloud (OneDrive, Drive) : le client copie `backups/` lui-même ; une
  synchronisation automatique est une évolution possible.
- **Embarquer le justificatif de virement dans le reçu PDF** : une première lecture de la demande
  du client, écartée le 15/09/2026 — il parlait du manuel. Reste une idée à lui proposer.
- Une version du manuel **en arabe**, ou une version courte « prise en main » de 4 pages : la
  chaîne de `docs/manuel/` le permettrait, ce n'est pas demandé.
