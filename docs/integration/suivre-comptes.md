# Note d'intégration : suivre des comptes depuis le fil

Rédigée le 6 novembre 2026 par l'agent chargé du suivi des comptes (branche `worktree-agent-a19e376df01a820d9`). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md` et, si besoin, `DECISIONS.md`. Aucun de ces documents n'a été modifié ici.

## 1. Ce qui est fait

Fonctionnalité S-120 (brainstorming du cahier fonctionnel, module M18) : suivre des comptes depuis le fil, avec une liste gardée sur l'appareil et une vue « Suivis ».

- **Base locale** : nouvelle table `follow` (`shared/core/src/commonMain/sqldelight/.../db/Follow.sq`) : clé publique hexadécimale en clé primaire, date de suivi. Même base SQLite que les événements, créée et migrée automatiquement par SQLDelight.
- **`FollowStore`** (`shared/core/.../core/social/FollowStore.kt`) : suivre, ne plus suivre, est suivi, liste (du plus récent au plus ancien), compte. Seules les clés hexadécimales minuscules de 64 caractères sont acceptées ; suivre deux fois garde la première date.
- **`AppDependencies`** : nouveau champ facultatif `followStore` et fabrique `AppDependencies.withDatabase(identityStore, database, ...)` qui construit `LocalEventStore` et `FollowStore` sur la même base ouverte une seule fois. Les points d'entrée Android (`android/.../MainActivity.kt`), bureau (`desktop/.../Main.kt`) et iOS (`shared/ui/src/iosMain/.../MainViewController.kt`) l'utilisent. Sans `followStore` (tests, aperçus), la liste reste en mémoire le temps du processus.
- **`ContentLibrary`** : état `followed: Set<String>`, `isFollowed`, `follow`, `unfollow`, `toggleFollow`, `followedPosts` (assemblé sur l'appareil). Quand un serveur est configuré, les publications des comptes suivis sont demandées par lots d'au plus 20 clés (`ContentLibrary.FOLLOW_BATCH`), en ordre aléatoire, avec le paramètre `author` répétable qui existe déjà (`RemoteFilter.authors`, `docs/FORMATS.md` section 5) : aucun nouveau paramètre serveur, aucune modification de `RemoteEvents`, `ServerPublicationPort` ni `SyncService`. Seuls les événements authentiques (signature vérifiée) dont l'auteur figure dans le lot demandé sont conservés. Le premier lot injoignable arrête la tournée ; le reste attend l'actualisation suivante. Suivre un compte déclenche une tournée immédiate quand un serveur est configuré.
- **Fil** (`FeedScreen.kt`) : vue `FeedFilter.SUIVIS` (identifiant `suivis`, onglet « Suivis ») juste après « Tout ». État vide qui explique comment suivre, et un rappel discret « La liste des comptes que vous suivez reste sur votre appareil ». Sur chaque carte dont l'auteur n'est pas l'utilisateur : bouton secondaire petit « Suivre », qui devient « Suivi » avec une coche et redevient « Suivre » au clic suivant ; en haut à droite, à côté du badge [EXEMPLE] (sur téléphone, badge et bouton sont empilés pour laisser de la place au nom). Utilisable sans compte. Toucher l'avatar ou le nom ouvre une boîte (`AuthorDialog`, stylée comme celles de Mon espace) avec le pseudonyme (`shortNpub`), la clé `npub` complète sélectionnable, le badge « compte vérifié » pour les comptes éditoriaux, et le bouton Suivre ou Ne plus suivre ; la boîte se ferme après l'action. Pour ses propres publications, la boîte s'ouvre sans bouton de suivi.
- **Composants nouveaux** (`shared/ui/.../components/Following.kt`) : `FollowButton` et `AuthorDialog`, avec `AuthorDialogTags` pour les tests. Aucun composant existant, ni `shell/`, ni `theme/` n'a été modifié.
- **Textes** (`strings.xml`, fin de la section « Fil ») : `feed_tab_followed`, `feed_follow`, `feed_following`, `feed_unfollow`, `feed_follow_account`, `feed_unfollow_account`, `feed_author_open`, `feed_author_key_label`, `feed_author_key_hint`, `feed_author_close`, `feed_followed_empty`, `feed_followed_no_posts`, `feed_followed_private`.
- **Onglets sur téléphone** : cinq libellés ne tiennent plus en 360 dp ; dans `FeedScreen`, la rangée d'onglets défile horizontalement sur les écrans compacts et suit la vue balayée (début ou fin de la rangée). `PpfTabs` n'a pas été touché.
- **Accessibilité** : le bouton annonce « Suivre ce compte » ou « Ne plus suivre ce compte » ; l'en-tête de la carte est un bouton « Voir le compte » ; la clé est sélectionnable ; cibles de 36 dp minimum pour le bouton petit, 44 dp pour les boutons de la boîte ; contrastes de la palette commune, modes clair et sombre vérifiés par captures.

### Tests

- `shared/core/src/jvmTest/.../social/FollowStoreTest.kt` : 4 tests (suivre, ne plus suivre, idempotence et ordre, clés refusées, persistance sur la même base).
- `shared/ui/src/jvmTest/.../content/ContentLibraryFollowTest.kt` : 2 tests (45 clés demandées en lots de 20, 20 et 5, chaque clé une seule fois, jamais la liste entière ; suivre récupère les notes de l'auteur et écarte les contrefaçons et les intrus).
- `shared/ui/src/jvmTest/.../feed/FollowTest.kt` : 4 tests d'interface (suivre depuis la carte remplit la vue Suivis et ne plus suivre la vide ; la boîte de l'auteur affiche la clé et suit ou ne suit plus ; la liste survit à une relance sur la même base ; ses propres publications n'ont pas de bouton).
- `ScreenshotTest.kt` : capture de la vue Suivis vide, de la boîte de l'auteur et de la vue Suivis remplie (`PPF_SCREENSHOTS_DIR`). La capture prend désormais la racine la plus haute, ce qui permet de photographier une boîte de dialogue.
- Tous les tests existants restent verts : `shared/core` 53 tests, `shared/ui` 33 tests (`gradlew :shared:core:jvmTest :shared:ui:jvmTest`).

Les modules `android/` et `desktop/` n'ont pas été compilés ici (consigne : seules les tâches de test de `shared` sont lancées). Le changement y est mécanique (appel à `AppDependencies.withDatabase`), mais l'intégration continue doit le confirmer.

## 2. À reporter dans `docs/REGISTRE_DONNEES.md` (section 1 bis, données sur l'appareil)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Liste des comptes suivis : clé publique (pseudonyme) de chaque compte suivi et date du suivi | Assembler la vue « Suivis » du fil sur l'appareil ; demander au serveur les publications de ces comptes | Table `follow` de la base locale SQLite de l'application (Android : stockage privé de l'application ; ordinateur : fichier `ppf.db` du dossier de données privé ; iOS : bac à sable de l'application) | Jusqu'à ce que l'utilisateur cesse de suivre le compte ou désinstalle l'application ; la liste n'est pas liée au compte et n'est pas effacée à la déconnexion | L'utilisateur, par l'application. Jamais transmise au serveur comme liste : le serveur ne voit que des requêtes de publications par lots d'au plus 20 clés, en ordre aléatoire, sans compte ni adresse IP journalisés |

À ajouter au paragraphe d'introduction de la section 1 bis : qui l'on suit peut révéler une appartenance politique ou syndicale (donnée sensible, RGPD article 9) ; c'est pourquoi la liste reste sur l'appareil (S-120, AG-22, point d'attention n° 32).

## 3. À reporter dans `docs/GUIDE_PROPRIETAIRE.md` (section 2, « Ce qu'il faut vérifier »)

### Tester le suivi de comptes (ajouté le 6 novembre 2026)

1. Ouvrez l'application sans créer de compte. Dans le fil, l'onglet « Suivis » se trouve juste après « Tout » : touchez-le. Un encadré explique que vous ne suivez encore aucun compte, et une ligne discrète rappelle que la liste reste sur votre appareil.
2. Revenez sur « Tout ». Chaque publication porte, en haut à droite, un petit bouton « Suivre ». Touchez-le sur une publication de Politisation Populaire Française : il devient « Suivi » avec une coche.
3. Touchez de nouveau l'onglet « Suivis » : les cinq publications d'exemple de ce compte s'y trouvent maintenant.
4. Sur l'une d'elles, touchez le bouton « Suivi » : il redevient « Suivre » et la vue Suivis se vide.
5. Touchez l'avatar ou le nom de l'auteur d'une publication : une petite boîte s'ouvre avec son pseudonyme (« npub1… »), sa clé publique complète (vous pouvez la sélectionner pour la copier) et un bouton « Suivre » ou « Ne plus suivre ». « Fermer » referme la boîte sans rien changer.
6. Suivez un compte, fermez complètement l'application, puis rouvrez-la : le compte est toujours suivi (bouton « Suivi », publications dans l'onglet Suivis). La liste est enregistrée sur l'appareil, et nulle part ailleurs.
7. Sur téléphone, la rangée d'onglets défile de gauche à droite : balayez-la, ou balayez les vues, pour atteindre « Revendications ».

Ce qui n'existe pas encore : la synchronisation de cette liste entre vos appareils (sauvegarde chiffrée S-112, à étudier) et le suivi depuis Mon espace.

## 4. Choix faits (à consigner dans `DECISIONS.md` si la session principale le juge utile)

1. **Liste privée, assemblage sur l'appareil** : règle validée du brainstorming (M18, S-120, AG-22). Le serveur ne reçoit jamais la liste ; il reçoit des demandes de publications par lots d'au plus 20 clés, en ordre aléatoire (`shuffled()`), via le paramètre `author` existant. Pas de nouveau paramètre serveur.
2. **Pas de curseur `since` pour les comptes suivis** : chaque tournée redemande les 100 dernières notes de chaque lot ; les doublons sont ignorés par `LocalEventStore`. Un curseur par auteur demanderait une requête dans `Event.sq`, hors périmètre.
3. **Vue Suivis dérivée des 100 dernières notes locales** (`posts`) : suffisant pour le prototype ; voir l'évolution 1 ci-dessous.
4. **Suivre sans compte** : le suivi ne signe rien et ne quitte pas l'appareil, il n'a donc pas besoin de clés. La liste n'est pas liée au compte : elle n'est pas effacée à la déconnexion (comme les réactions locales).
5. **Sans `FollowStore`, liste en mémoire** : le fil se comporte de la même façon dans les tests et les aperçus ; les trois plateformes fournissent toujours la base.
6. **Filtrage défensif** : une réponse à une demande par auteurs ne stocke que les événements des auteurs demandés, signature vérifiée.
7. **La boîte de l'auteur se ferme après Suivre ou Ne plus suivre** ; le bouton de la carte reflète aussitôt l'état.
8. **Onglets défilants sur téléphone**, gérés dans `FeedScreen` sans toucher `PpfTabs`.

## 5. Évolutions souhaitables, hors périmètre de cette branche

1. `Event.sq` et `LocalEventStore` : requête `selectByKindsAndAuthors` (et éventuellement un curseur par auteur), pour que la vue Suivis ne dépende plus de la limite de 100 notes de `posts`.
2. `PpfTabs` : variante défilante avec centrage automatique de l'onglet actif, à la place du défilement approximatif (début ou fin de rangée) fait dans `FeedScreen`.
3. `RemoteEvents` : rien de requis. Si l'agent de la messagerie ajoute une méthode à l'interface, `ContentLibraryFollowTest.FakeRemote` devra l'implémenter (une ligne).
4. Mon espace : afficher la liste des comptes suivis et permettre d'en retirer ; sauvegarde chiffrée facultative (S-112).
5. Phase 3 : quand Tor ou les relais Nostr arriveront, les lots pourront partir par des circuits différents, ce qui renforcera réellement la séparation entre les lots.

## 6. Questions ouvertes pour la propriétaire

1. **Texte de l'invitation** : `feed_need_account` dit « Pour publier, réagir ou suivre des comptes, créez le vôtre », alors que suivre fonctionne sans compte. Je n'ai pas modifié ce texte (consigne : ajouts seulement). Proposition : « Pour publier ou réagir, créez le vôtre ».
2. **Limite de la protection par lots** : sans compte ni adresse IP journalisés, le serveur ne peut pas relier les lots à une personne, mais une corrélation temporelle entre lots d'une même tournée reste théoriquement possible pour un serveur malveillant. C'est la règle du brainstorming, appliquée telle quelle ; le modèle de menaces (`THREAT_MODEL.md`) pourrait le mentionner.
3. **Déconnexion** : faut-il effacer la liste des comptes suivis quand l'utilisateur efface ses clés de l'appareil ? Aujourd'hui elle reste (elle n'est liée à aucun compte).
4. **Comptes éditoriaux** : faut-il proposer « Suivre » sur les publications des comptes PPF, qui apparaissent déjà dans « Comptes PPF » ? Je l'ai laissé possible, par cohérence (tout auteur se suit).
