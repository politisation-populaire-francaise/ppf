# Infrastructure

Tout ce qui sert à faire tourner la plateforme est décrit ici, en code, pour pouvoir la redéployer ailleurs en quelques heures à partir des sauvegardes (cahier des charges, sections 3.5 et 5.3).

## Principes

- **Un seul serveur au départ** (Hostinger KVM 1 : 1 vCPU, 4 Go de mémoire, 50 Go de disque), loué au premier déploiement. Pendant le développement, il ne porte que la préproduction ; au lancement public, il porte aussi la production.
- **Production et préproduction séparées** : deux projets Docker Compose distincts (`production/` et `preproduction/`), chacun avec ses conteneurs, sa base de données, son réseau interne, ses volumes, ses secrets et son utilisateur système. Rien n'est partagé, sauf le reverse proxy (`proxy/`), qui est le seul point d'entrée.
- **Préproduction fermée au public** : joignable uniquement à travers le tunnel WireGuard (`wireguard/`), avec un mot de passe en plus. Jamais de données réelles : uniquement des données fictives marquées [EXEMPLE].
- **Budget mémoire** (4 Go au total, système compris) :

| Service | Limite mémoire | Limite processeur |
|---|---|---|
| Caddy (proxy) | 128 Mo | 0,5 |
| Serveur de production | 1 024 Mo | 1,0 |
| Base de production | 768 Mo | 1,0 |
| Serveur de préproduction | 512 Mo | 0,5 |
| Base de préproduction | 384 Mo | 0,5 |
| Système et marge | environ 1,2 Go | |

- **Aucune adresse IP journalisée** : les journaux d'accès du proxy sont désactivés ; le serveur n'installe aucun journal de requêtes.
- **Durcissement du serveur** (à faire lors du premier déploiement, guide dans `docs/guides/serveur-hostinger-kvm1.md`) : pare-feu, SSH par clé uniquement, pas de connexion root, mises à jour de sécurité automatiques, conteneurs sans privilège.
- **Deuxième serveur pour la production** dès que le budget le permet : il suffit de déplacer le projet `production/` et de changer une ligne dans le proxy.

## Contenu

| Dossier | Rôle |
|---|---|
| `docker/` | Image du serveur (construction en deux étapes, exécution sans privilège) |
| `production/` | Projet Compose de la production |
| `preproduction/` | Projet Compose de la préproduction (limitée, accessible par WireGuard) |
| `proxy/` | Caddy : HTTPS automatique, en-têtes de sécurité, aiguillage vers les deux environnements |
| `wireguard/` | Tunnel chiffré vers la préproduction (modèle de configuration, sans clés) |
| `scripts/` | Déploiement et sauvegardes (squelettes) |

Les fichiers `.env.example` listent les variables attendues ; les vrais fichiers `.env` ne sont jamais dans le dépôt.

## Circuit de mise en ligne

1. Tests automatiques au vert dans l'intégration continue.
2. Déploiement en préproduction (`scripts/deploy.sh preproduction`).
3. Validation par la propriétaire à travers WireGuard.
4. Déploiement en production (`scripts/deploy.sh production`). Jamais de déploiement direct en production.
