# Codes QR : composant commun des applications

- Pôle « Décider », 8 novembre 2026. Décision du chef d'équipe du même jour (option A) ; téléchargement des bibliothèques accepté par la propriétaire dans le chat de « Décider » (« Oui »).
- Premier usage : la validation en personne (D-112, `docs/integration/une-personne-une-voix.md`, section 4.3). Réutilisé ensuite par « Agir » (AG-61) et « Communauté » (invitations, vérification des contacts).

## 1. Ce que fournit le composant (`shared/ui/.../ui/components/qr`)

| Élément | Rôle |
|---|---|
| `QrCode(text, contentDescription, modifier)` | Dessine le code QR de `text` (UTF-8, correction d'erreur M), noir sur blanc avec sa marge, **quel que soit le thème** : les lecteurs ont besoin du contraste de la norme (un code inversé en mode sombre n'est pas lu par toutes les caméras). Description accessible obligatoire. |
| `QrScanner(onRead, modifier)` | Lit les codes avec la caméra (Android) ; `onRead` reçoit le texte de chaque code lu, parfois plusieurs fois pour le même code : l'appelant garde ce dont il a besoin. Ailleurs, une ligne dit que la lecture se fait avec un téléphone. |
| `canScanQr` | Vrai seulement là où la lecture est possible (Android aujourd'hui). |
| `encodeQr(text)` | La grille du code, ou `null` (texte trop long, ou plateforme sans encodeur). |
| `QrTags` | Étiquettes de test (`qr-code`, `qr-scanner`). |

## 2. Bibliothèques (justification)

| Bibliothèque | Version | Licence | Où | Taille | Pourquoi |
|---|---|---|---|---|---|
| ZXing core (`com.google.zxing:core`) | 3.5.3 (Maven Central, vérifiée le 8 novembre 2026) | Apache 2.0 | Android et ordinateur | 0,61 Mo | Dessiner et lire les codes ; Java pur, aucune dépendance, aucun réseau. En maintenance (stable, sans nouveautés) : suffisant pour un format figé. |
| CameraX (`androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | 1.6.1 (Google Maven, stable, vérifiée le 8 novembre 2026) | Apache 2.0 | Android seulement | 2,6 Mo | Afficher la caméra et analyser ses images ; AndroidX, sans services Google Play. |

Écartés : **ML Kit** (service Google, interdit par `tools/forbidden-dependency-groups.txt`) ; QRose (une bibliothèque de plus, plus jeune) ; un encodeur maison (environ 500 lignes à maintenir). iOS : le code commun reste compatible (`QrCode.ios.kt`, sans encodeur ni lecteur pour l'instant) ; l'encodeur et la caméra du système viendront avec la compilation iOS.

## 3. Vie privée et permission

- **Permission `CAMERA`** déclarée « non requise » (`uses-feature android:required="false"` : un téléphone sans caméra installe l'application) et **demandée seulement à l'ouverture d'un lecteur**, avec la raison à l'écran ; jamais à l'installation.
- Chaque image de la caméra est lue sur le téléphone puis abandonnée : rien n'est enregistré ni envoyé par le composant. Ce que l'appelant fait du texte lu relève de son propre registre des données.
- Aucun traceur, aucun appel réseau (vérifié par `tools/check-dependencies.sh` et `tools/check-no-trackers.sh`).

## 4. Validation en personne (« Décider »)

- **Citoyen** : « Montrer mon code au validateur » dans « Mon niveau de votant » (compte non vérifié, sans validation ni certification en cours) : une preuve signée de la clé du compte (`ValidationProof`, type 22243), renouvelée chaque minute, valable cinq minutes et acceptée une fois.
- **Validateur** : « Je suis validateur : valider une personne » tire la clé de la séance de cet appareil (en mémoire seulement : une nouvelle séance si l'application se ferme), la montre pour l'ouvrir dans l'espace de l'équipe, puis lit les codes et envoie chacun **une fois**, signé avec la clé de séance (`POST /api/v1/urnes/validations`), et dit en clair ce qu'il est devenu.
- Pour essayer avec un seul téléphone : le code du citoyen s'affiche aussi sur l'ordinateur (application de bureau), le téléphone le lit.
