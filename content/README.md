# Contenus éditoriaux intégrés à l'application

Ce dossier contient les contenus signés et livrés avec l'application, pour que les fiches essentielles soient lisibles hors ligne dès l'installation (cahier des charges, section 3.1 ; cahier fonctionnel, M6 et S-26).

Tout ce qui s'y trouve aujourd'hui est marqué **[EXEMPLE]** : des brouillons de démonstration, à faire relire par un avocat avant toute publication réelle. Les contenus réels seront rédigés par l'équipe éditoriale (jamais par l'IA sans relecture) et publiés par le circuit prévu (relecture, validation, publication).

## Format d'une fiche (`fiches/*.md`)

Un en-tête entre deux lignes `---`, puis le texte en Markdown (titres `##`, listes `-`, gras `**`).

| Champ | Rôle |
|---|---|
| `slug` | Identifiant stable de la fiche (étiquette `d` de l'événement), jamais modifié |
| `title`, `summary` | Titre et résumé |
| `topics` | Thèmes, séparés par des virgules (étiquettes `t`) |
| `type` | `fiche-pratique`, `dossier`, `decryptage` ou `article` |
| `origin` | `human` ou `ai-assisted` (l'origine est toujours affichée) |
| `published_at`, `updated_at` | Dates (AAAA-MM-JJ) de première publication et de dernière mise à jour |

### Encarts

Dans le texte d'une fiche, un bloc ouvert par `::: en-bref`, `::: legal`, `::: illegal`, `::: attention` ou `::: conseil` et fermé par `:::` devient un encart coloré avec son titre (bleu « En bref », vert « Légal », rouge « Illégal », orange « Attention », neutre « Conseils »). Les listes à puces y sont recommandées. Toute fiche commence par un encart « En bref ».

## Format d'une revendication (`revendications/*.md`)

Même en-tête qu'une fiche (sans `type`), avec en plus :

| Champ | Rôle |
|---|---|
| `status` | `nouvelle`, `en-debat`, `tres-soutenue`, `retenue` ou `campagne` (étiquette `ppf-status`), mis à jour par l'équipe |
| `territory` | Territoire concerné : « France », un département, une métropole (étiquette `ppf-territory`) |

Le titre est la revendication elle-même, en une ou deux phrases ; le résumé la précise ; le corps est l'argumentaire. Les exemples livrés avec l'application ne sont pas des revendications de PPF : les vraies seront proposées et votées par les citoyens (cahier fonctionnel, M11).

## Format d'une publication du fil (`fil/*.md`)

| Champ | Rôle |
|---|---|
| `created_at` | Date et heure, `AAAA-MM-JJTHH:MM`, lues en temps universel |
| `topics` | Thèmes, séparés par des virgules (étiquettes `t`) |

Le corps est le texte de la publication (type 1, note courte).

## Signer et intégrer les contenus

```bash
./gradlew :tools:content-signer:run
```

L'outil lit `content/fiches`, `content/revendications` et `content/fil`, signe chaque fichier en événement (fiches et revendications : NIP-23, type 30023 ; fil : type 1 ; voir `docs/FORMATS.md`) et écrit `fiches.json`, `revendications.json` et `fil.json` dans `shared/ui/src/commonMain/composeResources/files/contents/`, que l'application charge au premier démarrage. La clé de signature des exemples est lue dans le fichier indiqué par `PPF_CONTENT_KEY_FILE` (par défaut `~/.ppf/content-example.nsec`, hors dépôt) ; si le fichier n'existe pas, l'outil crée une nouvelle clé et affiche la clé publique à inscrire dans `EditorialTrust`.
