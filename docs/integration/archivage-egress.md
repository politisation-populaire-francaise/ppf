# Archivage horodaté : connexions sortantes du serveur (D-111)

Pour le Directeur technique, à reporter dans `infra/EGRESS.md` et dans le pare-feu sortant. Pôle « Décider », 7 novembre 2026. Code : `server/src/main/kotlin/.../server/archive/` (adresses par défaut dans `ArchiveConfig.kt`).

- **Processus** : toujours le serveur (`ppf-server`, conteneur du serveur), jamais l'application ni le reverse proxy.
- **Environnements** : production par défaut (`PPF_ARCHIVE_ENABLED` allumé quand `PPF_ENVIRONMENT=production`) ; préproduction seulement si `PPF_ARCHIVE_ENABLED=true` y est réglé ; jamais en développement ni en test (éteint par défaut, tests sur un faux réseau). Éteint, le serveur n'ouvre aucune de ces connexions.
- **Toutes** : HTTPS sur le port 443, TCP, sortant seulement ; aucune redirection suivie ; délai d'attente de 30 secondes par requête ; trois essais au plus (après 2 puis 4 secondes) ; agent utilisateur `PPF-server (archive; https://politisation-populaire-francaise.org)`. Mandataire possible : propriétés JVM `https.proxyHost`/`https.proxyPort` ou variable `HTTPS_PROXY=http://hôte:port` (tunnel `CONNECT`, le mandataire ne voit que le nom d'hôte).
- **Hôtes réglables** : `PPF_ARCHIVE_AGGREGATORS`, `PPF_ARCHIVE_CALENDARS`, `PPF_ARCHIVE_EXPLORERS`, `PPF_ARCHIVE_TSA_URL` (`off` coupe l'horodatage RFC 3161). La liste ci-dessous est celle des valeurs par défaut.
- **Résolution DNS** : ces noms sont résolus par le serveur (ou par le mandataire s'il y en a un).

| Hôte exact | Port | Processus | Rôle | Environnements | Fréquence | Ce qui sort |
|---|---|---|---|---|---|---|
| `a.pool.opentimestamps.org` | 443 | serveur | Agrégateur OpenTimestamps (`POST /digest`) | production ; préproduction si réglé | Au plus une requête par heure (s'il y a des empreintes en file), 3 essais au plus | La racine d'un lot : 32 octets |
| `b.pool.opentimestamps.org` | 443 | serveur | Agrégateur OpenTimestamps (`POST /digest`) | production ; préproduction si réglé | Au plus une requête par heure, 3 essais au plus | La racine d'un lot : 32 octets |
| `a.pool.eternitywall.com` | 443 | serveur | Agrégateur OpenTimestamps (`POST /digest`) | production ; préproduction si réglé | Au plus une requête par heure, 3 essais au plus | La racine d'un lot : 32 octets |
| `ots.btc.catallaxy.com` | 443 | serveur | Agrégateur OpenTimestamps (`POST /digest`) | production ; préproduction si réglé | Au plus une requête par heure, 3 essais au plus | La racine d'un lot : 32 octets |
| `alice.btc.calendar.opentimestamps.org` | 443 | serveur | Calendrier, mise à niveau des preuves (`GET /timestamp/<hex>`) | production ; préproduction si réglé | Environ une requête par heure et par lot en attente (2 à 15 heures par lot) | Une valeur intermédiaire de la preuve (dans l'adresse) |
| `bob.btc.calendar.opentimestamps.org` | 443 | serveur | Calendrier, mise à niveau des preuves | production ; préproduction si réglé | Idem | Une valeur intermédiaire de la preuve |
| `finney.calendar.eternitywall.com` | 443 | serveur | Calendrier, mise à niveau des preuves | production ; préproduction si réglé | Idem | Une valeur intermédiaire de la preuve |
| `btc.calendar.catallaxy.com` | 443 | serveur | Calendrier, mise à niveau des preuves | production ; préproduction si réglé | Idem | Une valeur intermédiaire de la preuve |
| `mempool.space` | 443 | serveur | Explorateur Bitcoin, API Esplora (`GET /api/block-height/<h>`, `GET /api/block/<hachage>/header`) | production ; préproduction si réglé | Deux requêtes par bloc d'ancrage et par heure tant que la preuve n'est pas vérifiée (en pratique une fois par lot) | Une hauteur de bloc, un hachage de bloc |
| `blockstream.info` | 443 | serveur | Explorateur Bitcoin, API Esplora (mêmes adresses) | production ; préproduction si réglé | Idem | Une hauteur de bloc, un hachage de bloc |
| `freetsa.org` | 443 | serveur | Autorité d'horodatage RFC 3161 (`POST /tsr`) | production ; préproduction si réglé | Une requête par lot réussi (au plus une par heure), plus 5 relances au plus par heure pour les lots des 7 derniers jours sans jeton ; une requête par seconde au plus | La racine d'un lot et un nombre aléatoire (requête RFC 3161, sans identité) |

**Calendriers** : un calendrier n'est contacté que si son adresse, lue dans une attestation en attente, est exactement `https://<hôte>` avec un hôte de `*.calendar.opentimestamps.org`, `*.calendar.eternitywall.com` ou `*.calendar.catallaxy.com` (liste blanche du client officiel). Les quatre hôtes ci-dessus sont ceux vers lesquels les agrégateurs par défaut renvoient aujourd'hui ; un agrégateur pourrait en désigner un autre du même domaine. Pour le pare-feu, autoriser donc ces trois suffixes de domaine, ou ces quatre hôtes et surveiller les refus.

**Ce qui ne sort jamais** : un contenu, une empreinte de contenu (seule la racine d'un lot sort, chaque feuille étant salée de 16 octets aléatoires), une donnée d'utilisateur, une adresse IP de citoyen, un en-tête de requête d'un citoyen.
