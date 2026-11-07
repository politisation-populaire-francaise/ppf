# Connexions sortantes autorisées

La liste de tout ce que le serveur de PPF contacte sur Internet. Une connexion qui n'est pas ici n'a pas lieu d'être. Aujourd'hui, la liste est vérifiée en relecture ; avant la production, elle sera appliquée par le réseau : les conteneurs ne pourront sortir que vers ces hôtes, par un proxy de sortie filtrant (décision à venir dans le bloc D-075 à D-079).

Chaque pôle qui ajoute une connexion envoie sa ligne au Directeur technique, qui la reporte ici ; le pôle l'inscrit aussi dans `docs/THREAT_MODEL.md` et `docs/REGISTRE_DONNEES.md`. Aucune donnée personnelle ne sort jamais vers ces hôtes.

| Hôte | Port | Qui se connecte | Pour quoi | Environnements | Pôle | État |
|---|---|---|---|---|---|---|
| Miroirs Ubuntu (`archive.ubuntu.com`, `security.ubuntu.com` ou le miroir de l'hébergeur) | 80, 443 | Système | Mises à jour de sécurité | Machine | Directeur technique | En place |
| `download.docker.com` | 443 | Système | Paquets de Docker Engine | Machine | Directeur technique | En place |
| `ghcr.io`, `pkg-containers.githubusercontent.com` | 443 | Docker (système) | Téléchargement des images signées du serveur | Machine | Directeur technique | En place |
| `registry-1.docker.io`, `auth.docker.io`, `production.cloudflare.docker.com` | 443 | Docker (système) | Images de base PostgreSQL/PostGIS et Caddy, épinglées par empreinte | Machine | Directeur technique | En place |
| `github.com` | 443 | Système | Copie publique du dépôt (fichiers `infra/` du commit déployé), cosign | Machine | Directeur technique | En place |
| `tuf-repo-cdn.sigstore.dev`, `rekor.sigstore.dev` | 443 | Système (cosign) | Vérification de la signature des images (racine de confiance et journal public de Sigstore) | Machine | Directeur technique | En place |
| `acme-v02.api.letsencrypt.org` (et les serveurs de validation de Let's Encrypt, en entrée sur le port 80) | 443 | Caddy | Certificats HTTPS | Machine | Directeur technique | En place |
| Modèle d'IA de modération (`PPF_AI_BASE_URL`) | 443 | Serveur | Relecture des revendications proposées | Production, préproduction | Décider | Hébergement à décider ; éteint |
| `www.data.gouv.fr`, `static.data.gouv.fr` | 443 | Serveur | Compteurs des pétitions officielles (Assemblée, Sénat), chaque semaine | Production | Décider | Annoncé, pas codé |
| Calendriers OpenTimestamps (liste exacte à venir) ou autorité RFC 3161 | 443 | Serveur | Horodatage des engagements publics : seule une empreinte SHA-256 part | Production | Décider | Annoncé, pas codé |
| `data.assemblee-nationale.fr`, `www.senat.fr`, `data.senat.fr`, `data.europarl.europa.eu`, `www.hatvp.fr` | 443 | Importeur de données ouvertes (conteneur à part) | Données parlementaires, une fois par jour ou par semaine | Production seulement (exemples en préproduction) | Voir | Annoncé, pas codé |
