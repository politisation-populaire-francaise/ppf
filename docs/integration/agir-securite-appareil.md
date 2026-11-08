# Note d'intégration : sécurité de l'appareil, étape 1 (M33 : S-43, S-44, S-45 ; AG-56)

Rédigée le 8 novembre 2026 par un agent de la session « Agir » (branche `worktree-agent-a713aef63eb5a4009`, partie de `feature/agir` 61b080d). La session « Agir » fusionne, numérote les décisions proposées (section 6, bloc D-081 à D-089) et met à jour `DECISIONS.md`, `CHANGELOG.md`, `ROADMAP.md` et `GUIDE_PROPRIETAIRE.md`. Cette note a mis à jour elle-même `REGISTRE_DONNEES.md` et `THREAT_MODEL.md` (sections « Ajout du 8 novembre 2026 : sécurité de l'appareil »).

## 1. Ce qui est fait

1. **Verrouillage de l'application (S-43)**, facultatif, **désactivé par défaut** (voir décision 1) :
   - un code de 6 à 16 chiffres, tapé deux fois ; les codes trop simples sont refusés (un seul chiffre répété, suite montante ou descendante comme 123456 ou 987654, motif répété comme 121212 ou 123123) ;
   - jamais le code ni sa longueur sur l'appareil : seulement une **empreinte lente et salée** (PBKDF2-HMAC-SHA-256, 600 000 itérations comme le recommande l'OWASP, sel aléatoire de 16 octets) ; sur Android, cette empreinte est en plus mélangée à une clé HMAC générée dans l'**Android Keystore** (« poivre ») qui ne sort jamais du téléphone ; comparaison en temps constant ;
   - demandé au démarrage, puis au retour dans l'application après le délai choisi : « Immédiatement » (par défaut, le plus protecteur), 1, 5 ou 15 minutes. Le temps passé ailleurs est mesuré sur deux horloges (une horloge monotone, que l'on ne peut pas changer, et l'heure du système, qui avance même quand le téléphone dort) : on retient la plus grande ;
   - **attente croissante** après 5 codes faux : 30 secondes, 1, 5, 15 minutes, puis 1 heure à chaque nouvel essai ; le compteur survit à un redémarrage et ne revient à zéro qu'au bon code ; une heure reculée n'allonge pas l'attente au-delà du délai ;
   - **pas d'effacement automatique** après N essais (voir question 2) ;
   - écran de verrouillage accessible : titre de fenêtre pour le lecteur d'écran, champ nommé « Code de verrouillage », clavier numérique sur téléphone, Entrée valide au clavier, erreurs et attente annoncées (`liveRegion`), contrastes des jetons de `PpfTheme`, modes clair et sombre ; l'interface est cachée dessous (rien n'est dessiné, rien n'est lu par le lecteur d'écran) mais reste en mémoire : un brouillon survit au verrouillage ;
   - **le 15 et le 112 restent appelables sans déverrouiller**, comme sur l'écran de verrouillage du téléphone ;
   - « Code oublié ? » : la seule sortie est l'effacement rapide (avec le rappel de la phrase de récupération) ;
   - réglages dans « Mon espace », carte « Sécurité de cet appareil » (avec ou sans compte) : activer, choisir le délai, « Verrouiller maintenant », « Changer le code » (code actuel demandé), désactiver (mise en garde du principe 10, puis code actuel demandé).
2. **Effacement rapide (S-44)** : un geste, puis une confirmation qui dit ce qui est effacé, rappelle la phrase de récupération et précise que rien n'est effacé sur le serveur (« Annuler » ou « Tout effacer »). Accessible depuis « Mon espace », la feuille AIDE (en dernier, pour ne jamais être pressé par erreur en cherchant un numéro), le mode manifestation (« Préparer mon téléphone ») et l'écran de verrouillage (« Code oublié ? »). Il enchaîne :
   - le chemin existant d'« Effacer mes clés » (`AccountSession.signOut` : clé, suivis, conversations, salons, agenda, réponses aux appels, témoignages, D-032 et D-039) et le point de ralliement ;
   - l'effacement de l'identité et des réglages de sécurité ;
   - puis **tout le reste de l'appareil** : sur Android, l'« effacement des données » du système (`ActivityManager.clearApplicationUserData`) qui supprime la base locale de tous les pôles, toutes les préférences, l'identité, les fichiers, le cache, les médias et les clés de l'Android Keystore, et arrête l'application (si le système refuse, PPF fait la même chose fichier par fichier puis s'arrête) ; sur ordinateur, suppression de toutes les lignes de toutes les tables de la base locale (avec `secure_delete`, puis `VACUUM` : les pages libérées disparaissent du fichier), suppression de tous les autres fichiers du dossier privé, puis fermeture. Effacer au niveau du stockage plutôt que table par table garantit que les tables ajoutées plus tard par les autres pôles sont effacées aussi, sans y penser ;
   - un échec le dit et propose de réessayer ; sans moyen de tout effacer (iOS, pas encore compilé), l'application efface la clé et les réglages et dit d'effacer le reste en la désinstallant.
   - **Le code PIN de contrainte n'est pas codé** (avis d'un avocat indispensable : Code pénal, articles 434-4 et 434-15-2).
3. **Application discrète (S-45), partie sûre seulement** : « Masquer le contenu dans les applications récentes » sur Android (`FLAG_SECURE`, `expect`/`actual`, rien ailleurs), **activé par défaut** (voir décision 3). L'écran explique que cela bloque aussi les captures et l'enregistrement de l'écran de PPF. Le désactiver affiche d'abord une mise en garde (principe 10) ; le réactiver se fait en un geste. Les fenêtres de dialogue (feuille AIDE, écran de verrouillage) en héritent. Sur ordinateur, le réglage n'est pas montré (aucun moyen portable).
4. **« Préparer mon téléphone » (AG-56)** dans le mode manifestation, à la place de l'annonce « bientôt » : l'état de PPF sur le téléphone, ligne par ligne, avec son état en mots (« Fait » ou « À faire », jamais la couleur seule) et le geste qui le corrige :
   - verrouillage de PPF (bouton « Activer le verrouillage ») ;
   - déverrouillage par **code seul** : toujours vrai, PPF ne propose ni empreinte ni visage (voir décision 2), donc rien à couper avant de partir ;
   - contenu masqué dans les applications récentes (Android ; bouton « Masquer ») ;
   - phrase de récupération notée sur papier (case cochée en mémoire seulement : seule la personne le sait) ;
   - effacement rapide prêt (le bouton) ;
   - puis un guide générique des réglages du téléphone, **[EXEMPLE]** : biométrie du téléphone désactivée ou mode « Verrouillage » d'Android, code plutôt que schéma, notifications masquées sur l'écran verrouillé, sauvegarde automatique des photos coupée ; et l'information juridique **[EXEMPLE, à vérifier par un avocat]** sur l'arrêt de la Cour de cassation (assemblée plénière, 7 novembre 2022, n° 21-83.146 : le code de déverrouillage peut être une « convention secrète de déchiffrement », article 434-15-2 du Code pénal) ; « Nom et icône discrets : à l'étude ».
5. **Rien n'est envoyé** : aucun appel réseau dans ce module ; testé (aucune requête ne contient le code ni l'empreinte).

### Annoncé « bientôt » ou laissé pour plus tard

- **Biométrie** (empreinte, visage) : non faite, voir décision 2.
- **Nom et icône neutres** (S-45) : étude seulement, section 4.
- **Code PIN de contrainte** (S-44) : rien n'est codé avant l'avis d'un avocat.
- **Chiffrement de la base locale** avec une clé tirée du code : étape suivante à étudier (section 5, question 4).
- **iOS** : réglages en mémoire seulement, pas de masquage ni d'effacement complet tant que l'application n'est pas compilée.

## 2. Fichiers

Nouveaux (pôle Agir) :
- `shared/core/.../core/agir/security/AppLock.kt` (règles du code, attentes, horloges, verrou), `DeviceSecuritySettings.kt` (réglages, empreinte, interfaces `DeviceSecurityStorage` et `DeviceEraser`), `Pbkdf2.kt` (PBKDF2 portable sur le SHA-256 de bitcoin-kmp déjà présent, et `expect` vers le fournisseur de la plateforme) ;
- `actual` : `androidMain/.../AndroidDeviceSecurity.kt` (préférence privée, poivre du Keystore, effacement par le système), `Pbkdf2.android.kt` ; `jvmMain/.../DesktopDeviceSecurity.kt` (fichier privé, effacement de la base et des fichiers), `Pbkdf2.jvm.kt` ; `iosMain/.../Pbkdf2.ios.kt` ;
- `shared/ui/.../ui/agir/security/DeviceSecurityState.kt` (état, `LocalDeviceSecurity`), `LockScreen.kt` (`DeviceSecurityGate`, écran de verrouillage, écran après effacement), `DeviceSecurityCard.kt` (carte de « Mon espace », bloc de la feuille AIDE, « Préparer mon téléphone »), `SecurityParts.kt` (champ du code, interrupteurs, dialogues), `SecureWindow.kt` et ses `actual` Android (`FLAG_SECURE`), ordinateur et iOS (rien) ;
- `shared/ui/src/commonMain/composeResources/values/strings_security.xml` ;
- tests : `shared/core/src/commonTest/.../agir/security/AppLockTest.kt`, `shared/core/src/jvmTest/.../agir/security/DesktopDeviceEraserTest.kt`, `shared/ui/src/jvmTest/.../agir/security/DeviceSecurityTest.kt`.

Fichiers partagés modifiés (changements minimaux) :
- `shared/ui/.../ui/AppDependencies.kt` : deux champs facultatifs `securityStorage` et `deviceEraser` (nuls par défaut : en mémoire, rien n'est effacé sur le disque ; c'est ce qui protège l'ordinateur de la personne qui lance les tests) ;
- `android/.../MainActivity.kt` et `desktop/.../Main.kt` : les deux arguments ;
- `shared/ui/.../ui/shell/AdaptiveShell.kt` : `rememberDeviceSecurity` (effacement relié au chemin d'« Effacer mes clés » et au point de ralliement) et `ProvideDeviceSecurity` + `DeviceSecurityGate` autour de l'interface ;
- `shared/ui/.../ui/account/MySpaceScreen.kt` : un appel `DeviceSecurityCard()` ;
- fichiers d'Agir : `HelpSheet.kt` (bloc « Effacer mes données » en dernier), `ManifestationScreen.kt` (`PreparePhoneBlock` à la place de l'annonce).

Aucune dépendance nouvelle (PBKDF2 du JDK et d'Android, SHA-256 de bitcoin-kmp déjà là, cycle de vie de Compose déjà présent par Compose Multiplatform), aucune icône nouvelle, **aucune migration** (ni SQLDelight, ni Flyway : les réglages sont une préférence, comme le point de ralliement), aucun hôte contacté.

## 3. Menaces

Voir la section ajoutée à `THREAT_MODEL.md`. En résumé : téléphone pris déverrouillé (verrou, délai « Immédiatement »), code deviné (attente croissante), copie des fichiers puis essai de tous les codes (empreinte lente ; poivre du Keystore sur Android ; **sur ordinateur, seul le chiffrement du disque protège vraiment**), contrainte biométrique (code seul), contenu visible dans les applications récentes (`FLAG_SECURE`), téléphone sur le point d'être pris (effacement rapide), effacement incomplet sur ordinateur (SSD : copies d'anciens blocs possibles au niveau du matériel), effacement par erreur (confirmation).

**Limite honnête** : le verrouillage de PPF est une porte, pas un chiffrement. Un code à 6 chiffres n'a qu'un million de valeurs : sans le poivre du Keystore, une copie du fichier se casserait en quelques heures sur une carte graphique. Sur Android, les fichiers de l'application sont déjà chiffrés par le téléphone et la clé du Keystore ne sort pas ; sur ordinateur, le guide doit conseiller le chiffrement du disque.

## 4. Étude : nom et icône neutres (S-45, « Plus tard, à étudier »), sans code

- **Faisabilité sur Android** : oui, avec des `activity-alias` déclarés dans le manifeste (par exemple « Notes » ou « Calculatrice », chacun avec son icône), activés et désactivés par `PackageManager.setComponentEnabledSetting`. Contraintes connues : le lanceur peut mettre quelques secondes à se mettre à jour et certains lanceurs suppriment le raccourci de l'écran d'accueil ; changer d'alias peut fermer l'application ; le nom réel reste visible dans les Paramètres (liste des applications), dans le magasin d'applications et pour toute personne qui ouvre l'application.
- **Risque de magasin** : les règles de Google Play sur la représentation trompeuse et l'usurpation d'identité visent les applications qui se font passer pour une autre ou cachent leur fonction ; un camouflage en « Calculatrice » qui n'en est pas une pourrait être refusé ou retiré. Un nom **générique et honnête** (« PPF » seul, icône sobre) est moins risqué qu'un faux usage. F-Droid n'a pas cette règle, mais exige la transparence des fonctions. À vérifier dans les règles en vigueur au moment de coder.
- **Valeur réelle** : faible contre une fouille sérieuse (le nom réel est dans les Paramètres), réelle contre un regard par-dessus l'épaule. Le masquage dans les applications récentes et le verrouillage couvrent déjà l'essentiel.
- **Recommandation** : plus tard, proposer seulement un choix entre l'icône normale et une icône sobre avec le nom « PPF », sans imiter une autre application ; ne rien faire qui ressemble à une application d'un autre usage.

## 5. Questions, avec recommandations (à transmettre au chef d'équipe)

1. **Verrouillage désactivé par défaut ?** Le principe 10 demande le réglage le plus protecteur par défaut. Mais un verrouillage activé d'office demanderait un code à la première ouverture, avant même de savoir ce qu'est PPF, et un code oublié ne se récupère qu'en effaçant tout ; la lecture se fait sans compte (CLAUDE.md, section 7). Recommandation : **désactivé par défaut, proposé clairement** : dans « Mon espace » et dans « Préparer mon téléphone » ; plus tard, le proposer aussi à la création du compte.
2. **Effacement automatique après N codes faux ?** Recommandation : **non** pour l'instant : un enfant, un proche ou un inconnu effacerait tout ; et un effacement déclenché devant une autorité touche aux mêmes articles que le code de contrainte (avis d'un avocat). L'attente croissante suffit contre l'essai au hasard.
3. **Biométrie** : `androidx.biometric` n'a qu'une version stable de janvier 2021 (1.1.0) ; la ligne 1.4.0 est en bêta depuis le 4 novembre 2026 seulement (page officielle des versions, consultée le 8 novembre 2026). L'API du système (`android.hardware.biometrics.BiometricPrompt`, Android 9 et plus) éviterait toute dépendance. Mais le cahier (AG-56, cahier des charges 5.13) demande justement de passer au code seul avant une manifestation, et une empreinte se force plus facilement qu'un code. Recommandation : **ne pas proposer la biométrie** dans PPF (code seul, toujours) ; si la propriétaire la veut plus tard, l'API du système sans dépendance, désactivée par défaut, avec le passage au code seul en un geste dans « Préparer mon téléphone ».
4. **Chiffrer la base locale avec une clé tirée du code** (pour que le verrouillage protège aussi contre une copie des fichiers, surtout sur ordinateur) ? Recommandation : à étudier en étape 2 avec le Directeur technique (SQLCipher ou équivalent : nouvelle dépendance, licence, taille de l'APK, migration de la base existante, compatibilité iOS).
5. **Textes [EXEMPLE]** (guide des réglages du téléphone, information sur l'arrêt du 7 novembre 2022) : à ajouter à la liste « à faire relire par un avocat » de la feuille de route, avec une legal team.
6. **Désinstaller à la place de l'effacement** sur ordinateur : l'effacement supprime les lignes et les fichiers, mais un SSD peut garder d'anciens blocs. Recommandation : le guide de la propriétaire conseille le chiffrement du disque (BitLocker sous Windows) pour les militants qui utilisent l'ordinateur.

## 6. Décisions proposées (à numéroter par la session « Agir » : bloc D-081 à D-089)

Prises en autonomie, techniques et gratuites, sauf la 1 (principe 10) à confirmer par le chef d'équipe :
1. **Verrouillage facultatif, désactivé par défaut**, délai « Immédiatement » par défaut une fois activé (question 1).
2. **Code seul, pas de biométrie** dans PPF (question 3) ; AG-56 est donc toujours satisfait (« code seul en un geste » : il n'y a rien à couper).
3. **« Masquer le contenu » activé par défaut** sur Android (principe 10 : le plus protecteur ; il ne gêne ni la lecture ni le lecteur d'écran ; il bloque les captures d'écran, ce qui est dit dans l'interface et réversible après une mise en garde).
4. **Empreinte du code** : PBKDF2-HMAC-SHA-256, 600 000 itérations (OWASP), sel de 16 octets, poivre HMAC de l'Android Keystore ; fournisseur de la plateforme (JDK, Android) et version portable sur bitcoin-kmp pour iOS ; aucune dépendance nouvelle. Argon2 écarté : il demanderait une bibliothèque native de plus.
5. **Attente croissante** (5 essais libres, puis 30 s, 1, 5, 15 min, 1 h), gardée après un redémarrage, sans effacement automatique (question 2).
6. **Effacement rapide au niveau du stockage** (système Android ; toutes les tables et tous les fichiers sur ordinateur) plutôt que table par table, pour couvrir aussi les tables futures de tous les pôles ; confirmation unique avec rappel de la phrase et de ce qui reste sur le serveur.
7. **Le 15 et le 112 sur l'écran de verrouillage**, sans déverrouiller.
8. **Stockage des réglages en préférence privée** (comme le point de ralliement), pas dans la base locale : pas de migration, et ils disparaissent avec les données de l'application.

## 7. Comment tester

Tests ciblés (par le lanceur `gradle-safe.ps1`) :
- `:shared:core:jvmTest --tests "*AppLockTest*" --tests "*DesktopDeviceEraserTest*"` : vecteur RFC 7914 de PBKDF2, même résultat pour le fournisseur du JDK ; règles du code ; désactivé par défaut, contenu masqué par défaut ; démarrage verrouillé, bon et mauvais code ; sel différent par appareil ; attentes croissantes gardées après un redémarrage, heure reculée ; verrouillage après le délai (deux horloges) ; changement et désactivation avec le code actuel ; poivre nécessaire ; effacement de la base ouverte et des fichiers sur ordinateur ;
- `:shared:ui:jvmTest --tests "*DeviceSecurityTest*"` : à 360, 700 et 1000 dp, verrouillage désactivé par défaut et activé depuis « Mon espace » (code trop simple refusé) ; démarrage suivant verrouillé, interface cachée au lecteur d'écran, mauvais puis bon code (Entrée), « Verrouiller maintenant » ; attente après 5 erreurs, 15 et 112 appelables sans déverrouiller ; effacement rapide annulable puis complet (clé, réglages, point de ralliement, fermeture) ; « Code oublié ? » ; sans moyen d'effacer tout ; échec puis nouvel essai ; feuille AIDE ; « Préparer mon téléphone » (thème sombre compris) ; mise en garde avant d'afficher le contenu ; rien n'est envoyé au serveur.

Pour la propriétaire (application de bureau ou APK de test) : « Mon espace », « Sécurité de cet appareil », activer « Verrouiller PPF par un code », choisir un code de 6 chiffres (pas 123456) ; fermer et rouvrir : PPF demande le code ; taper un faux code, puis le bon. Sur le téléphone : ouvrir la liste des applications récentes, PPF apparaît vide ; une capture d'écran de PPF est refusée. **Ne pas tester « Tout effacer » sur un appareil où l'on garde un vrai compte sans avoir noté sa phrase.** Essai sur téléphone : reporté à la phase de test (ROADMAP).
