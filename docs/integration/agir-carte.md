# Note d'intégration : vue carte de l'agenda (M1, AG-01, S-05, S-35)

Rédigée le 7 novembre 2026 par l'agent « carte » (branche `worktree-agent-aa2e9d93c925b2c1e`, partie de `feature/agir` à `d99c093`). Elle s'adresse à la session principale, qui fusionne, numérote les décisions et met à jour `CHANGELOG.md`, `ROADMAP.md`, `DECISIONS.md`, `FORMATS.md`, `GUIDE_PROPRIETAIRE.md` et `content/README.md`. `REGISTRE_DONNEES.md` et `THREAT_MODEL.md` ont été complétés ici, à la demande de la session principale.

Spécifications appliquées : cahier des charges, sections 3.1 (offline-first : « carte » parmi les données essentielles), 4 (« OpenStreetMap via MapLibre, jamais Google Maps ; tuiles hors ligne pour les zones choisies, phase ultérieure »), 5.1 et 6 ; cahier fonctionnel M1 et section 3 (« la carte reste disponible sans réseau ») ; inventaire : AG-01 (filtres, hors ligne, accès par « Carte »), S-05 (entrée « Carte »), S-35 (MapLibre et OpenStreetMap, position facultative jamais envoyée, zones téléchargeables plus tard) ; contrat de l'espace Agir, section 2 (`g` : géohash placé par l'auteur, aucun service tiers) ; D-025 (identité visuelle), D-039 à D-044.

<!-- ETAT -->

## 1. Choix de la bibliothèque de rendu

Vérifié le 7 novembre 2026 sur Maven Central (`maven-metadata.xml`, modules Gradle et POM des artefacts), sur le dépôt et la documentation officielle de MapLibre Compose, et en lisant le code source de la version retenue.

| Option | État au 7 novembre 2026 | Android | Ordinateur | iOS |
|---|---|---|---|---|
| **A. MapLibre Compose** (`org.maplibre.compose:maplibre-compose`, projet de l'organisation MapLibre, licence BSD-3-Clause), **retenue** | Version 0.20.0 du 10 octobre 2026, compilée avec Kotlin 2.4.20 et Compose Multiplatform 1.12.1 (compatibles avec nos 2.4.21 et 1.12.2) ; publications toutes les une à deux semaines ; Android et iOS en « bêta », ordinateur et web en « alpha » ; depuis la 0.15 (août 2026) elle dessine avec MapLibre Native par son interface C (`org.maplibre.nativeffi:maplibre-native-ffi`, BSD-2-Clause, version 0.202610.0, « expérimentale » selon son dépôt) | Oui : moteur OpenGL ES (`maplibre-compose-runtime-opengl-android`), Android 7 et plus (notre minimum est Android 8) ; aucune dépendance Google, Mapbox ou de suivi (vérifié dans l'arbre des dépendances) | **Non pour nous** : le moteur de l'ordinateur exige **Java 25** (interface de mémoire étrangère) ; nous compilons et livrons avec Java 21 (D-009) | Oui pour la bibliothèque (iOS 15.5 et plus), mais l'application iOS n'est pas compilée |
| B. MapLibre Native Android en direct (`org.maplibre.gl:android-sdk`, BSD-2-Clause) | Mûr (versions 13.x, puis `2026.11.2-globe` le 2 novembre 2026), une vue Android classique | Oui | Non | Non : il faudrait écrire une deuxième intégration pour iOS |
| C. Dessin maison du fond en Compose (sans MapLibre) | À écrire entièrement | Oui | Oui | Oui | 

Raisons du choix A : c'est la piste demandée en priorité, elle reste dans l'écosystème MapLibre exigé par le cahier des charges, son interface est en Compose et commune à Android et iOS (le code de rendu Android n'utilise que l'API commune de la bibliothèque : il pourra être partagé avec iOS le jour où iOS sera compilé), et elle offre deux crochets qui nous donnent une garantie forte contre les tiers : **réécrire toute adresse demandée par le moteur** et **fournir nous-mêmes les octets** de chaque ressource (`MapRequestInterceptor`, `MapResourceProvider`). Risque assumé et noté : bibliothèque en version 0.x dont l'API change souvent (chaque mise à jour devra être vérifiée), moteur Android sur une interface C récente. Repli simple si elle déçoit sur téléphone : l'option B derrière la même interface `AgendaMapRenderer`, sans rien changer d'autre.

**Ordinateur** : la carte n'y est pas dessinée aujourd'hui ; l'agenda garde la liste avec une phrase honnête (« Sur cet appareil, la carte n'est pas encore disponible : sur ordinateur, la bibliothèque de carte demande une version de Java plus récente que celle de l'application. Elle est déjà dans l'application Android. Toutes les mobilisations sont dans la liste ci-dessous. »). Pour l'y ajouter : passer la chaîne d'outils à Java 25 (décision structurante, à proposer), ajouter `maplibre-compose-runtime-opengl-windows-x64` (et les variantes macOS et Linux) et l'option `--enable-native-access=ALL-UNNAMED`.

**iOS** : tout le code commun (géohash, PMTiles, style, points, routeur, écran) est en Kotlin commun pur ; ce qui dépend de la plateforme passe par `expect`/`actual` (`platformAgendaMapRenderer`, `rememberServerTilesAllowed`, `platformGunzip`) ; les `actual` iOS répondent « pas encore » et compilent sans bibliothèque. À la compilation d'iOS : déplacer `AgendaMapRenderer.android.kt` dans un ensemble de sources commun à Android et iOS, ajouter la dépendance pour iOS et les options d'éditeur de liens indiquées par MapLibre Compose, écrire la décompression gzip avec `platform.zlib`.

## 2. Fonds de carte, données et licences

### Format : PMTiles

Un seul fichier par fond, lu par plages d'octets (spécification PMTiles version 3) : pas de serveur de tuiles, un simple fichier servi tel quel par Caddy (`file_server` gère les requêtes partielles) ou par la route de développement. Les tuiles sont des tuiles vectorielles (MVT) du schéma libre de Protomaps, version 4 (couches `earth`, `water`, `roads`, `boundaries`, `places`). Le lecteur et l'écrivain PMTiles sont écrits en Kotlin commun (`core/agir/map/Pmtiles.kt`), testés contre les valeurs de la spécification.

### Deux fonds

| Fond | Contenu | Où | Quand il sert |
|---|---|---|---|
| **Intégré** `files/carte/fond-france.pmtiles` | Le monde jusqu'au zoom 4 (côtes, pays), puis la France métropolitaine, la Corse, la Guadeloupe, la Martinique, la Guyane, La Réunion et Mayotte jusqu'au **zoom 7** ; seulement la terre, l'eau, les autoroutes et routes principales, les frontières et limites de régions, les noms de pays, de régions et de villes | Ressources de l'application (APK, applications de bureau) | Toujours, sans réseau ni serveur ; la carte zoome jusqu'à 11 en agrandissant ce fond |
| **Détaillé** `france.pmtiles` | France métropolitaine, zooms 8 à 14 (rues) | Dossier des tuiles de notre serveur (pas dans le dépôt) | Seulement avec un serveur configuré, un réseau, et hors du mode « Économiseur de données » d'Android ; le fond intégré reste dessous si une tuile manque |

### Taille du fond intégré : le compromis

Mesuré le 7 novembre 2026 avec `tools/map-data` sur la construction Protomaps du 6 novembre 2026 (version 4.15.2), tuiles filtrées puis compressées (gzip) :

| Zoom | Tuiles | Taille | Ce qu'on y voit |
|---|---|---|---|
| 0 à 3 (monde) | 85 | 0,92 Mo | Continents, pays, grandes villes du monde |
| 4 à 6 (France et outre-mer) | 28 | 0,54 Mo | La France entière, régions, grandes villes et autoroutes |
| 7 | 46 | 1,13 Mo | Villes moyennes, routes principales |
| **Total retenu (0 à 7)** | **159** | **2,59 Mo** | |
| 8 (non retenu) | 138 | + 1,75 Mo | Petites villes |
| Monde entier au zoom 4 (non retenu) | 256 | + 1,1 Mo | Le reste du monde plus net |

Choix : **zoom 7**, monde jusqu'au zoom 3. Un téléphone ancien sur une connexion faible télécharge l'APK une fois : 2,6 Mo pour voir toute la France sans réseau, c'est peu à côté du moteur de carte lui-même (section 6) ; le zoom 8 aurait ajouté deux tiers de poids pour des villes que la liste nomme déjà, et le détail viendra du serveur. Les tuiles d'origine pesaient 15,3 Mo : le filtre des couches (sans bâtiments, points d'intérêt, occupation du sol) les divise par six. La préparation a téléchargé 11,4 Mo en 168 requêtes partielles, sur l'ordinateur de développement.

Glyphes : 8 plages par police (latin, latin étendu, ponctuation, symboles), 123 Ko (régulière) et 134 Ko (grasse).

### Glyphes et pictogrammes

- **Glyphes** des étiquettes : faits par l'outil à partir des polices **Atkinson Hyperlegible déjà intégrées** (SIL OFL 1.1, `licenses/OFL-AtkinsonHyperlegible.txt`), régulière et grasse : rien n'est téléchargé, et la carte garde la police de PPF (D-025). Format MapLibre (`{fontstack}/{range}.pbf`, champs de distance signée à 24 px, bordure 3, rayon 8, seuil 0,25, comme l'outil de référence `sdf-glyph-foundry`). Une plage absente reçoit une réponse vide (les noms en écritures non latines ne s'affichent pas : acceptable pour une carte de la France).
- **Pictogrammes** : aucun. Les points sont des cercles dessinés par le moteur, le type est écrit en toutes lettres dans l'étiquette (jamais la couleur seule) ; pas de fichier de sprites.

### Style

Construit sur l'appareil (`MapStyle`), aux couleurs de la palette D-025 pour le clair et le sombre (mer bleutée tirée de la couleur de marque, terre de la couleur de page, routes et frontières discrètes, noms à l'encre avec un liseré), sans aucune adresse web : tuiles `ppf-carte://fond/{z}/{x}/{y}`, `ppf-carte://serveur/{z}/{x}/{y}`, glyphes `ppf-carte://glyphes/{fontstack}/{range}.pbf`. Les noms sont en français quand OpenStreetMap a le nom français (`name:fr`).

### Licences

| Élément | Licence | Ce qu'elle impose | Comment c'est respecté |
|---|---|---|---|
| Données OpenStreetMap | ODbL 1.0 | Attribution, licence nommée ; une base dérivée diffusée reste sous ODbL | « © contributeurs OpenStreetMap » affiché en permanence sur la carte ; « Sources de la carte » nomme l'ODbL et openstreetmap.org/copyright ; métadonnées du fichier ; le fond intégré est une « œuvre produite » distribuée avec la mention |
| Schéma et constructions Protomaps | Logiciel BSD-3-Clause, données ODbL | Attribution OpenStreetMap | Idem ; aucun service Protomaps n'est appelé par l'application |
| Polices Atkinson Hyperlegible | SIL OFL 1.1 | Distribuer avec la licence | Déjà dans `licenses/` |
| MapLibre Compose | BSD-3-Clause | Mention de la licence | À ajouter à l'écran des licences quand il existera |
| MapLibre Native (interface C) | BSD-2-Clause | Idem | Idem |
| JavaCPP (`org.bytedeco:javacpp`, dépendance d'exécution de l'interface C) | Apache 2.0 ou GPL v2 avec exception de liaison, au choix | Mention | Idem |
| spatial-k (`org.maplibre.spatialk`), htmlconverter | Apache 2.0 | Mention | Idem |

## 3. Écran

- L'entrée « Carte » garde l'agenda. Sous les onglets « À venir » et « Passés », un choix **« Liste » / « Carte »** (deux boutons radio, lus comme tels par les lecteurs d'écran, « Liste » d'abord et par défaut : la liste reste le chemin principal). Il n'apparaît que là où la carte peut être dessinée (Android aujourd'hui).
- **La carte** montre exactement la liste de l'onglet, avec les filtres de l'agenda (calculés sur l'appareil) : chaque mobilisation qui a un point (`g`) devient un cercle (couleur de marque ; accent si en cours ; ambre si reportée ; gris si annulée) avec une étiquette « Type » et « jour » (« Rassemblement / Demain », « Annulé : Rassemblement / Samedi 5 décembre »). Au-dessus : le bouton « Filtres » (avec le nombre de filtres actifs), « N mobilisations sur la carte » et, s'il y en a, « N sans lieu placé sur la carte : elles sont dans la liste », avec « Voir la liste ».
- **Toucher un point** montre les mobilisations de ce lieu (même géohash à 7 caractères, environ 150 m) dans un encadré : type, titre, date, lieu, et « Voir la mobilisation », qui ouvre le **détail existant** (`AgendaLibrary.open`). Toucher la carte ailleurs ferme l'encadré. Sur ordinateur et tablette en paysage (deux volets), la carte reste à gauche et le détail s'ouvre à droite ; sur téléphone, le retour ramène la carte telle qu'elle était (vue, caméra et lieu gardés en mémoire).
- **Commandes** sans aucun geste (WCAG 2.5.7) : « Zoomer », « Dézoomer », « Recentrer sur la France ». Rotation et inclinaison désactivées.
- **Attribution** « © contributeurs OpenStreetMap » toujours visible ; la toucher ouvre « Sources de la carte ». Aucun ornement par défaut de la bibliothèque (ni logo, ni lien).
- **Accessibilité** : la carte n'est jamais le seul moyen d'accéder à une information (tout est dans la liste) ; elle porte une description (« Carte des mobilisations, N points… ») ; les statuts sont dits en mots ; boutons de 44 dp ; modes clair et sombre.
- **Économie de données** : aucune tuile fine sans serveur ; avec un serveur, rien n'est demandé sans réseau ni quand l'« Économiseur de données » d'Android est actif.
- Fichiers : `shared/ui/.../ui/agir/map/` (`AgendaMapPane`, `AgendaMapState`, `AgendaMapRenderer`, `BundledMap`, rendu Android `androidMain/.../AgendaMapRenderer.android.kt`), `shared/core/.../core/agir/map/` (`Geohash`, `Pmtiles`, `Gzip`, `MapArea`, `MapPoints`, `MapStyle`, `MapResources`, `HttpRangeSource`), textes dans `strings_carte.xml`.

## 4. Géohash et exemples

- `Geohash` (Kotlin commun pur) : encodage et décodage, cellule, validation ; testé sur les exemples de référence (`u4pruydqqvj`, `ezs42`) et en aller-retour.
- Les huit exemples de `content/agenda/` ont un `geohash` en centre-ville ou sur une place publique (place de la République à Paris, gare Saint-Charles à Marseille, centres de Rennes, Lyon 7e, Lille, Toulouse, Saint-Denis, Strasbourg), jamais une adresse de personne. `tools/content-signer` lit maintenant `geohash` (vérifié) ; seul `agenda.json` a été régénéré.
- Le formulaire « Proposer un événement » n'écrit toujours pas de point (voir « Ce qui reste »).

Texte à ajouter au tableau « Format d'une mobilisation de l'agenda » de `content/README.md` :

> | `geohash` | Facultatif : le point sur la carte (géohash de 9 caractères au plus), en centre-ville ou sur une place publique, jamais une adresse de personne |

## 5. Garantie « aucun tiers »

1. Le style ne contient aucune adresse web (test `MapStyleTest`).
2. Toute adresse demandée par le moteur qui n'est pas `ppf-carte://` est **réécrite** en `ppf-carte://refuse` et **refusée sans être chargée** (`MapRequestInterceptor`) ; toutes les adresses `ppf-carte://` sont servies par l'application elle-même (`MapResourceProvider` → `MapResourceRouter`, testé) : fond intégré, glyphes, et, plus tard, fond détaillé lu **par l'application** sur **notre** serveur seulement (`HttpRangeSource`, HTTPS obligatoire hors machine locale, réponse partielle exigée : un serveur qui enverrait le fichier entier est ignoré sans rien télécharger).
3. Aucun ornement par défaut (logo, attribution liée), aucun style de démonstration (la bibliothèque en a un par défaut, jamais utilisé et de toute façon bloqué par le point 2).
4. `tools/check-no-trackers.sh` interdit désormais les serveurs de tuiles connus (`tile.openstreetmap.org`, `demotiles.maplibre.org`, `api.protomaps.com`, `api.maptiler.com`, `tiles.openfreemap.org`, `tiles.stadiamaps.com`, `arcgisonline.com`).
5. La seule permission ajoutée au manifeste par la bibliothèque est `ACCESS_NETWORK_STATE` (lecture de l'état du réseau) ; aucune permission de localisation.

## 6. Poids ajouté

<!-- POIDS -->

## 7. Ce que le serveur devra servir au déploiement

<!-- SERVEUR -->

## 8. Tests

<!-- TESTS -->

## 9. Fichiers partagés touchés hors de mon périmètre

- `shared/ui/.../ui/agir/agenda/AgendaScreen.kt` (fichier de l'agent qui modifie le détail, les cartes et le formulaire) : **insertions limitées** : sept imports ; une ligne `val map = rememberAgendaMapState()` dans `AgendaScreen` ; un paramètre `map` passé à `AgendaList` ; dans `AgendaList`, une ligne pour le choix « Liste » / « Carte » sous les onglets et un bloc de quatre lignes qui affiche `AgendaMapPane` à la place de la liste ; dans `ListPage`, l'encadré « La vue carte arrive bientôt » remplacé par `AgendaMapUnavailable()` (même étiquette de test `agenda-map-soon`, même titre ; le texte devient la phrase honnête de `strings_carte.xml`, affichée seulement là où la carte n'existe pas : les tests existants de l'agenda passent sans changement). La chaîne `agenda_map_soon_text` de `strings_agenda.xml` n'est plus utilisée : la session principale peut la retirer. `MetaLine`, `TypeMark`, `typeLabel`, `dayHeader`, `whenText` sont réutilisés tels quels (internes au module).
- `shared/ui/src/commonMain/kotlin/.../ui/icons/Lucide.kt` : régénéré après l'ajout de trois icônes (`tools/icons/lucide/list.svg`, `minus.svg`, `locate-fixed.svg`, recopiées du jeu Lucide, à comparer avec les fichiers officiels comme `building-2`). En cas de conflit : régénérer (`python -I tools/icons/generate_icons.py`).
- `shared/ui/build.gradle.kts` : bloc `androidMain.dependencies` (MapLibre Compose et son moteur OpenGL ES).
- `gradle/libs.versions.toml` : version `maplibreCompose` et deux bibliothèques ; fichiers de verrouillage régénérés.
- `settings.gradle.kts` : `include(":tools:map-data")`.
- `server/.../Application.kt` : un paramètre `tiles: TilesConfig = TilesConfig.fromEnvironment()`, une ligne `tilesRoutes(tiles)` et la ligne `PPF_TILES_DIR` de la documentation des variables.
- `tools/content-signer/.../AgendaContent.kt` : lecture de `geohash` (deux lignes) ; `content/agenda/*.md` : une ligne `geohash` chacun ; `agenda.json` régénéré (les autres fichiers remis à leur état).
- `tools/forbidden-patterns.txt` : serveurs de tuiles tiers interdits.
- `tools/check-apk.sh` : fond intégré, glyphes et bibliothèques natives de la carte exigés dans l'APK.
- `docs/REGISTRE_DONNEES.md`, `docs/THREAT_MODEL.md` : lignes de la carte (demandées par la session principale).
- Rien dans `AppDependencies`, `AdaptiveShell.kt`, `strings.xml`, `strings_agenda.xml`, `AgendaLibrary`, `MobilizationDetail`, le formulaire, `infra/`, `.github/`, `docs/guides/`.

## 10. Décisions techniques proposées (à numéroter D-xxx)

1. **MapLibre Compose 0.20.0** (moteur OpenGL ES) derrière une interface `AgendaMapRenderer`, Android seulement ; ordinateur et iOS gardent la liste avec une phrase honnête.
2. **Fonds PMTiles** : un fond intégré à faible zoom (monde jusqu'au zoom 4, France et outre-mer jusqu'au zoom 7, couches filtrées), un fond détaillé servi plus tard par notre serveur comme simple fichier ; lecteur PMTiles en Kotlin commun.
3. **Aucune requête directe du moteur de carte** : style sans adresse web, adresses réécrites et refusées, ressources servies par l'application.
4. **Glyphes faits à partir d'Atkinson Hyperlegible** par notre outil, sans téléchargement.
5. **Préparation des données** par `tools/map-data` (lecture par plages d'une construction Protomaps, filtre des couches et des types) ; jamais par l'application ni par l'intégration continue.
6. **Route de développement `/tiles/`** (`PPF_TILES_DIR`), une plage par requête, 8 Mo au plus, aucune requête sans plage, aucun journal.
7. **Points des exemples** en centre-ville ; aucune position de l'utilisateur, jamais.

## 11. Ce qui reste

- **Tuiles fines hors ligne par zone (S-35)** : télécharger à l'avance une zone (une ville, le parcours d'une manifestation) depuis notre serveur ; MapLibre Compose a un stockage hors ligne (`OfflineStorage`), à brancher sur notre routeur.
- **Carte de la manifestation en direct (AG-24)** : tracés, points de rendez-vous et de dispersion, street medics, zones dangereuses et sorties (sans aucune position des forces de l'ordre).
- **Tracé d'un parcours** (ligne GeoJSON dans l'événement, format à ajouter au contrat).
- **Placer un événement sur la carte dans le formulaire** (toucher la carte pour poser le point, ou chercher une commune dans une liste intégrée, sans service de géocodage tiers) ; avertissement « jamais une adresse privée » et précision limitée pour les contributeurs.
- **Couches appels et organisations** (leurs contenus ont déjà l'étiquette `g` dans le contrat).
- **Ordinateur** (Java 25) et **iOS** (compilation), voir section 1.
- **Écran des licences** de l'application (MapLibre, JavaCPP, spatial-k).
- **Brancher le fond détaillé** : adresse du fichier du serveur dans `AgendaMapState(tilesUrl = …)` quand l'application Android aura un serveur.
- **Captures** de la carte réelle : impossibles dans les tests JVM (le moteur natif ne s'y dessine pas) ; à faire sur téléphone.

## 12. Questions pour la propriétaire

Aucune dépense n'est engagée ni nécessaire : tout est libre et gratuit ; le seul coût futur est l'espace disque et la bande passante du serveur déjà prévu.

1. **Poids de l'APK** (section 6) : la carte ajoute surtout les bibliothèques natives de MapLibre pour trois architectures. Faut-il plus tard proposer un APK par architecture (le vôtre n'aurait que celle de votre téléphone, environ un tiers du poids ajouté) ? Recommandation : oui au moment de la publication sur le site ; un seul APK pour les essais.
2. **Carte sur ordinateur** : elle demande de passer la chaîne d'outils à Java 25 (gratuit, mais c'est un changement de toute l'application de bureau). Recommandation : attendre que la carte soit validée sur Android, puis le proposer.
3. **Points des exemples** : ils sont placés sur des lieux publics réels (place de la République, gare Saint-Charles, centres-villes). Cela vous convient-il, ou préférez-vous des points volontairement approximatifs ?
4. **Placer le point d'un événement** dans le formulaire (étape suivante) : précision complète pour les organisations vérifiées et limitée (environ 150 m) pour les contributeurs, avec la mise en garde « jamais une adresse privée » ? Recommandation : oui.
5. **Fond détaillé** : le mettre à jour tous les deux ou trois mois (rues nouvelles) vous paraît-il suffisant ?

## 13. À transmettre à la session infrastructure

Consigne de la session principale : je n'ai modifié ni `infra/`, ni `.github/workflows/ci.yml`, ni `docs/guides/`. Voici ce qu'il faudra pour servir le fond détaillé, avec le texte proposé.

1. **Dossier des tuiles** sur le serveur, en lecture seule pour le proxy : `/srv/ppf/tiles/` (un fichier `france.pmtiles`, propriétaire un utilisateur de déploiement, droits `0644`), partagé par la production et la préproduction (ce sont des données publiques identiques ; aucun secret, aucune donnée personnelle).
2. **Volume du proxy** dans `infra/proxy/compose.yml`, service `caddy`, liste `volumes` :

   ```yaml
         # Map backgrounds (public OpenStreetMap data, PMTiles), served by range requests.
         - /srv/ppf/tiles:/srv/tiles:ro
   ```

3. **Caddy** (`infra/proxy/Caddyfile`), dans le bloc `{$PROD_DOMAIN}`, **avant** `encode` et `reverse_proxy` (une réponse partielle ne doit pas être recompressée ; Caddy gère `Range` nativement dans `file_server`) :

   ```caddyfile
   	# Map backgrounds: plain PMTiles files read by byte ranges (docs/integration/agir-carte.md).
   	# Public data; no access log (global option); cached a day by the applications.
   	handle_path /tiles/* {
   		@pmtiles path_regexp ^/[a-z0-9][a-z0-9-]{0,62}\.pmtiles$
   		handle @pmtiles {
   			root * /srv/tiles
   			header Cache-Control "public, max-age=86400"
   			header Access-Control-Allow-Origin "*"
   			header Content-Type "application/vnd.pmtiles"
   			file_server {
   				browse off
   			}
   		}
   		respond 404
   	}
   ```

   Le même bloc dans le bloc `:8443` de la préproduction (même dossier). `Access-Control-Allow-Origin` n'est utile que pour une future carte du site web (MapLibre GL JS) ; à retirer si la session préfère attendre.
4. **Espace disque** (contrainte du chef d'équipe : moins de 10 Go pour les tuiles du serveur). Estimation tirée de la mesure du 7 novembre 2026 : les tuiles d'origine de l'emprise (France métropolitaine, Corse, outre-mer) pèsent 32 Mo au seul zoom 8 ; en multipliant par 2,5 environ à chaque zoom (4 fois plus de tuiles, chacune plus légère, la mer stockée une seule fois), on obtient :

   | Emprise et zooms | Taille estimée sur disque |
   |---|---|
   | France métropolitaine et outre-mer, zooms 8 à 14, **toutes couches** (`pmtiles extract` brut) | 6 à 9 Go : **trop près de la limite** |
   | **Retenu** : mêmes zones et zooms, **filtré** comme le fond intégré mais avec toutes les routes et rues (sans bâtiments, points d'intérêt ni occupation du sol, qui pèsent la moitié aux zooms 13 et 14) | **3 à 5 Go** |
   | Option légère : zooms 8 à 12 partout, puis 13 et 14 seulement sur les 22 métropoles et l'outre-mer | **1 à 2 Go** |

   Ces chiffres sont des estimations : `pmtiles extract … --dry-run` donne la taille exacte avant tout téléchargement, à faire au premier déploiement. Si le filtré dépasse 5 Go, prendre l'option légère. Le fond intégré à l'application, lui, pèse **2,59 Mo** (zooms 0 à 7). Garder une seule version à la fois sur le serveur.
5. **Bande passante** (estimation) : une carte de ville vue en détail demande 20 à 60 tuiles de 20 à 80 Ko, soit **1 à 3 Mo par consultation détaillée** ; le cache du moteur sur le téléphone (30 Mo) évite de redemander. Pour 1 000 utilisateurs qui regardent chacun deux villes par mois : environ 2 à 6 Go par mois. Hostinger KVM 1 annonce un trafic largement supérieur (à vérifier dans l'offre au moment de la location). Sans le serveur, le fond intégré ne coûte aucune bande passante.
6. **Mise à jour** : refaire l'extraction tous les deux ou trois mois (guide `tools/map-data/README.md`, section 3), sous un nouveau nom de fichier (`france-2027-01.pmtiles`) pour ne jamais servir un fichier à moitié copié ; puis changer l'adresse côté application.
7. **Aucun journal** : l'option globale `log { output discard }` du Caddyfile couvre déjà ces requêtes, qui révèlent la zone regardée (THREAT_MODEL.md).
8. **Intégration continue** : rien à télécharger ; `tools/check-apk.sh` vérifie désormais la présence du fond intégré et des bibliothèques natives de la carte dans l'APK (aucune option nouvelle à passer dans `ci.yml`).

## 14. Comment tester (à reporter dans `docs/GUIDE_PROPRIETAIRE.md`)

### Tester la carte de l'agenda (ajouté le 7 novembre 2026)

Sur le téléphone Android (la carte n'existe pas encore sur l'ordinateur) :

1. Installez l'APK d'essai comme d'habitude (`gradlew :android:assemblePreview`). Coupez le Wi-Fi et les données mobiles si vous voulez vérifier que la carte marche sans réseau.
2. Touchez « Carte » dans la barre du bas. Sous « À venir » et « Passés », choisissez « Carte » (à côté de « Liste »).
3. La carte de la France s'affiche, avec les côtes, les frontières, les grandes routes et les noms des villes, en français. Les mobilisations d'exemple sont des points : Paris, Lyon, Marseille, Lille, Toulouse, Saint-Denis, Strasbourg. Sous chaque point, son type et son jour (« Rassemblement / Demain »). Le point de Saint-Denis est gris et marqué « Annulé », celui de Toulouse est ambre et marqué « Reporté ».
4. Pincez pour zoomer, faites glisser pour vous déplacer, ou utilisez les boutons « + », « − » et le viseur (« Recentrer sur la France »).
5. Touchez le point de Paris : un encadré montre le rassemblement de la place de la République. Touchez « Voir la mobilisation » : le détail habituel s'ouvre. Revenez en arrière : la carte est restée là où vous l'aviez laissée.
6. Touchez « Filtres », choisissez « Grève » et appliquez : seul le point de Lille reste (« 1 mobilisation sur la carte »).
7. En bas à gauche, « © contributeurs OpenStreetMap » est toujours affiché ; touchez-le pour lire les sources de la carte.
8. Passez le téléphone en mode sombre : la carte change de couleurs.
9. Sur l'ordinateur, l'agenda reste en liste, avec la phrase « Sur cet appareil, la carte n'est pas encore disponible… ».

Ce qu'il faut me dire : si la carte s'affiche (ou si l'écran reste vide ou gris), si elle est lisible en clair et en sombre, si les points sont faciles à toucher, et si l'application reste fluide sur votre téléphone.
