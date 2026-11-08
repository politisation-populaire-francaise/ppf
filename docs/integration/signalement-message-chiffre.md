# S-127 « Signaler un message chiffré » : conception

Pôle Communauté, brouillon 0.3 du 9 novembre 2026, à valider par le chef d'équipe avant tout code (règles qu'il a données le 8 novembre, section 0). Dépend de l'étape 2 du socle du back-office (signalements et DSA, Directeur technique) et de l'étape 2 de M30 (boîtes par usage, D-146), pas encore faites : rien n'est codé avant elles. Cahier fonctionnel 2.1, M14, S-127 : « Un participant transmet lui-même à la modération une copie du message et de son contexte immédiat ; les conditions d'utilisation le prévoient. »

Changements depuis la version 0.2 : la preuve tient dans le **sceau** seul (section 2.2), la transmission suit les réponses du Directeur technique (dossier de M30, section 4), l'écran montre aussi ce que la preuve révèle forcément (section 3), le moyen de retrouver l'enveloppe sans désigner le message au serveur (section 2.3), et la vérification `ReportProof.verify` est détaillée (section 5).

## 0. Règles du chef d'équipe (8 novembre 2026)

- Seule la personne qui a **reçu** le message choisit de le transmettre.
- L'écran montre **exactement** ce qui part : le message, sa signature, et rien d'autre de la conversation.
- Ça part **chiffré** vers l'équipe de modération.
- L'**IA ne lit jamais** les échanges chiffrés (CLAUDE.md) : un signalement de message privé va à des personnes de l'équipe seulement, jamais à la modération assistée par IA.
- L'auteur du message n'est **pas prévenu** par défaut.

## 1. Le problème

- Les messages privés (NIP-17) sont chiffrés de bout en bout : le serveur et l'IA ne les lisent jamais. Seul un participant peut montrer un message à l'équipe.
- Le message lui-même (le « rumor », type 14) **n'est pas signé** : c'est voulu par NIP-17, pour que l'auteur puisse nier si le téléphone de son correspondant est saisi. Seul le sceau (type 13, qui contient le message chiffré) porte la signature de l'auteur.
- L'appareil ne garde que le message ouvert (table `message` : identifiant du rumor, auteur, texte, dates), ni le sceau ni l'enveloppe (type 1059). Une simple copie transmise par le destinataire ne prouve donc rien : n'importe qui peut écrire un faux message « de » quelqu'un.

## 2. Prouver que le message vient de son auteur

### 2.1 Trois options

Ce que la signature Nostr permet de prouver : le sceau (type 13) est signé par la clé de l'auteur et contient le message chiffré pour le destinataire (NIP-44 v2). Si l'équipe obtient le sceau et de quoi ouvrir **ce seul** contenu, elle vérifie elle-même que l'auteur l'a signé. NIP-44 v2 dérive, pour chaque message, ses propres clés (chiffrement et authentification) de la clé de conversation et d'un aléa : les clés d'un message n'ouvrent que lui, et on ne remonte pas d'elles à la clé de conversation (fonction de dérivation à sens unique). Elles existent déjà dans le cœur (`Nip44.messageKeys`).

| Option | Comment | Avantages | Inconvénients |
|---|---|---|---|
| A. Copie sans preuve | Le destinataire envoie le texte, la date et l'auteur annoncés | Simple, marche toujours | Ne prouve rien : un faux message « de » quelqu'un passe ; l'équipe ne peut pas trancher |
| B. Garder tous les sceaux | L'appareil garde le sceau signé de chaque message reçu ; il l'envoie avec les clés du message | Preuve toujours possible, même hors ligne | **Supprime le déni plausible** de tous les auteurs au-delà de la conservation du serveur : un téléphone saisi prouverait qui a écrit chaque message, même ancien (contexte politique, gardes à vue) ; plus de données sur l'appareil |
| **C. Preuve au moment du signalement (recommandée)** | L'appareil ne garde aucun sceau ; au signalement, il relit sur le serveur l'enveloppe du message, gardée 90 jours par défaut (D-028), l'ouvre, et envoie le sceau et les clés de ce seul message | Preuve vérifiable par l'équipe ; rien de plus sur l'appareil ; le déni plausible reste entier pour tous les messages non signalés ; la clé de conversation ne sort jamais | Pas de preuve après la durée de conservation : alors repli sur A, avec la mention « non prouvé » ; demande le réseau au moment du signalement |

**Recommandation : C**, avec repli sur A affiché clairement.

Précision sur le déni plausible : un téléphone saisi avec la clé du compte peut **déjà** relire sur le serveur les enveloppes encore gardées (90 jours par défaut, jusqu'à 365 si la personne l'a choisi pour sa boîte) et en tirer les sceaux signés. L'option C n'ajoute rien à cette exposition ; l'option B l'étendrait à tout l'historique de l'appareil.

### 2.2 Ce qui prouve, et ce qui part (changement depuis la version 0.2)

- **Le sceau suffit.** Il est signé par l'auteur, et sa signature couvre son contenu chiffré. Ouvert avec les clés de ce seul message, il donne le rumor complet : auteur, date écrite par l'auteur, texte, et destinataires (étiquettes `p`). Ces destinataires prouvent que le message était bien adressé au signaleur.
- **L'enveloppe n'apporte rien de vérifiable** sans interroger le serveur (seul à savoir qu'il l'a reçue et quand) ; or l'équipe ne doit pas l'interroger : un identifiant d'enveloppe désigne son destinataire, le serveur apprendrait qui signale. Je propose donc au Directeur technique de retirer l'enveloppe et ses clés du format (section 6) : le signalement contient le sceau signé et les trois clés de son contenu.
- Le texte, l'auteur et la date affichés à l'équipe sont **ceux que son client a déchiffrés**, jamais ceux qu'annonce le signaleur. Les champs annoncés ne servent que pour une copie non prouvée (option A).

### 2.3 Retrouver l'enveloppe sans désigner le message au serveur

- L'appareil ne connaît pas l'identifiant de l'enveloppe d'un message (il ne garde que le rumor), et je ne propose pas de le garder : demander une enveloppe par son identifiant désignerait au serveur le message précis.
- À l'ouverture de l'écran « Signaler » (pas à l'envoi : relire n'engage à rien), l'appareil relit ses enveloppes **comme une synchronisation ordinaire**, par la même requête (`#p` = sa clé, type 1059), sur la fenêtre où l'enveloppe peut se trouver : de la réception du message moins 2 jours et 1 heure (dates d'enveloppe tirées au hasard dans les 2 jours précédents, `GiftWrap.TIMESTAMP_JITTER_SECONDS`, plus la marge d'horloge de la synchronisation, `CLOCK_SKEW_MARGIN_SECONDS`) jusqu'à la réception plus 1 heure. Il ouvre chaque enveloppe de la page jusqu'à trouver le rumor du même identifiant, garde en mémoire seulement le sceau de ce message, et oublie le reste.
- Ce que le serveur voit : la boîte d'une clé relue sur une fenêtre passée. Il ne sait ni quel message, ni de qui (les enveloppes sont émises par des clés à usage unique), ni si un signalement suivra. Risque restant, inscrit au modèle de menaces : un serveur compromis qui rapprocherait cette relecture de l'arrivée d'un dossier de signalement peu après saurait que cette clé a probablement signalé quelqu'un, sans savoir qui.
- Rien trouvé (enveloppe effacée après la durée de conservation, ou boîte pleine) : copie non prouvée, dite avant l'envoi.

### 2.4 Le contexte de la conversation : deux options (à trancher)

Le cahier fonctionnel (S-127) parle d'une « copie du message et de son contexte immédiat » ; la règle du chef d'équipe dit « rien d'autre de la conversation ». Contradiction à trancher :

- **Option 1 (recommandée, conforme à la règle)** : seuls les messages choisis partent, tous de la **même** personne, 10 au plus, choisis un par un, chacun prouvé séparément. Elle couvre le harcèlement en plusieurs messages sans montrer les messages du signaleur ni ceux des autres participants.
- Option 2 (conforme au cahier) : le message, plus jusqu'à 5 messages avant et 5 après, **aucun coché par défaut**, chacun ajouté à la main et montré avant l'envoi.

Si l'option 1 est retenue, le cahier fonctionnel (S-127, « et de son contexte immédiat ») est à corriger, avec la validation de la propriétaire.

## 3. Les écrans

**Point d'entrée** : un appui long (ou le menu du message, au clavier) sur un message **reçu** d'une autre personne, entrée « Signaler ce message ». Rien sur ses propres messages.

**Écran « Signaler à l'équipe de modération »**, dans cet ordre :

1. **Motif** (obligatoire, liste fermée de la charte) : violence, harcèlement, haine, menace, contenu illicite, autre ; une phrase facultative (500 caractères au plus).
2. **Messages transmis** : le message choisi, avec « Ajouter un autre message de cette personne » (option 1) ; chaque message montré en entier avec sa date et son état :
   - « Prouvé : signé par son auteur, vérifiable par l'équipe » ;
   - « Non prouvé : trop ancien, l'équipe verra une simple copie » ;
   - « Vérification en cours… » pendant la relecture (section 2.3), « Sans réseau : la preuve sera cherchée à l'envoi ».
3. **Ce que l'équipe verra aussi**, toujours affiché, en clair :
   - « Que ces messages vous étaient adressés : votre pseudonyme (pas votre identité réelle). » La preuve le contient forcément (destinataires signés par l'auteur).
   - Pour une conversation à plusieurs : « Les pseudonymes des N autres participants de cette conversation, pas leurs messages. » (même raison) ;
   - Pour une réponse : « Que ce message répond à un autre message, sans son texte. »
4. La phrase « **Rien d'autre de la conversation n'est transmis.** L'équipe de modération est formée de personnes ; aucune IA ne lit ce signalement. La personne signalée n'est pas prévenue. »
5. Boutons : **Annuler** en premier, puis « Envoyer le signalement ».

**Après l'envoi** : « Signalement envoyé. Numéro de dossier : … Vous serez informé de la décision dans Mes échanges. » ; puis « Bloquer ce compte ? » (S-122), proposé, jamais imposé.

Accessibilité : chaque état de preuve est un texte, pas seulement une icône ; ordre de lecture identique à l'ordre visuel ; tout au clavier. Les textes sont externalisés (`strings_signalement.xml`).

## 4. Transmission (réponses du Directeur technique, à confirmer à l'étape 2 du socle)

- **Pas** d'événement 1984 avec des étiquettes `p` ou `e` en clair : le serveur apprendrait qui signale qui.
- Un **dossier de M30**, ticket `report`, adressé à la boîte de modération (boîtes par usage, étape 2 de M30, D-146) : auteur à usage unique, preuve de travail, aucun lien avec le compte côté serveur, et un numéro de dossier pour informer le signaleur de la décision (DSA, article 16, paragraphe 5) dans « Mes échanges ».
- Le contenu du dossier est le JSON `ppf-report-dm` (section 6), **chiffré pour `PPF_MODERATION_KEY`**, la clé de l'équipe de modération (déjà utilisée pour les témoignages d'Agir, intégrée par environnement dans l'application, jamais reçue du serveur). Le serveur principal ne peut pas le lire.
- Le signalement va à des personnes de l'équipe, jamais à la modération assistée par IA. L'auteur signalé n'est pas prévenu par l'application (risque de représailles) ; la décision suit la procédure du socle (décision motivée, recours, PHAROS si nécessaire).

## 5. Vérification : `core/messaging/ReportProof`

Code commun du pôle Communauté, appelé par le client de l'équipe (étape 3 de M30) ; le socle n'affiche que des métadonnées et ne déchiffre jamais.

- `ReportProof.verify(seal, keys, reporter): ReportProof.Result`, avec `Result = Proven(author, rumor) | Unproven(reason)`.
- Contrôles, dans l'ordre ; le premier qui échoue donne la raison :
  1. le sceau est un événement de type 13 dont l'identifiant et la signature BIP-340 sont valides ;
  2. son contenu est une charge NIP-44 v2 bien formée ; le code d'authentification calculé avec la clé HMAC fournie est égal à celui de la charge (comparaison en temps constant) ;
  3. le déchiffrement avec la clé ChaCha et le nonce fournis donne un remplissage valide et un UTF-8 valide ;
  4. le texte obtenu est un rumor JSON de type 14, dont l'identifiant recalculé est le sien (`Rumor.isConsistent`) et dont l'auteur est la clé du sceau (règle de NIP-17 contre l'usurpation) ;
  5. les étiquettes `p` du rumor contiennent `reporter`.
- Pourquoi des clés inventées ne passent pas : NIP-44 n'engage pas la clé de chiffrement (le code d'authentification couvre le texte chiffré, pas la clé ChaCha), mais trouver une autre clé ChaCha qui transforme un texte chiffré signé en un rumor JSON valide, dont l'empreinte SHA-256 est son propre identifiant, est hors de portée (il faudrait inverser ChaCha20 et SHA-256). Les contrôles 3 et 4 sont donc ce qui ferme la porte : ils sont obligatoires.
- Le client affiche « Message prouvé : écrit par cette clé, adressé au signaleur » ou « Copie non prouvée », avec le texte **déchiffré** ; il peut renvoyer au serveur le code `proof=proven|unproven` (métadonnée journalisée) pour la file ; rien de plus.
- Tests prévus : vecteurs NIP-44 officiels (déjà au cœur) ; un vrai message prouvé de bout en bout ; sceau d'un autre auteur ; signature altérée ; clé HMAC fausse ; clé ChaCha fausse ; rumor dont l'auteur n'est pas celui du sceau ; rumor non adressé au signaleur ; version de format inconnue refusée ; plus de 10 messages refusés.

## 6. Format `ppf-report-dm`, version 1 (proposition à confirmer par le Directeur technique, à écrire dans `docs/FORMATS.md` avec le code)

```json
{
  "format": "ppf-report-dm",
  "version": 1,
  "reason": "harcelement",
  "note": "facultatif, 500 caractères au plus",
  "reporter": "<clé publique du signaleur, hexadécimal>",
  "messages": [
    {
      "seal": { "...": "événement de type 13 complet, signé par l'auteur" },
      "keys": { "chacha_key": "<hex>", "chacha_nonce": "<hex>", "hmac_key": "<hex>" }
    },
    {
      "unproven": { "author": "<hex>", "created_at": 1767225600, "text": "copie annoncée par le signaleur" }
    }
  ]
}
```

- Chaque message est soit prouvé (`seal` et `keys`), soit une copie non prouvée (`unproven`), jamais les deux.
- 10 messages au plus avec l'option 1 (11 avec l'option 2) ; motif dans la liste fermée ; lecteur strict qui refuse une version inconnue ou un champ manquant.
- Différence avec la proposition du Directeur technique : ni enveloppe ni clés d'enveloppe (section 2.2), ni texte annoncé à côté d'un message prouvé.

## 7. Salons

- Les salons publics ne sont pas chiffrés : un message de salon se signale comme un contenu public (NIP-56 sur l'événement, S-38), sans clés ; les salons réservés chiffrés suivront MLS (plus tard).

## 8. Données

- Sur l'appareil : rien de nouveau. Le sceau relu reste en mémoire le temps de l'écran ; après l'envoi, seuls l'identifiant du message, la date d'envoi et le numéro de dossier sont gardés (« déjà signalé le … », pour éviter un doublon et retrouver la décision), sans le texte ; effacés par l'effacement rapide (S-44, partie `communaute-messaging`).
- Sur le serveur : le dossier chiffré dans la boîte de modération, durée de conservation fixée par l'étape 2 (DSA). À ajouter au registre RGPD à ce moment-là.

## 9. Menaces

- Faux signalement d'un message inventé : la preuve par le sceau le démasque ; sans preuve, la mention « non prouvée » le dit.
- Clés de message inventées pour faire dire autre chose à un sceau authentique : refusées par les contrôles 3 et 4 de la section 5.
- L'équipe qui garderait les clés d'un message : elles n'ouvrent que ce message, déjà montré par le signaleur.
- Fuite d'autres messages : seules les clés des messages choisis sortent, jamais la clé de conversation. Les pseudonymes des destinataires d'un message prouvé sont visibles de l'équipe (inévitable : ils sont signés avec lui), et l'écran le dit.
- Un téléphone saisi : il ne contient pas plus de preuves qu'aujourd'hui (aucun sceau gardé ; la relecture des enveloppes encore sur le serveur était déjà possible).
- Le serveur principal compromis : il ne lit pas les signalements (chiffrés pour l'équipe) ; il peut au plus rapprocher une relecture de boîte d'un dossier arrivé peu après (section 2.3).

## 10. Réponses du Directeur technique (8 novembre 2026)

1. **Clé** : une clé par usage, et elle existe déjà : `PPF_MODERATION_KEY`, la clé de l'équipe de modération (témoignages d'Agir, infrastructure des deux environnements), distincte de la boîte de M30. Côté application, intégrée par environnement (comme `ContactInbox`), jamais reçue du serveur.
2. **Format** : `{"format":"ppf-report-dm","version":1,"reason":<liste fermée de la charte>,"note":<500 caractères au plus, facultatif>,"reporter":<clé publique du signaleur, destinataire des enveloppes>,"messages":[{"wrap_id","keys":{"wrap":{...},"seal":{...}},"text","created_at","author"}]}` ; 11 messages au plus (le signalé, 5 avant, 5 après : borne haute) ; clés NIP-44 d'un message en hexadécimal (clé ChaCha, nonce, clé HMAC) ; lecteur strict qui refuse une version inconnue. Ma contre-proposition est en section 6.
3. **Transport** : pas un événement 1984 en clair ; un dossier de M30, ticket `report`, adressé à la boîte de modération (section 4).
4. **Vérification** : dans le client de l'équipe, par du code commun du pôle Communauté (section 5).

## 11. Ce qui reste avant de coder

- Chef d'équipe : valider l'option C (section 2.1) et choisir le contexte (section 2.4, option 1 recommandée) ; si c'est l'option 1, faire valider par la propriétaire la correction du cahier fonctionnel.
- Directeur technique : accord sur le format sans enveloppe (section 6) ; étape 2 du socle (signalements, DSA) et étape 2 de M30 (boîtes par usage).
