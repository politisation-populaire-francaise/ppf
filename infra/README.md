# Infrastructure

Tout ce qui sert à faire tourner la plateforme est décrit ici, en code, pour pouvoir la redéployer ailleurs en quelques heures à partir des sauvegardes (cahier des charges, sections 3.5 et 5.3). Décisions : D-070 à D-074 (`docs/DECISIONS.md`).

## Principes

- **Un seul serveur au départ** (Hostinger KVM 1 : 1 vCPU, 4 Go de mémoire, 50 Go de disque, Ubuntu 24.04). Pendant le développement, il ne porte que la préproduction ; au lancement public, il porte aussi la production.
- **Le serveur ne compile jamais rien.** L'intégration continue (GitHub Actions, branche `main` seulement) construit le jar, en fait une image, la publie sur le registre de GitHub (`ghcr.io/<organisation>/ppf-server`) et la **signe sans clé stockée** (cosign et Sigstore, identité du workflow). Le serveur refuse toute image dont la signature ne désigne pas ce workflow sur `main`.
- **Déploiement par empreinte** (digest) : une image précise, jamais « la dernière ». L'image porte le commit dont elle vient ; les fichiers Compose et Caddy de **ce commit** sont extraits du dépôt public pour la lancer.
- **Préproduction automatique, production manuelle** : la préproduction suit `main` toute seule (minuteur toutes les 10 minutes) ; la production n'accepte qu'une empreinte qui a déjà tourné en préproduction, validée par la propriétaire, avec `--confirm`, et sauvegarde sa base avant.
- **Production et préproduction séparées** : deux projets Docker Compose distincts, chacun avec ses conteneurs, sa base de données, son réseau interne sans sortie, ses volumes, ses secrets (un fichier par conteneur), son utilisateur (UID 10001 pour la production, 10002 pour la préproduction). Seul le reverse proxy est commun. Limite assumée : un seul moteur Docker sur une seule machine ; la vraie séparation viendra avec le deuxième serveur (il suffira d'y déplacer `production/` et le site `production.caddy`).
- **Préproduction fermée au public** : `https://preprod.politisation-populaire-francaise.org`, avec un vrai certificat, mais le proxy ferme toute connexion qui ne vient pas du tunnel WireGuard (10.8.0.0/24). Les pages web demandent en plus un mot de passe ; l'API des applications s'appuie sur le tunnel seul, parce que les APK d'essai sont publics (D-072, à valider). Jamais de données réelles : uniquement les contenus fictifs [EXEMPLE] embarqués dans l'image, chargés au démarrage (`PPF_EXAMPLES_DIR`, refusé par le serveur en production).
- **Aucune adresse IP écrite** : journaux d'accès du proxy désactivés, aucun journal de requêtes dans le serveur, journalisation du pare-feu coupée, journal du système gardé en mémoire un jour au plus, journaux des conteneurs limités en taille.
- **Budget mémoire** (4 Go au total, système compris, plus 2 Go de swap de secours) :

| Service | Limite mémoire | Limite processeur |
|---|---|---|
| Caddy (proxy) | 128 Mo | 0,5 |
| Serveur de production | 1 024 Mo | 1,0 |
| Base de production | 768 Mo | 1,0 |
| Serveur de préproduction | 512 Mo | 0,5 |
| Base de préproduction | 384 Mo | 0,5 |
| Système et marge | environ 1,2 Go | |

## Contenu

| Chemin | Rôle |
|---|---|
| `docker/server.Dockerfile` | Image d'exécution du serveur (jar construit avant, JRE 21, utilisateur sans privilège, contrôle de santé intégré) |
| `../.dockerignore` | N'envoie à la construction que le jar et les contenus d'exemple |
| `preproduction/compose.yml`, `production/compose.yml` | Projets Compose des deux environnements |
| `proxy/compose.yml`, `proxy/Caddyfile`, `proxy/sites/*.caddy` | Caddy : HTTPS automatique, en-têtes de sécurité, un fichier par site déployé |
| `env/*.env.example` | Modèles des fichiers `.env` du serveur (les vrais sont dans `/srv/ppf`, jamais dans le dépôt) |
| `scripts/bootstrap-server.sh` | Préparation et durcissement d'un serveur neuf (à relancer après chaque modification ; `--lockdown` ferme root et les mots de passe) |
| `scripts/deploy.sh` | Déploiement (installé en `/usr/local/sbin/ppf-deploy`) |
| `scripts/wg-add-peer.sh` | Ajout d'un appareil au tunnel (installé en `/usr/local/sbin/ppf-wg-add-peer`) |
| `systemd/ppf-preprod-update.*` | Mise à jour automatique de la préproduction toutes les 10 minutes |
| `scripts/open-data-import.sh`, `systemd/ppf-open-data-*` | Import des données parlementaires ouvertes en production (D-075), activé au lancement public |
| `wireguard/` | Modèle de configuration du tunnel (les clés sont générées sur le serveur) |
| `scripts/backup.md` | Plan des sauvegardes hors site (avant la production) |
| `EGRESS.md` | Liste des connexions sortantes autorisées (relue aujourd'hui, appliquée par le réseau avant la production) |

## Sur le serveur

| Chemin | Contenu | Droits |
|---|---|---|
| `/etc/ppf/deploy.conf` | Organisation GitHub, image, identité attendue de la signature | root |
| `/opt/ppf/repo` | Copie publique du dépôt (lecture seule) | root |
| `/srv/ppf/<environnement>/.env`, `secrets/` | Réglages et mots de passe de la base (générés sur place) | root, 0600/0400 |
| `/srv/ppf/<environnement>/releases/<commit>` | Fichiers `infra/` du commit déployé (5 derniers gardés pour revenir en arrière) | root |
| `/srv/ppf/<environnement>/history` | Date, empreinte et commit de chaque déploiement | root |
| `/srv/ppf/proxy/conf` | Caddyfile et sites déployés, montés dans Caddy | root |
| `/etc/wireguard/` | Clés et configuration du tunnel | root, 0600 |

## Circuit de mise en ligne

1. Tests automatiques au vert dans l'intégration continue ; fusion dans `main` par une demande de fusion relue.
2. L'intégration continue publie et signe l'image ; la préproduction l'installe d'elle-même dans les 10 minutes (`ppf-deploy preproduction --if-changed`) et revient à la version précédente si la nouvelle n'est pas en bonne santé.
3. Validation par la propriétaire à travers WireGuard (`docs/guides/wireguard-preproduction.md`).
4. Production, sur son accord : `ppf-deploy production --from-preproduction --confirm`. Jamais de déploiement direct en production.

Première installation : `docs/guides/serveur-hostinger-kvm1.md`.
