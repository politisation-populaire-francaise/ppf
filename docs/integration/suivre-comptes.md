# Note d'intégration : suivre des comptes depuis le fil

Rédigée le 6 novembre 2026 par l'agent chargé du suivi des comptes (branche `worktree-agent-a19e376df01a820d9`), corrigée après la relecture indépendante du même jour. Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md`, `THREAT_MODEL.md` et, si besoin, `DECISIONS.md`. Aucun de ces documents n'a été modifié ici.

## 1. Ce qui est fait

Fonctionnalité S-120 (brainstorming du cahier fonctionnel, module M18) : suivre des comptes depuis le fil, avec une liste gardée sur l'appareil et une vue « Suivis ».

- **Base locale** : nouvelle table `follow` (`shared/core/src/commonMain/sqldelight/.../db/Follow.sq`) : clé publique hexadécimale en clé primaire, date de suivi. Même base SQLite que les événements. **Le schéma passe en version 2** : la migration `.../db/migrations/1.sqm` crée la table dans les bases existantes (version 1, événements seuls) ; les bases neuves sont créées directement en version 2. Les trois pilotes (JDBC, Android, natif) appliquent la migration à l'ouverture. L'agent messagerie ajoutera `2.sqm` pour ses tables.
- **`FollowStore`** (`shared/core/.../core/social/FollowStore.kt`) : suivre, ne plus suivre, est suivi, liste (du plus récent au plus ancien), compte, tout effacer, et `FollowStore.isValidKey` (64 caractères hexadécimaux minuscules). Suivre deux fois garde la première date.
- **`AppDependencies`** : champ facultatif `followStore` et fabrique `AppDependencies.withDatabase(identityStore, database, ...)` qui construit `LocalEventStore` et `FollowStore` sur la même base ouverte une seule fois. Les points d'entrée Android (`android/.../MainActivity.kt`), bureau (`desktop/.../Main.kt`) et iOS (`shared/ui/src/iosMain/.../MainViewController.kt`) l'utilisent. Sans `followStore` (tests, aperçus), la liste reste en mémoire le temps du processus.
- **`ContentLibrary`** : état `followed: Set<String>`, `isFollowed`, `follow`, `unfollow`, `toggleFollow`, `clearFollowed`, `followedPosts` (assemblé sur l'appareil). La clé est validée avant toute modification de l'état. Les écritures dans `FollowStore` sont sérialisées par un `Mutex`, dans l'ordre des gestes de l'utilisateur. Quand un serveur est configuré :
  - à chaque passe de synchronisation (`start`, `refresh`), les publications des comptes suivis sont demandées par lots d'au plus 20 clés (`ContentLibrary.FOLLOW_BATCH`), en ordre aléatoire (`Random` injectable), avec le paramètre `author` répétable qui existe déjà (`RemoteFilter.authors`, `docs/FORMATS.md` section 5) : aucun nouveau paramètre serveur, aucune modification de `RemoteEvents`, `ServerPublicationPort` ni `SyncService` ;
  - **les lots ne partent jamais dans la même passe qu'une publication de l'utilisateur** : si `publishPending` vient d'envoyer quelque chose, les lots attendent l'actualisation suivante ;
  - suivre un compte ne demande que **la nouvelle clé** (un lot d'une clé), jamais toute la liste ;
  - seuls les événements authentiques (signature vérifiée) dont l'auteur figure dans le lot demandé sont conservés ; le premier lot injoignable arrête la tournée.
- **Déconnexion** : effacer ses clés efface aussi la liste des comptes suivis (protection par défaut). `AccountSession` reçoit un `onSignOut` ; `AdaptiveShell` le relie à `ContentLibrary.clearFollowed()`. Le texte d'avertissement `account_sign_out_warning` le dit : « La liste des comptes que vous suivez sera aussi effacée. »
- **Fil** (`FeedScreen.kt`) : vue `FeedFilter.FOLLOWED` (identifiant `suivis`, onglet « Suivis ») juste après « Tout ». État vide qui explique comment suivre, et rappel discret « La liste des comptes que vous suivez reste sur votre appareil ». Sur chaque carte dont l'auteur n'est pas l'utilisateur : bouton secondaire petit « Suivre », qui devient « Suivi » avec une coche et redevient « Suivre » au clic suivant ; en haut à droite, à côté du badge [EXEMPLE] (empilés sur téléphone). Utilisable sans compte. L'avatar et le nom forment **un bouton** (« Voir le compte », rôle bouton, 44 dp de haut au minimum ; les lettres de l'avatar sont décoratives et masquées aux lecteurs d'écran) qui ouvre `AuthorDialog` : titre identique au nom de la carte (nom de l'application pour un compte éditorial, « Vous » pour soi, `npub` court sinon), mention « Compte vérifié » pour les comptes éditoriaux, clé `npub` complète sélectionnable, bouton Suivre ou Ne plus suivre ; la boîte se ferme après l'action. Pour ses propres publications, la boîte s'ouvre sans bouton de suivi.
- **Composants nouveaux** (`shared/ui/.../components/Following.kt`) : `FollowButton` (libellé visible comme nom du bouton, état exposé par `stateDescription` : « Suivi » / « Non suivi ») et `AuthorDialog`, avec `AuthorDialogTags` pour les tests. Aucun composant existant, ni `theme/`, n'a été modifié ; `shell/AdaptiveShell.kt` a seulement reçu le branchement `onSignOut` (deux lignes).
- **Textes** (`strings.xml`) : ajouts à la fin du bloc « Fil » (`feed_tab_followed`, `feed_follow`, `feed_following`, `feed_unfollow`, `feed_not_followed`, `feed_author_open`, `feed_author_key_label`, `feed_author_key_hint`, `feed_author_close`, `feed_followed_empty`, `feed_followed_no_posts`, `feed_followed_private`) ; deux textes existants modifiés sur consigne : `feed_need_account` (« Pour publier ou réagir, créez le vôtre ») et `account_sign_out_warning` (phrase ajoutée). Aucun tiret cadratin.
- **Onglets sur téléphone** : cinq libellés ne tiennent plus en 360 dp ; dans `FeedScreen`, la rangée d'onglets défile horizontalement sur les écrans compacts et suit la vue balayée (début ou fin de la rangée). `PpfTabs` n'a pas été touché.
- **Accessibilité** : rôle bouton et libellé d'action sur l'en-tête de la carte ; état du bouton Suivre annoncé ; clé sélectionnable ; cibles tactiles de 48 dp garanties par Material 3 (`minimumInteractiveComponentSize`) autour du petit bouton (36 dp visibles) et des boutons texte de la boîte (40 dp visibles), 44 dp pour l'en-tête ; couleurs et typographie de la palette commune uniquement, modes clair et sombre vérifiés par captures.

### Tests

- `shared/core/src/jvmTest/.../local/DatabaseMigrationTest.kt` : 3 tests (schéma en version 2 ; une base fichier de version 1, écrite à la main avec le schéma de `Event.sq` et `user_version = 1`, gagne la table `follow` à l'ouverture par `JvmDatabase.open` et garde ses événements ; une base neuve a toutes les tables).
- `shared/core/src/jvmTest/.../social/FollowStoreTest.kt` : 5 tests (suivre, ne plus suivre, idempotence et ordre, clés refusées et `isValidKey`, effacement, persistance sur la même base).
- `shared/ui/src/jvmTest/.../content/ContentLibraryFollowTest.kt` : 3 tests (45 clés demandées en lots de 20, 20 et 5, dans l'ordre d'un `Random` injecté, différent de l'ordre de suivi, chaque clé une seule fois, et aucune clé dans la passe générale ; suivre n'envoie que la nouvelle clé, écarte contrefaçons et intrus, ignore les clés invalides, `clearFollowed` vide l'appareil ; les lots attendent l'actualisation suivante quand une publication vient de partir).
- `shared/ui/src/jvmTest/.../feed/FollowTest.kt` : 6 tests d'interface (suivre depuis la carte remplit la vue Suivis et ne plus suivre la vide ; la boîte de l'auteur affiche titre et clé et suit ou ne suit plus ; la liste survit à une relance sur la même base ; ses propres publications n'ont pas de bouton ; en fenêtre compacte 360x740, suivre depuis une carte et atteindre « Revendications » par les onglets défilants ; effacer ses clés efface la liste, après l'avertissement).
- `ScreenshotTest.kt` : captures de la vue Suivis vide, de la boîte de l'auteur et de la vue Suivis remplie (`PPF_SCREENSHOTS_DIR`). La capture prend la racine la plus haute, ce qui permet de photographier une boîte de dialogue.
- Tous les tests existants restent verts (`gradlew :shared:core:jvmTest :shared:ui:jvmTest`).

Les modules `android/` et `desktop/` n'ont pas été compilés ici (consigne : seules les tâches de test de `shared` sont lancées). Le changement y est mécanique (appel à `AppDependencies.withDatabase`), mais l'intégration continue doit le confirmer.

## 2. À reporter dans `docs/REGISTRE_DONNEES.md` (section 1 bis, données sur l'appareil)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Liste des comptes suivis : clé publique (pseudonyme) de chaque compte suivi et date du suivi | Assembler la vue « Suivis » du fil sur l'appareil ; demander au serveur les publications de ces comptes | Table `follow` de la base locale SQLite de l'application (Android : stockage privé de l'application ; ordinateur : fichier `ppf.db` du dossier de données privé ; iOS : bac à sable de l'application) | Jusqu'à ce que l'utilisateur cesse de suivre le compte, efface ses clés de l'appareil (la liste est alors effacée avec elles) ou désinstalle l'application | L'utilisateur, par l'application. Jamais transmise au serveur comme liste : le serveur ne voit que des demandes de publications par lots d'au plus 20 clés, en ordre aléatoire, et la clé seule du compte que l'on vient de suivre ; sans compte ni adresse IP journalisés |

À ajouter au paragraphe d'introduction de la section 1 bis : qui l'on suit peut révéler une appartenance politique ou syndicale (donnée sensible, RGPD article 9) ; c'est pourquoi la liste reste sur l'appareil (S-120, AG-22, point d'attention n° 32). À la différence des réactions « J'aime », qui ne sont pour l'instant conservées qu'en mémoire le temps de la session, cette liste est enregistrée sur l'appareil.

## 3. À reporter dans `docs/THREAT_MODEL.md`

Paragraphe proposé, section sur le serveur :

> **Liste des comptes suivis.** L'appareil ne transmet jamais cette liste. Il demande les publications des comptes suivis par lots d'au plus 20 clés, en ordre aléatoire, et la clé seule d'un compte au moment où il est suivi. Limite connue : une passe de lots part sur la même connexion, dans la même seconde ; un serveur malveillant peut donc corréler les lots d'une passe et reconstituer la liste d'un appareil anonyme, sans pouvoir la relier à une personne ni à un compte tant qu'aucune adresse IP n'est journalisée et qu'aucune publication ne voyage dans la même passe (l'application s'en assure : après une publication, les lots attendent l'actualisation suivante). La corrélation temporelle entre une publication signée et une demande de clé isolée faite peu après reste possible. Des circuits distincts par lot (Tor, phase 3) ou des relais Nostr réduiront ce risque ; d'ici là, le modèle de menaces accepte cette limite pour un serveur hébergé par PPF.

## 4. À reporter dans `docs/GUIDE_PROPRIETAIRE.md` (section 2, « Ce qu'il faut vérifier »)

### Tester le suivi de comptes (ajouté le 6 novembre 2026)

1. Ouvrez l'application sans créer de compte. Dans le fil, l'onglet « Suivis » se trouve juste après « Tout » : touchez-le. Un encadré explique que vous ne suivez encore aucun compte, et une ligne discrète rappelle que la liste reste sur votre appareil.
2. Revenez sur « Tout ». Chaque publication porte, en haut à droite, un petit bouton « Suivre ». Utilisez-le sur une publication de Politisation Populaire Française : il devient « Suivi » avec une coche.
3. Touchez de nouveau l'onglet « Suivis » : les cinq publications d'exemple de ce compte s'y trouvent maintenant.
4. Sur l'une d'elles, utilisez le bouton « Suivi » : il redevient « Suivre » et la vue Suivis se vide.
5. Touchez l'avatar ou le nom de l'auteur d'une publication : une petite boîte s'ouvre avec son nom, sa clé publique complète (« npub1… », que vous pouvez sélectionner pour la copier) et un bouton « Suivre » ou « Ne plus suivre ». « Fermer » referme la boîte sans rien changer.
6. Suivez un compte, fermez complètement l'application, puis rouvrez-la : le compte est toujours suivi (bouton « Suivi », publications dans l'onglet Suivis). La liste est enregistrée sur l'appareil, et nulle part ailleurs.
7. Si vous avez un compte : dans Mon espace, « Se déconnecter de cet appareil » prévient que la liste des comptes suivis sera aussi effacée ; après confirmation, l'onglet Suivis est de nouveau vide.
8. Sur téléphone, la rangée d'onglets défile de gauche à droite : balayez-la, ou balayez les vues, pour atteindre « Revendications ».

Ce qui n'existe pas encore : la synchronisation de cette liste entre vos appareils (sauvegarde chiffrée S-112, à étudier) et la gestion de la liste depuis Mon espace.

## 5. Choix faits (à consigner dans `DECISIONS.md` si la session principale le juge utile)

1. **Liste privée, assemblage sur l'appareil** : règle validée du brainstorming (M18, S-120, AG-22). Le serveur reçoit des demandes par lots d'au plus 20 clés, en ordre aléatoire, via le paramètre `author` existant ; pas de nouveau paramètre serveur.
2. **Lots et publications séparés** : les lots ne partent jamais dans la passe où une publication vient d'être envoyée.
3. **Suivre envoie la clé seule** du nouveau compte, pas la liste.
4. **Pas de curseur `since` pour les comptes suivis** : chaque tournée redemande les 100 dernières notes de chaque lot ; les doublons sont ignorés par `LocalEventStore`.
5. **Vue Suivis dérivée des 100 dernières notes locales** (`posts`) : suffisant pour le prototype ; voir l'évolution 1 ci-dessous.
6. **Suivre sans compte** : le suivi ne signe rien et ne quitte pas l'appareil. **Effacer ses clés efface la liste** (protection par défaut).
7. **Sans `FollowStore`, liste en mémoire** : le fil se comporte de la même façon dans les tests et les aperçus ; les trois plateformes fournissent toujours la base.
8. **Filtrage défensif** : une réponse à une demande par auteurs ne stocke que les événements des auteurs demandés, signature vérifiée.
9. **Onglets défilants sur téléphone**, gérés dans `FeedScreen` sans toucher `PpfTabs`.

## 6. Point à traiter par la session principale : le curseur de synchronisation

`SyncService.pull` prend pour curseur `LocalEventStore.latestCreatedAt(kinds)`, c'est-à-dire `MAX(created_at)` des événements locaux non en attente, tous auteurs confondus. Les notes des comptes suivis récupérées par lots entrent dans la même table : si l'une d'elles est plus récente que tout ce que la passe générale a déjà vu, la passe générale suivante repart de cette date et **peut sauter les notes d'autres auteurs publiées entre l'ancien curseur et cette note**. Même mécanisme, déjà connu, qu'une note antidatée. Pistes : curseur fondé sur la date de réception côté serveur (`received_at`), ou curseur par source (passe générale et lots) ; à décider avec l'agent serveur, hors de cette branche.

## 7. Évolutions souhaitables, hors périmètre de cette branche

1. `Event.sq` et `LocalEventStore` : requête `selectByKindsAndAuthors` (et éventuellement un curseur par auteur), pour que la vue Suivis ne dépende plus de la limite de 100 notes de `posts`.
2. `PpfTabs` : variante défilante avec centrage automatique de l'onglet actif, à la place du défilement approximatif (début ou fin de rangée) fait dans `FeedScreen`.
3. `RemoteEvents` : rien de requis. Si l'agent de la messagerie ajoute une méthode à l'interface, `ContentLibraryFollowTest.FakeRemote` devra l'implémenter (une ligne).
4. Mon espace : afficher la liste des comptes suivis et permettre d'en retirer ; sauvegarde chiffrée facultative (S-112).
5. Phase 3 : circuits distincts par lot (Tor) ou relais Nostr, pour une vraie séparation entre les lots.

## 8. Questions ouvertes pour la propriétaire

1. **Comptes éditoriaux** : faut-il proposer « Suivre » sur les publications des comptes PPF, qui apparaissent déjà dans « Comptes PPF » ? Laissé possible, par cohérence (tout auteur se suit).
2. **Réactions** : les « J'aime » restent en mémoire le temps de la session ; faut-il les enregistrer sur l'appareil comme la liste des comptes suivis, et les effacer de même à la déconnexion ?
