# Note d'intégration : annuaire des organisations et accueil de l'espace « Agir »

- **État final (7 novembre 2026), fait** : modèle commun, exemples signés, accueil d'Agir, annuaire, page d'une organisation, routage, note ; tests du bloc verts (17 dans `shared/core`, 23 dans `shared/ui`, plus 3 de captures). Tous les tests de `shared` passent (`:shared:core:jvmTest` 223, `:shared:ui:jvmTest` 144, 0 échec) ; Android, ordinateur et web compilent et passent dans `gradlew build`.
- **Vérifié** : le seul échec du `build` complet, `server` `GroupsApiTest.theSameRoomCreatedAtTheSameInstantByEightAccountsExistsOnce` (concurrence des salons), passe relancé seul (`:server:test --tests "*GroupsApiTest*"` : 9 sur 9). C'était la charge de la machine (quatre agents en parallèle) ; aucun fichier du serveur n'est modifié sur cette branche, rien n'a été corrigé (hors périmètre). Ce test reste fragile sous charge : à surveiller en intégration continue.
- **À faire (session principale)** : brancher l'agenda, les appels et l'agenda d'une organisation (section 4) ; la décision du 7 novembre 2026 (aucune période probatoire) est prise en compte (section 6).

Rédigée le 6 novembre 2026 par l'agent « organisations » (branche `worktree-agent-a5acd468182a4dcf0`, partie de `feature/agir`, commit 8b11d40). Elle s'adresse à la session principale, qui fusionne et met à jour `CHANGELOG.md`, `ROADMAP.md`, `DECISIONS.md`, `FORMATS.md`, `REGISTRE_DONNEES.md`, `THREAT_MODEL.md` et `GUIDE_PROPRIETAIRE.md`. Aucun de ces documents n'a été modifié ici. Le contrat commun (`docs/integration/agir-contrat.md`, version 1) est respecté ; les deux écarts mineurs sont signalés en section 4.

## 1. Ce qui est fait

Fonctionnalités : M3 et AG-03 (annuaire, badge « vérifiée »), AG-17 (page de l'organisation), AG-22 (suivre une organisation, liste sur l'appareil), écran d'accueil de l'espace « Agir » avec ses rubriques. Aucun espace pour un parti ou un candidat (point d'attention n° 7, option A).

### Modèle commun (`shared/core/.../core/agir/organisations/`)

- **`Organisation`** : lecture (`from`, `parse`) et construction (`build`) d'une fiche de l'annuaire, événement de type 30023 avec `ppf-type` = `organisation` (contrat, section 4). `Organisation.from(event, trust)` n'accepte une fiche que si elle est **signée par une clé de `EditorialTrust`** et si sa signature est valide (`checkSignature = false` pour relire la base locale, qui ne contient que des événements vérifiés à l'arrivée ; l'auteur est toujours revérifié). Étiquettes obligatoires : `d` (1 à 64 caractères `a-z0-9._~-`), `title` (1 à 140 caractères), `summary`, `ppf-org-type`, `ppf-territory`, `ppf-verified` (date `AAAA-MM-JJ` qui existe), `published_at` ; sans l'une d'elles, la fiche est écartée. Un `ppf-org-type` inconnu (version future) est lu comme `autre` plutôt que de cacher la fiche. Les contacts sont **nettoyés à la lecture** : sites `http` ou `https` seulement, nom d'hôte ASCII (une adresse internationale reste sous sa forme `xn--`, qui ne peut imiter un autre nom), sans partie « utilisateur@ » ; courriel simple (aucun `?`, `&`, espace) ; téléphone fait de chiffres (lien `tel:` construit à partir des seuls chiffres) ; `p` : première clé de 64 caractères hexadécimaux minuscules. `isNational` (territoire `France`), `displayName` (sans [EXEMPLE]), `phoneUri`, `emailUri`, `hostOf`.
- **`VerifiedOrganisations`** : la règle commune du contrat. `isVerified(pubkeyHex)` est vrai quand la clé figure en `p` d'une fiche de confiance ; `organisationFor(pubkeyHex)` rend la fiche. Si deux fiches nomment la même clé, la plus récente la décrit (à date égale, le plus petit identifiant). Une clé éditoriale n'est pas une organisation : « clé éditoriale ou organisation vérifiée » s'écrit `trust.isTrusted(k) || verified.isVerified(k)`. `fromEvents(events, trust)` part d'événements quelconques ; `of(organisations)` de fiches déjà acceptées.
- **`OrganisationQuery`** : mots (nom, résumé, territoire, type, présentation, avec `FrenchText.matchesAllWords`), types, territoire (comparaison repliée par `FrenchText.fold`), « près de chez moi » (`near`). Tri par nom (accents, casse et [EXEMPLE] ignorés) ; avec `near`, les organisations du territoire d'abord, puis celles qui agissent partout en France. Rien n'est envoyé ni enregistré.
- **`Territories.isNear`** : sans carte ni service tiers. Deux territoires sont proches s'ils sont égaux une fois repliés, si les mots significatifs de l'un sont tous des mots de l'autre (« Lyon » et « Lyon 3e », « Lyon » et « Métropole de Lyon »), ou s'ils partagent un numéro de département écrit tel quel (« Nord (59) », « 59 ») ou lu dans un code postal (« 59000 » : 59 ; « 97411 » : 974 ; « 20000 » : 2A ou 2B). Limite connue : aucune liste des communes sur l'appareil, donc « Lille » et « Nord » ne sont proches que si l'un porte le numéro du département. Les exemples écrivent le département après la commune (« Lille (59) »).

### Interface (`shared/ui/.../ui/agir/`)

- **`AgirScreen`** (`AgirScreen.kt`, accueil dans `AgirHome.kt`) : phrase d'introduction tirée de la vision (« Se mobiliser, s'organiser, s'entraider, témoigner, communiquer même sans Internet »), puis les rubriques en cartes (grille de 1 colonne sous 560 dp, 2 sous 900 dp, 3 au-delà, `agirColumns`) : Agenda des mobilisations, Appels, Organisations, Collectifs, Guides pour s'organiser. Puis « En préparation », sans aucune date, avec une ligne d'explication et sept cartes désactivées (lecteurs d'écran : « Pas encore disponible ») : Témoigner, Mode manifestation, Secours et sécurité en manifestation, Solidarité et caisses de grève, Je veux aider, Matériel militant, Messagerie sans Internet. Navigation interne (`AgirRoute` : accueil, agenda, appels, organisations, collectifs) avec un bouton de retour sur chaque écran.
  - **Emplacements** : `agenda` et `calls` (`AgirSlot = @Composable (modifier: Modifier, onBack: () -> Unit) -> Unit`), nuls par défaut : la rubrique affiche alors « En préparation » et le retour. `organisationAgenda: (@Composable (pubkeyHex: String, modifier: Modifier) -> Unit)?` remplit l'agenda de la page d'une organisation ; nul, la page dit « Agenda de l'organisation : bientôt. ».
  - **Collectifs** : la vue « Collectifs » existante de Messages (`CollectivesPage`, `CollectivesFullScreen`), affichée dans Agir avec un retour, sur le même `GroupsLibrary` que Messages (celui du shell). `MessagesScreen` n'a pas été modifié.
  - **Guides pour s'organiser** : mène à « Comprendre » (`onNavigate(Space.COMPRENDRE)`).
  - **Ouvrir une page d'organisation depuis un autre bloc** : `organisations.openKey(pubkeyHex)` (vrai si une fiche décrit la clé : vérifiée, ou révoquée, dont la page le dit) ; `AgirScreen` passe alors à l'annuaire, et le retour de l'annuaire ramène à l'écran d'origine (testé avec un faux agenda).
- **`OrganisationsLibrary`** (`organisations/OrganisationsLibrary.kt`) : rien n'est demandé avant le premier affichage (`ensureStarted`). Import des exemples signés (`files/contents/organisations.json`) dans `LocalEventStore` (fiches de confiance seulement), puis, si un serveur est configuré, synchronisation par `RemoteEvents.fetchByTags([ppf-type:organisation], kind 30023)`, page par page (200, 5 pages au plus), avec **son propre curseur** `pull:30023:tag:ppf-type=organisation` (jamais au-delà de l'heure courante), comme `SyncService`. États observables : `status` (`Idle`, `Loading`, `Unavailable`, `Offline`, `Synced`, `Unreachable`), `organisations`, `verified`, `query`, `results`, `territories` (12 au plus, les plus fréquents), `selected`, `publications` (notes de type 1 signées par la clé `p` présentes dans la base locale : 500 notes récentes parcourues, 20 affichées).
- **`OrganisationsScreen`** : recherche, champ « Près de chez moi » avec la mention « Ce territoire reste sur votre appareil : il n'est jamais envoyé, ni enregistré. », puces de type et de territoire (`components/Chips.kt`), état de synchronisation, nombre de résultats, sections « Près de chez vous » et « Partout en France » quand un territoire est saisi, état vide avec « Effacer la recherche et les filtres », encadré « Une organisation figure dans l'annuaire après vérification par l'équipe de PPF. L'annuaire n'accueille ni parti politique ni candidat. ». Un volet sur téléphone et tablette (liste, puis page avec retour), deux volets côte à côte sur ordinateur (liste de 320 à 420 dp, page à droite).
- **`OrganisationPage`** (AG-17) : avatar (initiales, coins arrondis des organisations), type, [EXEMPLE], nom, « Vérifiée par PPF le 12 octobre 2026 », territoire (« Partout en France » pour une organisation nationale), résumé, bouton **Suivre** (`FollowButton` existant) avec « Votre abonnement reste sur votre appareil : personne d'autre ne sait que vous suivez cette organisation. », ou « Cette organisation n'a pas encore de compte sur PPF : il n'y a rien à suivre pour l'instant. » sans clé `p` ; présentation (Markdown) ; contacts : téléphone (`tel:`) et courriel (`mailto:`) ouverts directement, site ouvert **seulement après la mise en garde « Vous quittez PPF »** (hôte en clair, adresse complète, risque de traceurs) dont le **premier bouton, le plus visible, est « Rester sur PPF »** et le second « Ouvrir le site » ; adresse postale sélectionnable ; publications ; agenda ; ligne « Fiche mise à jour le …, rédigée avec l'aide d'une IA » (étiquette `ppf-origin`).
- **Suivre** passe par la liste des comptes suivis du fil (`ContentLibrary.toggleFollow`, `FollowStore`) : suivre une organisation remplit aussi la vue « Suivis » du fil, et la clé seule est demandée au serveur (comportement existant de S-120).
- **Accessibilité** : cartes cliquables d'un seul tenant (rôle bouton), cartes désactivées annoncées, titres de section en `heading`, contacts de 48 dp au moins avec l'action annoncée (« Appeler le 04 65 71 20 14 »), avatars décoratifs masqués, puces de 48 dp (Material), aucun geste obligatoire, couleurs et typographie du thème uniquement, modes clair et sombre.

### Exemples (`content/organisations/`, signés dans `organisations.json`)

Huit organisations **fictives**, [EXEMPLE] dans le nom, une de chaque type, nationales et locales : Association fictive Les Ateliers de la Lanterne (association, Lyon (69), avec compte), Syndicat fictif des livreurs à vélo (syndicat, France, avec compte), Union locale fictive de Lille (syndicat, Lille (59)), Legal team de démonstration du Nord (legal-team, Nord (59), avec compte), Street medics de démonstration de Marseille (street-medics, Marseille (13)), Réseau fictif d'avocats bénévoles (avocats-benevoles, France), Collectif fictif du quartier des Tilleuls (collectif, Rennes (35), sans téléphone), Bibliothèque militante imaginaire (autre, Paris (75)). Aucune organisation réelle, aucune personne ; les textes de la legal team et des street medics rappellent d'appeler d'abord le 15 ou le 112.

- **Courriels et sites** : `…@example.org` et `https://example.org/…`, domaine réservé à la documentation (RFC 2606, section 3).
- **Téléphones** : uniquement dans les blocs réservés par l'Arcep aux œuvres audiovisuelles, numéros qui « ne pourront ni appeler ni être appelés » : racines 01 99 00, 02 61 91, 03 53 01, 04 65 71, 05 36 49 et 06 39 98 (six blocs de 10 000 numéros). Source officielle vérifiée le 6 novembre 2026 : Arcep, décision n° 2018-0881 modifiée établissant le plan national de numérotation et ses règles de gestion, version du 1er janvier 2026, section 2.5 (numéros techniques), catégorie des numéros pour les œuvres audiovisuelles, page 45/67 ; exposé des motifs : « six blocs de numéros géographiques et mobiles de 10 000 numéros chacun seront réservés à cet usage » : https://www.arcep.fr/uploads/tx_gsavis/18-0881.pdf
- **Clés `p`** : trois clés publiques secp256k1 tirées au hasard (script local, la clé secrète n'a jamais été affichée ni conservée). Personne ne peut donc publier en leur nom : leurs pages montrent « Aucune publication ».
- **Origine** : `origin: ai-assisted` (textes rédigés par l'IA), affichée sur la page.

## 2. Formats

### Fiche d'organisation (événement, contrat section 4)

Conforme au contrat, plus une étiquette facultative déjà utilisée par les contenus éditoriaux : `ppf-origin` (`human` ou `ai-assisted`), pour afficher l'origine (règle IA de CLAUDE.md). Ordre des étiquettes produites par `Organisation.build` : `d`, `title`, `summary`, `published_at`, `ppf-type`, `ppf-org-type`, `ppf-territory`, `ppf-verified`, `ppf-origin`, `p`, `r`…, `ppf-contact-phone`, `ppf-contact-email`, `ppf-contact-address`, `g`. Au plus 5 sites lus. À reporter dans `docs/FORMATS.md`.

### Fichier source (`content/organisations/*.md`, à reporter dans `content/README.md`)

En-tête entre deux lignes `---`, puis la présentation en Markdown.

| Champ | Étiquette | Obligatoire |
|---|---|---|
| `slug` | `d` | oui |
| `title` | `title` (nom, avec [EXEMPLE] pour un exemple) | oui |
| `summary` | `summary` | oui |
| `org_type` | `ppf-org-type` (`association`, `syndicat`, `collectif`, `legal-team`, `street-medics`, `avocats-benevoles`, `autre`) | oui |
| `territory` | `ppf-territory` (« Commune (dd) », « Département (dd) » ou « France ») | oui |
| `verified` | `ppf-verified` (AAAA-MM-JJ) | oui |
| `origin` | `ppf-origin` | oui |
| `published_at`, `updated_at` | `published_at`, date de l'événement (midi UTC) | oui |
| `pubkey` | `p` (clé hexadécimale de l'organisation) | non |
| `websites` | `r`, séparés par des virgules | non |
| `phone`, `email`, `address` | `ppf-contact-phone`, `ppf-contact-email`, `ppf-contact-address` | non |
| `geohash` | `g` | non |

`gradlew :tools:content-signer:run` écrit `organisations.json` à côté de `fiches.json`. Attention : la signature BIP-340 utilise un aléa, donc relancer l'outil change les signatures de **tous** les fichiers JSON ; je n'ai gardé que `organisations.json` (les autres fichiers ont été remis à leur version de `feature/agir`). À la fusion, régénérer une seule fois sur la branche fusionnée.

## 3. Fichiers partagés touchés hors de mon périmètre

| Fichier | Changement |
|---|---|
| `shared/core/.../core/editorial/Article.kt` | une ligne : `ORGANISATION("organisation"),` dans `ArticleType` |
| `shared/ui/.../ui/content/ComprendreScreen.kt` | deux lignes : l'import `organisations_content_type` et `ArticleType.ORGANISATION -> Res.string.organisations_content_type` dans `typeLabel` (le `when` est exhaustif : sans cette ligne, le module ne compile plus). Aucun autre changement : `ContentLibrary` ne lit déjà que `ppf-type` = `fiche-pratique` pour les guides et `revendication` pour le mur, donc les fiches d'organisation n'apparaissent pas dans « Comprendre » (vérifié par le test `comprendreNeverListsTheOrganisations`, après passage par l'annuaire). L'agent « appels » ajoutera sans doute la même ligne pour `APPEL` : conflit trivial. |
| `shared/ui/.../ui/shell/AdaptiveShell.kt` | deux lignes : `import org.politisationpopulairefrancaise.ui.agir.AgirScreen` et `Space.AGIR -> AgirScreen(dependencies, contentLibrary, accountSession, windowClass, modifier, onNavigate, groups = groupsLibrary)` après la ligne de `Space.COMPRENDRE` |
| `tools/content-signer/.../ContentSigner.kt` | le bloc « organisations » (24 lignes), un import, une ligne de KDoc |
| `tools/icons/lucide/` | sept SVG : `phone`, `mail`, `external-link`, `building-2`, `camera`, `bluetooth`, `printer` ; `Lucide.kt` régénéré (7 lignes ajoutées). Ces SVG ont été écrits d'après les formes de Lucide, sans téléchargement : à comparer avec les fichiers officiels de la version 1.69.0 lors de la régénération après fusion. |

`AppDependencies`, `strings.xml`, `MessagesScreen` et la base SQLDelight ne sont pas modifiés (aucune migration).

## 4. Brancher les emplacements (session principale)

Garder l'annuaire pour toute la session, dans `AdaptiveShell`, et le partager avec l'agenda et les appels :

```kotlin
val organisationsLibrary = rememberOrganisationsLibrary(dependencies)
// ...
Space.AGIR -> AgirScreen(
    dependencies, contentLibrary, accountSession, windowClass, modifier, onNavigate,
    organisations = organisationsLibrary,
    groups = groupsLibrary,
    agenda = { slotModifier, onBack -> AgendaScreen(/* ... */, isVerified = organisationsLibrary.verified::isVerified, onOrganiser = organisationsLibrary::openKey, modifier = slotModifier, onBack = onBack) },
    calls = { slotModifier, onBack -> CallsScreen(/* ... */, modifier = slotModifier, onBack = onBack) },
    organisationAgenda = { key, slotModifier -> OrganisationAgenda(organiser = key, modifier = slotModifier) },
)
```

- Les écrans branchés posent leurs `testTag` sur une boîte intérieure : `AgirScreen` passe `Modifier.fillMaxSize()`, sans étiquette.
- Avant de lire `organisationsLibrary.verified`, appeler `organisationsLibrary.ensureStarted()` (sans effet la deuxième fois) : l'annuaire ne charge rien tant qu'on ne le lui demande pas.
- Sans `organisations`, `AgirScreen` construit son propre annuaire, recréé à chaque visite de l'espace (import idempotent, synchronisation à partir du curseur) : suffisant tant que personne d'autre n'a besoin des clés vérifiées.
- Écarts au contrat, à trancher par la session principale : (1) étiquette facultative `ppf-origin` sur les fiches ; (2) convention « Commune (dd) » pour le territoire des exemples, utile à « près de chez moi » par code postal. Le serveur doit accepter `ppf-origin` (étiquette inconnue ignorée) et ne pas exiger d'autre forme de territoire.

## 5. Tests

43 tests, tous verts (`gradlew :shared:core:jvmTest --tests "*agir.organisations*"` et `gradlew :shared:ui:jvmTest --tests "*agir*"`) :

- `shared/core/src/commonTest/.../core/agir/organisations/` : `OrganisationTest` (8 : aller-retour complet, confiance et contrefaçon, chaque étiquette obligatoire, date impossible, type inconnu lu comme « autre », nettoyage des contacts, règles des contacts, refus de `build`, organisations nationales), `VerifiedOrganisationsTest` (4 : clés des seules fiches de confiance, contrefaçon, fiche la plus récente, vide par défaut), `OrganisationQueryTest` (5 : tri par nom, mots, types et territoire, « près de chez moi » par code postal, commune, Corse et ordre local puis national, règles de `Territories`).
- `shared/ui/src/jvmTest/.../ui/agir/` : `AgirScreenTest` (8 : rubriques et « En préparation » désactivées, 1, 2 ou 3 colonnes à 360, 700 et 1000 dp, agenda et appels « En préparation » puis retour, écrans branchés, guides vers « Comprendre » et collectifs de Messages, annuaire et retour, page ouverte depuis un écran branché puis retour vers lui, routage du shell sur ordinateur et depuis le menu du téléphone), `organisations/OrganisationsLibraryTest` (6 : exemples hors ligne et clés vérifiées, rien sans confiance, seule la requête générale part vers le serveur et le curseur avance, serveur injoignable, nouvelle version et clé retirée, notes de l'organisation trouvées sur l'appareil), `organisations/OrganisationsScreenTest` (9 : exemples et filtres par type, recherche, territoire et effacement, « près de chez moi », page sur tablette avec vérification, contacts et suivi gardé sur l'appareil, mise en garde avant un site avec le choix sûr en premier, organisation sans compte, deux volets sur ordinateur, publications et agenda branché, « Comprendre » ne montre aucune fiche d'organisation).
- `AgirScreenshotTest` (3, n'écrit rien sans `PPF_SCREENSHOTS_DIR`) : accueil, « En préparation », annuaire, annuaire filtré « près de chez moi », page, contacts, mise en garde ; téléphone clair et sombre, ordinateur.

## 6. Décisions techniques proposées (à numéroter D-xxx)

1. **Confiance** : une fiche d'organisation n'est importée, stockée ni affichée que signée par une clé de `EditorialTrust` ; la signature est vérifiée à l'arrivée, l'auteur à chaque lecture.
2. **Lecture tolérante, contacts stricts** : étiquette obligatoire manquante = fiche écartée ; type inconnu = « autre » ; contacts réduits à ce qu'un lien sûr peut ouvrir (`https`/`http` ASCII sans « utilisateur@ », `mailto:` simple, `tel:` de chiffres).
3. **Organisation vérifiée** = clé `p` d'une fiche de confiance ; la fiche la plus récente décrit la clé ; une clé éditoriale n'est pas une organisation.
4. **Retrait d'une organisation** : republier la fiche sous la même adresse sans `p` retire la clé des organisations vérifiées sur l'appareil (testé). Contrat version 2 (7 novembre 2026, demande du Directeur technique pour le back-office) : `ppf-org-status` = `revoquee` retire la vérification par une mention explicite, sur l'appareil comme sur le serveur ; la fiche reste lisible (badge « Vérification retirée », absente de la liste par défaut, filtre « Inclure les vérifications retirées », page « Vérification retirée par PPF le … » ouverte par `openKey`), jamais effacée en silence (`docs/FORMATS.md`, section 4 sexies).
5. **Synchronisation de l'annuaire** : requête générale par étiquette, avec son curseur propre ; ouvrir une page ne demande rien au serveur ; seul « Suivre » envoie la clé de l'organisation (comportement existant de S-120).
6. **« Près de chez moi »** en mémoire seulement, jamais envoyé ni enregistré ; règle de proximité par mots et numéros de département (section 1). Évolution possible : une table hors ligne des communes et codes postaux (code officiel géographique de l'Insee, données publiques) livrée avec l'application, à vérifier (licence, poids) avant toute décision.
7. **Suivre une organisation** = suivre son compte dans la liste du fil (`FollowStore`), un seul réglage pour l'utilisateur.
8. **Publications** = notes de type 1 signées par la clé `p` déjà présentes sur l'appareil ; pas de requête par organisation.
9. **Collectifs** : la vue de Messages réutilisée dans Agir avec un retour, plutôt qu'un saut vers l'onglet de Messages.
10. **Sites web** : toujours la mise en garde « Vous quittez PPF », choix sûr en premier ; téléphone et courriel sans mise en garde (ils ouvrent une application de l'appareil, pas une page qui peut pister).
11. **Origine** affichée sur chaque fiche (`ppf-origin`), `ai-assisted` pour les exemples.

Décision de la propriétaire du 7 novembre 2026, transmise par la session principale : **aucune période probatoire**, une organisation vérifiée publie directement dès sa vérification (elle remplace, pour ce point, la publication directe « après un certain temps sans incident » de l'option C du point d'attention n° 6). Vérifié : aucun texte de l'interface ni des exemples n'évoque de délai ou de relecture des organisations vérifiées ; `VerifiedOrganisations.isVerified` ne dépend d'aucune ancienneté (la date `ppf-verified` n'est qu'affichée), ce qui permet à l'agenda et aux appels d'appliquer la règle du contrat (section 5 : organisation vérifiée = publiée, 201) dès la vérification.

## 7. À reporter dans `docs/REGISTRE_DONNEES.md`

### Sur l'appareil (section 1 bis)

| Donnée | Finalité | Où | Durée | Accès |
|---|---|---|---|---|
| Fiches de l'annuaire des organisations (événements signés publics : nom, présentation, type, territoire, date de vérification, coordonnées publiques de l'organisation, clé publique si elle a un compte) | Consulter l'annuaire hors ligne, reconnaître les organisations vérifiées | Table `event` de la base locale (comme les fiches pratiques) | Jusqu'à une version plus récente de la fiche ou la désinstallation | L'utilisateur, par l'application ; ce sont des données publiques |
| Organisations suivies | Déjà couvert par la ligne « Liste des comptes suivis » (S-120) : la clé de l'organisation entre dans la table `follow` | Table `follow` | Comme la liste des comptes suivis | L'utilisateur ; jamais envoyée comme liste |
| Curseur de synchronisation de l'annuaire (`pull:30023:tag:ppf-type=organisation`, une date) | Ne redemander que les fiches nouvelles | Table `sync_cursor` | Jusqu'à la désinstallation | Technique |

Le territoire saisi pour « près de chez moi » reste en mémoire tant que l'annuaire existe (la visite de l'espace Agir, ou la session de l'application si l'annuaire est gardé dans le shell) : il n'est ni enregistré ni envoyé (aucune ligne à ajouter, mais à mentionner). Suivre un syndicat révèle une appartenance syndicale (RGPD, article 9) : c'est pourquoi le suivi reste sur l'appareil (AG-22, point 32).

### Sur le serveur

Rien de nouveau côté appareil. Les fiches sont des contenus éditoriaux publics : ne jamais y écrire les coordonnées d'une personne (règle à faire figurer dans la procédure de vérification, question 4).

## 8. À reporter dans `docs/THREAT_MODEL.md`

- **Fausse organisation, usurpation** : seule une clé éditoriale peut publier une fiche ; une fiche signée par une autre clé est ignorée sur l'appareil (et refusée par le serveur, contrat section 5). Une clé éditoriale compromise permettrait de « vérifier » n'importe quelle clé : rotation et révocation des clés éditoriales (déjà prévues pour la signature des contenus) ; `EditorialTrust` est figée dans l'application en phase 1.
- **Lien piégé dans une fiche** : seuls des liens `https`/`http` à hôte ASCII sans « utilisateur@ » sont gardés ; l'hôte est montré en clair avant de quitter PPF, avec le choix sûr en premier ; `tel:` et `mailto:` sont reconstruits à partir de valeurs nettoyées (pas d'en-tête `?subject=`, pas de code de service).
- **Profilage par l'annuaire** : l'appareil télécharge l'annuaire entier (requête générale) ; ni la recherche, ni le territoire, ni la page ouverte ne quittent l'appareil. Limite connue, déjà acceptée pour S-120 : « Suivre » envoie la clé de l'organisation suivie, seule, au serveur, qui pourrait la relier à l'adresse de connexion s'il la journalisait (ce n'est pas le cas).
- **Site tiers** : après « Ouvrir le site », le site de l'organisation peut pister le visiteur ; la mise en garde le dit.

## 9. Questions pour la propriétaire

1. **Procédure réelle de vérification** : qui vérifie une organisation, sur quelles pièces (statuts, récépissé de déclaration, numéro RNA ou SIREN, rencontre ou visio, charte signée lors de la vérification), à quelle fréquence on la revérifie, et comment on retire le badge. La date affichée (« Vérifiée par PPF le … ») suppose cette procédure. Rappel de ta décision du 7 novembre 2026 : aucune période probatoire, une organisation vérifiée publie directement dès sa vérification ; la vérification elle-même porte donc tout le contrôle.
2. **Statut juridique de PPF** pour les contenus des organisations (hébergeur ou éditeur) : à confirmer par un avocat (points d'attention n° 6 et n° 21). Tant que ce n'est pas tranché, l'annuaire ne publie que des fiches rédigées par l'équipe ; les publications des organisations (AG-18) ne sont que lues sur l'appareil.
3. **Partis et candidats** (point n° 7, option A) : l'annuaire les exclut. Faut-il une règle écrite pour les cas limites (organisation de jeunesse d'un parti, association de soutien à un candidat, syndicat proche d'un parti) ?
4. **Coordonnées** : n'accepter que des coordonnées propres à l'organisation (jamais le téléphone ou l'adresse d'un bénévole) ; que faire pour un petit collectif sans local ni numéro ? Courriel seul, comme dans l'exemple du collectif des Tilleuls ?
5. **« Près de chez moi »** : faut-il garder le territoire saisi sur l'appareil (par exemple dans Mon espace) pour ne pas le retaper ? Aujourd'hui il n'est gardé nulle part, le réglage le plus protecteur.
6. **Liste des organisations suivies rendue publique** (AG-22, au choix de l'utilisateur après une mise en garde) : non réalisée ici ; à prévoir avec les profils.
7. **Rubriques « En préparation »** : la liste et l'ordre te conviennent-ils ? Le « Mode manifestation » doit-il rester dans Agir ou devenir le bouton AIDE (écart de navigation encore ouvert) ?

## 10. Comment tester (à reporter dans `docs/GUIDE_PROPRIETAIRE.md`)

### Tester l'espace « Agir » et l'annuaire (ajouté le 6 novembre 2026)

1. Ouvrez l'application. Sur téléphone, touchez le menu (trois traits en haut à gauche) puis « Agir » ; sur ordinateur, « Agir » est dans la barre de gauche.
2. L'accueil d'Agir montre une phrase d'introduction, puis cinq rubriques : Agenda des mobilisations, Appels, Organisations, Collectifs, Guides pour s'organiser. Plus bas, « En préparation » liste sept rubriques grisées, qui ne s'ouvrent pas.
3. Tournez le téléphone ou élargissez la fenêtre : les cartes passent d'une colonne à deux, puis trois sur un grand écran.
4. Touchez « Agenda des mobilisations » ou « Appels » : un écran « En préparation » s'affiche (ces rubriques sont développées en parallèle). « Retour à Agir » ramène à l'accueil.
5. Touchez « Organisations » : huit organisations fictives, toutes marquées [EXEMPLE] et « Vérifiée ». Touchez la puce « Syndicats » : il en reste deux. « Tous les types » les remet toutes.
6. Dans « Près de chez moi », tapez 59000 : apparaissent d'abord les organisations du Nord, puis celles qui agissent partout en France. Ce que vous tapez reste sur votre appareil.
7. Ouvrez « Association fictive Les Ateliers de la Lanterne » : sa page indique la date de vérification, son territoire, sa présentation et ses contacts. Touchez « Suivre » : le bouton devient « Suivi », et la phrase dessous rappelle que l'abonnement reste sur votre appareil.
8. Touchez le site de l'association : une fenêtre « Vous quittez PPF » s'ouvre ; le grand bouton « Rester sur PPF » la referme sans rien ouvrir. « Ouvrir le site » ouvrirait une page d'exemple qui n'existe pas.
9. Touchez le téléphone : l'application d'appel s'ouvre avec un numéro de fiction réservé par l'Arcep (il n'appelle personne).
10. Revenez à l'accueil d'Agir, touchez « Collectifs » : ce sont les mêmes salons que dans Messages. « Guides pour s'organiser » ouvre « Comprendre ».
11. Dans « Comprendre », aucune organisation n'apparaît parmi les fiches pratiques.

Les captures d'écran se produisent sans appareil : variable `PPF_SCREENSHOTS_DIR=<dossier>` puis `gradlew :shared:ui:jvmTest --tests "*AgirScreenshotTest*"`.

## 11. Ce qui reste

- Brancher l'agenda, les appels et l'agenda d'une organisation (section 4) ; garder l'annuaire dans le shell.
- Côté serveur (agent « serveur ») : refuser une fiche d'organisation d'une clé non éditoriale, servir `tag=ppf-type:organisation`.
- Logos des organisations (images auto-hébergées), carte de l'annuaire (M3 « carte », en attente du choix de la bibliothèque de rendu), publications des organisations sous leur nom (AG-18, après l'avis juridique), import des agendas existants (AG-20).
- Comparer les sept icônes avec les fichiers officiels de Lucide 1.69.0.
- Confirmer en intégration continue le `build` complet (localement, seul le test de concurrence `GroupsApiTest` du serveur a échoué, voir l'état en tête de note).
