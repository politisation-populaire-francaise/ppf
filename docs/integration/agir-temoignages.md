# Note d’intégration : témoignages d’« Agir » et chaîne des médias commune (M5 : AG-07, AG-08, AG-54)

Rédigée le 7 novembre 2026 par un agent de la session « Agir », branche `worktree-agent-aaa3cf95481a62eb0` (partie de `feature/agir`, commit 778816a). La session « Agir » fusionne, numérote les décisions et met à jour `FORMATS.md`, `DECISIONS.md`, `CHANGELOG.md`, `ROADMAP.md` et `GUIDE_PROPRIETAIRE.md`. Cette note a mis à jour elle-même `REGISTRE_DONNEES.md` et `THREAT_MODEL.md` (sections « Ajout du 7 novembre 2026 : témoignages »).

## 1. Ce qui est fait

1. **Chaîne des médias commune** (`shared/core/.../core/media/`, Kotlin commun pur, aussi utilisée par le serveur ; `shared/ui/.../ui/media/` pour l’interface). « Voir » peut s’en servir telle quelle pour « Le mouvement ».
   - **Reconnaissance du fichier** par ses premiers octets (`MediaSniffer`), jamais par son nom : JPEG, PNG, WebP acceptés ; HEIF, vidéo (MP4, QuickTime, 3GP, WebM, Matroska, AVI), son (MP3, AAC, M4A, Ogg, FLAC, WAV) reconnus pour expliquer le refus.
   - **Suppression des métadonnées sans décoder** (`MetadataStripper`), par liste blanche : on ne garde que ce qu’il faut pour afficher l’image, tout le reste part, y compris les blocs inconnus et tout ce qui suit la fin de l’image (photos animées, cartes de profondeur, images HDR, vignettes du format multi-images).
     - JPEG (`JpegCleaner`) : gardés le cadre et ses tables (SOF, DHT, DQT, DRI, DNL, DAC), tous les balayages d’une image progressive, un bloc JFIF réécrit sans sa vignette, le bloc Adobe de transformation des couleurs (indispensable aux JPEG CMJN) ramené à sa seule valeur ; retirés EXIF, XMP et XMP étendu, profils ICC, MPF, FlashPix, IPTC et Photoshop, commentaires, blocs APP3 à APP15, marqueurs inconnus. Un fichier coupé après son dernier balayage retrouve sa fin.
     - PNG (`PngCleaner`) : gardés IHDR, PLTE, IDAT, IEND, tRNS, cHRM, gAMA, sRGB, cICP, sBIT, bKGD (avec leur somme de contrôle) ; retirés tEXt, zTXt, iTXt (dont XMP), eXIf, tIME, iCCP, pHYs, sPLT, hIST, les images d’un PNG animé (l’image fixe reste) et tout morceau auxiliaire inconnu ; un morceau critique inconnu fait refuser le fichier.
     - WebP (`WebpCleaner`) : gardés VP8X (indicateurs ICC, EXIF et XMP effacés), ANIM, ANMF, ALPH, VP8, VP8L ; retirés ICCP, EXIF, XMP et les morceaux inconnus ; taille RIFF recalculée.
     - **Orientation** : lue dans l’EXIF ; soit appliquée aux pixels quand la photo est redessinée (cas normal, voir ci-dessous), soit gardée **seule** dans un bloc EXIF minimal (un en-tête TIFF, une entrée, rien d’autre : `MinimalExif`) quand la photo est nettoyée sans être décodée. Ce bloc minimal n’est pas compté comme métadonnée.
     - `findMetadata` et `isClean` permettent au serveur de vérifier à nouveau une photo avant de la publier.
   - **Redessin à partir des pixels** (`PhotoPipeline`) quand la plateforme sait décoder la photo (Android : `BitmapFactory`, y compris HEIF à partir d’Android 9 ; ordinateur : ImageIO) : remise droite, réduite à 2048 pixels de côté au plus, zones floutées, réencodée en JPEG (qualité 85), puis nettoyée encore et vérifiée propre. Cela affaiblit aussi deux empreintes que le nettoyage seul laisse : les tables de quantification JPEG (qui trahissent le fabricant) et le bruit du capteur. Sans décodeur (iOS plus tard, format inconnu de la plateforme), la photo est nettoyée sans perte et ne peut pas être floutée : l’envoi avec des zones est alors refusé (`BlurUnavailable`), jamais envoyé sans flou.
   - **Floutage manuel** (`ImageTransforms.blur`, `BlurZone`, `ui/media/BlurEditor.kt`) : la personne touche la photo pour poser une zone ; des boutons ajoutent une zone au centre, la déplacent, l’agrandissent, la réduisent, la retirent, sans aucun glissé (WCAG 2.2, critère 2.5.7) et au clavier. Le flou est une mosaïque de 6 blocs au plus sur le grand côté de la zone, chaque bloc étant la moyenne de ses pixels : rien des pixels d’origine ne subsiste dans la zone (un flou léger peut être en partie inversé, pas ceci). Aucune détection automatique, aucun service extérieur ; une détection sur l’appareil pourra venir plus tard (question 6).
   - **Empreintes SHA-256** de l’original (avant toute modification) et de la copie publiée (`MediaDigest`, `PreparedPhoto.originalSha256`, `publishedSha256`).
   - **Chiffrement des fichiers** (`MediaCipher`) : la construction NIP-44 version 2 (HKDF-SHA-256, ChaCha20, HMAC-SHA-256 sur nonce et chiffré, bourrage qui cache la taille exacte) appliquée à des octets, avec une clé aléatoire propre à chaque fichier à la place d’une clé de conversation. Ce sont les primitives déjà vérifiées par les vecteurs officiels de NIP-44 ; rien n’est inventé, seules la couche base64 et la limite de 64 Kio des messages sont laissées de côté. La clé du fichier voyage dans l’enveloppe chiffrée, puis est effacée de la mémoire.
   - **Provenance (AG-54)** (`Provenance`) : dès que la photo est choisie, avant toute modification, l’appareil signe une déclaration (empreinte de l’original, type, taille, mode « import » ou « camera », date). Elle est signée **par une clé créée pour cette seule photo et détruite aussitôt** (cahier des charges 5.12 : la clé « est choisie pour ne pas relier le témoin à son pseudonyme ») ; l’enveloppe, scellée par le compte, relie les deux pour l’équipe seule. `Provenance.sign` accepte aussi la clé du compte (question 1).
   - **Horodatage** : interface `Timestamping` (seule une empreinte sort) avec `NoTimestamping` qui ne fait rien. L’archive horodatée (D-111) de « Décider » n’est pas dans `main` ; sur leur branche `feature/decider-archive-serveur`, elle s’appelle `ArchiveService.stamp(digest: ByteArray)` côté serveur : il suffira de passer `TestimonySetup(timestamping = { digest -> if (archive.stamp(digest)) QUEUED else ALREADY_KNOWN })`. L’appareil appelle aussi `Timestamping` au choix de la photo (crochet prêt pour une route d’horodatage).
2. **Témoigner (AG-07)**, rubrique « Témoigner » de l’accueil d’Agir (sortie des rubriques « En préparation »), écran `ui/agir/testimonies/TestimonyScreen.kt` :
   - texte obligatoire (20 à 8 000 caractères), date (JJ/MM/AAAA), heure (HH:MM ou « 18h05 ») et lieu déclarés, photo facultative ; mentions « relu avant publication », « métadonnées retirées sur votre appareil » et, tant que les clés sont celles d’exemple, « Version d’essai : n’envoyez rien de réel » ;
   - **compte requis** : sans compte, une carte explique pourquoi et mène à « Mon espace » ;
   - liste plaine de ce qui est retiré de la photo (« position GPS, date, modèle de l’appareil et réglages (EXIF) »…), transparence d’abord ;
   - **original pour les avocats (AG-08)** : désactivé par défaut ; l’activer ouvre une mise en garde (« Annuler » d’abord, principe 10) ; il est alors chiffré pour une **clé de conservation des preuves distincte** de la clé de modération, dans une seconde enveloppe, avec son propre fichier chiffré ; jamais publié ; aucun accès avocat codé ;
   - case de la charte décochée par défaut ; erreurs sous chaque champ après une tentative d’envoi ;
   - **chiffrement** : le témoignage est un « rumor » (NIP-59) scellé par le compte et emballé pour la clé de modération (`TestimonyComposer`, réutilise `GiftWrap` et `Nip44` existants) ; le serveur ne voit qu’une enveloppe signée par une clé à usage unique ;
   - **sans réseau** : l’enveloppe et les fichiers, déjà chiffrés, attendent sur l’appareil et partent à l’ouverture suivante de l’écran ou par « Actualiser » (S-27) ; le brouillon en clair ne vit qu’en mémoire ;
   - **« Mes témoignages »** : date, « avec photo », « photo d’origine confiée », « en réponse à un appel », état (en attente de réseau, en attente de relecture, publié, refusé avec le motif) ; ni texte, ni photo, ni lieu gardés ; « Effacer cette liste » après confirmation ; effacée aussi avec les clés (`AdaptiveShell`, comme les réponses des appels) ;
   - **réponse à un appel à témoins** : le bouton « Je témoigne » des appels `temoins`, désactivé jusqu’ici (D-041), ouvre le formulaire relié à l’appel (adresse `a`), là où Agir le branche (`LocalTestify`, fourni par `AgirScreen`) ; le retour ramène à l’appel. Ailleurs (sans Agir), il reste désactivé comme avant.
   - Écran adaptatif : formulaire et liste côte à côte sur ordinateur, l’un sous l’autre sur téléphone et tablette ; modes clair et sombre ; composants de `ui/components`, aucune couleur en dur (zones dessinées avec `scrim`).
3. **Serveur** (`server/.../server/agir/testimonies/`) :
   - `POST /api/v1/testimonies/files` : un fichier chiffré, octets bruts ; nommé par son SHA-256 ; forme seule vérifiée (version 2, taille minimale) ; 32 Mo au plus par fichier, 4 Go pour le dossier (`507 storage_full`) ; renvoyer le même fichier est sans effet ;
   - `POST /api/v1/testimonies` : les deux enveloppes (vérifiées : type 1059, signature, une seule étiquette `p` égale à la clé de modération, ou à la clé de conservation pour la seconde), les noms des fichiers (qui doivent être arrivés et n’appartenir à personne d’autre), les empreintes à horodater ; `202 pending_review`, `200 duplicate` ;
   - `GET /api/v1/testimonies/status?id=` (1 à 20) : état, date de réception à l’heure, décision, motif, identifiant du témoignage publié ; `no-store` ;
   - `GET /api/v1/media/<sha256>` : photos des témoignages publiés ;
   - file de relecture **dédiée** (`testimony_submission`), pas `held_content` : celle-ci publie l’événement « tel qu’il a été signé », ce qui n’a pas de sens pour une enveloppe chiffrée ;
   - **publication après relecture** (`TestimonySpace.approve`) : l’équipe fournit la version publique (type 30023, `ppf-type` = `temoignage`, signée par une clé éditoriale **hors du serveur**, `PublishedTestimony`) et ses photos ; le serveur vérifie la clé éditoriale, la correspondance des empreintes et l’absence de toute métadonnée, puis la publie comme un contenu ordinaire ; `reject` avec un motif ;
   - rétention : fichiers orphelins 24 heures, témoignages décidés 30 jours après la décision, originaux pour les avocats 6 ans ; tâche périodique ;
   - rien de la requête n’est gardé, dates arrondies à l’heure, journaux réduits à des nombres ; limites globales par minute (les enveloppes sont signées par des clés à usage unique : pas de budget par compte possible) ;
   - **clés** : `PPF_MODERATION_KEY`, `PPF_EVIDENCE_KEY` (hexadécimal ou npub) ; sans elles hors production, clés d’exemple ; **en production, routes fermées (503)** sans clés réelles ou avec les clés d’exemple ; routes fermées aussi sans `PPF_TESTIMONY_DIR` ;
   - **serveur de développement** (`runDev`) : dossier temporaire, clés d’exemple, routes `/dev/testimonies` (liste déchiffrée avec la clé d’exemple, photo déchiffrée, approbation qui publie une version `[EXEMPLE]` signée par la clé de développement, refus avec motif).
4. **Clés d’exemple** (`TestimonyRecipients.EXAMPLE`) : leurs clés secrètes sont dérivées (SHA-256) de deux phrases écrites dans le code ; **tout le monde peut donc ouvrir ce qui leur est envoyé**, c’est voulu et affiché. Aucune clé secrète n’est écrite dans le dépôt.

## 2. Refusé ou reporté (avec la raison)

| Sujet | État | Raison |
|---|---|---|
| Vidéo | Refusée avec une explication honnête | Aucune suppression fiable des métadonnées (atomes `udta`, `meta`, `©xyz` des MP4 et MOV, métadonnées Matroska) sans une bibliothèque lourde ; FFmpeg ou équivalent pèserait des dizaines de mégaoctets, en licence LGPL ou GPL ; voir la question 4 |
| Son | Refusé, même raison | Idem (ID3, atomes M4A, commentaires Vorbis) |
| HEIF sur ordinateur | Refusé (ImageIO ne le lit pas) ; accepté sur Android 9 et plus, redessiné en JPEG | Pas de décodeur pur Kotlin |
| Prise de vue directe dans l’application | Reportée : seul l’import d’une photo est proposé | L’appareil photo du système passerait par un `FileProvider` et une permission ; `CaptureSource.CAMERA` est prêt dans le format |
| Plusieurs photos | Une seule par témoignage | Première version ; le format en accepte jusqu’à 4 par enveloppe |
| Détection automatique des visages | Non | Pas de service tiers ; un modèle embarqué est à étudier (question 6) |
| Horodatage réel | `NoTimestamping` | L’archive de « Décider » (D-111) n’est pas dans `main` |
| Back-office de relecture (M15) | File `agir.testimonies` (`TestimonyQueue`, permission `queue.agir.testimonies.decide`, rôle modérateur) : liste, détail limité à ce que le serveur sait (date arrondie à l’heure, nombre de photos chiffrées, original confié ou non), refus avec motif obligatoire, action sensible ; journal écrit par le socle. Routes `/dev/testimonies` gardées en développement, sur le port public seulement | Lecture et approbation (publication de la version publique) : avec le client de l’équipe, qui déchiffre sur ses appareils et fait signer par une clé éditoriale ; `approve`, `encryptedFile`, `evidenceEnvelope` sont prêts |
| Accès des avocats (AG-08) | Conservation chiffrée seulement | Avocat ; procédure de garde de la clé à décider |
| Galerie et carte des témoignages publiés (VO-08) | Format fourni, affichage non fait | Pôle « Voir » ; le format est en section 3.5 |
| iOS | Sélecteur et décodeur absents (`available = false`) | Pas de compilation iOS tant que le compte Apple n’existe pas |
| C2PA | Étude courte, aucun code | Section 6 |

## 3. Formats à reporter dans `docs/FORMATS.md`

### 3.1 Enveloppes (jamais publiées)

- **Témoignage** : rumor (NIP-59) de type **8590**, auteur = clé du compte du témoin, scellé (type 13) par le compte puis emballé (type 1059) pour la clé de modération (`p`). Étiquettes : `["ppf-charter","1.0"]`, facultatives `["ppf-fact-date","AAAA-MM-JJ"]`, `["ppf-fact-time","HH:MM"]`, `["location","…"]` (200 caractères au plus), `["a","30023:<clé>:<d>"]` (appel à témoins), et pour chaque photo `["imeta", "ppf-file <sha256 du chiffré>", "m image/jpeg", "x <sha256 de la copie>", "size <octets>", "decryption-key <hex>", "encryption-algorithm nip44-v2-bytes", "ox <sha256 de l’original>", "dim <l>x<h>", "ppf-blurred 0|1", "ppf-reencoded 0|1"]`, `["ppf-provenance","<déclaration JSON>"]`, `["ppf-original","evidence"|"device"]`. `content` : le texte (8 000 caractères au plus).
- **Original pour les avocats** : rumor de type **8592**, même auteur, emballé pour la clé de conservation des preuves. Étiquettes : `["e","<id du rumor du témoignage>"]`, un `imeta` comme ci-dessus pour l’original (`m` = type de l’original), `["ppf-provenance", …]`. `content` vide.
- **Déclaration de provenance** : événement signé de type **8591**, par une clé à usage unique par défaut : `["x","<sha256 de l’original>"]`, `["m",…]`, `["size",…]`, `["ppf-capture","import"|"camera"]`, `["ppf-captured-at","<secondes>"]`, `created_at` = date de capture, `content` vide. Son identifiant est l’empreinte horodatée.
- Les types 8590 à 8592 sont privés (ils ne voyagent que chiffrés) ; aucun NIP connu ne les utilise en novembre 2026, à vérifier dans la liste des NIP avant la phase 3 (Nostr).

### 3.2 Fichier chiffré (`nip44-v2-bytes`)

`0x02` ‖ nonce (32 octets aléatoires) ‖ ChaCha20(bourrage NIP-44 du fichier) ‖ HMAC-SHA-256(nonce ‖ chiffré) (32 octets), les trois clés de message dérivées par HKDF-expand(clé du fichier, nonce, 76) comme dans NIP-44. Bourrage NIP-44 : préfixe de longueur de 2 octets, ou de 6 octets à partir de 64 Kio, puis zéros jusqu’à la taille arrondie. Nom du fichier sur le serveur : SHA-256 du chiffré.

### 3.3 API du serveur

| Requête | Rôle | Réponses |
|---|---|---|
| `POST /api/v1/testimonies/files` (`application/octet-stream`) | Un fichier chiffré sur l’appareil (32 Mo au plus) | `201 {"id"}`, `200 {"id"}` (déjà là) ; `400 not_encrypted`, `413 too_large`, `429 rate_limited`, `507 storage_full`, `503 testimonies_unavailable` |
| `POST /api/v1/testimonies` (JSON `{"envelope", "evidence_envelope"?, "files", "evidence_files", "stamps"}`, 160 Kio au plus) | Dépose un témoignage dont les fichiers sont arrivés | `202 {"id","status":"pending_review"}`, `200 {"id","status":"duplicate"}` ; `400 malformed` \| `bad_envelope` \| `wrong_recipient` \| `bad_evidence_envelope` \| `bad_evidence` \| `bad_files` \| `bad_stamps` \| `invalid_signature` ; `404 missing_file` ; `409 file_taken` ; `413 too_large` ; `429 rate_limited` |
| `GET /api/v1/testimonies/status?id=<hex>&id=<hex>` | 1 à 20 identifiants d’enveloppe : `[{"id","status":"pending|approved|rejected","received_at","decided_at","reason","published"}]`, `received_at` arrondi à l’heure ; identifiant inconnu absent ; `no-store` | `200` ; `400 bad_parameter` |
| `GET /api/v1/media/<sha256>` | Photo d’un témoignage publié, avec son type ; `Cache-Control: public, max-age=31536000, immutable` | `200` ; `404 not_found` |

### 3.4 Variables d’environnement

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_TESTIMONY_DIR` | Dossier des fichiers chiffrés (`files/`) et des photos publiées (`public/`) | aucun : routes fermées (le serveur de développement prend un dossier temporaire) |
| `PPF_MODERATION_KEY`, `PPF_EVIDENCE_KEY` | Clés publiques (hex ou npub) ; obligatoires et différentes des clés d’exemple en production | clés d’exemple hors production |
| `PPF_TESTIMONY_MAX_FILE_MB`, `PPF_TESTIMONY_MAX_DISK_MB` | Taille d’un fichier, du dossier | 32, 4096 |
| `PPF_TESTIMONY_RETENTION_DAYS`, `PPF_EVIDENCE_RETENTION_DAYS`, `PPF_TESTIMONY_CLEANUP_INTERVAL_MINUTES` | Rétentions et fréquence du ménage | 30, 2190, 60 |

Pour le Directeur technique : `PPF_TESTIMONY_DIR` doit être un volume persistant de la préproduction (et plus tard de la production), inclus dans les sauvegardes chiffrées ; aucun nouvel hôte contacté (rien à ajouter à `infra/EGRESS.md`).

### 3.5 Témoignage publié (pour VO-08, « Voir »)

Type 30023, `ppf-type` = `temoignage` (nouvelle valeur `ArticleType.TEMOIGNAGE`, ajoutée à la fin), signé par une clé éditoriale : `d` aléatoire (`temoignage-<16 hex>`, ne relie ni au témoin ni à l’enveloppe), `title`, `published_at`, `ppf-origin`, facultatives `ppf-fact-date`, `ppf-fact-time`, `location`, `a` (l’appel à témoins, pour que l’appel puisse lier vers le témoignage), un `imeta` par photo (`url /api/v1/media/<sha256>`, `m`, `x`, `dim`). `content` : le texte publié.

## 4. Fichiers

Nouveaux :
- `shared/core/.../core/media/` : `MediaKind.kt`, `ExifOrientation.kt`, `MetadataStripper.kt`, `JpegCleaner.kt`, `PngCleaner.kt`, `WebpCleaner.kt`, `ImageTransforms.kt`, `MediaCipher.kt`, `Provenance.kt` (avec `Timestamping`), `PhotoPipeline.kt` ;
- `shared/core/.../core/agir/testimonies/` : `TestimonyFormats.kt`, `TestimonyComposer.kt`, `TestimonyReader.kt`, `TestimonyTransport.kt`, `TestimonyStore.kt`, `PublishedTestimony.kt` ; `sqldelight/.../Testimony.sq`, `migrations/14.sqm` ;
- `shared/ui/.../ui/media/` : `PlatformMedia.kt` (+ `.android.kt`, `.jvm.kt`, `.ios.kt`), `BlurEditor.kt` ; `shared/ui/.../ui/agir/testimonies/` : `TestimonyLibrary.kt`, `TestimonyScreen.kt` ; `composeResources/values/strings_media.xml`, `strings_temoignages.xml` ;
- `server/.../server/agir/testimonies/` : `TestimonyConfig.kt`, `TestimonyStore.kt`, `TestimonySpace.kt`, `TestimonyApi.kt` ; `db/migration/V90__agir_temoignages.sql` ;
- tests : `core/media/` (`MediaSamples`, `MetadataStripperTest`, `MediaChainTest` en commun ; `RealImagesTest`, `PhotoPipelineTest` sur JVM), `core/agir/testimonies/` (`TestimonyComposerTest`, `TestimonyStoreTest`), `core/local/TestimonyMigrationTest` ; `server/.../agir/testimonies/` (`TestimonyFixtures`, `TestimonyApiTest`, `DevTestimonies`) ; `shared/ui/.../agir/testimonies/TestimonyScreenTest`, `agir/calls/CallTestifyTest`.

Fichiers partagés modifiés (quelques lignes chacun) :

| Fichier | Changement |
|---|---|
| `core/publication/ServerPublicationPort.kt` | `client` et `baseUrl` passent de `private` à `internal` (le transport des témoignages réutilise le client et l’adresse du serveur configuré) |
| `core/editorial/Article.kt` | `ArticleType.TEMOIGNAGE` ajouté à la fin |
| `ui/content/ComprendreScreen.kt` (Comprendre) | Une ligne dans le `when` exhaustif des libellés de type, plus son import : conséquence obligatoire de la nouvelle valeur |
| `core/jvmTest/.../local/DatabaseMigrationTest.kt`, `SchemaMigrationTest.kt` | Version du schéma 15 au lieu de 11 ; tables `testimony` attendues |
| `ui/agir/AgirScreen.kt`, `AgirHome.kt`, `ui/agir/calls/CallDetail.kt` (Agir) | Route et rubrique « Témoigner », `LocalTestify`, bouton « Je témoigne » actif |
| `ui/shell/AdaptiveShell.kt` | La bibliothèque des témoignages vit toute la session, est effacée avec les clés et passée à `AgirScreen` (5 lignes) |
| `shared/ui/build.gradle.kts`, `shared/ui/gradle.lockfile` | `androidx.activity:activity-compose` 1.13.0 dans `androidMain` (le sélecteur de photos du système) : même artefact et même version que l’application Android, déjà résolu à l’exécution ; Apache 2.0, Google, maintenu ; aucun nouveau téléchargement ; Android seulement |
| `server/.../Application.kt` | Paramètre `testimonies` ajouté en dernier, démarrage et routes |
| `server/src/test/.../TestDatabase.kt`, `DevServer.kt` | Tables vidées entre les tests ; routes `/dev/testimonies` (dans `publicPort`) |
| `server/.../agir/testimonies/TestimonyQueue.kt`, `Application.kt`, `backoffice/Permissions.kt` | File `agir.testimonies` du back-office, enregistrée par `teamBackOffice?.queues?.register(...)` ; permission ajoutée au rôle modérateur |

**Migrations provisoires** : `V90__agir_temoignages.sql` (Flyway) et `14.sqm` (schéma local en version 15 ; SQLDelight accepte l’écart avec 11 à 13 réservés aux autres pôles, vérifié par `TestimonyMigrationTest`). À renuméroter à l’entrée dans `main`, avec la ligne de `DatabaseMigrationTest.theSchemaIsAtVersion15`.

## 5. Décisions proposées (numéros à attribuer dans le bloc d’Agir, D-081 et suivants)

- **T-1. Liste blanche plutôt que liste noire** pour les métadonnées, en Kotlin commun sans dépendance, la même sur l’appareil et sur le serveur.
- **T-2. Copie publiée redessinée** à partir des pixels (remise droite, 2048 pixels au plus, JPEG 85) dès que la plateforme sait décoder ; nettoyage sans perte en repli ; jamais d’envoi d’une photo à flouter sans flou.
- **T-3. Floutage par mosaïque grossière** (6 blocs au plus par zone), manuel, sans glissé ; pas de détection automatique.
- **T-4. Fichiers chiffrés par la construction NIP-44 v2 sur des octets** avec une clé par fichier, clé transportée dans l’enveloppe NIP-59 (même idée que les messages de fichiers NIP-17, avec nos primitives déjà vérifiées au lieu d’AES-GCM).
- **T-5. Deux clés distinctes** : modération (lit les témoignages) et conservation des preuves (lit les originaux) ; l’original n’est confié que sur choix du témoin, après mise en garde.
- **T-6. Provenance signée par une clé à usage unique** par défaut (cahier des charges 5.12), horodatée par son identifiant.
- **T-7. File dédiée** `testimony_submission` plutôt que `held_content` ; publication d’une version publique signée par une clé éditoriale hors du serveur, vérifiée (clé, empreintes, aucune métadonnée).
- **T-8. Rétentions** : 24 heures pour un fichier orphelin, 30 jours après la décision, 6 ans pour un original confié ; dates à l’heure près.
- **T-9. Routes fermées en production** sans clés réelles ; clés d’exemple dérivées de phrases publiques et annoncées.
- **T-10. Vidéo et son refusés** dans cette version, avec une explication.

## 6. C2PA : étude courte (aucun code)

C2PA (Coalition for Content Provenance and Authenticity) décrit un « manifeste » attaché au fichier (dans un JPEG, des blocs APP11 au format JUMBF), qui liste des assertions (actions : capture, recadrage, floutage ; « ingrédients » : fichiers d’origine ; parfois une vignette et des métadonnées de prise de vue) et les signe avec un certificat X.509 (COSE). D’après ce que j’en sais, à vérifier sur la spécification officielle (c2pa.org) avant toute décision :
- **risque pour le témoin** : un manifeste de capture signé par l’appareil ou par une application porte une identité de certificat et souvent des métadonnées et une vignette de l’original ; publié tel quel, il défait le nettoyage. Il ne faut donc jamais publier le manifeste d’un appareil de témoin ;
- **ce qui serait utile** : un manifeste ajouté **par PPF** sur la copie publiée, signé par la plateforme, disant seulement « image recadrée, floutée et réduite par PPF le … » (actions de type « redacted »), sans ingrédient identifiant ; les outils de vérification du public le liraient ;
- **coûts et dépendances** : la bibliothèque de référence est en Rust (c2pa-rs) ; rien de maintenu en Kotlin multiplateforme ; il faudrait une bibliothèque native par plateforme, ou écrire JUMBF et COSE nous-mêmes ; les certificats reconnus par les vérificateurs viennent d’autorités de certification (coût probable) ;
- **recommandation** : ne rien faire maintenant ; garder notre provenance (événement signé, empreinte horodatée D-111, vérifiable avec « Vérifier un contenu PPF », S-46) ; revoir C2PA pour la copie publiée seulement, signée par la plateforme, quand le back-office existera.

## 7. Questions pour la propriétaire (avec recommandations), à transmettre par le chef d’équipe

1. **Clé qui signe la provenance.** Le cahier des charges (5.12) demande une clé qui ne relie pas le témoin à son pseudonyme ; la consigne de cette tâche disait « la clé du compte ». J’ai suivi le cahier des charges : une clé créée pour la photo et détruite aussitôt, l’enveloppe scellée par le compte reliant les deux pour l’équipe seule. **Recommandation : garder la clé à usage unique.**
2. **Garde des deux clés privées** (modération, conservation des preuves). Options : (A) clé de modération sur les appareils de deux modératrices ou modérateurs, déchiffrement dans le back-office sur leur appareil ; clé de conservation hors ligne, chez la propriétaire, puis un avocat partenaire ; (B) une seule clé pour tout (plus simple, mais les modérateurs liraient les originaux avec leur position GPS) ; (C) clés sur le serveur (refusé : le serveur pourrait tout lire). **Recommandation : A.** Coût : aucun maintenant ; une clé de sécurité physique plus tard serait une dépense à valider.
3. **Durée de conservation des originaux confiés.** Proposé : 6 ans (prescription de la plupart des délits, article 8 du code de procédure pénale). Autres : 3 ans, ou 20 ans pour les crimes. **Recommandation : 6 ans**, à faire relire par l’avocat à la fin.
4. **Vidéos et sons.** Options : (A) refus, comme maintenant ; (B) nettoyage maison des MP4 et MOV en Kotlin (retirer les atomes `udta`, `meta`, `©xyz`, puis vérifier), sans dépendance, quelques jours de travail et des tests sur de vrais fichiers ; (C) une bibliothèque lourde de type FFmpeg (dizaines de Mo, LGPL ou GPL, projets peu maintenus). **Recommandation : B dans une vague suivante**, vidéos courtes seulement, et toujours un refus pour les formats non maîtrisés.
5. **Être crédité.** Aujourd’hui, le témoignage publié ne nomme jamais le témoin (signé par l’équipe). Faut-il offrir, après mise en garde, de le publier sous son pseudonyme ? **Recommandation : oui, plus tard, désactivé par défaut** (principe 10).
6. **Détection des visages sur l’appareil**, pour proposer des zones à flouter. Elle demanderait un modèle embarqué (quelques Mo, à choisir pour sa licence et sa maintenance). **Recommandation : à étudier après la vague 3** ; l’outil manuel reste.
7. **Retrait par le témoin.** Il n’existe pas encore de moyen pour le témoin de demander l’effacement de son témoignage ou de l’original sur le serveur. **Recommandation : une demande signée par le compte, chiffrée pour l’équipe, dans la prochaine vague** (droit d’effacement, RGPD article 17).

## 8. Tests

- `shared/core` (JVM) : 22 tests des médias (dont 3 sur de vraies images encodées puis décodées par Java : mêmes pixels, aucune chaîne plantée), 5 de la chaîne photo avec un décodeur ImageIO (remise droite, réencodage, flou, refus), 5 du témoignage (règles, chiffrement pour la seule clé de modération, original pour la seule clé de conservation, fichier altéré refusé, clés effacées), 2 de la liste locale (aucun texte dans le fichier de la base), 1 de migration ; tests de migration existants mis à la version 15.
- `server` (PostgreSQL 17 embarqué) : 8 tests : parcours complet avec le vrai transport de l’appareil (chiffré sur disque, date à l’heure, empreintes horodatées, ouverture avec la clé d’exemple, publication, photo servie), refus (destinataire, signature, fichier manquant, fichier non chiffré, fichier d’un autre, empreintes), taille, routes fermées, photo sale et clé non éditoriale refusées, rétention avec une horloge déplacée, clés d’exemple refusées en production, colonnes des tables (rien de la requête).
- `shared/ui` (JVM) : formulaire complet avec photo floutée et original confié (vérifiés en ouvrant les enveloppes), envoi différé sans réseau, compte requis et vidéo refusée, réponse à un appel, téléphone, tablette sombre, ordinateur ; « Je témoigne » d’un appel à témoins.
- `bash tools/check-no-trackers.sh` et la détection des secrets : OK à chaque commit.

## 9. Comment tester

1. `gradlew :server:runDev` : la console annonce « Testimonies: encrypted for the example keys, review at /dev/testimonies ».
2. Lancer l’application de bureau reliée à ce serveur (`PPF_SERVER_URL=http://localhost:8080`, puis `gradlew :desktop:run`), créer un compte dans « Mon espace », ouvrir Agir, puis « Témoigner ». Écrire un texte [EXEMPLE], ajouter une photo, poser une zone floutée, cocher la charte, envoyer.
3. Ouvrir http://localhost:8080/dev/testimonies : le témoignage déchiffré avec la clé d’exemple ; la photo (lien `url`) n’a plus de métadonnées et porte le flou. Pour l’approuver : `curl -X POST http://localhost:8080/dev/testimonies/<id>/approve` ; pour le refuser : `curl -X POST --data "[EXEMPLE] motif" http://localhost:8080/dev/testimonies/<id>/reject`. Avec `PPF_BACKOFFICE_ORIGIN=http://localhost:8081`, la file « Agir : témoignages en relecture » apparaît aussi dans le back-office (refus seulement).
4. Dans l’application, « Mes témoignages », « Actualiser » : l’état change (publié ou refusé avec le motif).
5. Dans Agir, « Appels », l’appel à témoins d’exemple : « Je témoigne » ouvre le formulaire relié à l’appel.
6. Sur téléphone (APK d’essai), la photo se choisit par le sélecteur de photos du système, sans aucune permission ; à tester en phase de test avec une vraie photo prise par le téléphone (vérifier dans la réponse de `/dev/testimonies` que la position a disparu).
