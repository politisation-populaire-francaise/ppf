# S-127 « Signaler un message chiffré » : conception

Pôle Communauté, brouillon 0.2 du 8 novembre 2026, à valider par le chef d'équipe avant tout code (règles qu'il a données le 8 novembre, section 0). Dépend de l'étape 2 du socle du back-office (signalements et DSA, file `reports`, Directeur technique), pas encore faite : rien n'est codé avant elle. Cahier fonctionnel 2.1, M14, S-127 : « Un participant transmet lui-même à la modération une copie du message et de son contexte immédiat ; les conditions d'utilisation le prévoient. »

## 0. Règles du chef d'équipe (8 novembre 2026)

- Seule la personne qui a **reçu** le message choisit de le transmettre.
- L'écran montre **exactement** ce qui part : le message, sa signature, et rien d'autre de la conversation.
- Ça part **chiffré** vers l'équipe de modération.
- L'**IA ne lit jamais** les échanges chiffrés (CLAUDE.md) : un signalement de message privé va à des personnes de l'équipe seulement, jamais à la modération assistée par IA.
- L'auteur du message n'est **pas prévenu** par défaut.

## 1. Le problème

- Les messages privés (NIP-17) sont chiffrés de bout en bout : le serveur et l'IA ne les lisent jamais. Seul un participant peut montrer un message à l'équipe.
- Le message lui-même (le « rumor », type 14) **n'est pas signé** : c'est voulu par NIP-17, pour que l'auteur puisse nier si le téléphone de son correspondant est saisi. Seul le sceau (type 13, qui contient le message chiffré) porte la signature de l'auteur, et l'appareil ne le garde pas une fois le message ouvert.
- Une simple copie transmise par le destinataire ne prouve donc rien : n'importe qui peut écrire un faux message « de » quelqu'un.

## 2. Prouver que le message vient de son auteur : trois options

Ce que la signature Nostr permet de prouver : le sceau (type 13) est signé par la clé de l'auteur et contient le message chiffré pour le destinataire (NIP-44 v2). Si l'équipe obtient le sceau et de quoi ouvrir **ce seul** contenu, elle vérifie elle-même que l'auteur l'a signé. NIP-44 v2 dérive, pour chaque message, ses propres clés (chiffrement et authentification) de la clé de conversation et d'un aléa : les clés d'un message n'ouvrent que lui, et on ne remonte pas d'elles à la clé de conversation (fonction de dérivation à sens unique). Elles existent déjà dans le cœur (`Nip44.messageKeys`).

| Option | Comment | Avantages | Inconvénients |
|---|---|---|---|
| A. Copie sans preuve | Le destinataire envoie le texte, la date et l'auteur annoncés | Simple, marche toujours | Ne prouve rien : un faux message « de » quelqu'un passe ; l'équipe ne peut pas trancher |
| B. Garder tous les sceaux | L'appareil garde le sceau signé de chaque message reçu ; il l'envoie avec les clés du message | Preuve toujours possible, même hors ligne | **Supprime le déni plausible** de tous les auteurs : un téléphone saisi prouverait qui a écrit chaque message (contexte politique, gardes à vue) ; plus de données sur l'appareil |
| **C. Preuve au moment du signalement (recommandée)** | L'appareil ne garde aucun sceau ; au signalement, il relit sur le serveur l'enveloppe (type 1059) du message, gardée 90 jours au moins (D-028), l'ouvre, et envoie l'identifiant de l'enveloppe et les clés de ce seul message (enveloppe et sceau) | Preuve vérifiable par l'équipe ; rien de plus sur l'appareil ; le déni plausible reste entier pour tous les messages non signalés ; la clé de conversation ne sort jamais | Pas de preuve après la durée de conservation (90 jours, ou plus si l'auteur l'a choisi) : alors repli sur A, avec la mention « non prouvé » ; demande le réseau au moment du signalement |

**Recommandation : C**, avec repli sur A affiché clairement.

Ce que l'équipe voit dans le back-office, après vérification par son client (seul à déchiffrer) : « Message prouvé : signé par cette clé, reçu par le signaleur le … » ou « Copie transmise par le destinataire, non prouvée (message trop ancien) ».

### Le contexte de la conversation : deux options

Le cahier fonctionnel (S-127) parle d'une « copie du message et de son contexte immédiat » ; la règle du chef d'équipe dit « rien d'autre de la conversation ». Contradiction à trancher :

- **Option 1 (recommandée, conforme à la règle)** : seul le message choisi part. Pour un harcèlement en plusieurs messages, la personne fait plusieurs signalements, ou en choisit plusieurs à la fois, un par un, chacun prouvé.
- Option 2 (conforme au cahier) : le message, plus jusqu'à 5 messages avant et 5 après, **aucun coché par défaut**, chacun ajouté à la main et montré avant l'envoi.

Je recommande l'option 1, avec la possibilité de choisir plusieurs messages de la **même** personne : elle couvre le harcèlement sans montrer les messages du signaleur ni ceux des autres participants. Si l'option 1 est retenue, le cahier fonctionnel (S-127, « et de son contexte immédiat ») est à corriger.

## 3. Transmission

- Le signalement est un événement NIP-56 (type 1984) adressé à l'API habituelle, qui ne le publie jamais (contrat du back-office, section 9). Son contenu (textes, clés des messages) est **chiffré pour la clé de l'équipe de modération** (CLAUDE.md : « contenus sensibles en attente… chiffrés avec la clé publique de l'équipe de modération ») : le serveur principal ne peut pas le lire, seul le client de l'équipe le déchiffre.
- Le signaleur choisit un motif (liste de la charte : violence, harcèlement, haine, menace, contenu illicite, autre), peut ajouter une phrase, et voit avant l'envoi **exactement** ce qui part : le ou les messages, la mention « avec sa signature, vérifiable par l'équipe » (ou « sans preuve ») et la phrase « Rien d'autre de la conversation n'est transmis » ; Annuler vient en premier.
- Le signalement va à des personnes de l'équipe de modération, jamais à la modération assistée par IA (l'IA ne lit jamais les échanges chiffrés).
- L'auteur signalé n'est pas prévenu par l'application (risque de représailles) ; la décision de l'équipe suit la procédure du socle (décision motivée, recours, PHAROS si nécessaire).
- Bloquer le compte (S-122) est proposé juste après l'envoi, jamais imposé.

## 4. Salons

- Les salons publics ne sont pas chiffrés : un message de salon se signale comme un contenu public (NIP-56 sur l'événement, S-38), sans clés ; les salons réservés chiffrés suivront MLS (plus tard).

## 5. Données

- Sur l'appareil : rien de nouveau (le signalement envoyé n'est pas gardé, sauf son état « envoyé le … » pour éviter un doublon, sans le texte).
- Sur le serveur : le signalement chiffré dans la file `reports`, durée de conservation fixée par l'étape 2 (DSA). À ajouter au registre RGPD à ce moment-là.

## 6. Menaces

- Faux signalement d'un message inventé : la preuve par le sceau le démasque ; sans preuve, la mention « non prouvée » le dit.
- L'équipe qui garderait les clés d'un message : elles n'ouvrent que ce message, déjà montré par le signaleur.
- Fuite d'autres messages : seules les clés des messages choisis sortent, jamais la clé de conversation.
- Un téléphone saisi : il ne contient pas plus de preuves qu'aujourd'hui (aucun sceau gardé).
- Le serveur principal compromis : il ne lit pas les signalements (chiffrés pour l'équipe).

## 7. Questions pour le Directeur technique (étape 2 du socle)

1. La clé de l'équipe de modération : la même que la « clé de boîte » de M30 (option B, client d'équipe), ou une clé distincte ? Je recommande une clé distincte par usage (modération, M30), gardée de la même façon.
2. Le format du contenu chiffré d'un signalement : je propose un objet JSON versionné `{v: 1, reason, note, messages: [{wrap_id, keys: {wrap, seal}, text, created_at, author}]}`, décrit dans `docs/FORMATS.md` le moment venu.
3. La vérification de la preuve se fait dans le client de l'équipe (qui seul déchiffre) : le socle doit-il seulement afficher un bloc chiffré, ou prévoir un emplacement « vérification » que le client remplit ?
