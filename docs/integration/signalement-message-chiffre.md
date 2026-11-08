# S-127 « Signaler un message chiffré » : conception

Pôle Communauté, brouillon 0.1 du 8 novembre 2026. Dépend de l'étape 2 du socle du back-office (signalements et DSA, file `reports`, Directeur technique), pas encore faite : rien n'est codé avant elle. Cahier fonctionnel 2.1, M14, S-127 : « Un participant transmet lui-même à la modération une copie du message et de son contexte immédiat ; les conditions d'utilisation le prévoient. »

## 1. Le problème

- Les messages privés (NIP-17) sont chiffrés de bout en bout : le serveur et l'IA ne les lisent jamais. Seul un participant peut montrer un message à l'équipe.
- Le message lui-même (le « rumor », type 14) **n'est pas signé** : c'est voulu par NIP-17, pour que l'auteur puisse nier si le téléphone de son correspondant est saisi. Seul le sceau (type 13, qui contient le message chiffré) porte la signature de l'auteur, et l'appareil ne le garde pas une fois le message ouvert.
- Une simple copie transmise par le destinataire ne prouve donc rien : n'importe qui peut écrire un faux message « de » quelqu'un.

## 2. Ce que je propose : une preuve au cas par cas, sans rien garder de plus

- L'appareil **ne garde aucun sceau** : le déni plausible des auteurs reste entier pour tous les messages qui ne sont pas signalés.
- Au moment du signalement, l'appareil relit sur le serveur l'enveloppe (type 1059) de chaque message signalé : le serveur la garde au moins 90 jours (D-028). Il l'ouvre comme d'habitude, retrouve le sceau signé par l'auteur, et transmet à l'équipe :
  1. l'identifiant de l'enveloppe (le serveur l'a déjà : rien de nouveau ne sort) ;
  2. les **clés de ce seul message** pour ouvrir l'enveloppe, puis le sceau (NIP-44 v2 : clés de chiffrement et d'authentification dérivées de la clé de conversation et de l'aléa du message, `Nip44.messageKeys`) ; la clé de conversation elle-même ne sort jamais, donc aucun autre message ne devient lisible ;
  3. le texte, la date et l'auteur annoncés.
- L'équipe vérifie dans le back-office : l'enveloppe est bien celle que le serveur a reçue pour le signaleur, les clés données l'ouvrent (le code d'authentification de NIP-44 correspond), le sceau est signé par l'auteur, et le message obtenu est celui qui est signalé. Affichage : « Message prouvé : écrit par cette clé » ou, si l'enveloppe a déjà été effacée (plus de 90 jours, ou durée choisie par l'auteur), « Copie transmise par le destinataire, non prouvée ».
- **Contexte immédiat** : jusqu'à 5 messages avant et 5 après dans la même conversation, choisis par le signaleur (cochés par défaut, décochables), chacun prouvé de la même façon quand c'est possible. Jamais toute la conversation.

## 3. Transmission

- Le signalement est un événement NIP-56 (type 1984) adressé à l'API habituelle, qui ne le publie jamais (contrat du back-office, section 9). Son contenu (textes, clés des messages) est **chiffré pour la clé de l'équipe de modération** (CLAUDE.md : « contenus sensibles en attente… chiffrés avec la clé publique de l'équipe de modération ») : le serveur principal ne peut pas le lire, seul le client de l'équipe le déchiffre.
- Le signaleur choisit un motif (liste de la charte : violence, harcèlement, haine, menace, contenu illicite, autre), peut ajouter une phrase, et voit avant l'envoi exactement ce qui part (« Ces messages seront montrés à l'équipe de modération de PPF »), avec Annuler en premier.
- L'auteur signalé n'est pas prévenu par l'application (risque de représailles) ; la décision de l'équipe suit la procédure du socle (décision motivée, recours, PHAROS si nécessaire).
- Bloquer le compte (S-122) est proposé juste après l'envoi, jamais imposé.

## 4. Salons

- Les salons publics ne sont pas chiffrés : un message de salon se signale comme un contenu public (NIP-56 sur l'événement, S-38), sans clés ; les salons réservés chiffrés suivront MLS (plus tard).

## 5. Données

- Sur l'appareil : rien de nouveau (le signalement envoyé n'est pas gardé, sauf son état « envoyé le … » pour éviter un doublon, sans le texte).
- Sur le serveur : le signalement chiffré dans la file `reports`, durée de conservation fixée par l'étape 2 (DSA). À ajouter au registre RGPD à ce moment-là.

## 6. Menaces

- Faux signalement d'un message inventé : la preuve par le sceau le démasque ; sans preuve, la mention « non prouvée » le dit.
- Fuite d'autres messages : seules les clés des messages choisis sortent, jamais la clé de conversation.
- Un téléphone saisi : il ne contient pas plus de preuves qu'aujourd'hui (aucun sceau gardé).
- Le serveur principal compromis : il ne lit pas les signalements (chiffrés pour l'équipe).

## 7. Questions pour le Directeur technique (étape 2 du socle)

1. La clé de l'équipe de modération : la même que la « clé de boîte » de M30 (option B, client d'équipe), ou une clé distincte ? Je recommande une clé distincte par usage (modération, M30), gardée de la même façon.
2. Le format du contenu chiffré d'un signalement : je propose un objet JSON versionné `{v: 1, reason, note, messages: [{wrap_id, keys: {wrap, seal}, text, created_at, author}]}`, décrit dans `docs/FORMATS.md` le moment venu.
3. La vérification de la preuve se fait dans le client de l'équipe (qui seul déchiffre) : le socle doit-il seulement afficher un bloc chiffré, ou prévoir un emplacement « vérification » que le client remplit ?
