# Messagerie chiffrée : cœur dans `shared/core` (note d'intégration)

Branche `worktree-agent-ae49a3e8d47c5b225`, 6 novembre 2026. Mise en œuvre de la décision D-027 (option A : conversations privées et de petit groupe en NIP-17 sur NIP-59 et NIP-44, notre serveur comme relais) dans le code commun, sans interface et sans serveur. Cette note sert à la session principale pour fusionner la branche et mettre à jour `CHANGELOG.md`, `ROADMAP.md`, `FORMATS.md`, `REGISTRE_DONNEES.md` et `DECISIONS.md`, qui n'ont pas été touchés.

## 1. Ce qui est fait

Sept commits, un par étape plus les corrections de la relecture, tous dans `shared/core` (plus cette note) :

| Commit | Contenu |
|---|---|
| `feat(core): add ChaCha20, HMAC-SHA-256, HKDF-SHA-256 and constant-time comparison` | `core/crypto/ChaCha20.kt`, `HmacSha256.kt`, `HkdfSha256.kt`, `ConstantTime.kt` ; tests avec les vecteurs des RFC 8439, 4231 et 5869 |
| `feat(core): add NIP-44 v2 encrypted payloads` | `core/messaging/Nip44.kt` ; tests `Nip44Test` (commun) et `Nip44VectorsTest` (JVM, vecteurs officiels complets) |
| `feat(core): add NIP-59 gift wraps and NIP-17 chat messages` | `core/messaging/GiftWrap.kt` (`Rumor`, `GiftWrap`, `GiftWrapException`), `ChatMessage.kt` (`Conversation`, `ChatMessage`), `MessagingSigner.kt` ; kinds 13, 14 et 1059 dans `EventKinds` ; tests dont les exemples complets des textes NIP-59 et NIP-17 |
| `feat(core): store conversations and messages on the device` | `sqldelight/.../db/Messaging.sq`, migration `db/migrations/2.sqm`, `core/messaging/MessageStore.kt` ; tests `MessageStoreTest`, `SchemaMigrationTest` |
| `feat(core): add the messaging service and tag filters on remote events` | `core/messaging/MessagingService.kt` ; `RemoteEvents.fetchByTags` et `TagFilter` ; `ServerPublicationPort` et `InMemoryPublicationPort` ; tests `MessagingServiceTest`, `InMemoryPublicationPortTest` |
| `fix(core): harden message delivery after review` | corrections issues de la relecture par un sous-agent et des règles du serveur (section 2 bis) : file d'attente par destinataire avec enveloppes reconstruites à l'envoi, relevé résistant aux inondations, limite de taille des textes, clés de destinataires vérifiées avant tout stockage, `429` traité comme « réessayer », dates futures ramenées à maintenant, pagination des messages par (date, id), `MessagingSigner` exposant chiffrer et déchiffrer plutôt que la clé |
| `docs(integration): ...` | cette note |

Tests : `gradlew :shared:core:jvmTest` passe 110 tests (49 existants, 61 nouveaux). `gradlew :shared:core:compileKotlinIosArm64` compile (aucune API JVM dans `commonMain` ; les seuls usages JVM sont dans `jvmTest`).

### Architecture en une image

```
texte  →  ChatMessage.compose  →  Rumor (kind 14, non signé, id calculé)
       →  GiftWrap.seal   : NIP-44(rumeur, clé expéditeur↔destinataire), kind 13 signé par l'expéditeur, sans étiquette, date aléatoire
       →  GiftWrap.wrap   : NIP-44(sceau, clé jetable↔destinataire), kind 1059 signé par la clé jetable, ["p", destinataire], date aléatoire
       →  PublicationPort.publish (une enveloppe par participant, expéditeur compris)
serveur : ne voit que des kind 1059 (auteur aléatoire, étiquette p, date brouillée, contenu opaque)
réception : RemoteEvents.fetchByTags(p = ma clé) → GiftWrap.unwrap → ChatMessage.fromRumor → MessageStore
```

### API principale

- `MessagingSigner` (étend `EventSigner`) : `encrypt(recipient, text)` et `decrypt(sender, payload)` (NIP-44) en plus de `sign`, à la manière des signataires Nostr (NIP-07, NIP-46) qui n'exposent jamais la clé. `LocalMessagingSigner(IdentityKeys)` sur l'appareil ; un signataire distant pourra l'implémenter plus tard.
- `Nip44.conversationKey(keys, other)`, `Nip44.encrypt(text, key, nonce = aléatoire)`, `Nip44.decrypt(payload, key)` ; `Nip44Exception` pour tout ce qui est invalide.
- `GiftWrap.seal`, `GiftWrap.wrap`, `GiftWrap.wrapFor(rumor, sender, recipient, now)`, `GiftWrap.unwrap(wrap, recipient)` ; `GiftWrap.randomPastTimestamp(now)`.
- `ChatMessage.compose(author, recipients, text, createdAt, subject?, replyTo?)` (texte de 1 à `MAX_TEXT_BYTES` = 16 Kio), `ChatMessage.fromRumor(rumor)`, `message.toRumor()`, `message.recipients` ; `Conversation.idOf(participants)`, `Conversation.participantsOf(id)`.
- `MessageStore(database, clock)` : `store(message, read)`, `find`, `conversations(limit)`, `conversation(id)`, `messages(conversationId, limit, before: ChatMessage?)`, `markRead`, `markMessageRead`, `unreadCount`, `deleteConversation`, `queueOutgoing(messageId, recipient)`, `outgoing`, `removeOutgoing`, `outgoingCount`, `lastPullAt`, `setLastPullAt`.
- `MessagingService(signer, store, remote, clock)` : `send(text, recipients, subject?, replyTo?)` → `SendReport` (lève `IllegalArgumentException` avant tout stockage si le texte est vide ou trop long, ou si une clé de destinataire n'est pas un point valide de la courbe) ; `flushOutgoing()` → `FlushReport` ; `receive(wraps)` → `ReceiveReport` ; `pull(pageSize, maxPages)` → `PullReport`.
- `RemoteEvents.fetchByTags(tags, filter)` avec `TagFilter(name, value)` (bornes du serveur : nom de 1 à 64 caractères, valeur de 1024 octets au plus, 20 filtres par requête) ; `InMemoryPublicationPort` implémente désormais `RemoteEvents` (filtres du serveur reproduits en mémoire). `ServerPublicationPort.publish` traite `429 rate_limited` comme `Unavailable` (l'enveloppe est gardée et renvoyée plus tard ; `ServerException.retryAfterSeconds` porte l'en-tête `Retry-After`).

### Branchement à faire par la session principale (hors périmètre de cette branche)

1. `AppDependencies` : construire `MessageStore(database)` et `MessagingService(LocalMessagingSigner(keys), store, remote)` quand un compte et un serveur sont configurés ; appeler `pull()` et `flushOutgoing()` dans le cycle de synchronisation existant (`SyncService` ne connaît pas la messagerie, volontairement).
2. Écrans de conversation dans `shared/ui/.../messages` : liste `store.conversations()`, fil `store.messages(id)`, envoi `service.send(...)`, `store.markRead(id)` à l'ouverture.
3. Serveur : fusionner la branche de l'agent serveur **avant** toute version de test reliée à un serveur : sans le filtre `tag=`, `pull()` reçoit toutes les enveloppes du serveur, les ouvre pour rien (deux ECDH et deux vérifications de signature chacune) et épuise son budget de pages. Idéalement le serveur répond `400` à un paramètre inconnu, pour qu'un décalage de version soit bruyant plutôt que silencieux.
4. Documents : paragraphes prêts ci-dessous pour `FORMATS.md` (section 3) et `REGISTRE_DONNEES.md` (section 4) ; entrée `CHANGELOG.md` ; ligne « Messagerie (M14) » de `ROADMAP.md`.

## 2 bis. Règles du serveur prises en compte (information de l'agent serveur, 6 novembre 2026)

| Règle du serveur | Ce que fait le code commun |
|---|---|
| `GET /api/v1/events?tag=<nom>:<valeur>` répétable, 20 filtres au plus, même nom = OU, noms différents = ET, nom de 1 à 64 caractères, valeur de 1024 octets au plus, combinable avec `kind`, `author`, `since`, `until`, `limit` | `TagFilter` vérifie les bornes ; `ServerPublicationPort.fetchByTags` refuse plus de 20 filtres ; `InMemoryPublicationPort` applique la même sémantique ; `pull()` n'utilise qu'un filtre `p` |
| Une enveloppe kind 1059 porte **exactement une** étiquette `p` (hexadécimal minuscule), sinon `400 bad_recipient` | `GiftWrap.wrap` produit exactement une étiquette `p` ; `send` fabrique une enveloppe par participant |
| Enveloppes conservées 30 jours, 1000 au plus par destinataire | `pull()` relève 200 enveloppes par page et 10 pages au plus par défaut (2000 ≥ 1000), `pageSize` borné à 500 (limite du serveur) ; un appareil absent plus de 30 jours perd les messages intermédiaires (à expliquer dans l'interface) |
| `POST /api/v1/events` peut répondre `429 rate_limited` avec `Retry-After` (60 envois par minute et par clé) | `ServerPublicationPort.publish` renvoie `Unavailable(ServerException(429, retryAfterSeconds))` : la livraison reste en file et `flushOutgoing()` la refait plus tard ; `send` et `flushOutgoing` s'arrêtent au premier `Unavailable`, donc un gros groupe (plus de 60 participants) se vide en plusieurs passes |
| Un message de salon NIP-29 (kind 9) porte exactement une étiquette `h` | Non concerné par cette branche (salons non commencés) ; à respecter dans le futur module des collectifs |

Note : la limite de 60 envois par minute **et par clé** s'applique à la clé qui signe, c'est-à-dire à une clé jetable différente par enveloppe ; côté client, il faudra sans doute un autre critère (preuve de travail ALTCHA, question ouverte n° 3).

## 2. Choix faits (autonomie, à consigner dans `DECISIONS.md` si utile)

1. **Deux limites de taille.** Couche NIP-44 : 65 535 octets de texte clair (`Nip44.MAX_PLAINTEXT_BYTES`) ; le NIP-44 autorise jusqu'à 4 Gio avec un préfixe de longueur étendu et demande à chaque implémentation de fixer sa propre limite. Le rembourrage est codé exactement comme la spécification actuelle (préfixe de 2 octets, préfixe étendu de 6 octets au-delà de 65 535), mais la limite par défaut refuse, avant tout décodage, les charges utiles plus grandes (protection mémoire sur téléphone). La limite est un paramètre des fonctions, ce qui permet de tester les vecteurs du préfixe étendu. Couche message : **16 Kio de texte** (`ChatMessage.MAX_TEXT_BYTES`), vérifiés dans `compose` avant tout stockage, parce que deux couches de rembourrage, de base64 et de JSON multiplient la taille par 2,4 environ et que le serveur refuse les événements de plus de 64 Kio : le plafond réel est d'environ 26 Kio, 16 Kio laisse de la marge.
2. **Vecteurs officiels NIP-44** : le fichier `nip44.vectors.json` (domaine public, dépôt nostr-protocol/nips, servi depuis github.com/paulmillr/nip44, téléchargé le 6 novembre 2026) est conservé octet pour octet dans `shared/core/src/jvmTest/resources/nip44/nip44.vectors.json.gz`. Il est compressé parce que gitleaks (crochet et intégration continue, sans fichier de configuration dans le dépôt) prend les clés d'exemple `conversation_key` et `hmac_key` pour des secrets, et décode aussi le base64. Le test vérifie l'empreinte SHA-256 du fichier décompressé (`269ed0f6…5040`, celle imprimée dans le texte du NIP) avant de rejouer les vecteurs. Alternative plus propre si la propriétaire le souhaite : un `.gitleaks.toml` à la racine qui met ce dossier en liste blanche ; les clés d'exemple des textes NIP-59 et NIP-17 utilisées dans les tests portent un commentaire `gitleaks:allow`.
3. **ECDH par `Secp256k1.pubKeyTweakMul(02‖x, clé secrète)`**, coordonnée x non hachée, comme le demande le NIP-44 ; la clé secrète est validée (`secKeyVerify`) et la clé publique est rejetée si elle n'est pas sur la courbe (les huit cas invalides des vecteurs passent). Aucune nouvelle dépendance.
4. **Identifiant de conversation** = clés publiques des participants (moi compris) triées et jointes par des virgules, calculé par chaque appareil ; la table `conversation` ne stocke pas la liste à part, l'identifiant la contient.
5. **Sujet** : le plus récent (`created_at` de la rumeur) l'emporte, même s'il arrive dans le désordre (`subject_at`).
6. **Enveloppe pour soi-même** : chaque envoi produit une enveloppe par destinataire plus une pour l'expéditeur (NIP-17), ce qui permet à ses autres appareils de retrouver ses propres messages ; à la réception, un message dont je suis l'auteur est marqué lu.
7. **File d'attente par destinataire** (`outgoing_message` : identifiant du message, destinataire, date de mise en file). `send` fabrique d'abord toutes les enveloppes (ce qui vérifie les clés), puis enregistre le message, puis les lignes de livraison, puis publie ; une livraison acceptée ou refusée pour de bon est retirée (`delivered` ou `rejected`), une livraison que le serveur ne peut pas prendre (injoignable, `429`) reste en file. `flushOutgoing()` **refait une enveloppe neuve** (clé jetable et dates aléatoires fraîches) pour chaque livraison en attente : un message écrit hors ligne est ainsi daté du moment où il part, sinon la fenêtre de relevé des destinataires (point 8) pouvait le manquer. Si le serveur accepte une enveloppe mais que la réponse se perd, il en recevra une seconde pour le même message : les destinataires dédoublonnent par identifiant de rumeur. Le message reste lisible localement dans tous les cas.
8. **Curseur de réception** : les dates des enveloppes sont aléatoires jusqu'à deux jours dans le passé ; `pull()` interroge donc depuis « date du dernier `pull` complet, moins deux jours, moins une heure de marge » et laisse `MessageStore` ignorer les doublons. Le premier `pull` d'un appareil récupère tout l'historique (1000 enveloppes au plus, limite du serveur). Le curseur n'avance que si le `pull` est complet (`PullReport.complete`). Pagination du plus récent au plus ancien par `until` inclusif ; si la date la plus ancienne d'une page ne bouge pas (page entière à la même seconde, ce qui n'arrive qu'en cas d'inondation volontaire), le relevé saute cette seconde au lieu de s'arrêter, pour qu'une inondation ne puisse pas cacher les enveloppes plus anciennes (test `aFloodOfWrapsSharingOneDateCannotHideOlderMessages`). Un curseur côté serveur (date de réception ou numéro de séquence, paramètre `after=`) serait plus robuste que les dates déclarées : proposition pour une prochaine version de l'API.
9. **Robustesse de la réception** : tout ce qui n'est pas une enveloppe authentique adressée à ma clé, contenant un sceau authentique dont l'auteur est celui de la rumeur, avec un kind 14 bien formé dont je suis participant, est compté `ignored` et jeté sans exception. Une date déclarée plus de quinze minutes dans le futur est ramenée à maintenant, pour qu'un expéditeur ne puisse pas épingler une conversation en tête de liste.
10. **Sceau avec étiquettes** : NIP-59 dit que le sceau n'a pas d'étiquette, mais NIP-17 recommande d'y copier une éventuelle étiquette `expiration` ; nos sceaux n'en ont aucune et nous acceptons ceux qui en ont.
11. **`fetchByTags` a une implémentation par défaut** dans l'interface `RemoteEvents` (`Unavailable` avec `UnsupportedOperationException`) pour ne casser aucune implémentation existante (faux serveurs des tests, autres branches). `ServerPublicationPort` et `InMemoryPublicationPort` la redéfinissent.
12. **Rumeur en JSON** : `kotlinx.serialization` avec `ignoreUnknownKeys` et champs dans n'importe quel ordre, puisque les autres clients Nostr sérialisent différemment ; une rumeur qui contiendrait un `sig` est acceptée en ignorant ce champ.
13. **Pas codé, volontairement** : messages de fichiers (kind 15), suppression ou édition par kind 5 enveloppé, messages éphémères (`expiration`, kind 21059), listes de relais kind 10050 (notre serveur est le seul relais en phase 1), accusés de lecture et « en train d'écrire » (désactivés par défaut selon D-027 : rien ne quitte l'appareil), message à soi-même seul (NIP-17 l'autorise ; `compose` exige au moins un autre participant, à rouvrir si l'interface veut des « notes pour moi »), salons NIP-29, MLS.
14. **Pagination des messages d'une conversation** par couple (date, identifiant) (`messages(conversationId, limit, before = dernierMessageAffiché)`), pour que deux messages de la même seconde ne se perdent pas en remontant le fil.
15. **Relecture par un sous-agent** (diff complet) : la cryptographie et les enveloppes ont été jugées conformes ; cinq points importants ont été corrigés dans le commit `fix(core)` (inondation à date unique, enveloppes périmées après envoi différé, message stocké avant vérification des clés et de la taille, prérequis serveur, `429`), plus les points mineurs (dates futures, pagination, essuyage des tampons). Le seul point non repris est la numérotation de la migration (section 5).

## 3. Paragraphes à ajouter dans `docs/FORMATS.md`

### Nouvelle section « Messagerie : enveloppes NIP-59, messages NIP-17 »

Les conversations privées (deux personnes ou petit groupe) suivent les NIP-17, NIP-59 et NIP-44 de Nostr. Le serveur ne voit jamais ni l'auteur réel, ni les destinataires au-delà de l'étiquette de livraison, ni la date réelle, ni le contenu.

**Message (rumeur, kind 14)** : un événement NIP-01 **non signé**, avec son `id` mais sans `sig`. `content` est le texte brut du message (ni Markdown ni HTML). Étiquettes : une `["p", "<clé>"]` par destinataire (l'auteur n'y figure pas), `["e", "<id>"]` facultative pour une réponse, `["subject", "<sujet>"]` facultative. La conversation est l'ensemble des participants (auteur et destinataires) ; son identifiant local est leurs clés triées et jointes par des virgules. `created_at` est la vraie date du message. Une rumeur ne circule jamais seule.

**Sceau (kind 13)** : `content` = chiffrement NIP-44 (version 2) du JSON de la rumeur avec la clé de conversation de l'auteur et du destinataire ; signé par l'auteur ; aucune étiquette ; `created_at` tiré au hasard jusqu'à deux jours dans le passé. Le lecteur vérifie que la clé du sceau est celle de la rumeur, sinon n'importe qui pourrait écrire au nom de n'importe qui.

**Enveloppe (kind 1059)** : `content` = chiffrement NIP-44 du JSON du sceau avec une clé jetable, générée pour cette seule enveloppe puis détruite ; signée par la clé jetable ; une seule étiquette `["p", "<destinataire>"]` ; `created_at` aléatoire aussi. Il y a une enveloppe par participant, l'expéditeur compris (pour ses autres appareils). C'est le seul objet que le serveur reçoit et conserve.

**Chiffrement NIP-44 v2** : clé de conversation = HKDF-extract SHA-256 (sel `nip44-v2`) de la coordonnée x du point ECDH secp256k1 ; clés de message = HKDF-expand(clé de conversation, info = nonce de 32 octets, 76 octets) découpées en clé ChaCha20 (32), nonce ChaCha20 (12) et clé HMAC (32) ; texte rembourré à une taille qui ne révèle que l'ordre de grandeur ; charge utile = base64(0x02 ‖ nonce ‖ chiffré ‖ HMAC-SHA-256(nonce ‖ chiffré)). La plateforme accepte des textes de 1 à 65 535 octets. Le NIP-44 ne fournit ni confidentialité persistante ni déni : la compromission d'une clé expose les anciennes conversations ; c'est la limite connue acceptée par D-027 en attendant MLS.

### Section 5 (API du serveur), ligne `GET /api/v1/events`

Ajouter le paramètre `tag=<nom>:<valeur>`, répétable et combinable avec `kind`, `since`, `until` et `limit` : plusieurs valeurs pour un même nom sélectionnent l'une d'elles, des noms différents doivent tous correspondre (sémantique des filtres NIP-01). Exemple : `GET /api/v1/events?kind=1059&tag=p:<clé>&since=…`. C'est ainsi qu'une application récupère les enveloppes qui lui sont adressées. (À confirmer avec la mise en œuvre de l'agent serveur.)

## 4. Paragraphes à ajouter dans `docs/REGISTRE_DONNEES.md`

### Section 1 (serveur)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Enveloppes de messagerie (kind 1059) : contenu chiffré illisible pour le serveur, clé publique **jetable** de l'enveloppe, étiquette `p` = clé publique pseudonyme du destinataire, date **brouillée** (aléatoire jusqu'à deux jours avant l'envoi) | Transmettre les messages privés entre appareils, y compris hors ligne | Table `events` de PostgreSQL | 30 jours, et 1000 enveloppes au plus par destinataire (règle du serveur) ; les appareils gardent leur copie déchiffrée | Toute personne qui connaît la clé du destinataire peut lister ses enveloppes (nombre et dates brouillées, jamais le contenu ni l'expéditeur) tant que le serveur ne restreint pas la lecture par authentification (voir question ouverte n° 1) |

Ce que le serveur ne sait pas : qui écrit à qui (l'expéditeur est caché dans le sceau), le contenu, la vraie date, la liste des conversations ou des contacts de qui que ce soit.

### Section 1 bis (appareil)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Conversations (participants, sujet, date du dernier message) et messages déchiffrés (texte, auteur, date, réponse à, lu ou non) | Lire et écrire ses messages privés, hors ligne compris | Base locale SQLDelight, tables `conversation` et `message` ; Android : stockage privé de l'application ; ordinateur : fichier de données privé de l'utilisateur, **chiffrement par code à faire (S-43)** ; iOS : idem quand l'application existera | Jusqu'à suppression de la conversation par l'utilisateur ou déconnexion | L'utilisateur, par l'application |
| Livraisons en attente (`outgoing_message` : identifiant du message, destinataire, date de mise en file) | Envoyer dès que le serveur est joignable | Même base | Jusqu'à acceptation par le serveur | Idem |
| Date du dernier relevé (`messaging_state`) | Ne redemander au serveur que les enveloppes nouvelles | Même base | Permanent | Idem |

Les marques de lecture ne quittent jamais l'appareil : aucun accusé de lecture n'est envoyé (D-027).

## 5. Schéma de la base locale : migration

- `Messaging.sq` définit les tables pour les installations neuves ; `db/migrations/2.sqm` contient exactement ses `CREATE TABLE` et `CREATE INDEX` pour les bases existantes (sans `AS kotlin.Boolean`, qui n'a pas sa place dans une migration). SQLDelight déduit la version du schéma du plus grand numéro de migration plus un : elle passe à **3**.
- La migration `1.sqm` (table `follow`, branche des comptes suivis) **n'existe pas dans cette branche** et n'a pas été créée, conformément à la consigne : SQLDelight ne l'exige pas (la génération et les tests passent avec `2.sqm` seule). Après fusion des deux branches, une base en version 1 exécutera `1.sqm` puis `2.sqm` ; une base en version 2 exécutera `2.sqm`.
- **Attention à l'ordre de fusion** (point soulevé par la relecture) : si un APK contenant cette branche seule était installé sur un appareil, sa base passerait en version 3 et une `1.sqm` ajoutée ensuite ne s'exécuterait jamais sur cet appareil (table `follow` absente, plantage à la première utilisation). Donc : fusionner les deux branches avant de construire le moindre APK de test, ou, si la branche des comptes suivis est abandonnée ou retardée, renommer `2.sqm` en `1.sqm` avant la fusion. Ne jamais laisser de trou dans la numérotation. Activer un jour `verifyMigrations` de SQLDelight (avec des instantanés `.db` par version) permettrait de l'attraper en intégration continue.
- `shared/core/build.gradle.kts` n'a pas changé : les pilotes `JdbcSqliteDriver(url, properties, PpfDatabase.Schema)` (ordinateur), `AndroidSqliteDriver` et `NativeSqliteDriver` reçoivent le schéma et appliquent eux-mêmes `migrate` selon `PRAGMA user_version`. Aucune option `verifyMigrations` n'est activée (elle demanderait des fichiers `.db` de référence par version).
- `SchemaMigrationTest` (JVM) crée un fichier de base au schéma de la version 1 (les `CREATE` d'`Event.sq` exécutés à la main, `user_version = 1`, une ligne dans `event`), l'ouvre avec `JvmDatabase.open`, et vérifie que les tables `conversation`, `message`, `outgoing_wrap` et `messaging_state` existent, que `user_version` vaut `PpfDatabase.Schema.version` (au moins 3) et que la ligne existante est conservée.

## 6. Questions ouvertes

1. **Lecture des enveloppes par le serveur** : NIP-17 et NIP-59 recommandent de ne servir les kind 1059 qu'à leur destinataire authentifié (NIP-42). Sans cela, n'importe qui peut compter les enveloppes adressées à une clé (pas leur contenu ni leur expéditeur). À décider côté serveur : authentification par signature (défi signé par la clé du destinataire) ou acceptation de cette fuite en phase 1, à inscrire dans `THREAT_MODEL.md`.
2. **Durée de conservation des enveloppes sur le serveur** : fixée par l'agent serveur à 30 jours et 1000 enveloppes par destinataire ; un appareil absent plus longtemps perd les messages intermédiaires. À faire valider par la propriétaire, à inscrire dans `REGISTRE_DONNEES.md` et à expliquer dans l'interface. Attention : la limite de 1000 par destinataire permet à un inconnu de **noyer** la boîte d'une clé avec des enveloppes vides (les plus anciennes sont évincées) ; la question n° 3 en dépend.
3. **Anti-abus** : les enveloppes sont signées par des clés jetables, donc la limitation à 60 envois par minute et par clé ne mord pas sur un attaquant. Le serveur pourrait exiger la preuve de travail déjà prévue (type ALTCHA) à la publication des kind 1059, limiter par destinataire plutôt que par signataire, ou n'accepter des enveloppes que pour des destinataires connus. À traiter côté serveur.
4. **Sémantique de `tag=` répété** : confirmée par l'agent serveur (« OU entre valeurs d'un même nom, ET entre noms »), identique à celle de `InMemoryPublicationPort`. Un paramètre de requête inconnu devrait répondre `400` pour qu'un client plus récent que le serveur échoue bruyamment.
5. **Chiffrement du fichier de base de données sur ordinateur (S-43)** : les messages déchiffrés y sont en clair, comme la clé d'identité. À faire avant toute distribution large de l'application de bureau.
6. **`.gitleaks.toml`** : ajouter à la racine une liste blanche pour les vecteurs de test permettrait de garder `nip44.vectors.json` lisible ; sinon la version compressée convient.
7. **Vérification des contacts par QR code (S-128)** et **signalement d'un message chiffré par son destinataire (S-127)** : non commencés ; le second demande une décision sur ce qui est transmis à la modération (le texte déchiffré par le destinataire, signé par lui).
