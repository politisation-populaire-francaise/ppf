# Archivage horodaté : conception technique (D-111)

- Pôle « Décider », 7 novembre 2026. Décision technique du chef d'équipe : D-111. Ce document précise comment la réaliser. Codé le 7 novembre 2026 : le format `.ots` et la vérification Bitcoin dans `core/archive`, puis le côté serveur (section 7) ; ce qui reste est en section 8.
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
- Côté serveur seulement, avec BouncyCastle (`bcpkix-jdk18on` 1.86) : demande, vérification du jeton, conservation du jeton. L'application affiche l'heure attestée. La vérification porte sur la signature du jeton avec le certificat joint à la réponse ; l'empreinte SHA-256 de ce certificat est gardée avec le jeton. L'épinglage du certificat racine de FreeTSA (vérification de la chaîne jusqu'à lui) est une suite à faire : il demande de télécharger son fichier `cacert.pem`, téléchargement que la propriétaire doit d'abord accepter (section 8).
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

- Une table de preuves sur le serveur (migration Flyway `V10__archives.sql`) : empreinte, lot, preuve `.ots`, état (dans la file, en attente, ancrée, vérifiée), hauteur, hachage et date du bloc, jeton RFC 3161, jour de la demande. Aucune donnée personnelle : seulement des empreintes de contenus publics.
- API : `GET /api/v1/archives/<sha256>` (JSON : état, preuve en base64, bloc, jeton) et `GET /api/v1/archives/<sha256>.ots` (fichier binaire). L'étiquette `ppf-archive` existante garde son sens (empreinte, type de preuve, adresse de l'archive, date).
- Ce qui est horodaté : l'identifiant de chaque événement publié des types `pacte` et `engagement` (il est lui-même un SHA-256 du contenu signé), l'empreinte du texte du Pacte, l'empreinte de chaque document reçu ; plus tard, les copies archivées de « Voir » et les empreintes des témoignages.

## 6. Tests

- Fichiers `.ots` d'exemple du client officiel (`opentimestamps-client/examples`), sous licence LGPL-3.0-or-later, compatible avec l'AGPL-3.0 du projet ; ils seraient copiés dans les ressources de test avec leur mention de licence. Leur téléchargement dans le dépôt demandera l'accord de la propriétaire (nom, source, taille), comme tout téléchargement.
  - `hello-world.txt.ots` (bloc 358391, utilise RIPEMD-160), `bitcoin.pdf.ots` (empreinte SHA-1), `empty.ots`, `incomplete.txt.ots` et `two-calendars` (en attente), `merkle1` à `merkle3`, `bad-stamp.txt.ots` (doit échouer), `unknown-notary` (attestation inconnue gardée), `invalid/` (doivent être refusés).
- Tests unitaires octet pour octet repris des tests de python-opentimestamps (attestation en attente de « foobar » : `00 83dfe30d2ef90c8e 07 06 666f6f626172`).
- En-têtes de blocs enregistrés dans les tests (aucun réseau pendant les tests) ; faux calendrier et faux explorateurs pour le serveur.

## 7. Réalisé côté serveur (7 novembre 2026)

Code dans `server/src/main/kotlin/.../server/archive/`, tests dans `server/src/test/kotlin/.../server/archive/`.

- **Table** `archive_proof` (`V10__archives.sql`). États : `queued` (dans la file, pas encore de preuve), `pending` (lot horodaté par au moins deux calendriers, en attente de Bitcoin), `anchored` (un calendrier a donné une attestation Bitcoin), `verified` (bloc vérifié auprès des deux explorateurs). Chaque mise à jour nomme l'état attendu : une tâche lente n'écrase jamais le travail d'une autre. Les lignes ne sont jamais effacées.
- **Entrée** : `ArchiveService.stamp(empreinte)` met une empreinte SHA-256 dans la file (une seule fois ; marche aussi quand l'archivage est éteint). Aucun code ne l'appelle encore (section 8).
- **Lots** (`stampRound`, toutes les 60 minutes) : les empreintes de la file (10 000 au plus) entrent dans un arbre de Merkle `OtsBatch`, avec 16 octets aléatoires de `SecureRandom` par feuille ; la racine seule part, en `POST /digest` vers les quatre agrégateurs en même temps (corps : les 32 octets de la racine, en-tête `Accept: application/vnd.opentimestamps.v1`, réponse de 10 000 octets au plus, lue par `OtsTimestamp.read`). Une réponse ne compte que si elle mène à un calendrier de la liste blanche (ou déjà à Bitcoin). Avec moins de deux réponses, rien n'est gardé : les empreintes restent dans la file et repartent au lot suivant, avec de nouveaux aléas, donc une nouvelle racine.
- **RFC 3161** : juste après un lot réussi, la racine est envoyée à FreeTSA (empreinte SHA-256, nombre aléatoire de 64 bits, `certReq`). La réponse est vérifiée avec BouncyCastle (statut accordé, jeton présent, même nombre aléatoire, même empreinte et même algorithme, attribut du certificat signataire, signature du jeton avec le certificat joint, empreinte de ce certificat, usage « horodatage » seul et critique, validité du certificat à l'heure attestée). Le jeton DER, l'heure et l'empreinte SHA-256 du certificat sont écrits sur chaque ligne du lot. En cas d'échec, le lot reste horodaté par OpenTimestamps et son jeton est redemandé à chaque tour de mise à niveau, pour les lots des 7 derniers jours, 5 au plus par tour, une requête par seconde au plus, essais compris.
- **Mise à niveau** (`upgradeRound`, toutes les 60 minutes) : pour chaque attestation en attente des preuves `pending`, si l'adresse du calendrier est exactement `https://<hôte>` avec un hôte de la liste blanche (sans port, chemin, nom d'utilisateur, paramètre ni fragment ; jamais `http`), `GET <adresse>/timestamp/<hexadécimal du message de ce nœud>`, sans suivre de redirection. Réponse 200 : la preuve reçue est fusionnée à ce nœud et le `.ots` est réécrit ; 404 : toujours en attente. Les preuves d'un même lot partagent leurs nœuds de calendrier : une seule requête par calendrier et par lot. Un calendrier qui ne répond pas n'est plus interrogé avant le tour suivant.
- **Vérification Bitcoin** (même tour, preuves `anchored`) : pour chaque attestation Bitcoin, `GET /block-height/<hauteur>` puis `GET /block/<hachage>/header` auprès des deux explorateurs ; deux hachages différents donnent `SOURCES_DISAGREE` sans aller plus loin ; sinon `OtsBitcoinVerifier.check`. Seul `VERIFIED` fait passer la preuve à `verified`, avec la plus petite hauteur qui se vérifie, le hachage et l'heure du bloc lue dans son en-tête. Tout autre résultat est compté et journalisé par son code seul (`SOURCES_DISAGREE`, `WRONG_BLOCK`, `NOT_MINED`, `WRONG_MERKLE_ROOT` en avertissement pour l'équipe) ; la preuve reste ancrée, non vérifiée, et sera revérifiée au tour suivant. Si un explorateur ne répond pas, la vérification s'arrête pour ce tour.
- **Client HTTP sortant** (`ArchiveHttp`), demandes du Directeur technique : adresses de base réglables par environnement ; mandataire HTTPS lu dans les propriétés de la JVM `https.proxyHost` et `https.proxyPort`, sinon dans `HTTPS_PROXY` (ou `https_proxy`), de la forme `http://hôte:port` (tunnel `CONNECT` : le TLS reste de bout en bout ; pas d'authentification au mandataire ; `NO_PROXY` n'est pas lu) ; délai d'attente sur chaque requête (30 secondes par défaut, aussi sur la connexion et la socket) ; trois essais au plus, après 2 puis 4 secondes, et seulement après un échec qui peut passer (pas de réponse, 429, 5xx) ; jamais de redirection suivie ; corps lus jusqu'à une taille fixe ; agent utilisateur fixe et générique.
- **Tâches** : deux tâches internes du serveur, qui vivent et meurent avec l'application ; le premier tour attend 2 minutes après le démarrage (un serveur qui redémarre en boucle n'inonde personne) ; un tour ne commence jamais pendant qu'un autre tourne.
- **Allumage** : `PPF_ARCHIVE_ENABLED`, allumé par défaut en production seulement, éteint en développement, en test et en préproduction tant qu'on ne le règle pas à `true`. Éteint, le serveur ne contacte personne, met les empreintes dans la file et sert les preuves déjà gardées. Variables dans `docs/FORMATS.md`, section 5.
- **API** : `GET /api/v1/archives/<sha256>` et `GET /api/v1/archives/<sha256>.ots` (format dans `docs/FORMATS.md`, section 5).
- **Journaux** : des nombres, des codes courts et des noms de classes ; jamais une empreinte, une racine, une preuve, une adresse qui contient un message, ni un corps reçu. Un test capture tous les journaux pendant un horodatage complet et vérifie qu'aucune empreinte, racine ou adresse n'y figure.
- **Tests** (24, sans aucun réseau : moteur `MockEngine` de Ktor et PostgreSQL embarqué) : une seule racine par lot, la même pour les quatre agrégateurs et pour l'autorité, aucune empreinte dans les adresses ni dans les corps envoyés ; au moins deux réponses (une seule ne suffit pas, l'agrégateur en panne est essayé trois fois, 2 puis 4 secondes d'écart, une nouvelle racine au lot suivant) ; liste blanche (hôte inconnu, `http`, port, chemin, nom d'utilisateur, majuscules du schéma refusés, et aucune requête vers eux) ; fusion d'une mise à niveau (404 puis 200 avec une attestation Bitcoin) puis vérification sur un en-tête miné pour l'occasion ; deux explorateurs en désaccord (en-tête ou hachage) ne donnent jamais `verified`, un explorateur en panne non plus, le bloc de genèse oui ; une fausse preuve reste ancrée ; jetons RFC 3161 d'une fausse autorité construite avec BouncyCastle dans le test (bon jeton gardé ; mauvais nombre aléatoire, mauvaise empreinte, refus, jeton sans certificat, signature modifiée, octets quelconques refusés) ; une requête par seconde au plus vers l'autorité ; réglages, mandataire et délais ; API (JSON, fichier `.ots` relu par `OtsFile.read`, 400, 404, 503 sans base).

## 8. Reste à faire

- **Brancher `stamp()`** sur ce qui doit être horodaté : identifiant de chaque événement publié des types `pacte` et `engagement`, empreinte du texte du Pacte, empreinte de chaque document reçu ; plus tard, les copies archivées de « Voir » et les empreintes des témoignages. À faire avec le pôle concerné, dans le port de publication.
- **Épingler le certificat racine de FreeTSA** (`cacert.pem`, publié sur freetsa.org) et vérifier la chaîne jusqu'à lui : demande un téléchargement que la propriétaire doit accepter (nom, source, taille). En attendant, le jeton est vérifié avec le certificat joint et l'empreinte de ce certificat est gardée.
- **Côté application** : afficher l'état de la preuve, le bloc et l'heure attestée, recalculer le chemin de Merkle sur l'appareil, proposer le fichier `.ots` à télécharger avec le guide de vérification par le client officiel.
- **Liste des sorties** : reporter `docs/integration/archivage-egress.md` dans `infra/EGRESS.md` (Directeur technique) avant d'allumer l'archivage en préproduction ou en production.
- **Alerte à l'équipe** : aujourd'hui, un désaccord entre explorateurs ou une fausse preuve n'apparaît que dans le journal du serveur (avertissement) ; une alerte dans le back-office viendra avec lui.
- **Preuves bloquées** : une preuve dont aucun calendrier ne confirme jamais reste `pending` et est réinterrogée à chaque tour (une requête par calendrier et par lot) ; signaler à l'équipe les preuves en attente depuis plus de 48 heures.
- **Fichiers d'exemple du client officiel** (section 6) : toujours à télécharger avec l'accord de la propriétaire.
- **Numéros de migration** : provisoires depuis le 7 novembre 2026 (chef d'équipe) ; `V10__archives.sql` depuis le 7 novembre 2026, après `V7__territoires.sql` de « Comprendre » (entré le premier dans `main`) et `V9__urnes.sql` ; renommé encore à l'entrée dans `main` si un autre pôle y entre avant.

## Sources

- python-opentimestamps : https://github.com/opentimestamps/python-opentimestamps (opentimestamps/core/timestamp.py, op.py, notary.py, serialize.py ; opentimestamps/calendar.py)
- opentimestamps-server : https://github.com/opentimestamps/opentimestamps-server (otsserver/rpc.py, calendar.py, otsd)
- opentimestamps-client : https://github.com/opentimestamps/opentimestamps-client (otsclient/cmds.py, args.py, examples/)
- API Esplora : https://github.com/Blockstream/esplora/blob/master/API.md
- RFC 3161 : https://www.rfc-editor.org/rfc/rfc3161.txt ; RFC 5816 : https://www.rfc-editor.org/rfc/rfc5816.txt
- FreeTSA : https://freetsa.org/index_en.php et https://freetsa.org/freetsa.html
- eIDAS consolidé : https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:02014R0910-20241018
- Aruba, tarifs des horodatages qualifiés : https://www.aruba.it/listino-marche-temporali.aspx
