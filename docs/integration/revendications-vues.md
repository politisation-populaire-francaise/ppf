# Note d'intégration : vues et filtres du mur des revendications

Rédigée le 6 novembre 2026 par l'agent chargé des vues et filtres (branche `worktree-agent-a53ac9c273215c0ea`, partie de `fa71030`, branche `feature/revendications-vues-verifications`). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `REGISTRE_DONNEES.md`, `GUIDE_PROPRIETAIRE.md`, `THREAT_MODEL.md` et, si besoin, `DECISIONS.md`. Aucun de ces documents n'a été modifié ici.

Demande de la propriétaire (6 novembre 2026) : « l'utilisateur doit pouvoir accéder à différentes vues et personnaliser ses propres vues et appliquer des filtres ». Cahier fonctionnel, DE-01 : « tri par soutien, nouveauté, débat ou consensus ; filtres par thème et territoire ».

## 1. Ce qui est fait

### Modèle (`shared/core/.../core/revendications/`)

- **`RevendicationQuery.kt`** : une requête sur le mur, la même forme pour une VUE (requête nommée et enregistrée) et pour les FILTRES (requête temporaire posée par-dessus). Critères : `themes` (identifiants de thème, au moins un), `territories` (forme repliée par `FrenchText.fold`, au moins un), `statuses` (au moins un, écrits avec leur valeur d'étiquette, `en-debat`), `authors` (`ANYONE`, `FOLLOWED`, `MINE`), `text` (tous les mots doivent commencer un mot de la revendication, `FrenchText.matchesAllWords`, sur titre, résumé, argumentaire, territoire, sujets et identifiants de thème), `sort` (`NEWEST`, `POPULAR`, `UPDATED`, ou rien). Ensemble vide, `ANYONE` et texte vide veulent dire « aucune contrainte ».
  - `matches(article, context)` avec `RevendicationContext(followed, myKey)` : « Comptes suivis » sans compte suivi ne montre rien, « Mes revendications » sans clé ne montre rien.
  - `overlay(filters)` : la requête effective. Chaque critère fixé par la vue garde la valeur de la vue, les autres prennent celle des filtres ; tri = celui des filtres, sinon celui de la vue, sinon Nouveauté. `fixedCriteria` (sur la vue) dit quels critères sont verrouillés dans le panneau. `without(...)`, `constraintCount` (le nombre affiché sur le bouton « Filtres » : un par thème, territoire et statut, un pour les auteurs, un pour les mots).
  - Forme JSON stable (noms `themes`, `territories`, `statuses`, `authors`, `text`, `sort`), champs et valeurs inconnus ignorés à la lecture, valeurs par défaut non écrites (`{}` pour une requête vide).
  - **Tri** (`RevendicationSorting`) : Nouveauté = date de première publication (à défaut, de mise à jour), puis titre ; Mises à jour récentes = date de mise à jour ; **Popularité = rang du statut tant que les votes ne sont pas ouverts** (Devenue campagne > Retenue > Très soutenue > En débat > Nouvelle > sans statut), puis date de mise à jour. Ce rang provisoire est dans **une seule fonction documentée**, `popularityRank`, à remplacer par les vrais décomptes. Rien n'est calculé à partir de votes : la méthode « une personne, une voix » n'est pas décidée. L'interface l'explique sous le choix « Popularité ».
- **`RevendicationViews.kt`** : vues intégrées, identifiants stables : `tout` (Toutes, permanente, toujours en premier), `suivis` (Comptes suivis), `mes-revendications` (seulement avec un compte), `nationales` (territoire France), une vue par thème `theme:<id>`. Disposition par défaut : Toutes, Comptes suivis, Société, Santé, puis Mes revendications s'il y a un compte. Vues personnalisées : identifiant `custom:` suivi de 16 caractères hexadécimaux aléatoires, nom nettoyé de 1 à 40 caractères, au plus 20. `normalize` (Toutes en tête, pas de doublon, plafond de 20), `visible`, `catalog`.
- **`RevendicationViewStore.kt`** : lecture de la disposition enregistrée (`null` = jamais modifiée, la disposition par défaut s'applique ; une ligne illisible est ignorée sans masquer les autres), enregistrement complet en une transaction, remise à zéro.

### Base locale

- Nouveau fichier `shared/core/src/commonMain/sqldelight/.../db/RevendicationView.sq` : table `revendication_view(id TEXT NOT NULL PRIMARY KEY, position INTEGER NOT NULL, name TEXT, query TEXT)`. Une ligne sans nom ni requête désigne une vue intégrée ; une vue personnalisée a les deux (la requête en JSON). Table vide = disposition par défaut.
- **Migration `migrations/4.sqm` : le schéma passe de la version 4 à la version 5.** `DatabaseMigrationTest` (version 5, une base de version 1 gagne aussi cette table et s'en sert) et `SchemaMigrationTest` (tables `sync_cursor` et `revendication_view` présentes, version au moins 5) sont étendus.
- **Conflit attendu** : une autre branche non fusionnée crée peut-être aussi un `4.sqm`. Si elle est fusionnée avant, renommer le mien en `5.sqm` (même contenu, en-tête « version 5 to 6 »), passer `theSchemaIsAtVersion5` à 6 et compléter le commentaire de ce test.

### Interface (`shared/ui/.../ui/revendications/`)

- **`RevendicationsScreen.kt`** (refait) : onglets des vues (rangée défilante qui suit la vue affichée), balayage entre les vues avec `HorizontalPager` comme le fil ; dans chaque vue, une barre (« Filtres » avec le nombre de filtres actifs, « Tri : Nouveauté » qui ouvre un petit menu, « Proposer une revendication » si `onPropose` n'est pas nul), le nombre de revendications et la description de la vue avec « Personnaliser » à côté, les filtres actifs en puces retirables avec « Tout effacer », l'encadré d'introduction (vue Toutes), puis les cartes ou un état vide adapté : aucun compte suivi (explique comment suivre depuis le fil), aucun résultat avec filtres (bouton « Effacer les filtres »), aucune revendication à soi (bouton « Proposer » si offert), comptes suivis sans revendication, vue vide. Deux volets sur grand écran (liste de 380 dp et détail), un volet avec bouton Retour sur téléphone et tablette ; titre « Revendications » au-dessus des onglets sur tablette. `RevendicationCard` garde sa signature (le fil s'en sert toujours) ; elle affiche maintenant les **libellés des thèmes** (« Éducation », « Pouvoir d'achat ») au lieu des `#sujets` bruts (les sujets bruts restent affichés pour une revendication sans thème connu).
- **Nouvelle signature** : `RevendicationsScreen(library, session, windowClass, modifier, database = null, views = rememberRevendicationViewsState(database, session), onPropose = null)`. Le bouton « Proposer une revendication » n'apparaît que si `onPropose` est fourni : à brancher par la session principale avec son formulaire.
- **`RevendicationViewsState.kt`** : la disposition (lue une fois, enregistrée à chaque changement, écritures sérialisées par un `Mutex`, en mémoire sans base), la vue courante et les filtres par vue (en mémoire seulement, pour la durée de l'exécution). Création après la vue d'origine, renommage, modification des critères, retrait (Toutes est permanente), déplacements (Mes revendications masquée sans compte garde sa place), ajout depuis le catalogue, remise à zéro.
- **`FilterPanel.kt`** : feuille du bas sur téléphone (`ModalBottomSheet`, ouverte entièrement), boîte de dialogue de 560 dp au plus ailleurs. Sections : Trier par (avec la note de Popularité), Thèmes (15 puces), Territoire (puces construites à partir des territoires des revendications, orthographe d'origine, comparaison repliée, « France (national) »), Statut, Auteurs, Mots-clés. Les critères fixés par la vue sont affichés verrouillés (« Défini par la vue « Santé » »). Boutons : « Afficher N revendications » (compte en direct), « Effacer les filtres », « Enregistrer comme vue » (demande un nom, crée la vue juste après la vue courante, l'affiche et vide les filtres de la vue d'origine). Le même panneau sert à créer une vue personnalisée et à modifier ses critères.
- **`ManageViewsDialog.kt`** (« Personnaliser les vues ») : liste des vues affichées avec boutons Monter et Descendre (aucun glisser-déposer obligatoire), Renommer et Modifier les critères pour les vues personnalisées, Retirer (immédiat pour une vue intégrée, qui revient par le catalogue ; après avertissement pour une vue personnalisée), « Ajouter une vue » (catalogue des vues intégrées non affichées et « Créer une vue personnalisée »), « Rétablir les vues par défaut » après avertissement. Les avertissements mettent **le choix sûr en premier** et en évidence (« Garder mes vues », puis « Rétablir quand même »), comme dans les maquettes.
- **`ThemeLabels.kt`** : `themeLabel(themeId: String): String` (signature demandée par la session principale pour le formulaire de proposition) et `themeLabelResource`. **`RevendicationLabels.kt`** : libellés des vues, tris, statuts, auteurs et territoires.
- **Composants nouveaux** (`shared/ui/.../components/Chips.kt`) : `PpfSelectableChip(label, selected, onClick, modifier, enabled, leadingIcon)` (sémantique de case à cocher), `PpfRemovableChip`, `PpfRadioRow` (sémantique de bouton radio), `PpfToolbarButton` (avec pastille de nombre). Palette `PpfTheme.palette` et typographie Material uniquement ; cibles tactiles de 48 dp (`minimumInteractiveComponentSize`).
- **Icônes** : `sliders-horizontal` et `arrow-up-down`, reprises du dépôt officiel lucide-icons/lucide à la version épinglée 1.69.0, ajoutées à `tools/icons/lucide/` et générées dans `Lucide.kt`.
- **Textes** : nouveau fichier `shared/ui/src/commonMain/composeResources/values/strings_revendications_vues.xml` (dont un libellé par thème, `theme_<id>`, et des pluriels). `strings.xml` n'est pas modifié (aucun texte devenu inutile). Aucun tiret cadratin.
- **Accessibilité** : rôles case à cocher, bouton radio, bouton et onglet ; descriptions des boutons d'icône (« Monter « Santé » ») et des puces (« Retirer le filtre Éducation ») ; bouton Filtres annoncé avec son nombre (« Filtres, 1 filtre actif ») ; titres de section en en-têtes ; tout se fait au clavier ou au toucher, sans geste obligatoire ; modes clair et sombre vérifiés sur captures.

### Fichiers touchés hors de mon périmètre

- `shared/ui/.../ui/shell/AdaptiveShell.kt` : une ligne `rememberRevendicationViewsState(dependencies.database, accountSession)` à côté de la bibliothèque (pour que les filtres survivent à une visite d'un autre espace), un paramètre de plus à `SpaceContent`, et l'appel `RevendicationsScreen(contentLibrary, accountSession, windowClass, modifier, views = revendicationViews)`. C'est là que la session principale ajoutera `onPropose = ...`.
- `shared/ui/.../ui/icons/Lucide.kt` (régénéré) et `tools/icons/lucide/` (deux SVG).
- `shared/ui/src/jvmTest/.../ui/ScreenshotTest.kt` : trois captures de plus.
- `AppDependencies` n'est pas modifié. Rien dans `server/`, `web/`, `content/`, la messagerie ni `core/revendications/checks/`.

## 2. Tests

- `shared/core/src/commonTest/.../revendications/RevendicationQueryTest.kt` (15 tests) : chaque critère (thèmes et alias, thème inconnu, territoires repliés, statuts, auteurs avec et sans contexte, mots), combinaison, superposition vue et filtres, origine du tri, `without`, les trois tris (dont Popularité par statut puis mise à jour), aller-retour JSON avec noms stables, lecture tolérante.
- `.../revendications/RevendicationViewsTest.kt` (6 tests) : vues intégrées et leurs critères, disposition par défaut avec et sans compte, normalisation, catalogue, noms, identifiants.
- `shared/core/src/jvmTest/.../revendications/RevendicationViewStoreTest.kt` (5 tests) : table vide, enregistrement et relecture dans l'ordre, remise à zéro, lignes illisibles ignorées, persistance dans un fichier rouvert.
- `DatabaseMigrationTest` et `SchemaMigrationTest` étendus (version 5, table présente et utilisable après migration).
- `shared/ui/src/jvmTest/.../revendications/RevendicationViewsStateTest.kt` (6 tests) : un libellé par thème, disposition par défaut qui suit le compte, filtres par vue, création, renommage, retrait de la vue courante, limite de 20, enregistrement et relecture sur la base.
- `.../revendications/RevendicationViewsScreenTest.kt` (13 tests d'interface) : onglets par défaut et vue de thème (Santé, Société par l'alias « famille », libellés de thème sur les cartes) ; un filtre restreint la liste, compte en direct, sa puce le retire, « Tout effacer », filtres propres à chaque vue ; mots-clés et état vide avec « Effacer les filtres » ; vue de thème verrouillée dans le panneau ; tri Nouveauté, Popularité (menu) et Mises à jour (panneau) ; « Enregistrer comme vue » ajoute un onglet qui survit à une nouvelle instance de l'écran sur la même base ; création depuis le catalogue, renommage, déplacements, retraits (avec avertissement), ajout de Nationales, remise à zéro (avec avertissement) ; vue Comptes suivis vide puis remplie après avoir suivi le compte d'exemple depuis le fil ; Mes revendications avec un compte, absente sans compte ; téléphone (feuille du bas, balayage, détail et retour en gardant vue et filtre) ; tablette ; bouton « Proposer » seulement quand l'écran l'offre.
- `RevendicationsScreenTest`, `FeedScreenTest`, `FollowTest`, `AdaptiveShellTest` et les autres restent verts sans modification.
- Résultats : `gradlew :shared:core:jvmTest :shared:ui:jvmTest` et `gradlew build` passent (iOS compris pour les klibs) ; `bash tools/check-no-trackers.sh` et `bash tools/check-dependencies.sh` passent. Aucune dépendance nouvelle.

## 3. Écrans à regarder

Avec `PPF_SCREENSHOTS_DIR=<dossier>` puis `gradlew :shared:ui:jvmTest --tests "*ScreenshotTest*"` : `*-revendications.png` (onglets et barre), `*-revendications-filtres.png` (panneau de filtres : feuille du bas sur téléphone, boîte sur ordinateur), `*-revendications-filtrees.png` (puce « Éducation » et « Tout effacer »), `*-revendications-vues.png` (« Personnaliser les vues »), en clair et sombre sur téléphone, en clair sur ordinateur. Note : dans les captures « téléphone », la feuille du bas occupe la fenêtre de test (1024 dp) et non le cadre de 360 dp ; sur un vrai téléphone elle prend toute la largeur de l'écran.

## 4. À reporter dans `docs/REGISTRE_DONNEES.md` (section 1 bis, données sur l'appareil)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Vues du mur des revendications choisies par l'utilisateur : identifiants des vues intégrées affichées, et pour chaque vue personnalisée son nom et ses critères (thèmes, territoires, statuts, auteurs, mots-clés, tri), dans l'ordre des onglets | Afficher le mur avec les vues de l'utilisateur, d'une ouverture à l'autre | Table `revendication_view` de la base locale SQLite de l'application (mêmes emplacements que la table `follow`) | Jusqu'à ce que l'utilisateur rétablisse les vues par défaut, retire la vue ou désinstalle l'application | L'utilisateur, par l'application. Jamais transmise : aucune requête au serveur ne dépend des vues ni des filtres (le filtrage se fait sur l'appareil, sur la copie locale des revendications) |

Les **filtres temporaires** ne sont pas enregistrés : ils restent en mémoire pendant l'exécution de l'application. Raison de garder tout cela sur l'appareil : les thèmes, territoires et comptes que l'on regarde révèlent des centres d'intérêt politiques (donnée sensible, RGPD article 9).

Question à trancher par la session principale : faut-il aussi effacer les vues quand l'utilisateur efface ses clés (comme la liste des comptes suivis) ? Je ne l'ai pas fait : les vues ne sont pas liées au compte et existent sans compte.

## 5. À reporter dans `docs/THREAT_MODEL.md`

Paragraphe proposé : « Vues et filtres du mur des revendications. Ils sont appliqués sur l'appareil, à la copie locale des revendications ; le serveur ne reçoit ni les vues, ni les filtres, ni les mots recherchés. Les vues enregistrées sont dans la base locale, protégée comme le reste par le stockage privé de l'application (son chiffrement sur ordinateur reste à faire, S-43). »

## 6. À reporter dans `docs/GUIDE_PROPRIETAIRE.md`

### Tester les vues et les filtres du mur (ajouté le 6 novembre 2026)

1. Ouvrez « Revendications ». En haut, des onglets : Toutes, Comptes suivis, Société, Santé. Balayez l'écran vers la gauche ou touchez un onglet pour changer de vue. « Santé » ne montre que la revendication sur les médecins de garde.
2. Dans « Toutes », touchez « Filtres ». Choisissez le thème « Éducation » : le bouton du bas indique « Afficher 3 revendications ». Touchez-le. La liste n'en montre plus que trois, une puce « Éducation » apparaît au-dessus ; touchez sa croix pour retirer le filtre, ou « Tout effacer ».
3. Touchez « Tri : Nouveauté » et choisissez « Popularité » : en attendant l'ouverture des votes, l'ordre suit l'avancement (devenue campagne, retenue, très soutenue, en débat, nouvelle). Une phrase l'explique sous ce choix.
4. Ouvrez « Filtres » dans la vue « Santé » : le thème Santé est verrouillé, avec « Défini par la vue « Santé » ».
5. Dans « Toutes », mettez un filtre (par exemple le territoire « Métropole de Lyon »), puis « Enregistrer comme vue », donnez un nom et « Créer la vue ». Un nouvel onglet apparaît juste après « Toutes » et s'affiche. Fermez l'application et rouvrez-la : il est toujours là.
6. Touchez « Personnaliser » : montez ou descendez les vues avec les flèches, renommez ou modifiez une vue personnalisée, retirez une vue, ajoutez-en une (« Ajouter une vue », par exemple « Nationales »), ou « Rétablir les vues par défaut » (une question vous est posée d'abord, « Garder mes vues » en premier).
7. « Comptes suivis » : vide tant que vous ne suivez personne. Suivez le compte d'exemple depuis le fil (bouton « Suivre ») : les huit revendications d'exemple y apparaissent.
8. Avec un compte, l'onglet « Mes revendications » apparaît à la fin.

Vos vues restent sur votre appareil ; les filtres disparaissent quand l'application se ferme.

## 7. Choix faits (à consigner dans `DECISIONS.md` si utile)

1. **Popularité provisoire = rang du statut**, dans une seule fonction (`RevendicationSorting.popularityRank`), expliquée dans l'interface ; aucun calcul fondé sur des votes.
2. **Recherche par mots** élargie aux sujets bruts en plus des champs prévus (titre, résumé, argumentaire, territoire, identifiants de thème) : une revendication taguée « famille » se trouve en cherchant « famille ».
3. **Un identifiant de thème inconnu** (enregistré par une version plus récente) est comparé aux sujets eux-mêmes plutôt qu'ignoré.
4. **Filtres temporaires conservés pendant l'exécution** (et non seulement à l'écran) : l'état des vues est gardé par le shell, comme la bibliothèque, pour qu'une visite du fil ne les efface pas. Effacer les filtres garde le tri choisi.
5. **« Enregistrer comme vue »** : la nouvelle vue reprend les critères de la vue d'origine et ses filtres, et le tri seulement s'il a été choisi ; les filtres de la vue d'origine sont alors vidés.
6. **Retrait** : immédiat pour une vue intégrée (elle revient par le catalogue), après avertissement pour une vue personnalisée (ses critères seraient perdus).
7. **Liste en colonne défilante** (et non en liste paresseuse) : toutes les cartes de la vue sont composées, ce qui reste raisonnable avec la limite actuelle de 200 revendications chargées et rend les tests d'absence fiables. À passer en `LazyColumn` quand le mur grandira.
8. **« Personnaliser »** placé à côté du nombre de revendications plutôt que dans la rangée des boutons, pour que « Filtres » et « Tri » tiennent sur une ligne sur téléphone.

## 8. Ce qui reste

- **Popularité réelle** quand les votes existeront (méthode « une personne, une voix » à décider) : remplacer le corps de `popularityRank` ; ajouter les tris **Débat** et **Consensus** de DE-01 quand le débat (DE-20) et le baromètre (DE-21) existeront.
- **Mon territoire** : une vue « près de chez moi » demandera un territoire choisi par l'utilisateur (sur l'appareil) ; non fait.
- Liste de territoires hiérarchique (commune, département, région) : aujourd'hui, les territoires sont comparés tels qu'écrits sur les revendications.
- `PpfTabs` : variante défilante qui centre l'onglet actif (le défilement actuel est proportionnel).
- Regroupement par thème assisté par IA et synthèse publique (DE-01) : hors périmètre.
