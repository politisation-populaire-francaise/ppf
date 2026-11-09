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

## Décryptages, histoire des luttes, « Qui décide de ça ? » (`decryptages/`, `histoire/`, `qui-decide/`, `methode/`)

Même en-tête qu'une fiche (sans `type`, donné par le dossier), avec en plus :

| Dossier | Champs de l'en-tête |
|---|---|
| `decryptages` | `verdict` (`exact`, `plutot-exact`, `trompeur`, `contredit-par-les-faits`, `inverifiable`), `claim` (l'affirmation mot pour mot, sur une ligne), `claim_context` (où et quand elle circule, sans jamais nommer une personne privée) |
| `histoire` | `period` (`début | fin`, en années ou dates AAAA-MM-JJ), `country`, `claims` et `results` (listes séparées par ` ; `) |
| `qui-decide` | `persons` (identifiants des personnes publiques de `voir/personnes` à nommer dans l'encart, séparés par des virgules ; peut rester vide) |
| `methode` | rien de plus : la page `notre-methode` explique comment un décryptage est fait et définit les cinq verdicts |

Blocs propres à ces contenus : `::: objections` (paires `? objection` puis `> réponse sourcée`, obligatoire dans les décryptages et l'histoire), `::: chronologie` (lignes `- AAAA-MM-JJ : texte`), et pour « Qui décide de ça ? » un bloc `::: decideurs` en tête du corps (lignes `- niveau | rôles | ce que ce niveau décide (référence)`). L'outil de signature refuse un contenu dont un bloc est mal formé ou dont les sources manquent. Tous les contenus actuels sont des brouillons **[EXEMPLE]** rédigés avec l'aide d'une IA et vérifiés sur les textes officiels, à faire relire.

## Format d'une entrée du glossaire (`glossaire/*.md`)

Une entrée par fichier (décision D-048). Le terme est repéré tout seul dans **tous** les contenus de l'application (Comprendre, Voir, Agir, revendications, fiches) : sa première occurrence dans un contenu est soulignée en pointillés, suivie d'un petit « ? » ; un geste ouvre une infobulle avec la définition rapide, puis « En savoir plus » ouvre la page de l'entrée. Les rédacteurs n'ont rien à baliser dans les autres contenus.

```markdown
---
slug: motion-de-censure
title: [EXEMPLE] Motion de censure
summary: Vote par lequel les députés peuvent renverser le gouvernement.
variants: motion de censure, motions de censure
topics: parlement, gouvernement
see_also: 49-3, dissolution, gouvernement
related: parcours:le-49-3-et-la-motion-de-censure, qui-decide:ecole
origin: ai-assisted
published_at: 2026-11-07
updated_at: 2026-11-07
---
Explication détaillée (« En savoir plus ») : 80 à 250 mots, phrases courtes, un exemple concret,
ce qu'il ne faut pas confondre. Encarts `::: en-bref` permis.

## Sources

- [Constitution, article 49](https://www.legifrance.gouv.fr/...)
```

| Champ | Rôle |
|---|---|
| `slug` | Identifiant stable de l'entrée, jamais modifié |
| `title` | Le terme tel qu'il s'affiche, précédé de `[EXEMPLE] ` comme tout contenu pour l'instant |
| `summary` | **La définition rapide de l'infobulle** : une ou deux phrases, 200 caractères au plus, compréhensible sans rien d'autre |
| `variants` | Les formes repérées dans les textes, séparées par des virgules : singulier, pluriel, sigle et forme longue (`49.3, 49-3, article 49.3, article 49 alinéa 3`). Une virgule à l'intérieur d'une forme s'écrit `\,` (`Conseil économique\, social et environnemental, CESE` ; `article 49\, alinéa 3`). Au moins une. Une forme n'appartient qu'à une entrée |
| `topics` | Thèmes, séparés par des virgules (facultatif) |
| `see_also` | Slugs d'autres entrées du glossaire, séparés par des virgules (facultatif, « Voir aussi ») |
| `related` | Contenus liés, séparés par des virgules : `parcours:<slug>`, `fiche:<slug>`, `decryptage:<slug>`, `qui-decide:<slug>`, `histoire:<slug>` (facultatif, « Pour aller plus loin ») |
| `auto` | `non` pour un mot trop courant : l'entrée n'est jamais repérée dans les textes, seulement listée dans le glossaire (facultatif, `oui` par défaut) |
| `origin`, `published_at`, `updated_at` | Comme pour une fiche |

Le corps est l'explication détaillée, terminée par `## Sources` (sources primaires : Constitution et codes sur Légifrance, conseil-constitutionnel.fr, assemblee-nationale.fr, senat.fr, vie-publique.fr, service-public.fr, europa.eu, insee.fr). Les autres termes du glossaire y sont repérés aussi, jamais le terme de l'entrée elle-même.

Comment les formes sont repérées : sans tenir compte des majuscules, sauf pour un sigle (une forme entièrement en capitales, d'au moins deux lettres, chiffres permis : `UE`, `RIP`, `QPC`, `EPCI`, `CMP`), qui n'est repéré qu'écrit en capitales (« UE » n'est pas repéré dans « ue ») ; apostrophes droite et courbe équivalentes ; accents respectés (« Etat » n'est pas « État ») ; seulement entre deux limites de mot (« loi » n'est pas repéré dans « emploi ») ; à une même position, la forme la plus longue gagne (« motion de censure » avant « motion ») ; jamais dans les titres, les liens, les adresses écrites en clair (`http://…`, `https://…`, `www.…`, `ppf:…`), ni la section des sources. Le pluriel n'est repéré que s'il figure dans `variants`.

L'outil de signature refuse une entrée sans définition ou avec une définition de plus de 200 caractères, sans forme, avec une forme déjà utilisée par une autre entrée (sans tenir compte des majuscules ni des apostrophes : `UE` et `ue` sont la même forme), sans `## Sources`, ou dont `see_also` ou `related` nomme un slug inconnu. Résultat : `glossaire.json` (type 30023, `ppf-type` = `glossaire`, une étiquette `ppf-variant` par forme, `ppf-auto` = `non` si besoin, étiquettes `a` de rôle `voir-aussi` et `lie`).

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

Les sous-dossiers (`indicateurs`, `faits`, `personnes`, `textes`, `monde`, `dossiers`, `reponses`, `partis`, `transparence`, `victoires`, `communiques`) ne servent qu'à ranger : c'est le champ `type` qui compte (`indicateur`, `fait`, `personne`, `texte`, `mouvement`, `dossier`, `reponse`, `parti`, `transparence`, `victoire`, `communique`). Les victoires (`victoire`, PP-03) sont décrites dans `docs/integration/victoires.md`, section 2. Les pages de « Transparence de PPF » (`transparence`, PP-01) sont décrites dans `docs/integration/transparence-ppf.md`, section 2. L'en-tête reprend `slug`, `title`, `summary`, `topics`, `origin`, `published_at` et `updated_at` ; **chaque autre champ devient une étiquette `ppf-<champ>`** dont les valeurs sont séparées par `|` ; seuls les champs prévus par le schéma du type (`shared/core/.../core/voir/VoirSchemas.kt`) sont acceptés. Un champ répété donne plusieurs étiquettes (une mesure, une source, un mandat par ligne). Les lignes qui commencent par `#` sont des commentaires.

- `person`, `text`, `indicator` et `target` nomment une autre brique par son `slug` (lien).
- `next` est « libellé | cible », la cible étant `fiche:<slug>`, `voir:<slug>` ou `espace:<carte, revendications, decider…>`.
- Exemple de mesure : `measure: j1-aut | 2026-09-18 | 312000 | autorites | Ministère de l'Intérieur | https://… | moyenne | Journée du 18 septembre | France`.

La liste complète des champs et des valeurs est dans `docs/FORMATS.md`, section 4 quinquies. L'outil de signature vérifie chaque brique avec son schéma et **refuse** un champ inconnu, une valeur mal écrite (date, adresse `https://`, nombre, valeur hors liste), une affaire sans étape de procédure, un lien vers un `slug` qui n'existe pas ou d'un autre type, et toute brique ou ligne que l'application ignorerait (détail : `docs/integration/voir-schemas.md`).

## Format des contenus de « Décider » (`decider/**/*.md`)

Même principe que « Voir » : chaque champ de l'en-tête devient une étiquette `ppf-<champ>`, les champs multiples étant séparés par `|`. Les sous-dossiers `petitions`, `pacte` et `engagements` ne servent qu'à ranger ; c'est le champ `type` qui compte (`petition`, `pacte`, `engagement`).

- `text`, `person`, `candidate` et `pacte` nomment une autre brique par son `slug` (lien) ; un candidat doit avoir sa fiche dans `voir/personnes`.
- `next` est « libellé | cible », la cible étant `fiche:<slug>`, `voir:<slug>`, `decider:<slug>` ou `espace:<id>`.
- Une pétition : `platform`, `official-url` (obligatoirement sur le site officiel de la plateforme), `petition-status`, et des relevés `count: 412950 | 2026-11-06 | site des pétitions de l'Assemblée nationale`.
- Un Pacte : `election`, `pacte-status`, `sent`, `deadline`, une ligne `measure: identifiant | titre | engagement précis | texte à modifier` par mesure, une ligne `candidate:` par candidat.
- Une réponse : `pacte`, `candidate`, `received`, une ligne `answer: identifiant | signe ou refuse | commentaire` par mesure répondue, `archive`. **N'écrivez jamais l'empreinte du texte** : l'outil la calcule à partir du Pacte.

La liste complète est dans `docs/FORMATS.md`, section 4 septies. L'outil refuse une pétition hors d'un site officiel, un Pacte sans date limite, une réponse d'une personne qui n'est pas candidate ou à une mesure inconnue.

## Espace « Agir » : mobilisations, appels, organisations

Formats complets dans `docs/FORMATS.md`, section 4 sexies. Tous les exemples sont fictifs et marqués **[EXEMPLE]** : aucune organisation, personne ni élu réels ; lieux fictifs ou génériques ; sites et courriels en `example.org` ou `exemple.invalid` (noms réservés aux exemples) ; téléphones pris seulement dans les blocs que l'Arcep réserve aux œuvres audiovisuelles (racines 01 99 00, 02 61 91, 03 53 01, 04 65 71, 05 36 49 et 06 39 98 ; décision n° 2018-0881 modifiée, version du 1er janvier 2026, section 2.5).

### Format d'une mobilisation de l'agenda (`agenda/*.md`)

| Champ | Rôle |
|---|---|
| `slug` | Identifiant stable (étiquette `d`), jamais modifié |
| `title` | Titre, commençant par [EXEMPLE] pour un exemple |
| `type` | `manifestation`, `blocage`, `rassemblement`, `greve`, `assemblee-generale`, `reunion` ou `autre` |
| `start`, `end` | Début et fin facultative, `AAAA-MM-JJTHH:MM`, heure de Paris |
| `location`, `territory` | Lieu en clair ; commune, département ou « France » |
| `status` | Facultatif : `prevu` (par défaut), `annule`, `reporte` |
| `declaration` | Facultatif, pour une manifestation : `faite`, `en-cours`, `non-faite` |
| `summary`, `link` | Facultatifs : résumé, lien web |
| `organizers` | Facultatif : clés (`pubkey`) d'organisations fictives de `organisations/`, séparées par des virgules ; la page de ces organisations liste alors la mobilisation, avec la mention « Annoncée, non confirmée » (ces organisations fictives n'ont pas de clé secrète : elles ne peuvent pas confirmer) |
| `participation` | Facultatif : `participant` ou `organisateur` ; ajoute la réponse publique de la clé des exemples (l'équipe éditoriale) à la mobilisation, type 31925, datée comme elle (rubrique « Organisations participantes ») |
| `created_at` | Date de la version, `AAAA-MM-JJTHH:MM`, temps universel |

Le corps est la description (texte simple). L'outil vérifie chaque mobilisation avec les règles de l'application et écrit `agenda.json` (type 31923, NIP-52 ; avec les réponses publiques de type 31925 demandées par `participation`).

### Format d'un appel (`appels/*.md`)

| Champ | Rôle |
|---|---|
| `slug` | Identifiant stable (`d`), 64 caractères `a-z0-9-` au plus |
| `title`, `summary` | Titre (avec [EXEMPLE]) et résumé |
| `call_type` | Un type d'appel : `se-reunir`, `debattre`, `s-organiser`, `manifester`, `soutenir`, `petition`, `interpeller`, `boycotter`, `collecte`, `benevoles`, `information`, `temoins`, `autre` |
| `territory` | Territoire |
| `status`, `report` | `ouvert` ou `clos` ; le bilan d'un appel clos |
| `published_at`, `updated_at` | `AAAA-MM-JJTHH:MM`, heure de Paris |
| `deadline`, `start`, `end` | `AAAA-MM-JJ` (échéance qui dure toute la journée) ou `AAAA-MM-JJTHH:MM`, heure de Paris |
| `goal`, `location`, `r` | Objectif, lieu, lien |
| `ppf-...` | Les champs propres au type, sous le nom de leur étiquette ; un champ qui n'appartient pas au type arrête l'outil |

Le corps est le texte de l'appel (texte brut). L'outil construit chaque appel comme le formulaire de l'application et le vérifie avec ses règles à sa date de publication : un exemple qui enfreint une règle arrête l'outil. Un appel `status: clos` est la version de clôture de l'appel ouvert. Résultat : `appels.json` (type 30023, `ppf-type` = `appel`).

### Format d'une organisation de l'annuaire (`organisations/*.md`)

En-tête entre deux lignes `---`, puis la présentation en Markdown.

| Champ | Étiquette | Obligatoire |
|---|---|---|
| `slug` | `d` | Oui |
| `title` | `title` (nom, avec [EXEMPLE] pour un exemple) | Oui |
| `summary` | `summary` | Oui |
| `org_type` | `ppf-org-type` (`association`, `syndicat`, `collectif`, `legal-team`, `street-medics`, `avocats-benevoles`, `autre`) | Oui |
| `territory` | `ppf-territory` (« Commune (dd) », « Département (dd) » ou « France ») | Oui |
| `verified` | `ppf-verified` (AAAA-MM-JJ) | Oui |
| `origin` | `ppf-origin` (`human` ou `ai-assisted`) | Oui |
| `published_at`, `updated_at` | `published_at`, et date de l'événement (midi UTC) | Oui |
| `pubkey` | `p` (clé hexadécimale de l'organisation, si elle a un compte) | Non |
| `websites` | `r`, séparés par des virgules | Non |
| `phone`, `email`, `address` | `ppf-contact-phone`, `ppf-contact-email`, `ppf-contact-address` : coordonnées publiques de l'organisation, jamais celles d'une personne | Non |
| `geohash` | `g` | Non |

Résultat : `organisations.json` (type 30023, `ppf-type` = `organisation`). L'application n'accepte ces fiches que signées par une clé de l'équipe éditoriale. Les clés `pubkey` des exemples ont été tirées au hasard sans garder leur clé secrète : personne ne peut publier en leur nom.

## Signer et intégrer les contenus

```bash
./gradlew :tools:content-signer:run
```

L'outil lit `content/fiches`, `content/parcours`, `content/decryptages`, `content/histoire`, `content/qui-decide`, `content/methode`, `content/glossaire`, `content/revendications`, `content/fil`, `content/voir`, `content/agenda`, `content/appels` et `content/organisations`, signe chaque fichier en événement (fiches, parcours, contenus de « Comprendre », glossaire, revendications, appels et organisations : NIP-23, type 30023 ; fil : type 1 ; agenda : type 31923 ; voir `docs/FORMATS.md`) et écrit `fiches.json`, `parcours.json`, `comprendre.json`, `glossaire.json`, `revendications.json`, `fil.json`, `voir.json`, `agenda.json`, `appels.json` et `organisations.json` dans `shared/ui/src/commonMain/composeResources/files/contents/`, que l'application charge au premier démarrage. La signature BIP-340 utilise un aléa : relancer l'outil change les signatures de tous les fichiers (les identifiants restent les mêmes), ce qui crée des différences inutiles entre branches ; ne garder que les fichiers réellement modifiés. La clé de signature des exemples est lue dans le fichier indiqué par `PPF_CONTENT_KEY_FILE` (par défaut `~/.ppf/content-example.nsec`, hors dépôt) ; si le fichier n'existe pas, l'outil crée une nouvelle clé et affiche la clé publique à inscrire dans `EditorialTrust`.
