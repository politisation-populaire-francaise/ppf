# « Voir », données ouvertes du Parlement : l’appareil et les écrans

Note du bloc 2 du contrat `voir-donnees-ouvertes.md` (sections 4 et 5), écrite le 7 novembre 2026. Branche `feature/voir-donnees-app`, worktree `.claude/worktrees/voir-donnees-app`. Aucune donnée réelle : tout est fictif et marqué [EXEMPLE].

## 1. Ce qui a été construit

### Sur l’appareil (`shared/core/.../core/voir/parlement/`)

- `ParliamentCatalog.kt` : tout ce que l’appareil sait des assemblées, lu dans les paquets qu’il détient. Membres et groupes triés par nom (sans la mention [EXEMPLE], sans accents) ; recherche sans accents (nom, groupe, sigle, territoire, parti national) avec `FrenchText` ; filtres par assemblée et par groupe ; scrutins du plus récent au plus ancien, par dossier législatif ; votes d’un membre (position enregistrée, « par délégation », mise au point, raison du non-vote) ; **participation** = part des scrutins publics de son assemblée, depuis le début de son mandat, présents sur l’appareil, où il a voté pour, contre ou s’est abstenu. Aucune fonction de classement. `ParliamentLinks` lit l’étiquette `ppf-official-id` sur un événement de « Voir » (`an:…`, `senat:…`, `pe:…` pour une personne ; `an-dossier:…` pour un texte) et ignore toute autre valeur.
- `ParliamentRepository.kt` : télécharge et vérifie. L’index est vérifié par `ParliamentIndexReader` (signature, clé de confiance `OpenDataKeys.bundledExamples()` par défaut, anti-retour en arrière par rapport à l’index enregistré) ; chaque paquet est vérifié (sha256 **et** taille de l’index, puis lecture stricte : `membres` doit être la liste des membres, `scrutins-an-2026-10` doit annoncer l’Assemblée et ce mois). Un index refusé laisse l’index précédent en place et le signale. Un paquet absent du nouvel index est effacé de l’appareil ; un paquet d’une version plus ancienne reste affiché avec un bouton « Mettre à jour ». Avertissement après 14 jours (`ParliamentFormat.STALE_AFTER_DAYS`).
- Repli sans serveur (ou serveur muet et appareil vide) : le jeu d’exemple intégré à l’application, vérifié de la même façon, gardé en mémoire seulement (jamais enregistré, il ne peut donc pas bloquer un vrai index plus tard).
- `ParliamentStore.kt` : la base locale (et une version en mémoire pour les tests).

### Base locale

- `Parliament.sq` et la migration **`migrations/11.sqm`** (schéma 11 vers 12). Le contrat réservait `12.sqm`, mais `main` ne va que jusqu’à `10.sqm` (Agir n’a pris aucun numéro) : sur consigne du chef d’équipe, le numéro suivant le plus haut de `main` a été pris, soit `11.sqm`.
- Tables : `parliament_index` (une ligne : le JSON exact de l’événement signé, le jour de l’import, le moment du téléchargement) et `parliament_package` (nom, sha256, JSON exact, date de la source, moment du téléchargement).

### Écrans (`shared/ui/.../ui/voir/parlement/`, textes dans `strings_voir_parlement.xml`)

- `OfficialParliamentCard(onOpen, modifier)` : la carte « Tous les parlementaires et leurs votes ».
- `ParliamentScreen(state, modifier, voirSheetFor)` et `ParliamentState` (avec `rememberParliamentState(dependencies)`) : sa propre pile de pages ; le bouton retour et le retour du système ferment la page du dessus, puis l’écran.
  - **Liste** : recherche, filtres assemblée et groupe, une, deux ou trois colonnes selon la largeur ; téléchargement de la liste des membres **après affichage de sa taille** et seulement sur un geste ; scrutins proposés mois par mois, chacun avec sa taille, jamais téléchargés sans geste (mode économie de données compris).
  - **Fiche d’un parlementaire** : mêmes rubriques pour tous (identité, participation, derniers votes, liens, provenance). Participation **toujours avec sa définition** (« Part des scrutins publics où ce membre a voté. Ne mesure pas le travail en commission ni les autres activités. ») et la mention « PPF ne classe jamais les parlementaires selon ce chiffre » ; positions écrites en toutes lettres (« Pour », « Contre », « Abstention », « Non-votant »), « par délégation », raison du non-vote, mise au point affichée **à côté** du vote enregistré ; couleurs neutres seulement. Liens vers la page officielle et la déclaration d’intérêts HATVP, toujours après la mise en garde « Vous quittez PPF » (`LeavePpfDialog`) ; lien vers la fiche « Voir » quand une fiche porte le même identifiant. Sénateurs : « Votes du Sénat : bientôt ».
  - **Fiche d’un scrutin** : assemblée, type, numéro et date, résultat, décompte, tableau par groupe (chaque ligne lue comme une phrase par les lecteurs d’écran), membres par position, liens officiels (scrutin, dossier).
  - **Partout** : bandeau « Données fictives [EXEMPLE] » quand l’index le dit, « Données du … », avertissement après 14 jours, mise à jour refusée, mention « D’après les données ouvertes de l’Assemblée nationale (du Sénat, du Parlement européen), mises à jour le … ; réutilisation par PPF, sans lien avec cette institution. Licence : … ». Jamais « données officielles » dans un titre.
- `BillOfficialVotes(text, parliament, modifier, onOpen)` et `PersonOfficialVotesLink(person, parliament, modifier, onOpen)` : lisent `ppf-official-id` sur la fiche elle-même et n’affichent rien sans l’étiquette. **Non appelés** depuis `VoirPages.kt` : l’intégrateur choisit leur place (`BillPage`, `PersonPage`).

### Jeu d’exemple intégré : convention attendue du bloc 1

`bundledParliamentFile(name)` lit `shared/ui/src/commonMain/composeResources/files/parlement/<name>.json` : **`index.json`** = l’événement d’index signé (JSON NIP-01 complet), et **`<nom du paquet>.json`** = les octets exacts de chaque paquet listé (par exemple `membres.json`, `scrutins-an-2026-10.json`). Tant que ces fichiers n’existent pas, l’écran sans serveur affiche « Les données des parlementaires ne sont pas disponibles pour le moment ».

## 2. Points d’accroche dans des fichiers communs

- `core/publication/RemoteEvents.kt` : type `OpenDataDownload` et méthode `fetchParliament(name, etag)` avec implémentation par défaut « indisponible ».
- `core/publication/ServerPublicationPort.kt` : `fetchParliament` (`GET /api/v1/parlement`, `GET /api/v1/parlement/{name}`, `If-None-Match`), `parliamentUrl`, `parliamentAnswer`.
- `ui/voir/VoirScreen.kt` : paramètre `parliament: ParliamentState? = null` ; la carte juste après la section « Responsables publics » de l’axe « Le pouvoir » ; l’écran « Parlementaires » à la place de « Voir » quand il est ouvert ; ouverture de la fiche « Voir » d’un parlementaire par son identifiant officiel.
- `ui/shell/AdaptiveShell.kt` : `rememberParliamentState(dependencies)` gardé comme `voirState` et passé à `VoirScreen`.
- `core/src/jvmTest/.../local/DatabaseMigrationTest.kt` : version du schéma attendue passée de 11 à 12.

## 3. Données gardées sur l’appareil (pour `REGISTRE_DONNEES.md`)

| Donnée | Pourquoi | Durée |
|---|---|---|
| Index signé des données ouvertes (JSON de l’événement, jour de l’import, moment du téléchargement) | Vérifier les paquets et refuser un retour en arrière | Jusqu’au prochain index valide, ou effacement des données de l’application |
| Paquets téléchargés (membres, scrutins d’un mois : JSON exact, empreinte, date de la source, moment du téléchargement) | Consultation hors connexion | Jusqu’à ce que l’index ne les liste plus, ou effacement des données de l’application |

Ce sont des données publiques des assemblées (mandat, groupe, territoire, votes), aucune donnée sur l’utilisateur : ni recherche, ni fiche consultée, ni filtre n’est enregistré ou envoyé. Le serveur apprend seulement qu’un appareil a téléchargé un paquet donné (nom du paquet).

## 4. Décisions techniques (pour D-065)

1. Participation calculée sur l’appareil, sur les seuls scrutins présents sur l’appareil depuis le début du mandat ; la période et le nombre de scrutins sont toujours écrits à côté du pourcentage ; aucun chiffre quand aucun scrutin n’est présent (plutôt qu’un faux zéro). Un « non-votant » (y compris pour présider la séance) compte comme n’ayant pas voté, la raison est affichée sur chaque vote.
2. Index demandé à chaque ouverture de l’écran (quelques centaines d’octets) ; tout paquet seulement sur un geste, taille affichée avant.
3. Un index refusé ne remplace jamais l’index enregistré ; le refus est dit à l’écran.
4. Le jeu d’exemple intégré n’est jamais enregistré en base.
5. Ordre des listes : par nom (membres, groupes), par date (scrutins) ; aucune liste triée par participation.
6. Mise au point et dysfonctionnement : le vote enregistré reste affiché, la position voulue est écrite à côté, avec la date si la source la donne.
7. Codes de non-vote traduits (`MG` membre du Gouvernement, `PAN` présidence de l’Assemblée nationale, `PSE` présidence de la séance) ; code inconnu affiché tel quel.

## 5. Tests

- `:shared:core:jvmTest` : `ParliamentCatalogTest` (9), `ParliamentRepositoryTest` (10 : mauvaise signature, clé non reconnue, retour en arrière, empreinte fausse, mois incohérent, données anciennes, copie de l’appareil, paquets périmés ou retirés, jeu intégré, repli), `ParliamentStoreTest` (3, dont la migration 11 vers 12 identique au schéma neuf), `ParliamentServerAnswerTest` (3). Les 8 tests existants de `ParliamentFormatTest` et les tests de migration passent.
- `:shared:ui:jvmTest` : `ParliamentScreenTest` (voir le résultat en fin de note), sur téléphone (400 dp) et ordinateur (1000 dp).
