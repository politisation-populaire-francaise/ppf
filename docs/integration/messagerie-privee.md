# Note d'intégration : écrans de la messagerie privée et des flux

Rédigée le 6 novembre 2026 par l'agent chargé des écrans de la messagerie (branche `worktree-agent-a693dd595b4628705`, partie de `main` à `c5b364f`). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md`, `THREAT_MODEL.md` et, si besoin, `DECISIONS.md`. Aucun de ces documents n'a été modifié ici.

Mise en œuvre de la décision D-027 (conversations privées et de petit groupe en NIP-17, chiffrement NIP-44, enveloppes NIP-59, notre serveur comme relais) et de la vue « Flux » décrite dans le brainstorming du cahier fonctionnel (entrée du 6 novembre, « Messagerie : trois vues »), sur le cœur déjà écrit et testé dans `shared/core/.../core/messaging/` (note `messagerie-core.md`).

## 1. Ce qui est fait

### Commits

| Commit | Contenu |
|---|---|
| `chore(messages): bring in the unfinished private conversation work` | fusion de la branche de l'agent précédent (`worktree-agent-af58d0012ab2b8cc2` : `MessagingSigner` exposé par `AccountSession`, `MessagingSession` et `LocalTime`, écrans inachevés) ; aucun conflit avec `main` |
| `feat(core): forget every conversation of the device in one call` | `MessageStore.clear()` et quatre requêtes dans `Messaging.sq` (aucune modification du schéma, aucune migration), avec un test |
| `feat(messages): private conversations and flows of followed accounts on screen` | les écrans, le branchement dans le shell, les textes dans `strings_messagerie.xml` |
| `test(messages): session, private conversations, flows and their layouts` | tests unitaires et d'interface, faux relais, appareil photo des écrans |
| `fix(messages): compile the local time offset on iOS` | correction de `LocalTime.ios.kt` (agent précédent) trouvée par `gradlew build` |
| `docs(integration): ...` | cette note |

### Ce que voit l'utilisateur

- **Sans compte** : la vue « Privé » invite à créer un compte (bouton « Créer mon compte » qui mène à Mon espace) ; « Nouvelle conversation » est désactivé. La vue « Flux » fonctionne sans compte.
- **Sans base locale** (aucune plateforme actuelle, seulement les tests) : « Les messages ne sont pas disponibles sur cet appareil ».
- **Liste des conversations** : la plus récente en haut ; pour chacune, l'avatar (celui de la personne, ou un carré avec le nombre de participants pour un groupe), le nom (clé `npub` courte ; « Politisation Populaire Française » pour un compte éditorial, comme dans le fil ; le sujet pour un groupe), le dernier message sur une ligne (« Vous : … » pour les siens), l'heure (aujourd'hui), « Hier », puis la date courte (« 27 oct. »), le nombre de messages non lus dans une pastille, et une petite horloge quand le dernier message attend encore le serveur. Une ligne d'état discrète : « Pas de serveur pour l'instant », « Serveur injoignable », « Relevé des messages… » ou « À jour avec le serveur ». Bouton d'actualisation quand un serveur est configuré. En bas : la clé publique de l'utilisateur, sélectionnable, « à donner aux personnes qui veulent vous écrire », et l'encadré des limites du chiffrement avec un bouton « Comment vos messages sont protégés ».
- **Conversation ouverte** (maquette de la propriétaire) : en-tête de 64 dp (retour sur téléphone, avatar, nom, ligne verte « Chiffré de bout en bout », bouton d'information), bulles (les autres à gauche, les siennes à droite en couleur de marque), séparateurs « Aujourd'hui », « Hier » ou date complète entre les jours, heure locale de chaque message, horloge « En attente d'envoi » sur un message qui n'est pas encore parti, nom de l'auteur dans chaque bulle des autres en groupe ; champ de saisie en pilule et bouton rond d'envoi ; sur ordinateur, Entrée envoie et Maj+Entrée va à la ligne. Une conversation neuve affiche « Écrivez le premier message… » : elle n'existe nulle part tant que le premier message n'est pas écrit.
- **Nouvelle conversation** : une ou plusieurs clés tapées ou collées (`npub1…` ou hexadécimal ; bouton « Ajouter » ou clés séparées par des espaces, pour un petit groupe ; puces retirables), ou des comptes suivis cochés, et un sujet facultatif. Chaque clé est vérifiée avant l'ouverture (point valide de la courbe, pas sa propre clé), avec un message d'erreur clair.
- **Explication du chiffrement** (boîte ouverte depuis la conversation ou la liste) : chiffrement sur l'appareil, enveloppe scellée, ce que voit le serveur (à quelle clé chaque enveloppe est destinée, rien d'autre), **limites connues** (pas de confidentialité persistante : une clé volée expose les anciens messages ; un serveur malveillant pourrait deviner qui échange avec qui en observant les envois ; MLS prévu), vérification par QR code à venir, liste des conversations sur l'appareil, ni accusé de lecture ni « en train d'écrire ».
- **Flux** : titre « Médias et élus suivis », rappel que la liste des comptes suivis reste sur l'appareil, puis une ligne par compte suivi (sa dernière publication, son heure) ; ouvert, un fil à sens unique (bulles à gauche, badge [EXEMPLE] pour les contenus d'exemple, séparateurs de jours, heure), un bouton « Suivi » pour cesser de suivre, et en bas, à la place de la saisie : « Flux public : vous recevez, vous ne répondez pas ici. »
- **Écrans adaptatifs** : quand la place le permet (largeur du contenu de 640 dp ou plus, c'est-à-dire l'ordinateur et la tablette en paysage), la liste et la conversation ou le flux ouvert sont côte à côte ; sinon un seul volet. Sur téléphone, la conversation ou le flux ouvert prend tout l'écran (sans l'en-tête ni les onglets), avec un bouton retour.
- **Déconnexion** : « Se déconnecter de cet appareil » efface aussi les conversations de l'appareil ; l'avertissement le dit (texte `account_sign_out_warning` complété).

### Fonctionnement

- **`MessagingSession`** (`ui/messages/MessagingSession.kt`) vit pendant toute l'exécution de l'application, retenue par `AdaptiveShell` comme la bibliothèque de contenus : la conversation ouverte, le flux ouvert et les messages en attente survivent à une visite dans un autre espace. Elle suit le compte de l'appareil (`setAccount(messagingSigner)`, appelé par le shell quand la clé change) et oublie tout quand les clés sont effacées (`forgetAll()`, branché sur `onSignOut` à côté de `clearFollowed`). Un travail commencé pour un compte qui n'est plus le compte courant s'arrête sans toucher ni la base ni l'écran ; une erreur (clés effacées pendant un échange, réponse inattendue) n'interrompt jamais l'application.
- **Synchronisation** : à l'ouverture de l'écran Messages, puis toutes les 20 secondes tant qu'il est affiché, et sur le bouton d'actualisation : d'abord les livraisons en attente (`MessagingService.flushOutgoing`, enveloppes refaites à l'envoi), puis les enveloppes adressées à l'utilisateur (`GET /api/v1/events?kind=1059&tag=p:<sa clé>`, `MessagingService.pull`). Sans serveur, tout reste sur l'appareil, visiblement en attente.
- **Hors ligne d'abord** : un message est enregistré sur l'appareil avant tout envoi ; il part dès que le serveur répond.
- **Marques de lecture** : un message est marqué lu quand la conversation est affichée, sur l'appareil seulement ; rien ne part vers le serveur (S-125). Aucun indicateur « en train d'écrire ».
- **Une seule identité par base** : si la base contient des conversations d'une autre identité (cas qui ne devrait pas arriver puisque la déconnexion efface tout, mais possible si des fichiers ont été manipulés), elles sont effacées avec le curseur de relevé avant d'afficher quoi que ce soit au nouveau compte ; son historique revient du serveur.
- **Flux** : assemblé sur l'appareil à partir de `ContentLibrary.followed` et `followedPosts` ; rien de nouveau n'est demandé au serveur (le bouton d'actualisation des flux appelle la synchronisation existante de la bibliothèque).

### Modification dans `core/messaging` (à signaler)

`MessageStore.clear()` efface conversations, messages, livraisons en attente et l'état de la messagerie (dont la date du dernier relevé). Sans lui, la déconnexion ne pouvait pas effacer le curseur de relevé (aucune requête ne le permettait) : un compte restauré ensuite sur l'appareil, ou un autre compte, n'aurait relevé ses enveloppes qu'à partir de cette date et aurait perdu son historique. Quatre requêtes ajoutées au milieu de `Messaging.sq` (`deleteAllMessages`, `deleteAllConversations`, `deleteAllOutgoing`, `deleteAllState`) ; schéma inchangé (version 3), aucune migration. Test `MessageStoreTest.clearingForgetsEverythingIncludingThePullCursor`.

## 2. Fichiers

Dans le périmètre :
- `shared/ui/src/commonMain/kotlin/.../ui/messages/` : `MessagingSession.kt` (réécrit), `PrivatePage.kt`, `ConversationScreen.kt`, `NewConversationDialog.kt`, `FlowsPage.kt` (avec `FlowScreen`), `LocalTime.kt` (libellés de jour et d'heure), `Accounts.kt` (nouveau : noms et avatars comme dans le fil), `MessagesScreen.kt`.
- `shared/ui/src/commonMain/kotlin/.../ui/components/Conversations.kt` : bulle lue comme un seul élément par les lecteurs d'écran, emplacement d'étiquette au-dessus du texte (badge [EXEMPLE]), Entrée pour envoyer dans `PpfPillTextField`.
- `shared/ui/src/commonMain/composeResources/values/strings_messagerie.xml` (nouveau) : tous les textes de la messagerie privée et des flux, y compris ceux que l'agent précédent avait mis dans `strings.xml` (retirés de ce fichier). Pluriel pour les non-lus.
- Tests : `shared/ui/src/jvmTest/kotlin/.../ui/messages/` (`FakeRelay.kt`, `MessagingSessionTest.kt`, `PrivateMessagesTest.kt`, `FlowsTest.kt`, `MessagesScreenshotTest.kt`), commentaire de `commonTest/.../MessagesScreenTest.kt`.

Hors du périmètre strict, volontairement minimes :
- `shared/ui/.../shell/AdaptiveShell.kt` : `rememberMessagingSession`, `onSignOut` qui appelle aussi `messaging.forgetAll()`, `messaging.setAccount(...)` dans le `LaunchedEffect(accountKey)`, un paramètre `messaging` pour `SpaceContent`, et l'appel `MessagesScreen(dependencies, accountSession, contentLibrary, messaging, windowClass, modifier, onNavigate)`.
- `shared/ui/src/commonMain/composeResources/values/strings.xml` : retrait du bloc ajouté par l'agent précédent (le fichier revient à son état de `main` sur ce point) et une phrase ajoutée à `account_sign_out_warning`.
- `shared/core/.../core/messaging/MessageStore.kt`, `shared/core/.../db/Messaging.sq`, `shared/core/src/jvmTest/.../messaging/MessageStoreTest.kt` (section précédente).

Non touchés : `server/`, `content/`, `core/revendications/`, `ui/revendications/`, `ui/messages/CollectivesPage.kt`, `ui/groups/`, `core/groups/`, `ui/account/MySpaceScreen.kt`, `AppDependencies`, aucune migration SQLDelight, aucune dépendance nouvelle.

## 3. Conflits à prévoir avec l'agent des salons

- `MessagesScreen.kt` : la signature change (ajout de `messaging` et `onNavigate`), un bloc est inséré avant la mise en page (boîte « Nouvelle conversation », relevé périodique, volet plein écran sur téléphone), le bouton « Nouvelle conversation » est branché, et les lignes `PRIVATE` et `FLOWS` du `when` changent. La ligne `COLLECTIVES` n'est pas modifiée. `PlannedView`, `messages_coming` et `messages_encrypted_note` restent utilisés par la vue Collectifs ; le texte `messages_encrypted_note` (« Le protocole de chiffrement est en cours de choix ») est désormais périmé, à corriger par qui termine les Collectifs.
- `AdaptiveShell.kt` : mêmes zones que l'agent des salons s'il y retient lui aussi un état (déclarations en tête d'`AdaptiveShell`, paramètres de `SpaceContent`, ligne `Space.MESSAGES`).
- `strings.xml` : le bloc retiré se trouvait juste avant « Messages (M14 et bloc communautaire) ».
- `Messaging.sq` : quatre requêtes ajoutées après `deleteMessagesOf`. Si l'agent des salons garde son réglage de conservation dans `messaging_state`, `clear()` l'efface aussi à la déconnexion, ce qui est voulu (réglage propre à l'identité effacée).

## 4. À reporter dans `docs/REGISTRE_DONNEES.md` (données sur l'appareil)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Conversations privées : identifiant (clés publiques des participants triées), sujet, date du dernier message | Afficher la liste des conversations | Table `conversation` de la base locale SQLite (Android : stockage privé de l'application ; ordinateur : `ppf.db` du dossier de données privé, non chiffré à ce jour, S-43 ; iOS : bac à sable) | Jusqu'à l'effacement des clés de l'appareil (déconnexion) ou la désinstallation | L'utilisateur, par l'application ; jamais transmises : le serveur ne voit que des enveloppes chiffrées |
| Messages déchiffrés : texte, auteur (clé publique), date écrite par l'auteur, date de réception, sujet, réponse à, marque « lu » | Afficher les conversations hors ligne | Table `message`, même base | Idem | Idem ; la marque « lu » ne quitte jamais l'appareil (pas d'accusé de lecture) |
| Livraisons en attente : identifiant du message, destinataire, date de mise en attente | Envoyer les messages écrits hors ligne dès que le serveur répond | Table `outgoing_message`, même base | Jusqu'à l'acceptation par le serveur, ou l'effacement des clés | Application seulement |
| Date du dernier relevé des enveloppes | Ne demander au serveur que les enveloppes récentes | Table `messaging_state`, même base | Jusqu'à l'effacement des clés | Application seulement |

À ajouter en introduction : les conversations sont des opinions et des relations, données sensibles (RGPD, article 9) ; elles ne sont stockées en clair que sur les appareils des participants. La vue « Flux » ne crée aucune donnée nouvelle (elle lit la copie locale des publications publiques et la liste des comptes suivis, déjà au registre).

Côté serveur, rien de nouveau par rapport à `serveur-relais.md` : enveloppes de type 1059, conservées selon D-028.

## 5. Tests

- `gradlew :shared:core:jvmTest` : 186 tests, tous verts (dont `MessageStoreTest.clearingForgetsEverythingIncludingThePullCursor`, nouveau).
- `gradlew :shared:ui:jvmTest` : 94 tests, tous verts, dont 28 nouveaux :
  - `MessagingSessionTest` (12, sur une vraie base locale) : sans base, sans compte ; message écrit hors ligne gardé en attente puis livré quand le relais répond (les livraisons partent avant le relevé) ; sans serveur, tout reste sur l'appareil ; l'appareil ne demande que les enveloppes adressées à sa clé (`kind=1059`, `tag=p:<clé>`, tout l'historique au premier relevé) ; deux comptes échangent par un même relais, non-lus et marques de lecture sur l'appareil seulement, sujet transmis ; un petit groupe de trois ; clé hors de la courbe ou propre clé refusées ; texte trop long refusé avec sa raison ; oubli complet à la déconnexion puis historique rendu par le serveur ; un autre compte ne voit rien des conversations précédentes ; la même clé ne change rien.
  - `PrivateMessagesTest` (10, interface) : lecture sans compte ; liste vide avec la clé à donner et les limites du chiffrement ; nouvelle conversation par `npub` (clé erronée et propre clé refusées), message hors ligne avec horloge et deux livraisons en attente ; **deux applications côte à côte (téléphones de 500 dp) qui échangent un message dans les deux sens par un faux relais** gérant les filtres par étiquette ; non-lus comptés (« 2 messages non lus ») puis lus sans rien envoyer ; fil regroupé par jour (« Aujourd'hui », « Hier », date) avec l'heure locale ; conversation plein écran avec retour sur téléphone ; conversation ouverte retrouvée après une visite dans le fil, boîte d'explication du chiffrement ; relevé de nouveau après 20 secondes tant que l'écran est affiché ; déconnexion qui efface les conversations.
  - `FlowsTest` (2) : suivre le compte d'exemple depuis le fil en fait une conversation à sens unique (publications, badge [EXEMPLE], pas de saisie, liste à côté sur ordinateur), cesser de le suivre depuis le flux le retire ; sur téléphone, flux plein écran avec retour.
  - `MessagesScreenshotTest` (4, actif seulement avec `PPF_SCREENSHOTS_DIR`) : liste, conversation, explication du chiffrement, nouvelle conversation, flux et flux ouvert, téléphone et ordinateur, clair et sombre. Captures vérifiées à l'œil.
  - `MessagesScreenTest` (commun, inchangé sauf son commentaire) reste vert.
- `gradlew build` : vert (Android, ordinateur, serveur, site, outils, et compilation des cibles iOS du code commun). Le premier passage a révélé une erreur dans `LocalTime.ios.kt` écrit par l'agent précédent (import d'une fonction membre de `NSTimeZone`) : corrigée dans le commit `fix(messages): compile the local time offset on iOS`.
- `bash tools/check-no-trackers.sh`, `bash tools/check-dependencies.sh` et `bash tools/check-apk.sh android/build/outputs/apk/debug/android-debug.apk` : verts.

## 6. Guide de test pour la propriétaire (à reporter dans `GUIDE_PROPRIETAIRE.md`)

### Ce qu'il faut savoir avant de commencer

- Pour s'écrire, deux comptes doivent passer par le même serveur. Le serveur de développement ne fonctionne que sur votre ordinateur, et l'application Android n'est pas encore reliée à un serveur : **un échange entre votre téléphone et votre ordinateur n'est pas encore possible**. Il le sera quand le serveur de préproduction sera en ligne. Sur le téléphone, vous pouvez déjà écrire un message : il reste sur le téléphone, marqué « En attente d'envoi ».
- Pour voir un vrai échange, le plus simple est d'ouvrir **deux fois l'application sur l'ordinateur, avec deux comptes d'essai**, reliées au serveur de développement. Chaque fenêtre garde ses données dans un dossier d'essai séparé : vos vraies données ne sont pas touchées.

### Échanger un message entre deux comptes sur l'ordinateur

1. Ouvrez une fenêtre PowerShell dans le dossier `C:\Users\clair\Documents\PPF` (dans l'Explorateur, ouvrez ce dossier, puis Fichier, « Ouvrir Windows PowerShell »).
2. Tapez `.\gradlew.bat :server:runDev` puis Entrée. Attendez la ligne qui se termine par `http://localhost:8080`. Laissez cette fenêtre ouverte : c'est le serveur. Il oublie tout quand on le ferme.
3. Ouvrez une deuxième fenêtre PowerShell dans le même dossier et tapez `.\gradlew.bat :desktop:createDistributable` puis Entrée (une seule fois ; quelques minutes la première fois).
4. Dans cette deuxième fenêtre, lancez le premier compte d'essai en collant ces trois lignes, puis Entrée :
   ```
   $env:APPDATA = "$env:USERPROFILE\ppf-essai-1"
   $env:PPF_SERVER_URL = "http://localhost:8080"
   & ".\desktop\build\compose\binaries\main\app\PolitisationPopulaireFrancaise\PolitisationPopulaireFrancaise.exe"
   ```
5. Ouvrez une troisième fenêtre PowerShell dans le même dossier et lancez le second compte de la même façon, avec `ppf-essai-2` à la place de `ppf-essai-1`.
6. Dans chacune des deux fenêtres de l'application : Mon espace, « Créer mon compte », suivez les étapes (douze mots, que vous pouvez ne pas garder puisque ce sont des comptes d'essai).
7. Dans la première fenêtre : Messages. En bas de la liste, sous « Votre clé publique, à donner aux personnes qui veulent vous écrire », sélectionnez la clé `npub1…` à la souris et copiez-la (Ctrl+C).
8. Dans la seconde fenêtre : Messages, « Nouvelle conversation », cliquez dans le champ « npub1… ou clé hexadécimale », collez (Ctrl+V), puis « Commencer ». La conversation s'ouvre à droite de la liste. Écrivez un message (il doit commencer par [EXEMPLE]) et appuyez sur Entrée.
9. Dans la première fenêtre : au plus tard 20 secondes après (ou tout de suite avec le bouton d'actualisation, la flèche en cercle à côté de « Conversations »), la conversation apparaît avec une pastille rouge « 1 ». Cliquez dessus : le message s'affiche, la pastille disparaît. Répondez : la réponse arrive dans la seconde fenêtre de la même façon.
10. Hors ligne : fermez le serveur (dans sa fenêtre, Ctrl+C). Écrivez un message dans une des fenêtres : il s'affiche avec une petite horloge et la mention « Serveur injoignable ». Relancez le serveur (`.\gradlew.bat :server:runDev`) : à l'actualisation suivante, l'horloge disparaît et le message arrive de l'autre côté.
11. Petit groupe : avec un troisième compte d'essai (`ppf-essai-3`), utilisez « Ajouter » dans « Nouvelle conversation » pour mettre deux clés ; donnez un sujet si vous voulez.
12. Flux : dans le Fil, suivez Politisation Populaire Française avec le bouton « Suivre » d'une publication ; dans Messages, onglet « Flux », le compte apparaît ; cliquez dessus pour lire ses publications comme une conversation à sens unique.
13. Explication : dans une conversation, le bouton « i » en haut à droite ouvre « Comment vos messages sont protégés ».
14. Déconnexion : dans Mon espace, « Se déconnecter de cet appareil » prévient que les conversations seront effacées ; après confirmation, Messages invite de nouveau à créer un compte.
15. À la fin, fermez les fenêtres. Les dossiers `ppf-essai-1`, `ppf-essai-2` (et `ppf-essai-3`) dans votre dossier personnel `C:\Users\clair` ne contiennent que ces comptes d'essai : vous pouvez les supprimer vous-même dans l'Explorateur.

### Sur le téléphone Android (APK)

1. Installez l'APK de cette version comme d'habitude, ouvrez l'application, créez un compte si ce n'est pas fait.
2. Messages : la liste est vide et indique « Pas de serveur pour l'instant ». Vérifiez l'affichage plein écran : « Nouvelle conversation », collez une clé `npub1…` (par exemple celle d'un compte d'essai de l'ordinateur, envoyée par un moyen à vous), « Commencer », écrivez : le message s'affiche avec l'horloge « En attente d'envoi ». La flèche de retour en haut à gauche ramène à la liste.
3. Onglet « Flux » : même essai qu'au point 12 ci-dessus.

## 7. Ce qui reste

- **Vérification d'un contact par QR code** (S-128) : expliquée dans l'interface comme « bientôt » ; à faire avec la caméra (dépendance à choisir).
- **Notifications** (UnifiedPush avec ntfy auto-hébergé, réveil seul sur iOS) : aujourd'hui, les messages ne sont relevés que tant que l'écran Messages est affiché. Une pastille de non-lus sur l'entrée « Messages » de la barre du bas viendrait avec.
- **MLS** (confidentialité persistante, grands groupes) : prévu par D-027 ; la limite est expliquée dans l'interface.
- **Chiffrement de la base locale sur ordinateur** (S-43) : les messages déchiffrés y sont en clair dans `ppf.db`, comme la clé d'identité.
- **Relier l'application Android à un serveur** : `MainActivity` passe `remote = null` ; à faire avec la préproduction (adresse HTTPS).
- **Signalement d'un message par son destinataire** (S-127), **suppression d'une conversation de l'appareil** (`MessageStore.deleteConversation` existe, il manque le bouton et sa confirmation), **messages plus anciens** (le fil affiche les 500 plus récents ; `MessageStore.messages(before = …)` permet la pagination), **profils** (noms et avatars au lieu des clés courtes).
- **Ordre de deux messages écrits dans la même seconde** : le tri par date puis identifiant de `MessageStore` peut les inverser ; un tri secondaire par date de réception serait plus juste (requête de `Messaging.sq`).
- **Taille des groupes** : aucune limite n'est imposée dans l'interface ; NIP-17 n'est pas fait pour de grands groupes. À trancher si besoin (question pour la propriétaire).
- **Réglage de la conservation** (D-028) : fait par l'agent des salons dans Mon espace ; le texte `messages_retention_note` dit seulement « trois mois au minimum » et pourra mentionner le réglage après fusion.

## 8. Choix faits (à consigner dans `DECISIONS.md` si utile)

1. `MessagingSession` unique pour toute l'exécution, qui suit le compte, plutôt qu'une session reconstruite par compte : la conversation ouverte survit à une visite ailleurs, et l'oubli à la déconnexion se fait à un seul endroit.
2. Relevé à l'ouverture de l'écran et toutes les 20 secondes tant qu'il est affiché, jamais en arrière-plan (en attendant les notifications).
3. Marque « lu » posée par l'affichage de la conversation, pas par son ouverture logique : sur ordinateur, une conversation ouverte dans un onglet non affiché ne marque rien comme lu.
4. Deux volets dès 640 dp de largeur de contenu ; liste de 300 à 400 dp (42 % de la largeur).
5. Les comptes éditoriaux sont nommés comme dans le fil (nom de l'application, avatar PPF) dans les conversations, les flux et la boîte « Nouvelle conversation ».
6. `MessageStore.clear()` ajouté au cœur (section 1) pour que la déconnexion efface aussi le curseur de relevé.
7. Textes sur ce que voit le serveur rendus exacts : il voit le destinataire de chaque enveloppe (étiquette `p`), pas l'expéditeur ni le contenu ; l'ancien texte « ne saura ni qui écrit, ni à qui » a été corrigé, et la corrélation possible des envois par un serveur malveillant est citée parmi les limites.
