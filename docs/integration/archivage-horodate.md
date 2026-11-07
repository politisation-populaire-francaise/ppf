# Archivage horodaté : conception technique (D-111)

- Pôle « Décider », 7 novembre 2026. Décision technique du chef d'équipe : D-111. Ce document précise comment la réaliser ; rien n'est codé.
- But : prouver qu'un texte existait à une date donnée, sans qu'on puisse le nier ni le retoucher (CLAUDE.md, section 7 ; cahier des charges, 3.2). Premiers usages : le texte du Pacte et chaque réponse de candidat ; puis les copies archivées de « Voir » (M4) et les empreintes des témoignages d'« Agir » (M5).
- Faits vérifiés le 7 novembre 2026 dans les sources de python-opentimestamps, opentimestamps-server et opentimestamps-client, la RFC 3161 et les pages de FreeTSA (adresses en fin de document).

## 1. Ce qui sort du serveur, et rien d'autre

- **Seul le serveur** contacte les services d'horodatage ; l'application ne contacte jamais un tiers.
- Il n'envoie qu'**une empreinte par lot** : les empreintes SHA-256 à horodater pendant une heure sont rassemblées dans un arbre de Merkle (comme le fait le client officiel) ; seule la racine part. Chaque feuille reçoit 16 octets aléatoires avant d'entrer dans l'arbre : un calendrier ne voit donc jamais l'empreinte d'origine, seulement SHA-256(empreinte ‖ aléa) combinée aux autres.
- Jamais un contenu, jamais une donnée d'utilisateur, jamais une adresse IP de citoyen.

## 2. OpenTimestamps (preuve principale)

### 2.1 Le format `.ots`, écrit par nous (`core/archive`, Kotlin commun)
- En-tête de 31 octets (`00 4f 70 65 6e 54 69 6d 65 73 74 61 6d 70 73 00 00 50 72 6f 6f 66 00 bf 89 e2 e8 84 e8 92 94`), version majeure `0x01`, opération de hachage du fichier, empreinte, puis l'arbre de la preuve ; tout octet en trop est refusé.
- Opérations : `f0` ajouter après, `f1` ajouter avant (argument de 1 à 4 096 octets), `08` SHA-256, `03` RIPEMD-160, `02` SHA-1, `f2` inverser, `f3` hexadécimal ; `67` Keccak-256 seulement dans les branches Ethereum, qu'on garde sans les vérifier. SHA-256, RIPEMD-160 et SHA-1 viennent de bitcoin-kmp, déjà dans le projet (la JVM n'a pas RIPEMD-160).
- Embranchements `ff`, attestations `00` + 8 octets d'étiquette + contenu : en attente `83dfe30d2ef90c8e` (adresse du calendrier), bloc Bitcoin `0588960d73d71901` (hauteur), Litecoin et inconnues gardées octet pour octet. Entiers en LEB128 ; profondeur au plus 256.
- Lecture puis réécriture identique octet pour octet : un fichier produit par nous est lisible par le client officiel (`ots verify`), et inversement.

### 2.2 Obtenir et compléter une preuve
- `POST /digest` (corps : la racine du lot, 64 octets au plus ; en-tête `Accept: application/vnd.opentimestamps.v1`) auprès des **quatre agrégateurs par défaut du client officiel** ; il faut **au moins deux réponses** (D-111), sinon le lot est relancé plus tard.
- Mise à niveau : `GET /timestamp/<hex>` sur l'adresse du calendrier écrite dans chaque attestation en attente, seulement si elle est dans la liste blanche du client officiel (`*.calendar.opentimestamps.org`, `*.calendar.eternitywall.com`, `*.calendar.catallaxy.com`), en HTTPS, sans redirection vers un autre hôte, réponse de 10 000 octets au plus. Réponse 404 « Pending confirmation » : on réessaie environ toutes les heures ; l'ancrage dans Bitcoin prend de 2 à 15 heures.
- Tâche interne du serveur, avec délai d'attente, recul exponentiel et peu d'essais (demande du Directeur technique) ; URLs de base et mandataire HTTPS réglables par environnement.

### 2.3 Vérifier
- **Sur l'appareil** : l'application recalcule le chemin de Merkle de la preuve, de l'empreinte jusqu'à la racine attestée, et affiche « ancré dans le bloc Bitcoin n° … » avec la date du bloc (à une ou deux heures près, comme toute date de bloc).
- **Sur le serveur** : l'en-tête de 80 octets du bloc est demandé à **deux explorateurs publics indépendants** (API Esplora de mempool.space et de blockstream.info) ; les deux doivent être identiques, son double SHA-256 doit redonner le hachage du bloc, sa preuve de travail doit respecter sa difficulté, et sa racine de Merkle (octets 36 à 67, dans l'ordre stocké) doit être égale au résultat de la preuve. Un désaccord bloque la mention « vérifiée » et prévient l'équipe.
- **Par chacun** : le fichier `.ots` se télécharge depuis la page du contenu ; le guide explique comment le vérifier avec le client officiel et son propre nœud Bitcoin.

## 3. RFC 3161 (preuve immédiate, en complément)

- La requête ne contient que l'empreinte (la racine du lot) et un nombre aléatoire ; elle n'identifie pas le demandeur (RFC 3161, 2.4.1).
- Autorité retenue : **FreeTSA** (`https://freetsa.org/tsr`). Ses conditions l'autorisent : usage gratuit, y compris commercial, sans attribution obligatoire, une requête par seconde au plus, sans garantie de service ; projet indépendant à but non lucratif. Certificat de signature P-384 valable jusqu'en février 2040 (l'ancien, expiré, est gardé pour relire les anciens jetons). Aucun compte, aucune dépense.
- DigiCert, Sectigo et GlobalSign sont écartés : leurs conditions pour un usage hors signature de code ne sont pas claires.
- Côté serveur seulement, avec BouncyCastle (`bcpkix-jdk18on` 1.86) : demande, vérification du jeton et de la chaîne de certificats, conservation du jeton. L'application affiche l'heure attestée.
- Valeur juridique : un horodatage non qualifié (OpenTimestamps comme FreeTSA) est recevable (eIDAS, article 41, paragraphe 1), mais c'est à celui qui s'en sert d'en prouver la fiabilité ; seul un horodatage **qualifié** bénéficie d'une présomption d'exactitude (article 41, paragraphe 2). Un prestataire qualifié est payant (par exemple, Aruba : de 13,50 euros les 50 à 300 euros les 2 000, hors taxes) : décision de la propriétaire, plus tard, si un usage judiciaire le demande.

## 4. Hôtes contactés par le serveur (pour `infra/EGRESS.md`, THREAT_MODEL et REGISTRE)

| Hôte (HTTPS, port 443) | Rôle | Ce qui part | Fréquence |
|---|---|---|---|
| a.pool.opentimestamps.org, b.pool.opentimestamps.org, a.pool.eternitywall.com, ots.btc.catallaxy.com | Agrégateurs OpenTimestamps | La racine d'un lot (32 octets) | Au plus une fois par heure |
| alice.btc.calendar.opentimestamps.org, bob.btc.calendar.opentimestamps.org, finney.calendar.eternitywall.com, btc.calendar.catallaxy.com | Calendriers (mise à niveau) | Une valeur intermédiaire de la preuve | Environ une fois par heure et par preuve en attente |
| mempool.space, blockstream.info | En-têtes de blocs Bitcoin | Une hauteur de bloc, un hachage de bloc | Une fois par bloc d'ancrage |
| freetsa.org | Autorité d'horodatage RFC 3161 | La racine d'un lot et un nombre aléatoire | Au plus une fois par heure |

Production seulement par défaut ; la préproduction peut être réglée pour les utiliser aussi (elle n'envoie que des empreintes de contenus fictifs) ou pour un faux calendrier de test.

## 5. Stockage et formats

- Une table de preuves sur le serveur (migration Flyway, numéro à demander au chef d'équipe) : empreinte, lot, preuve `.ots`, état (en attente, ancrée, vérifiée), hauteur, hachage et date du bloc, jeton RFC 3161, jour de la demande. Aucune donnée personnelle : seulement des empreintes de contenus publics.
- API : `GET /api/v1/archives/<sha256>` (JSON : état, preuve en base64, bloc, jeton) et `GET /api/v1/archives/<sha256>.ots` (fichier binaire). L'étiquette `ppf-archive` existante garde son sens (empreinte, type de preuve, adresse de l'archive, date).
- Ce qui est horodaté : l'identifiant de chaque événement publié des types `pacte` et `engagement` (il est lui-même un SHA-256 du contenu signé), l'empreinte du texte du Pacte, l'empreinte de chaque document reçu ; plus tard, les copies archivées de « Voir » et les empreintes des témoignages.

## 6. Tests

- Fichiers `.ots` d'exemple du client officiel (`opentimestamps-client/examples`), sous licence LGPL-3.0-or-later, compatible avec l'AGPL-3.0 du projet ; ils seraient copiés dans les ressources de test avec leur mention de licence. Leur téléchargement dans le dépôt demandera l'accord de la propriétaire (nom, source, taille), comme tout téléchargement.
  - `hello-world.txt.ots` (bloc 358391, utilise RIPEMD-160), `bitcoin.pdf.ots` (empreinte SHA-1), `empty.ots`, `incomplete.txt.ots` et `two-calendars` (en attente), `merkle1` à `merkle3`, `bad-stamp.txt.ots` (doit échouer), `unknown-notary` (attestation inconnue gardée), `invalid/` (doivent être refusés).
- Tests unitaires octet pour octet repris des tests de python-opentimestamps (attestation en attente de « foobar » : `00 83dfe30d2ef90c8e 07 06 666f6f626172`).
- En-têtes de blocs enregistrés dans les tests (aucun réseau pendant les tests) ; faux calendrier et faux explorateurs pour le serveur.

## Sources

- python-opentimestamps : https://github.com/opentimestamps/python-opentimestamps (opentimestamps/core/timestamp.py, op.py, notary.py, serialize.py ; opentimestamps/calendar.py)
- opentimestamps-server : https://github.com/opentimestamps/opentimestamps-server (otsserver/rpc.py, calendar.py, otsd)
- opentimestamps-client : https://github.com/opentimestamps/opentimestamps-client (otsclient/cmds.py, args.py, examples/)
- API Esplora : https://github.com/Blockstream/esplora/blob/master/API.md
- RFC 3161 : https://www.rfc-editor.org/rfc/rfc3161.txt ; RFC 5816 : https://www.rfc-editor.org/rfc/rfc5816.txt
- FreeTSA : https://freetsa.org/index_en.php et https://freetsa.org/freetsa.html
- eIDAS consolidé : https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:02014R0910-20241018
- Aruba, tarifs des horodatages qualifiés : https://www.aruba.it/listino-marche-temporali.aspx
