# Note d'intégration : affiche et tract à code QR (AG-61), étape 1

Rédigée le 9 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-a90a2a6bbf1710c8a`, partie de 4b80fe3f). Le bloc de numéros de décisions d'« Agir » est épuisé : la décision de conception est écrite en section 9 (« Décision à numéroter »), à numéroter par le chef d'équipe et à reporter dans `docs/DECISIONS.md`.

> **Rien n'est compilé ni testé.** Les compilations de branches étaient interdites ce jour-là (machine fragile). Le code et les tests ont été écrits en lisant les sources réelles de chaque API appelée (section 12) ; ils doivent être compilés et lancés (`gradle-safe.ps1`, section 11) avant toute entrée dans main.

## 1. Ce qui est fait

Cahier fonctionnel, AG-61 (ligne 404) : « Un événement ou n'importe quel contenu devient une affiche A4 ou A3 générée sur l'appareil ; le code QR contient l'événement signé lui-même (ajout à l'agenda sans Internet, signature vérifiée) ou l'adresse stable sans suivi ; guide de l'affichage libre. » Prolonge le matériel militant (AG-15) et le partage (S-37, M29).

1. **« Faire une affiche »** sur le détail d'une mobilisation de l'agenda (à venir, ni annulée ni passée, publiée) et sur le détail d'un appel (ouvert, publié) : écran avec le choix du format (A4 ou A3, boutons radio), un aperçu en proportions, une note sur ce que contient le code, une mention de l'imprimeur facultative, le bouton d'enregistrement et un paragraphe « Où coller vos affiches » qui ouvre la fiche `coller-des-affiches-et-distribuer-des-tracts`.
2. **Affiche fabriquée sur l'appareil**, jamais sur le serveur : un modèle commun (`core/agir/poster/Poster.kt`, `PosterContent`) et une mise en page commune (`PosterLayout.kt`, `PosterLayouts.layout`) qui ne dépendent d'aucun écran ; chaque plateforme mesure ses textes et dessine la liste d'éléments obtenue.
3. **Code QR** : l'événement signé lui-même, compressé, quand il tient dans un code lisible sur A4 ; sinon l'adresse stable du contenu, sans aucun paramètre (section 2).
4. **Enregistrement** : Android, un PDF dessiné par le système (`PdfDocument`) et enregistré par la boîte de dialogue des documents ; ordinateur, une image PNG à 150 points par pouce enregistrée par la boîte d'enregistrement ; iOS, pas encore (l'écran le dit).
5. **« Scanner une affiche »** dans l'agenda, là où une caméra lit les codes (Android) : lecteur commun de « Décider », signature vérifiée sur le téléphone, « Ajouter à mon agenda », code falsifié refusé, adresse ouverte quand le réseau est là. Absent sur ordinateur (pas de caméra ici).
6. **Rien n'est publié ni gardé** : faire une affiche ne publie rien (S-146 ne s'applique pas, un compte compromis peut faire une affiche d'un contenu déjà public) ; rien n'est stocké en dehors du fichier choisi par la personne et, au scan, de l'événement ajouté à l'agenda.

## 2. Format du code et limite de taille

Détail normatif : `docs/FORMATS.md`, section 4 sexdecies.

- **Forme « événement »** : `PPF:EVENT:1:` suivi du Base45 (RFC 9285) du gzip (RFC 1952) du JSON exact de l'événement signé, en UTF-8, tel que `SignedEvent.toJson()` l'écrit. À la lecture : Base45, gzip (64 Ko au plus une fois décompressé), JSON, puis `SignedEvent.verify()` (identifiant recalculé et signature BIP-340), la vérification commune de `core/signing`.
- **Pourquoi Base45 et des majuscules** : tous les caractères appartiennent au mode alphanumérique des codes QR (5,5 bits par caractère, contre 8 en mode octet). ZXing choisit ce mode de lui-même quand tout le texte s'y prête. Le préfixe est donc en capitales (`PPF:EVENT:1:` plutôt que `ppf:event:`) ; le chiffre est la version du format.
- **Pourquoi toujours compresser** : un seul format, plus simple à vérifier ; la compression divise par près de deux le JSON (clés et signature en hexadécimal, texte français). Mesure sur les huit mobilisations [EXEMPLE] de l'agenda : de 1 027 à 1 233 octets de JSON, de 986 à 1 187 caractères de code, toutes sous la limite.
- **Compression** : `Gzip` d'« Agir » (`core/agir/map/Gzip.kt`, `java.util.zip` sur Android et l'ordinateur). Pas de nouvel `expect` : la forme gzip coûte 18 octets d'en-tête de plus qu'un deflate brut, soit 27 caractères, ce qui ne justifiait pas du code de plateforme en plus. Sur iOS, `Gzip` n'existe pas encore : l'affiche prendrait l'adresse (mais iOS n'enregistre pas d'affiche à l'étape 1).
- **Limite** : 1 451 caractères (`PosterQr.MAX_CHARACTERS`), la capacité d'un code de **version 25** au niveau de correction **M** en mode alphanumérique (ISO/IEC 18004, table 7 : 1 000 mots de données, soit 7 985 bits utiles après l'en-tête). Version 25 : 117 modules de côté, plus 4 de marge de chaque côté. Sur l'affiche A4, le code mesure 250 points (88 mm) : 0,70 mm par module au pire ; sur A3, 125 mm et 1 mm par module. Au-delà de la limite, le code porte l'adresse.
- **Niveau de correction M** (15 % de modules perdus récupérables) : c'est celui du composant commun `encodeQr`, que je n'ai pas modifié (fichier de « Décider »). Une affiche se salit et se déchire : le niveau Q (25 %) serait plus robuste mais réduit la capacité à 1 041 caractères en version 25 (question 2).
- **Forme « adresse »** : `https://politisation-populaire-francaise.org/agir/agenda/<clé>/<identifiant>` pour une mobilisation, `/agir/appels/<clé>/<identifiant>` pour un appel (`core/agir/poster/AgirLinks.kt`). Construites comme celles de « Voir » et de « Comprendre » (`VoirLinks`, `ComprendreLinks`) : un chemin par contenu, à partir de ce qui ne change pas quand le contenu est corrigé ; ici la clé de l'auteur en plus, car plusieurs auteurs peuvent choisir le même identifiant. **Jamais de paramètre ni de fragment** : rien ne dit qui a imprimé l'affiche, où elle a été collée ni quand elle a été lue. À la lecture, seules ces adresses exactes sont suivies : `https`, un domaine de PPF (`OfficialSite`), ni nom d'utilisateur, ni port, ni paramètre, ni autre chemin.
- **Ce que le code ne contient jamais** : le compte de la personne qui fait l'affiche, son appareil, la date ou le lieu de l'impression. L'événement est celui que son auteur a signé et publié, tel quel.

## 3. Mise en page imprimée

- **Formats** : A4 (595 × 842 points) et A3 (842 × 1 191 points), en portrait. Les tailles sont écrites pour l'A4 ; l'A3 est un A4 agrandi (même mise en page, facteur 1,415).
- **De haut en bas** : la mention [EXEMPLE] pour un contenu d'exemple ; le genre (« RASSEMBLEMENT », « APPEL · SE RÉUNIR ») ; le titre, aussi grand que la place le permet (de 48 à 24 points, quatre lignes au plus, puis « … ») ; un filet ; « Quand » (le jour en toutes lettres, comme le détail de l'agenda : « Samedi 14 novembre 2026, 14 h à 17 h »), « Jusqu'au » pour l'échéance d'un appel, « Où » (le lieu au niveau que donne l'auteur, avec le territoire quand le lieu ne le nomme pas, sinon « Territoire »), « Organisé par » (l'auteur et les organisations qui l'ont confirmé avec leur propre clé, D-044, nommés comme dans l'application) ou « Lancé par » pour un appel ; le texte court (résumé, sinon le début de la description) dans la place restante. En bas : un filet, le code QR (88 mm sur A4) avec, à sa droite, « Scannez pour ajouter à votre agenda, même sans réseau » (ou la phrase de l'appel, ou de l'adresse) et comment faire ; puis le pied « Affiche faite avec l'application Politisation Populaire Française · politisation-populaire-francaise.org » et la mention de l'imprimeur si la personne en a tapé une.
- **Couleurs d'impression** (`PosterColor`, un seul endroit) : noir sur blanc pour tout ce qui doit être lu, le code d'abord (meilleur contraste sur toute imprimante, et celui qu'attendent les lecteurs de codes) ; bleu de la marque (#2B3A8F, contraste 10,6 sur blanc) seulement pour le genre, les étiquettes et les filets, qui restent gris foncé sur une imprimante noir et blanc ; pas de rouge (il pâlit sur beaucoup d'imprimantes) ; le blanc est le papier, rien n'est imprimé pour lui. Les jetons de `PpfTheme` restent pour les écrans.
- **Police** : Atkinson Hyperlegible de l'identité, lue dans les ressources de l'application (`Res.readBytes("font/…")`) ; la police sans empattement du système si elle ne peut pas être lue. Ordinateur : police faite en mémoire (`Font.createFont`), aucun fichier ; une ligne qu'elle ne sait pas afficher prend la police sans empattement de Java. Android : le système ne fait une police que depuis un fichier (API 26) ; les deux fichiers de police (publics) sont copiés dans `cache/affiche/` ; un caractère absent est dessiné par les polices de secours du système.
- **Coupure des lignes** : entre les mots seulement, jamais à une espace insécable (« 14 h » reste entier), dans un mot seulement s'il est plus large que la ligne.

## 4. Enregistrement selon la plateforme

| Plateforme | Fichier | Comment |
|---|---|---|
| Android | PDF, une page, texte et code vectoriels | `android.graphics.pdf.PdfDocument` (page en points), dessiné en mémoire avant l'ouverture de la boîte de dialogue `ActivityResultContracts.CreateDocument("application/pdf")` ; écrit seulement à l'endroit choisi, sans permission ; rien dans le stockage partagé sans ce choix. Le code est un seul tracé (`Path`) : les modules voisins se fondent sans filet blanc. |
| Ordinateur | PNG à 150 points par pouce (1 240 × 1 754 pixels en A4, 1 754 × 2 481 en A3), résolution écrite dans le fichier (`pHYs`) | Java2D (`BufferedImage`, `Graphics2D`), boîte d'enregistrement du système (`FileDialog`). Le code est dessiné en pixels entiers, sans lissage, pour garder des bords nets. **Pas de PDF** : le JDK n'écrit pas de PDF sans bibliothèque, et aucune n'a été ajoutée. |
| iOS | aucun | L'application iOS n'est pas construite et `encodeQr` n'y dessine pas encore de code : l'écran le dit. |

## 5. Scanner une affiche (Android)

- Bouton « Scanner une affiche » dans la barre d'outils de l'agenda, seulement là où `canScanQr` est vrai ; sur ordinateur il n'apparaît pas.
- Le lecteur commun `QrScanner` (`docs/integration/qr-codes.md`) demande la caméra seulement à l'ouverture de cet écran ; la première lecture est gardée, la caméra s'arrête dès qu'un résultat s'affiche.
- **Événement signé et valide** : « Signature vérifiée : c'est exactement ce que son auteur a signé », puis qui l'a signé : l'équipe de PPF, une organisation vérifiée, ou « le compte npub1…, que l'application ne connaît pas » avec une mise en garde (la signature prouve que le contenu n'a pas été modifié, pas que son auteur est fiable). Une mobilisation montre son genre, son statut (« Annulé », « Reporté »), son titre, sa date, son lieu, et « Ajouter à mon agenda » ; un appel se lit sur place (« lu sur l'affiche et vérifié, pas enregistré »).
- **Ajouter à mon agenda** (`AgendaLibrary.addFromPoster`) : l'événement est vérifié de nouveau, gardé dans le magasin local comme un événement reçu du serveur (remplacement des versions par `EventReplacement`, comme partout), puis ouvert. Rien n'est envoyé. Si l'appareil a déjà cette version, ou une plus récente, il le dit et garde la plus récente.
- **Code falsifié** (identifiant ou signature faux) : « Signature invalide : ce code a été modifié ou fabriqué. Rien n'est ajouté. » **Code abîmé** : message pour rapprocher le téléphone. **Code étranger** : « Ce code n'est pas celui d'une affiche PPF : rien n'est ouvert. »
- **Adresse d'une mobilisation** (`AgendaLibrary.openFromPoster`) : ouverte tout de suite si l'agenda la connaît ; sinon l'agenda entier est synchronisé comme d'habitude, puis elle est ouverte. Le serveur n'est jamais interrogé pour cet événement seul : il n'apprend pas quelle affiche a été lue. Sans réseau : « Il s'ouvrira quand votre téléphone aura du réseau », avec « Réessayer ». **Adresse d'un appel** : l'écran dit de le retrouver dans « Appels » (le paramètre `onOpenCall` d'`AgendaScreen` permettra au shell de l'ouvrir directement, question 5).

## 6. Vie privée et sécurité

- Aucun réseau pour faire une affiche ; aucun pour lire un code d'événement ; pour une adresse, seulement la synchronisation habituelle de l'agenda entier.
- Le fichier enregistré ne contient que ce qui est public (l'événement signé et publié, les noms affichés dans l'application) et ce que la personne a tapé elle-même (mention de l'imprimeur). Nom proposé : `affiche-<identifiant>-a4.pdf`, sans donnée personnelle.
- Modèle de menaces : `docs/THREAT_MODEL.md`, ajout du 9 novembre 2026 (affiche falsifiée, code remplacé par un autocollant, affiche qui trahirait qui l'a imprimée). Registre : `docs/REGISTRE_DONNEES.md`, ajout du même jour.

## 7. Limites (dites honnêtement)

- **Rien n'est compilé** ; les tests sont écrits, pas lancés.
- **Les pages du site aux adresses `/agir/agenda/…` et `/agir/appels/…` n'existent pas encore** : l'application sait les lire, mais une personne sans l'application qui scanne une affiche « adresse » tombera sur une page introuvable tant qu'elles ne sont pas écrites (question 1). Les affiches « événement » (le cas normal) n'en dépendent pas : sans l'application, un lecteur de codes ordinaire affiche un texte illisible commençant par `PPF:EVENT:1:`.
- **Un événement ajouté depuis une affiche s'affiche dans l'agenda comme un événement servi par le serveur**, même si son auteur est inconnu et que le serveur ne l'a jamais publié (affiche faite par un tiers avec sa propre clé). L'écran du scan le dit avant l'ajout ; l'agenda ne le marque pas ensuite (question 3).
- **Android** : si l'activité est recréée pendant que la boîte de dialogue des documents est ouverte (rotation, mémoire), le fichier créé reste vide ; il suffit de recommencer.
- **Aperçu approché** : l'aperçu de l'écran reprend les mots, le code et les proportions, pas la mise en page exacte (qui est celle du fichier, dessinée par la plateforme).
- **Ordinateur** : PNG seulement, à imprimer en taille réelle (« 100 % ») ; le fichier porte sa résolution, mais certains logiciels l'ignorent.
- **Pas d'autres contenus** à l'étape 1 (fiche, revendication) : le modèle commun (`PosterKind`) est prêt à les recevoir.
- **Règles de l'affichage** : je n'ai écrit aucune règle juridique. L'écran renvoie à la fiche `coller-des-affiches-et-distribuer-des-tracts` (rédigée avec ses sources par un autre agent) ; la vérification du code de l'environnement (article L581-13) demandée par le cahier fonctionnel reste à faire avec cette fiche et l'avocat. Dans les tests, hors du shell, la fiche n'est pas chargée : le lecteur dit qu'elle n'est pas encore sur l'appareil.

## 8. Fichiers

Créés (pôle « Agir ») :
- `shared/core/src/commonMain/.../core/agir/poster/Base45.kt`, `AgirLinks.kt`, `PosterQr.kt`, `Poster.kt`, `PosterLayout.kt` ;
- `shared/ui/src/commonMain/.../ui/agir/poster/PosterExport.kt`, `PosterScreen.kt`, `PosterScanScreen.kt` ;
- `actual` : `shared/ui/src/androidMain/.../ui/agir/poster/PosterExport.android.kt`, `jvmMain/.../PosterExport.jvm.kt`, `iosMain/.../PosterExport.ios.kt` ;
- `shared/ui/src/commonMain/composeResources/values/strings_affiche.xml` ;
- tests : `shared/core/src/commonTest/.../core/agir/poster/PosterQrTest.kt`, `PosterContentTest.kt`, `PosterLayoutTest.kt`, `shared/ui/src/jvmTest/.../ui/agir/poster/PosterScreenTest.kt`.

Modifiés (pôle « Agir ») :
- `ui/agir/agenda/AgendaLibrary.kt` : `PosterAddOutcome`, `addFromPoster`, `knows`, `openFromPoster` ;
- `ui/agir/agenda/AgendaScreen.kt` : paramètres `posterExporter` et `onOpenCall`, écrans de l'affiche et du scan par-dessus l'agenda, bouton « Scanner une affiche » ;
- `ui/agir/agenda/MobilizationDetail.kt` : paramètre `onMakePoster`, bouton « Faire une affiche » ;
- `ui/agir/calls/CallDetail.kt` : paramètre `onMakePoster`, bouton dans « Faire grandir l'appel » ;
- `ui/agir/calls/CallsScreen.kt` : paramètre `posterExporter`, écran de l'affiche ;
- `docs/integration/agir-affiche-qr.md` (cette note), `docs/FORMATS.md`, `docs/THREAT_MODEL.md`, `docs/REGISTRE_DONNEES.md`, `docs/GUIDE_PROPRIETAIRE.md`, `docs/CHANGELOG.md`, `docs/ROADMAP.md`.

**Aucun fichier d'un autre pôle n'est modifié.** Utilisés sans les toucher : le composant QR de « Décider » (`ui/components/qr`), `VoirLinks` de « Voir » (`SITE`, `encodeSegment`), `OfficialSite` (`core/editorial`), `GuideReader` et `LocalHelpState` du mode manifestation d'« Agir ». Le shell (`AdaptiveShell.kt`) n'est pas modifié : les écrans s'ouvrent à l'intérieur d'`AgendaScreen` et de `CallsScreen`, avec les valeurs par défaut des nouveaux paramètres. Rien dans les fichiers d'AG-60 (simulateur de grève).

## 9. Décision D-192

**Affiche et tract à code QR, étape 1 (AG-61)** : contexte, cahier fonctionnel ligne 404 ; décision par délégation de la propriétaire au chef d'équipe (brainstorming du 8 novembre 2026).

- **Options pour le contenu du code** : (A) l'événement signé seul, toujours ; (B) l'adresse stable seule ; (C) l'événement signé quand il tient dans un code lisible sur A4, l'adresse sinon. **Choix : C**, comme le demande le cahier fonctionnel : sans réseau dans le cas normal, une affiche lisible dans tous les cas.
- **Forme de l'événement** : `PPF:EVENT:1:` + Base45 (RFC 9285) du gzip (RFC 1952) du JSON exact, toujours compressé ; un seul format, en mode alphanumérique (environ 30 % de place gagnée sur du Base64), versionné par son préfixe. Écartés : JSON en clair (deux fois plus long), deflate brut (un `expect` de plus pour 27 caractères), Base64 (30 % plus long).
- **Limite** : version 25, niveau M, 1 451 caractères ; code de 88 mm sur A4 (0,70 mm par module au pire), 125 mm sur A3.
- **Adresses stables** : `/agir/agenda/<clé>/<identifiant>` et `/agir/appels/<clé>/<identifiant>` sur le domaine principal, sans paramètre ni fragment, sur le modèle de `VoirLinks` ; la clé de l'auteur fait partie du chemin.
- **Fabrication** : sur l'appareil, d'un modèle et d'une mise en page communs indépendants de l'écran ; PDF par `PdfDocument` sur Android, PNG à 150 points par pouce sur ordinateur (pas de PDF sans bibliothèque), rien sur iOS à l'étape 1 ; enregistrement seulement à l'endroit choisi par la personne.
- **Couleurs d'impression** : noir sur blanc, bleu de la marque pour le genre, les étiquettes et les filets ; définies une fois (`PosterColor`).
- **Scan** : lecteur commun de « Décider », signature vérifiée sur le téléphone, ajout dans le magasin local comme un événement reçu, signataire inconnu signalé avant l'ajout ; adresse ouverte après la synchronisation habituelle de tout l'agenda, jamais par une requête sur cet événement seul.
- **Mention de l'imprimeur** : champ facultatif, vide par défaut, gardé en mémoire seulement, imprimé en bas de l'affiche ; la fiche dit quand la loi la demande (question 4).
- **Rien n'est publié** : faire une affiche ne passe pas par le port de publication et n'est pas soumis à S-146.

## 10. Questions pour le chef d'équipe (avec recommandations)

1. **Pages du site aux adresses des affiches.** Les adresses `/agir/agenda/<clé>/<identifiant>` et `/agir/appels/<clé>/<identifiant>` n'ont pas encore de page (le site n'a que `/agenda`). **Recommandation** : les écrire avec le pôle du site avant que des affiches « adresse » soient imprimées en nombre (page rendue sans JavaScript, comme `/agenda`, avec le contenu signé et son lien de vérification) ; en attendant, garder le cas « adresse » rare (il ne sert qu'aux contenus très longs).
2. **Niveau de correction du code.** M aujourd'hui (celui du composant commun). **Recommandation** : demander à « Décider » un paramètre de niveau dans `encodeQr` (sans changer le défaut M), puis passer les affiches en Q : plus robuste aux taches et aux déchirures, au prix d'une limite de 1 041 caractères (les huit exemples de l'agenda, de 986 à 1 187 caractères, tomberaient en partie sur l'adresse). À trancher après un essai d'impression et de lecture réel sur la machine de la propriétaire.
3. **Événement ajouté depuis une affiche, d'un auteur inconnu.** Il s'affiche ensuite dans l'agenda comme les autres. **Recommandation** : à l'étape 2, le marquer « Ajouté depuis une affiche, pas encore vu sur le serveur » jusqu'à ce que la synchronisation le retrouve (il faudrait une petite table locale ou un état dans `AgendaState` : une migration, donc à décider avec le registre des migrations).
4. **Mention de l'imprimeur.** Facultative et vide par défaut ; la fiche explique la règle (le point est encore à trancher, `docs/ROADMAP.md`, contenus réels d'« Agir » : tract imprimé chez soi, loi du 29 juillet 1881). **Recommandation** : la garder facultative tant que l'avocat n'a pas tranché ; si elle est obligatoire pour une affiche imprimée chez soi, afficher une mise en garde avant l'enregistrement d'une affiche sans mention, jamais remplir le champ à la place de la personne.
5. **Ouvrir un appel depuis le scan.** `AgendaScreen(onOpenCall = …)` existe, mais le shell (`AdaptiveShell.kt`, hors du pôle) ne le branche pas. **Recommandation** : le brancher vers `callsLibrary.openAddress(address)` puis la navigation vers « Agir », « Appels », au moment de l'intégration.
6. **PDF sur ordinateur.** **Recommandation** : rester en PNG ; si la propriétaire veut un PDF, écrire un PDF minimal qui embarque l'image (le JDK compresse en deflate), sans bibliothèque, à l'étape 2.
7. **Autres contenus (fiche, revendication, contenus de « Voir »).** **Recommandation** : étape 2, en ajoutant leurs genres à `PosterKind` et leurs adresses stables (déjà définies pour « Voir » et « Comprendre »).

## 11. Comment tester

Après compilation (`gradle-safe.ps1` sur la machine de la propriétaire, une seule compilation à la fois) :
- `:shared:core:jvmTest --tests "*poster*"` : Base45 (exemples de la RFC 9285), aller et retour de l'événement signé, événement modifié refusé, code abîmé, adresse au-delà de la limite et sans paramètre, adresses refusées (paramètre, fragment, http, faux domaine, nom d'utilisateur, port), appel, modèle de l'affiche, mise en page en A4 et A3, coupure des lignes ;
- `:shared:ui:jvmTest --tests "*PosterScreenTest*"` : l'affiche s'ouvre depuis une mobilisation, A4 et A3, la fiche, l'export PNG dans un dossier temporaire du test dont le code se relit comme l'événement signé, pas d'entrée de scan sur ordinateur, pas d'affiche d'un événement annulé ou passé, ajout à l'agenda d'un événement lu, code falsifié refusé ;
- puis `:shared:ui:jvmTest` et `:android:assemblePreview` en entier (nouvelles entrées dans `AgendaScreen`, `CallsScreen`, `MobilizationDetail` et `CallDetail`).
- Sur téléphone : guide de la propriétaire, section « Tester l'affiche à code QR ».

## 12. API lues pour écrire sans compiler (lignes exactes)

- `SignedEvent.verify()`, `toJson()`, `fromJson()` : `core/signing/Event.kt`, lignes 36, 49, 52.
- `Gzip.decompress`, `Gzip.compress` : `core/agir/map/Gzip.kt`, lignes 14 et 17 (`null` sur iOS).
- `encodeQr`, `canScanQr`, `QrScanner`, `QrMatrix` : `ui/components/qr/QrCode.kt`, lignes 43, 46, 54, 34 ; `JvmQr.decode` : `jvmMain/.../QrCode.jvm.kt`, ligne 43.
- `LocalEventStore.store` et `LocalStoreOutcome` : `core/local/LocalEventStore.kt`, ligne 22.
- `OfficialSite.isOfficial` : `core/editorial/ContentCheck.kt`, ligne 18 ; `VoirLinks.SITE` et `encodeSegment` : `core/voir/VoirLinks.kt`, lignes 27 et 77.
- `ContentAddress.of` et `parse` : `core/signing/EventKinds.kt`, lignes 75 et 67.
- `GuideReader` : `ui/agir/manifestation/ManifestationParts.kt`, ligne 396 ; `LocalHelpState` et `HelpState.guide` : `HelpState.kt`, lignes 174 et 114.
- `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(…))` : modèle de `ui/agir/camera/ProtectedCamera.android.kt`, ligne 178.
- `Res.readBytes(...)` sans annotation : `ui/agir/agenda/AgendaLibrary.kt`, ligne 495 (chemin `font/atkinson_hyperlegible_regular.ttf` supposé relatif à `composeResources/` comme `files/contents/agenda.json` ; sinon la police du système est prise, sans erreur).
- `partyName`, `whenText`, `AgendaFormat`, `StatusPill`, `typeLabel` : `ui/agir/agenda/ParticipationViews.kt`, ligne 101, `AgendaLabels.kt`, lignes 141, 62, 161, 88 ; `Participations.hosting` : `core/agir/agenda/Participation.kt`, ligne 157.
- `typeLabelResource(CallType)` : `ui/agir/calls/CallLabels.kt`, ligne 25.
- `PpfRadioRow`, `PpfToolbarButton` : `ui/components/Chips.kt`, lignes 126 et 174 ; `SystemBack` : `ui/components/SystemBack.kt`, ligne 14.
- Plateformes, d'après leur documentation : `android.graphics.pdf.PdfDocument` (API 19, page en points), `Typeface.createFromFile(File)`, `Path.addRect(…, Path.Direction.CW)` ; `java.awt.Font.createFont`, `Font.canDisplayUpTo`, `javax.imageio` avec le nœud `pHYs` de `javax_imageio_png_1.0`.
- Points dont je suis le moins sûr : le chemin des polices pour `Res.readBytes` (repli prévu) ; la conversion SAM d'une lambda vers `fun interface` à fonction `suspend` (déjà utilisée par `CalendarExporter`) ; la lecture du code sur l'image entière dans le test (le test découpe la zone du code pour aider ZXing).
