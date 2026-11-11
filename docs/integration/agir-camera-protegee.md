# Note d'intégration : caméra protégée (AG-58), étape 1

Rédigée le 9 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-aa63b513c9dd49e1b`, partie de `feature/agir` 5c1a837). Décision : **D-089** (`docs/DECISIONS.md`), dernier numéro du bloc d'« Agir » (D-082 à D-089).

> **Rien n'est compilé ni testé.** Les compilations de branches étaient interdites ce jour-là (machine fragile, autre équipe en cours de compilation). Le code et les tests ont été écrits en lisant les sources réelles de chaque API appelée (section 10) ; ils doivent être compilés et lancés (`gradle-safe.ps1`, section 9) avant toute entrée dans main.

## 1. Ce qui est fait

Cahier fonctionnel, M5, AG-58 (ligne 461) : « Photos et vidéos prises dans PPF chiffrées hors de la galerie du téléphone, jamais copiées vers un service de sauvegarde ; plus tard, envoi continu par morceaux chiffrés vers les avocats partenaires. » Priorité « Tôt » pour la galerie chiffrée (cette étape, **photos seulement**), « Plus tard » pour l'envoi continu (rien n'est codé, section 5).

1. **Prise de vue en mémoire, jamais dans la galerie du téléphone** (Android) : CameraX `ImageCapture.takePicture(executor, OnImageCapturedCallback)` ; le JPEG de la caméra arrive dans un `ImageProxy` en mémoire, il est copié dans un tableau d'octets et l'image est fermée aussitôt. Aucun fichier, aucune entrée MediaStore, aucun stockage partagé. Flash coupé, aucun son joué par PPF (certains pays l'imposent au niveau du système). Caméra arrière, aperçu CameraX (`PreviewView`) comme le lecteur de codes QR de « Décider ».
2. **Chaîne des médias avant tout chiffrement** (`core/agir/camera/ProtectedCapture.kt`, sur `core/media`, rien n'est dupliqué) :
   - empreinte SHA-256 des octets de la caméra, tels qu'ils sont arrivés (`MediaDigest`) ;
   - métadonnées retirées par la liste blanche de `MetadataStripper`, **sans décoder** : les pixels restent ceux de la caméra, en pleine définition (EXIF avec le modèle de l'appareil, la date et les réglages, XMP, vignettes, blocs du fabricant, tout ce qui suit la fin de l'image) ;
   - orientation : celle que donne CameraX (`ImageInfo.rotationDegrees`, lue dans l'EXIF de la caméra quand il est fiable, prise de la requête sinon), écrite **seule** dans le bloc EXIF minimal de la chaîne (`MinimalExif`) ; l'orientation de l'EXIF de la caméra n'est pas gardée ;
   - copie vérifiée propre (`MetadataStripper.isClean`) avant de partir vers le coffre ; sinon rien n'est gardé.
   Aucune position n'est jamais demandée à l'appareil : le JPEG de la caméra n'en a pas, et aucune ne survivrait au nettoyage.
3. **Coffre chiffré** (`core/agir/camera/EncryptedPhotoVault.kt`, Android : `AndroidProtectedVault.kt`) : section 2.
4. **Galerie dans PPF** (`ui/agir/camera/ProtectedCameraScreen.kt`) : vignettes déchiffrées en mémoire seulement, la plus récente d'abord, chacune nommée pour le lecteur d'écran par sa date (« Photo du 9 novembre 2026 à 14:30 ») ; ouvrir une photo ; **supprimer** après confirmation (« Annuler » d'abord) ; **« Témoigner avec cette photo »** ; **« Enregistrer dans la galerie du téléphone »** après mise en garde.
   - Les pixels déchiffrés (vignettes, photo ouverte) **quittent la mémoire** quand l'écran se ferme et quand PPF se verrouille (M33) ; ils reviennent au déverrouillage.
   - La galerie est derrière le verrouillage de PPF quand il est activé (tout l'écran l'est, `DeviceSecurityGate`).
   - **`FLAG_SECURE` tant que la caméra protégée ou sa galerie est affichée**, même si « Masquer le contenu » est désactivé : rien dans les applications récentes, pas de capture ni d'enregistrement de l'écran. En sortant, la fenêtre revient au réglage de M33 (`KeepPhotosHidden`). Voir la question 3.
5. **« Témoigner avec cette photo »** : la photo déchiffrée est remise au formulaire « Témoigner » existant (`TestimonyLibrary.attachFromCamera`, une fonction de trois lignes sur `attach`) : la chaîne des témoignages s'applique comme pour une photo choisie (redessin, floutage, original pour les avocats facultatif) ; la déclaration de provenance (AG-54) dit `camera` et l'heure de la prise de vue. La photo reste aussi dans le coffre.
6. **« Enregistrer dans la galerie du téléphone »** (principe 10 : protégé par défaut, la personne peut assouplir pour ses propres données) : une mise en garde dit que la copie sort de la protection, peut être sauvegardée en ligne par le service photo du téléphone et vue par toute personne qui ouvre la galerie ; « Annuler » avant « Enregistrer quand même ». La copie est la photo protégée elle-même (métadonnées toujours retirées), sous un nom neutre `IMG_AAAAMMJJ_HHMM.jpg` qui ne nomme pas PPF : Android 10 et plus, MediaStore dans « Pictures » (aucune permission nécessaire) ; Android 8 et 9, la boîte de dialogue des documents du système, où la personne choisit le dossier (écrire dans MediaStore y demanderait la permission de stockage). La photo reste dans le coffre.
7. **Permission caméra** demandée seulement à l'ouverture de la caméra protégée, avec la raison à l'écran, comme le lecteur de codes QR ; jamais à l'installation. Déjà déclarée « non requise » dans le manifeste (commentaire mis à jour).
8. **Entrées** : rubrique « Caméra protégée » du mode manifestation (après « Sans nouvelles de moi », avant « Préparer mon téléphone ») ; bouton « Prendre une photo avec la caméra protégée » dans la rubrique photo de « Témoigner ». Les deux ouvrent l'écran par-dessus, avec un retour qui garde la place dans la page. **Sur ordinateur et iOS**, l'entrée dit : « La caméra protégée est disponible dans l'application Android. Sur cet appareil, aucune photo n'est prise ni gardée par PPF. »
9. **Sans compte** : la caméra protégée marche sans compte (rien ne part). « Témoigner » demande un compte, comme avant.
10. **Rien n'est envoyé** : aucun appel réseau, aucun code serveur, aucune migration (ni SQLDelight, ni Flyway), **aucune dépendance nouvelle** (CameraX 1.6.1 déjà au catalogue pour les codes QR, `javax.crypto` et Android Keystore), aucune icône nouvelle, aucun hôte tiers.

## 2. Cryptographie

| Élément | Choix |
|---|---|
| Clé de chaque photo | 32 octets aléatoires (`SecureRandom`), **une par photo**, jamais réutilisée ; effacée de la mémoire après usage |
| Chiffrement de la photo | AES-256-GCM (`javax.crypto`, `AES/GCM/NoPadding`), nonce aléatoire de 12 octets, étiquette de 128 bits ; données associées `ppf-camera-photo-v1:<identifiant>` |
| Clé maîtresse | AES-256-GCM **générée dans l'Android Keystore** (alias `ppf-camera-master`, `KeyGenParameterSpec` : chiffrer et déchiffrer, GCM, sans bourrage, 256 bits), **non exportable**, matérielle quand le téléphone a un Keystore matériel (TEE) ; nonce choisi par le Keystore à chaque emballage |
| Emballage de la clé d'une photo | AES-256-GCM sous la clé maîtresse, dans le Keystore ; données associées `ppf-camera-key-v1:<identifiant>` |
| Où vit la clé emballée | Dans le fichier de la photo elle-même : supprimer le fichier supprime la seule copie de sa clé |
| Liste des photos | Petit document JSON (identifiant, date, taille, empreintes, jamais un pixel) chiffré de la même façon, avec une clé neuve à chaque écriture (`index.bin`) |
| Format d'un fichier | `PPFC` ‖ version (1) ‖ longueur de la clé emballée (2 octets) ‖ clé emballée (nonce ‖ chiffré ‖ étiquette) ‖ photo chiffrée (nonce ‖ chiffré ‖ étiquette) |
| Où sont les fichiers | `noBackupFilesDir/ppf-camera/` : `<identifiant>.photo` (16 octets aléatoires en hexadécimal, rien de la photo) et `index.bin` ; jamais dans une sauvegarde Android ; `android:allowBackup="false"` gardé dans le manifeste |
| Intégrité | Un octet changé, un fichier renommé ou une clé déplacée vers un autre fichier sont refusés (étiquettes GCM et données associées) ; l'empreinte SHA-256 de la photo déchiffrée est comparée à la liste |
| Écriture | Fichier temporaire renommé (jamais à moitié écrit) ; rien n'est écrit en clair, à aucun moment |

L'interface commune `ProtectedVault` (`store`, `list`, `open`, `delete`, `eraseAll`) cache la plateforme ; la logique du format (`EncryptedPhotoVault`) est commune et testée sur ordinateur avec la cryptographie du JDK ; seules la clé maîtresse (`KeystoreVaultCrypto`) et le dossier (`DirectoryVaultFiles`) sont propres à Android. Sur ordinateur et iOS, pas de coffre à l'étape 1 (`rememberPlatformProtectedVault` rend `null`).

## 3. Effacement

- **Supprimer une photo** (après confirmation) efface son fichier, donc sa clé emballée, puis sa ligne de la liste.
- **Effacement rapide** (S-44) : partie **`agir-camera`**, inscrite dans `AdaptiveShell` juste après `agir-minuteur`. Elle **détruit d'abord la clé maîtresse du Keystore** (effacement cryptographique : toute copie laissée par la mémoire flash, par exemple par la répartition de l'usure, devient illisible d'un coup), puis supprime les fichiers, vérifie qu'il ne reste rien, attend la fin et peut être relancée. Une partie en échec le dit (« Parties non effacées »).
- Si l'effacement est interrompu après la clé (application fermée), les fichiers restants sont illisibles par construction : ils sont supprimés à la prochaine ouverture du coffre.
- « Effacer mes clés » **ne touche pas** aux photos : elles ne dépendent pas du compte (question 2).

## 4. Limites (dites honnêtement)

- **Captures d'écran du système** : `FLAG_SECURE` bloque les captures, l'enregistrement et l'aperçu des applications récentes pendant que la caméra protégée est affichée, mais un téléphone modifié (root), un enregistreur matériel ou un autre appareil qui filme l'écran n'en tiennent pas compte.
- **Téléphone déjà compromis** : un logiciel espion qui contrôle le téléphone peut voir l'image de la caméra et la mémoire de PPF ; le chiffrement ne protège que les fichiers au repos.
- **Photos prises avec l'application photo habituelle** : elles ne sont pas protégées (galerie, sauvegarde en ligne, position). L'écran le dit.
- **Copie dans la galerie** : une fois enregistrée, elle sort de la protection (mise en garde à chaque fois).
- **Téléphone saisi et déverrouillé** : quelqu'un qui ouvre PPF voit les photos, sauf si le verrouillage de PPF (M33) est activé ; l'effacement rapide reste la protection la plus forte.
- **Effacement cryptographique** : il repose sur la suppression de la clé par l'Android Keystore. Sur un téléphone sans Keystore matériel, ou si la puce garde une ancienne copie chiffrée de la clé, la garantie est plus faible (la clé reste toutefois liée à ce téléphone).
- **Ce qui se voit des fichiers** : quelqu'un qui lit le dossier privé de PPF (téléphone modifié, expertise) voit le nombre de photos, leur taille approximative et la date des fichiers, jamais leur contenu.
- **Vignettes** : chaque ouverture de la galerie déchiffre chaque photo pour en tirer sa vignette ; avec des centaines de photos, l'affichage sera lent (vignettes chiffrées à part : étape 2).
- **Une seule caméra** (arrière), photos seulement, aucun réglage (zoom, flash) à l'étape 1.
- **Transferts d'appareil à appareil** (Android 12 et plus) : `allowBackup="false"` n'empêche plus ces transferts pour une application qui vise Android 12 et plus ; les fichiers du coffre sont dans le dossier « sans sauvegarde », exclu, et la clé maîtresse ne quitte jamais le Keystore : une copie serait illisible. Le reste des données de PPF est concerné (question 5).

## 5. Étapes suivantes

- **Étape 2 : vidéo.** Demande `androidx.camera:camera-video` (même version 1.6.1, licence Apache 2.0, AndroidX, aucun service Google ; déjà tiré indirectement par `camera-view`, mais à déclarer et à verrouiller : `gradlew build --write-locks`, donc une compilation). Enregistrement vers un flux chiffré par morceaux (jamais un fichier en clair : `Recorder` avec une sortie vers un descripteur de fichier d'un tube, ou chiffrement de morceaux de quelques mégaoctets au fil de l'eau), métadonnées des MP4 retirées (atomes `udta`, `meta`, `©xyz` : pas d'outil fiable dans la chaîne aujourd'hui, à écrire ou à choisir), son coupé par défaut. Vignettes chiffrées à part pour une galerie rapide.
- **Étape 3 : envoi continu par morceaux chiffrés vers les avocats partenaires** (priorité « Plus tard »). **Rien n'est codé.** Avant toute ligne : présenter à la propriétaire le **coût du stockage sur le serveur** (une minute de vidéo pèse de 50 à 150 Mo ; le VPS KVM 1 a 50 Go de disque, préproduction comprise ; un stockage objet européen coûte de l'ordre de quelques euros par téraoctet et par mois, à chiffrer précisément), la durée de conservation et le cadre avec les avocats partenaires (qui détient les clés, accès, rétention de 6 ans des originaux des témoignages). Le format chiffré d'aujourd'hui (clé par fichier, enveloppée) se prête à une enveloppe supplémentaire pour la clé publique d'un avocat.
- **iOS** : caméra du système (AVFoundation, prise en mémoire), coffre dans le conteneur privé exclu d'iCloud (`NSURLIsExcludedFromBackupKey`), clé maîtresse dans le trousseau (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`), couverture de la fenêtre dans le sélecteur d'applications ; avec la compilation iOS.
- **Horodatage de l'empreinte à la prise de vue** (AG-54) : quand l'archive horodatée de « Décider » (D-111) sera dans main, l'empreinte de la photo protégée pourra être horodatée dès la prise de vue (seule l'empreinte sortirait, au choix de la personne).

## 6. Fichiers

Nouveaux (pôle Agir) :
- `shared/core/src/commonMain/.../core/agir/camera/ProtectedVault.kt` (`ProtectedPhoto`, `ProtectedPhotoMeta`, interface `ProtectedVault`, `ProtectedIds`, `InMemoryProtectedVault` pour les tests), `ProtectedCapture.kt` (`CameraShot`, `CaptureResult`, la chaîne avant le coffre), `EncryptedPhotoVault.kt` (`VaultCrypto`, `VaultFiles`, format, effacement) ;
- `shared/core/src/androidMain/.../core/agir/camera/AndroidProtectedVault.kt` (`KeystoreVaultCrypto`, `DirectoryVaultFiles`) ;
- `shared/ui/src/commonMain/.../ui/agir/camera/ProtectedCameraState.kt` (état, `LocalProtectedVault`, `rememberProtectedCameraState`), `ProtectedCameraScreen.kt` (entrées, écran, galerie, photo ouverte, dialogues), `ProtectedCameraPlatform.kt` (`expect` : coffre, caméra, copie vers la galerie, fenêtre cachée) ;
- `actual` : `androidMain/.../ui/agir/camera/ProtectedCamera.android.kt` (CameraX, MediaStore ou boîte de documents, `FLAG_SECURE`), `jvmMain/.../ProtectedCamera.jvm.kt` et `iosMain/.../ProtectedCamera.ios.kt` (rien : la phrase « disponible sur Android ») ;
- `shared/ui/src/commonMain/composeResources/values/strings_camera.xml` ;
- tests : `shared/core/src/commonTest/.../agir/camera/ProtectedCaptureTest.kt`, `shared/core/src/jvmTest/.../agir/camera/EncryptedPhotoVaultTest.kt`, `shared/ui/src/jvmTest/.../agir/camera/ProtectedCameraScreenTest.kt`.

Fichiers d'Agir modifiés : `ui/agir/manifestation/HelpState.kt` (propriété `camera`), `ManifestationScreen.kt` (rubrique et écran par-dessus ; paramètres `camera` et `onTestifyWithPhoto` ; le code du minuteur AG-55 est intact), `ui/agir/AgirScreen.kt` (la caméra de la coquille, passage vers « Témoigner »), `ui/agir/testimonies/TestimonyScreen.kt` (paramètre `camera`, bouton et écran par-dessus), `TestimonyLibrary.kt` (`attachFromCamera`, paramètre `capturedAt` de `attach`), test `ui/agir/security/DeviceSecurityTest.kt` (`agir-camera` dans la liste des parties attendues), `docs/integration/agir-securite-appareil.md`.

**Fichiers d'autres pôles touchés** (section 7) : `ui/shell/AdaptiveShell.kt` (coquille) ; `android/src/main/AndroidManifest.xml` (commentaire seulement). Aucun fichier de M24 « Collectifs », aucun fichier de « Décider » (le composant QR est lu, pas modifié).

## 7. Crochets dans les fichiers des autres pôles (lignes exactes)

- **Coquille, `shared/ui/.../ui/shell/AdaptiveShell.kt`**, ligne 105 (import) et lignes 245 à 248, juste après `RegisterErasable(dependencies, "agir-minuteur") { timer.eraseFromDevice() }` :
  ```kotlin
  // AG-58 « Caméra protégée » (D-089): the photos of the vault; erasing destroys the Keystore key, then the files.
  val protectedCamera = rememberProtectedCameraState()
  help.camera = protectedCamera
  RegisterErasable(dependencies, "agir-camera") { protectedCamera.eraseFromDevice() }
  ```
  `AgirScreen` (ligne 156) prend cette caméra par `help.camera`, sans nouveau paramètre ; hors de la coquille (tests), chaque écran construit la sienne.
- **Hôte Android, `android/src/main/AndroidManifest.xml`**, lignes 11 à 15 : le commentaire de la permission `CAMERA` cite aussi la caméra protégée ; aucune permission, aucun attribut nouveau. `android:allowBackup="false"` est gardé ; il n'y a pas de `dataExtractionRules` (question 5).

## 8. Questions pour le chef d'équipe (avec recommandations)

1. **Garder aussi l'original de la caméra, avec ses métadonnées, pour les avocats ?** Aujourd'hui seule la copie nettoyée est gardée (pixels intacts, pleine définition) ; l'original de la caméra (modèle de l'appareil, date, réglages, jamais de position) n'est jamais écrit, seule son empreinte est gardée. **Recommandation : non à l'étape 1** ; l'original pour les avocats (AG-08) passe déjà par « Témoigner », où il est chiffré pour la clé de conservation des preuves ; à revoir avec les avocats partenaires à l'étape 3.
2. **« Effacer mes clés » doit-il effacer les photos protégées ?** **Recommandation : non** : elles ne dépendent pas du compte (la caméra marche sans compte) et peuvent être des preuves que la personne veut garder en changeant de compte ; l'effacement rapide et la suppression une à une restent là.
3. **`FLAG_SECURE` imposé dans la caméra protégée**, même quand « Masquer le contenu » est désactivé. **Recommandation : le garder** : une capture d'écran mettrait la photo dans la galerie et dans les sauvegardes, exactement ce que la fonction évite ; la personne garde le moyen d'assouplir pour ses photos (« Enregistrer dans la galerie du téléphone », avec mise en garde). À dire dans le guide (c'est fait).
4. **Clé maîtresse dans StrongBox** (puce de sécurité séparée, Android 9 et plus, sur une partie des téléphones) et **clé utilisable seulement téléphone déverrouillé** (`setUnlockedDeviceRequired`) : plus forts, mais plus lents (StrongBox) et avec des défauts connus selon les fabricants (une clé devenue inutilisable ferait perdre les photos). **Recommandation : pas à l'étape 1** ; à essayer sur plusieurs téléphones en phase de test, avec repli automatique.
5. **Transferts d'appareil à appareil** (constat, hors de la caméra) : l'application vise Android 16 (`targetSdk` 36) ; depuis Android 12, `android:allowBackup="false"` n'empêche plus ces transferts, donc les préférences privées (réglages de sécurité, minuteur, point de ralliement) pourraient être copiées vers un nouveau téléphone lors d'un transfert. **Recommandation** (Directeur technique, hôte Android) : ajouter `android:dataExtractionRules` avec `<cloud-backup>` et `<device-transfer>` qui excluent tous les domaines, et un test de `check-apk.sh` qui le vérifie.
6. **Vidéo (étape 2)** : déclarer `androidx.camera:camera-video` (même version, déjà présent par `camera-view`) et écrire le nettoyage des métadonnées MP4 dans `core/media` ; **recommandation** : oui, au prochain créneau de compilation, avec le poids mesuré de l'APK.
7. **Envoi continu vers les avocats (étape 3)** : rien n'est codé ; **recommandation** : préparer d'abord pour la propriétaire une fiche de coût (stockage sur le VPS ou stockage objet européen, volumes, durée) et le cadre avec les avocats partenaires, avant toute conception.

## 9. Comment tester

Tests écrits, **non lancés** (à lancer par `gradle-safe.ps1`, dès que les compilations sont de nouveau permises) :
- `:shared:core:jvmTest --tests "*ProtectedCaptureTest*" --tests "*EncryptedPhotoVaultTest*"` : toutes les métadonnées fabriquées disparaissent (modèle, date, numéro de série, position, logiciel, auteur, commentaire), l'orientation de la caméra reste seule (avec ou sans EXIF), refus de ce qui n'est pas un JPEG nettoyable ; coffre : rien en clair dans les fichiers (ni début ni milieu de la photo, ni métadonnées, ni empreinte, ni date), la photo revient à l'identique, une clé par photo, fichier déplacé, octet changé ou liste changée refusés, suppression du fichier et de la ligne, fichier hors liste jamais montré, effacement : clé détruite d'abord, copies des fichiers illisibles même avec une nouvelle clé, effacement en échec qui le dit et se relance, fichiers sans clé supprimés ;
- `:shared:ui:jvmTest --tests "*ProtectedCameraScreenTest*" --tests "*DeviceSecurityTest*"` : sur ordinateur, l'entrée du mode manifestation (360, 700 en sombre, 1000 dp) et celle de « Témoigner » disent que la caméra protégée est sur Android ; sans coffre, pas de galerie ; une prise de vue (simulée) nettoyée puis gardée et montrée ; ouvrir, « Annuler » qui garde, supprimer après confirmation ; copie vers la galerie seulement après la mise en garde, « Annuler » qui n'envoie rien, nom neutre, photo toujours gardée ; « Témoigner avec cette photo » qui remet les octets et la date ; le formulaire « Témoigner » qui reçoit la photo avec une provenance `camera` à l'heure de la prise ; effacement rapide qui vide le coffre et l'écran ; la galerie à 360, 760 (sombre) et 1000 dp ; partie `agir-camera` inscrite dans l'effacement rapide.

Pour la propriétaire, sur son téléphone Android : `docs/GUIDE_PROPRIETAIRE.md`, section « Tester la caméra protégée ».

## 10. API lues pour écrire sans compiler

- CameraX 1.6.1 : `ImageCapture.Builder().setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(FLASH_MODE_OFF)`, `takePicture(Executor, OnImageCapturedCallback)`, `ImageProxy.format`, `planes[0].buffer`, `imageInfo.rotationDegrees`, `close()` ; `ProcessCameraProvider.getInstance`, `bindToLifecycle`, `Preview`, `PreviewView` comme `components/qr/QrCode.android.kt`.
- Activity 1.13.0 : `ActivityResultContracts.RequestPermission`, `ActivityResultContracts.CreateDocument(mimeType)`.
- Android : `KeyGenParameterSpec` et `AndroidKeyStore` comme `core/identity/AndroidIdentityStore.kt` ; `MediaStore.MediaColumns.RELATIVE_PATH` et `IS_PENDING` (Android 10, appel gardé par `Build.VERSION.SDK_INT`) ; `WindowManager.LayoutParams.FLAG_SECURE` comme `SecureWindow.android.kt`.
- Commun : `MetadataStripper.strip`, `isClean`, `MinimalExif` (interne au module `shared/core`), `PhotoPipeline.preview`, `Provenance.signWithOneTimeKey(…, capturedAt)`, `RegisterErasable`, `LocalDeviceSecurity`, `LocalLifecycleOwner` (déjà utilisé par `LockScreen.kt`).
