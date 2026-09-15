# Serveur de test — déployer la démonstration

Pour Wadii. Un VPS Linux avec Docker, joignable par son adresse IP, qui fait tourner **l'interface
et l'API** avec le jeu de données **fictif** (`demo`), derrière un mot de passe partagé. Le client
essaie l'application depuis son navigateur ; rien n'est installé chez lui.

**Ce n'est pas la production** : les acquéreurs sont inventés, rien n'est sauvegardé, et tout se
remet à zéro d'une commande (§ 9). Ne jamais y saisir de vraies données.

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

## 4. Mot de passe de démonstration (`htpasswd`)

C'est ce que le client tapera dans son navigateur. La commande demande le mot de passe deux fois ;
l'identifiant est `spi` (changez-le si vous voulez). Le fichier est ignoré par git.

```bash
(umask 077; printf 'spi:%s\n' "$(openssl passwd -apr1)" > htpasswd) && test -s htpasswd && echo "htpasswd écrit"
```

**Ce fichier doit exister avant le premier `up`** : Docker remplace un chemin absent par un
dossier vide, et nginx refuse alors de démarrer (§ 11).

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

Pare-feu : `ufw status`. S'il est **inactif sur un serveur partagé, le laisser ainsi** : l'activer
avec seulement 22 et 80 couperait Odoo et Dockge. PostgreSQL (5432) et l'API (8080) de cette
application ne sont de toute façon pas publiés hors de la machine ; seul le port de la
démonstration l'est, derrière le mot de passe. Si un jour `ufw` est activé, y ajouter chaque port
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

Depuis le serveur — 401 sans identifiant, 200 avec (la deuxième commande demande le mot de passe) :

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/api/projects
```

```bash
curl -s -u spi -o /dev/null -w '%{http_code}\n' http://localhost/api/projects
```

Depuis le Mac — 80 ouvert, 5432 et 8080 refusés :

```bash
nc -zv <IP> 80; nc -zv <IP> 5432; nc -zv <IP> 8080
```

Dans le navigateur : `http://<IP>/` — ou `http://<IP>:8088/` si `DEMO_HTTP_PORT` est fixé, et
remplacer `localhost` par `localhost:8088` dans les `curl` ci-dessus — demande l'identifiant, puis
affiche le tableau de bord avec « Résidence Démo El Hana » et les quatre autres.

## 8. Donner l'accès au client

L'adresse `http://<IP>/`, l'identifiant et le mot de passe — **de vive voix**, pas par courriel.
Le prévenir : le navigateur affiche « Non sécurisé » parce qu'il n'y a pas de HTTPS sans nom de
domaine ; c'est normal pour une démonstration à données fictives (§ 12).

## 9. Remettre le jeu de démonstration à zéro

```bash
cd /opt/spi-ghomrassen && docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v && docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
```

`-v` supprime la base **et** les pièces jointes ; au redémarrage le semis recrée tout. `.env` et
`htpasswd` sont relus : les mots de passe ne changent pas.

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
| `frontend` en `Restarting`, log nginx `open() "/etc/nginx/htpasswd" failed` ou `is a directory` | `htpasswd` absent au premier `up` : Docker a créé un dossier | `rm -rf htpasswd`, refaire § 4, puis `up -d` |
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
