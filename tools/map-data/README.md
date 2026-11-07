# Préparer les données de la carte

Ce dossier contient l’outil qui prépare, **sur l’ordinateur d’un développeur et de temps en temps seulement**, les données de la carte de l’agenda : les glyphes des noms de lieux et le fond de carte de la France à faible zoom intégré à l’application. L’application et l’intégration continue ne téléchargent jamais rien : elles utilisent les fichiers déjà rangés dans le dépôt.

Note complète : `docs/integration/agir-carte.md`.

## Ce que l’outil produit

| Fichier | Contenu | Taille | D’où |
|---|---|---|---|
| `shared/ui/src/commonMain/composeResources/files/carte/fond-france.pmtiles` | Fond de carte : le monde jusqu’au zoom 4, la France métropolitaine, la Corse et les départements d’outre-mer jusqu’au zoom 7 ; seulement la terre, l’eau, les grandes routes, les frontières et les noms (pays, régions, villes) | voir la note | Construction quotidienne du fond OpenStreetMap de Protomaps (données © contributeurs OpenStreetMap, licence ODbL) |
| `shared/ui/src/commonMain/composeResources/files/carte/glyphes/regular/*.pbf` et `bold/*.pbf` | Glyphes des étiquettes (champs de distance signée, 24 px) | quelques centaines de kilooctets | Polices Atkinson Hyperlegible déjà intégrées à l’application (licence SIL OFL 1.1) : rien n’est téléchargé |

## Pas à pas

Toutes les commandes se lancent depuis la racine du dépôt.

### 1. Les glyphes (aucun téléchargement)

```
gradlew :tools:map-data:run --args="glyphes"
```

À refaire seulement si les polices de l’application changent.

### 2. Le fond de carte intégré (téléchargement de quelques mégaoctets)

1. Choisissez une construction récente sur la page des constructions de Protomaps (liste lisible à l’adresse `https://build-metadata.protomaps.dev/builds.json`) : par exemple `20261106.pmtiles`, version 4 du schéma. Ces fichiers ne sont gardés qu’une semaine environ.
2. Mesurez d’abord, sans rien écrire :

   ```
   gradlew :tools:map-data:run --args="fond https://build.protomaps.com/20261106.pmtiles --max-zoom 8 --essai"
   ```

   L’outil lit le fichier distant **par plages d’octets** : il ne télécharge que les tuiles utiles (quelques mégaoctets, pas les 139 Go du fichier), les filtre et affiche la taille obtenue zoom par zoom.
3. Écrivez le fichier (le zoom 7 est le compromis retenu, voir la note) :

   ```
   gradlew :tools:map-data:run --args="fond https://build.protomaps.com/20261106.pmtiles"
   ```

4. Vérifiez : `gradlew :shared:ui:jvmTest --tests "*AgendaMapScreenTest*"`, puis committez le fichier avec la date de la construction dans le message.

L’outil accepte aussi un fichier PMTiles local à la place de l’adresse (par exemple un extrait déjà téléchargé).

### 3. Le fond détaillé du serveur (au premier déploiement, pas avant)

Le fond détaillé (zooms 8 à 14) n’est pas dans le dépôt : il sera rangé sur le serveur et servi tel quel, par plages d’octets, par Caddy (`file_server`) ou par la route de développement `/tiles/` du serveur (`PPF_TILES_DIR`). Pour le produire, on utilise l’outil officiel `pmtiles` (go-pmtiles, licence BSD-3-Clause), téléchargé depuis ses publications GitHub en vérifiant sa somme de contrôle :

```
pmtiles extract https://build.protomaps.com/20261106.pmtiles france.pmtiles --bbox=-5.25,41.25,9.7,51.2 --maxzoom=14
```

puis on copie `france.pmtiles` dans le dossier des tuiles du serveur. Taille et bande passante estimées : voir la note.

Pour essayer en local avec le serveur de développement :

```
set PPF_TILES_DIR=C:\chemin\vers\le\dossier
gradlew :server:runDev
```

## Licences à respecter

- **Données OpenStreetMap** (ODbL 1.0) : la mention « © contributeurs OpenStreetMap » est affichée en permanence sur la carte, et la boîte « Sources de la carte » renvoie à openstreetmap.org/copyright. Le fond intégré est une « œuvre produite » au sens de l’ODbL : il peut être distribué avec l’application avec cette mention.
- **Schéma Protomaps** (logiciel sous licence BSD-3-Clause, données ODbL) : aucun service Protomaps n’est appelé par l’application.
- **Polices Atkinson Hyperlegible** (SIL OFL 1.1, `licenses/OFL-AtkinsonHyperlegible.txt`) : les glyphes sont un dérivé autorisé, distribué avec la police.
