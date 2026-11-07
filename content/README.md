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

## Format d'un parcours (`parcours/*.md`)

Un parcours « Comprendre en 5 minutes » (cahier fonctionnel, M13.1) : quelques étapes courtes, chacune suivie d'une question. Même en-tête qu'une fiche, avec `type: parcours` et en plus `duration` (durée de lecture annoncée, en minutes). Le corps :

```
::: en-bref
- Trois ou quatre points essentiels.
:::

## Titre de la première étape

Deux ou trois paragraphes courts.

::: quiz
? La question ?
- [ ] une réponse fausse
- [x] la seule bonne réponse
- [ ] une autre réponse fausse
> L'explication, avec sa source (Constitution, article 39).
:::

## Titre de l'étape suivante

...

::: et-maintenant
- [Voir les revendications des citoyens](ppf:revendications)
- Une autre action concrète, en texte.
:::

## Sources

- Chaque source avec son adresse officielle.
```

L'outil de signature refuse un parcours incomplet (étape sans question, question sans explication ou avec plusieurs bonnes réponses, « Et maintenant ? » ou sources absentes). Les quatre parcours actuels sont des brouillons **[EXEMPLE]** rédigés avec l'aide d'une IA (`origin: ai-assisted`), vérifiés sur Légifrance, le Conseil constitutionnel, l'Assemblée nationale et service-public, à faire relire par l'équipe éditoriale.

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

## Format des contenus de « Voir » (`voir/**/*.md`)

Les sous-dossiers (`indicateurs`, `faits`, `personnes`, `textes`, `monde`, `dossiers`, `reponses`) ne servent qu'à ranger : c'est le champ `type` qui compte (`indicateur`, `fait`, `personne`, `texte`, `mouvement`, `dossier`, `reponse`). L'en-tête reprend `slug`, `title`, `summary`, `topics`, `origin`, `published_at` et `updated_at` ; **tout autre champ devient une étiquette `ppf-<champ>`** dont les valeurs sont séparées par `|`. Un champ répété donne plusieurs étiquettes (une mesure, une source, un mandat par ligne). Les lignes qui commencent par `#` sont des commentaires.

- `person`, `text`, `indicator` et `target` nomment une autre brique par son `slug` (lien).
- `next` est « libellé | cible », la cible étant `fiche:<slug>`, `voir:<slug>` ou `espace:<carte, revendications, decider…>`.
- Exemple de mesure : `measure: j1-aut | 2026-09-18 | 312000 | autorites | Ministère de l'Intérieur | https://… | moyenne | Journée du 18 septembre | France`.

La liste complète des champs et des valeurs est dans `docs/FORMATS.md`, section 4 quinquies. L'outil de signature relit chaque brique comme l'application et **refuse** celle qu'elle ignorerait (statut manquant, affaire sans étape de procédure, valeur inconnue, lien vers un `slug` qui n'existe pas).

## Signer et intégrer les contenus

```bash
./gradlew :tools:content-signer:run
```

L'outil lit `content/fiches`, `content/parcours`, `content/revendications`, `content/fil` et `content/voir`, signe chaque fichier en événement (fiches, parcours et revendications : NIP-23, type 30023 ; fil : type 1 ; voir `docs/FORMATS.md`) et écrit `fiches.json`, `parcours.json`, `revendications.json` et `fil.json`, `voir.json` dans `shared/ui/src/commonMain/composeResources/files/contents/`, que l'application charge au premier démarrage. La clé de signature des exemples est lue dans le fichier indiqué par `PPF_CONTENT_KEY_FILE` (par défaut `~/.ppf/content-example.nsec`, hors dépôt) ; si le fichier n'existe pas, l'outil crée une nouvelle clé et affiche la clé publique à inscrire dans `EditorialTrust`.
