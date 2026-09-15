# Serveur de test — déployer la démonstration

Pour Wadii. Un VPS Linux avec Docker, joignable par son adresse IP, qui fait tourner **l'interface
et l'API** avec le jeu de données **fictif** (`demo`). Le client ouvre l'adresse et se trouve
directement dans l'application — aucun mot de passe, rien d'installé chez lui.

**Ce n'est pas la production** : les acquéreurs sont inventés, rien n'est sauvegardé, l'accès est
libre (§ 4), et tout se remet à zéro d'une commande (§ 9). Ne jamais y saisir de vraies données.

Conventions : les commandes se lancent dans la session SSH, en `root` — sinon, les préfixer de
`sudo`. `<IP>` est l'adresse publique du serveur. Le dépôt vit dans `/opt/spi-ghomrassen`.

## 1. État des lieux

```bash
echo "USER=$(id -un) HOST=$(hostname) IP=$(hostname -I | awk '{print $1}')"; . /etc/os-release && echo "OS=$PRETTY_NAME"; docker --version; docker compose version; git --version
```

Attendu : Docker 24 ou plus, `docker compose` version 2, git présent. Si `docker compose` manque
(ancien `docker-compose` avec un tiret), installer le plugin : `apt-get install docker-compose-plugin`.

## 2. Cloner le dépôt

```bash
git clone https://github.com/wadiihelal/Spi_Ghomrassen.git /opt/spi-ghomrassen && cd /opt/spi-ghomrassen
```

## 3. Mot de passe PostgreSQL (`.env`)

Partagé entre les conteneurs `postgres` et `backend` ; personne n'a besoin de le connaître.

```bash
(umask 077; printf 'DB_PASSWORD=%s\n' "$(openssl rand -base64 24)" > .env) && echo ".env écrit"
```

Il n'est lu qu'à la **création** du volume PostgreSQL : le fixer avant le premier `up` (§ 6).
Le fichier est ignoré par git.

## 4. Accès libre — ce que cela implique

Décision du 15/09/2026 : **pas de mot de passe**. Le client ouvre l'adresse et se trouve
directement dans l'application, sans rien à saisir. C'est ce qui rend la démonstration fluide.

Le revers, à connaître précisément : l'application n'a aucune authentification (SEC-01), donc
**toute personne qui connaît l'adresse peut lire et supprimer n'importe quel enregistrement** —
et les robots d'indexation trouvent une adresse IP sur un port HTTP en quelques heures. C'est
tenable ici parce que les données sont **fictives** et que la remise à zéro prend une minute
(§ 8). Deux réflexes qui suffisent à vivre avec :

- **arrêter la démonstration quand elle ne sert pas** — `docker compose … down` (§ 8) ;
- **la remettre à zéro juste avant un rendez-vous client**, pour partir d'un jeu propre.

Ne jamais servir de vraies données de cette façon. Pour rétablir un mot de passe partagé, tout
est expliqué en tête de `frontend/nginx.demo.conf` : il reste deux lignes à décommenter.

## 5. Ports déjà pris et pare-feu

Ce serveur héberge déjà d'autres services — constaté le 15/09/2026 : Apache y sert Akaunting sur le
port 80, Odoo répond sur 8069, Dockge sur 5001. La démonstration vit donc sur **8088**. Avant de
lancer, voir ce qui écoute :

```bash
ss -tlnp | grep -E ':(80|443|5432|8080)\s' || echo "80, 443, 5432 et 8080 libres"
```

Si le port 80 est pris, publier la démonstration sur un autre port en l'écrivant dans `.env` — le
compose de démo remplace le `80:80` de la production — et l'adresse devient `http://<IP>:8088/` :

```bash
echo 'DEMO_HTTP_PORT=8088' >> .env
```

**Et, dans le même `.env`, l'adresse exacte par laquelle on ouvrira l'application** — c'est
obligatoire, et c'est le piège le plus vicieux du déploiement :

```bash
echo "APP_CORS_ALLOWED_ORIGINS=http://$(hostname -I | awk '{print $1}'):8088" >> .env && cat .env
```

Le navigateur envoie un en-tête `Origin` sur toute **écriture**, même quand la page et l'API sont
servies par le même serveur — mais **pas** sur les lectures. Si cette adresse ne correspond pas
exactement (protocole, adresse, port, sans barre oblique finale), les écrans s'affichent
parfaitement et **toute création répond « Accès refusé »**. Une IP qui change, un port qui change :
cette ligne est à reprendre.

Pare-feu : `ufw status`. S'il est **inactif sur un serveur partagé, le laisser ainsi** : l'activer
avec seulement 22 et 80 couperait Odoo et Dockge. PostgreSQL (5432) et l'API (8080) de cette
application ne sont de toute façon pas publiés hors de la machine ; seul le port de la
démonstration l'est. Si un jour `ufw` est activé, y ajouter chaque port
des autres services **avant** `ufw enable`, et 22 en premier. Chez Hetzner, OVH ou DigitalOcean,
un pare-feu « cloud » peut exister en plus dans la console du fournisseur.

## 6. Lancer

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml config -q && echo "compose valide"
```

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Premier build : 3 à 6 minutes (Maven, Angular, images). Puis environ une minute de démarrage :
Flyway crée le schéma, le semis `demo` charge cinq résidences et leurs acquéreurs. Suivre :

```bash
docker compose logs -f backend
```

Attendu, dans cet ordre : `Profil demo actif — chargement de données fictives`, puis
`Started SpiGhomrassenApplication`. `Ctrl-C` pour sortir des logs (les conteneurs continuent).

## 7. Vérifier

Depuis le serveur — l'API doit répondre **200** et citer les résidences de démonstration :

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8088/api/projects
```

```bash
curl -s http://localhost:8088/api/projects | grep -o 'SPI-DEMO-RES-[A-E]' | sort -u
```

Sur le serveur, la répartition des ports — c'est la vérification la plus parlante : seul
`frontend` doit montrer `0.0.0.0:...->80/tcp` ; `postgres` et `backend` n'affichent qu'un port
nu (`5432/tcp`, `8080/tcp`), signe qu'ils ne sont joignables que par le réseau Docker :

```bash
docker compose ps
```

Depuis le Mac — le port de la démonstration ouvert, 5432 et 8080 refusés :

```bash
nc -zv <IP> 8088; nc -zv <IP> 5432; nc -zv <IP> 8080
```

Dans le navigateur : `http://<IP>:8088/` affiche **directement** le tableau de bord, avec
« Résidence Démo El Hana » et les quatre autres. Aucune fenêtre d'identifiant ne doit apparaître.

## 8. Donner l'accès au client, et refermer après

Une seule chose à transmettre : l'adresse `http://<IP>:8088/`. Prévenez-le que le navigateur
affichera « Non sécurisé » — il n'y a pas de HTTPS sans nom de domaine, et les données sont
fictives (§ 12).

Comme l'accès est libre (§ 4), le bon réflexe est d'**arrêter la démonstration entre deux
séances** :

```bash
cd /opt/spi-ghomrassen && docker compose -f docker-compose.yml -f docker-compose.demo.yml down
```

et de la relancer avant le rendez-vous suivant (§ 6, sans `--build` si le code n'a pas changé) :

```bash
cd /opt/spi-ghomrassen && docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
```

`down` sans `-v` conserve les données ; c'est `down -v` qui repart d'un jeu neuf (§ 9).

## 9. Remettre le jeu de démonstration à zéro

```bash
cd /opt/spi-ghomrassen && docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v && docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
```

`-v` supprime la base **et** les pièces jointes ; au redémarrage le semis recrée tout. `.env` est relu : le mot de
passe PostgreSQL ne change pas.

## 10. Mettre à jour après un push

```bash
cd /opt/spi-ghomrassen && git pull && docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Flyway applique les nouvelles migrations au démarrage ; les données saisies restent.

## 11. Logs et dépannage

```bash
docker compose ps
```

```bash
docker compose logs --tail=200 backend
```

| Symptôme | Cause | Remède |
|---|---|---|
| Les écrans s'affichent mais toute création donne **« Accès refusé »** | `APP_CORS_ALLOWED_ORIGINS` ne correspond pas à l'adresse ouverte dans le navigateur | corriger la ligne dans `.env` (§ 5), puis `up -d --force-recreate backend` |
| Le navigateur demande encore un identifiant | ancienne configuration nginx encore montée | `git pull` puis `up -d --force-recreate frontend` |
| Toutes les pages en **500**, log nginx `htpasswd ... (13: Permission denied)` | un `htpasswd` traîne et reste référencé | `git pull` (l'accès est libre depuis le 15/09/2026), puis `up -d --force-recreate frontend` |
| `backend` en `Restarting` | Flyway ou semis en erreur | `logs backend`, m'envoyer les 50 dernières lignes |
| Page blanche ou 502 juste après `up` | l'API démarre encore | attendre une minute, recharger |
| `password authentication failed` dans `logs backend` | `.env` changé après la création du volume | § 9 (remise à zéro) ou remettre l'ancien mot de passe |

## 12. HTTPS, plus tard

Impossible sans nom de domaine. Le jour où un sous-domaine pointe sur l'IP : Caddy devant nginx
(une dizaine de lignes dans le compose de démo), certificat Let's Encrypt automatique, cadenas
dans le navigateur. À faire si l'adresse doit circuler au-delà du client.

## 13. Ce que ce serveur n'est pas

Pas la production, pas de sauvegardes, pas de vraies données. L'étape suivante est l'installation
sur le portable du client, base vide — voir `DEMO_FEEDBACK_SPEC.md`, Lots 3 et 4.
