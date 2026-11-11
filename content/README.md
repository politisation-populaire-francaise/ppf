# Contenus éditoriaux intégrés à l'application

Ce dossier contient les contenus signés et livrés avec l'application, pour que les fiches essentielles soient lisibles hors ligne dès l'installation (cahier des charges, section 3.1 ; cahier fonctionnel, M6 et S-26).

**Contenus réels et contenus d'exemple** (décision de la propriétaire du 9 novembre 2026) : autant que possible, de vrais contenus plutôt que des exemples, avec la mention « Rédigé par l'IA ». Les contenus de « Comprendre » (`fiches`, `parcours`, `decryptages`, `histoire`, `qui-decide`, `methode`, `glossaire`, `enseignants`) sont de vrais contenus : factuels, vérifiés sur les sources officielles, chaque source avec sa date de consultation, sans le préfixe `[EXEMPLE]`, avec `origin: ai-assisted` (l'application et le site affichent qu'ils sont rédigés avec l'aide d'une IA). Les fiches juridiques restent à faire relire par un avocat avant l'ouverture publique (feuille de route). Ce qui serait inventé reste marqué **[EXEMPLE]** (fil, revendications, agenda, appels, organisations, exemples de « Décider ») ; les prises de position de PPF sont, pour l'instant, rédigées par l'IA et étiquetées comme telles (D-212, dossier `cap`).

**Décision de la propriétaire du 9 novembre 2026** : autant que possible, de vrais contenus plutôt que des exemples, avec la mention « Rédigé par l'IA ». Un contenu réel a un titre sans [EXEMPLE] et `origin: ai-assisted`, et chaque affirmation y est vérifiée sur une source officielle citée avec sa date de consultation (premiers concernés : les fiches pratiques d'« Agir » dans `fiches/`) ; l'agenda, les appels, les organisations, la solidarité et les données de test restent des exemples marqués [EXEMPLE].

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

L'outil de signature refuse un parcours incomplet (étape sans question, question sans explication ou avec plusieurs bonnes réponses, « Et maintenant ? » ou sources absentes). Les parcours sont de vrais contenus rédigés avec l'aide d'une IA (`origin: ai-assisted`), vérifiés sur Légifrance, le Conseil constitutionnel, l'Assemblée nationale et service-public.

## Décryptages, histoire des luttes, « Qui décide de ça ? » (`decryptages/`, `histoire/`, `qui-decide/`, `methode/`)

Même en-tête qu'une fiche (sans `type`, donné par le dossier), avec en plus :

| Dossier | Champs de l'en-tête |
|---|---|
| `decryptages` | `verdict` (`exact`, `plutot-exact`, `trompeur`, `contredit-par-les-faits`, `inverifiable`), `claim` (l'affirmation mot pour mot, sur une ligne), `claim_context` (où et quand elle circule, sans jamais nommer une personne privée) |
| `histoire` | `period` (`début | fin`, en années ou dates AAAA-MM-JJ), `country`, `claims` et `results` (listes séparées par ` ; `) |
| `qui-decide` | `persons` (identifiants des personnes publiques de `voir/personnes` à nommer dans l'encart, séparés par des virgules ; peut rester vide) |
| `methode` | rien de plus : la page `notre-methode` explique comment un décryptage est fait et définit les cinq verdicts |

Blocs propres à ces contenus : `::: objections` (paires `? objection` puis `> réponse sourcée`, obligatoire dans les décryptages et l'histoire), `::: chronologie` (lignes `- AAAA-MM-JJ : texte`), et pour « Qui décide de ça ? » un bloc `::: decideurs` en tête du corps (lignes `- niveau | rôles | ce que ce niveau décide (référence)`). L'outil de signature refuse un contenu dont un bloc est mal formé ou dont les sources manquent. Ce sont de vrais contenus rédigés avec l'aide d'une IA et vérifiés sur les textes officiels.

## Textes du cap du mouvement (`cap/`, D-200)

Une série de textes courts et sourcés, reliés à une action de l'application (feuille de route du MVP 1, section 5). Même en-tête qu'une fiche (sans `type`, donné par le dossier), avec en plus `nature` (`faits` pour un texte factuel, `position` pour une position de PPF) et `rank` (place dans la série, à partir de 1 ; le nom du fichier commence par ce numéro). Corps : un encart `::: en-bref`, le texte, un bloc `::: et-maintenant` obligatoire, puis `## Sources`. Une position rédigée avec l'aide d'une IA (`origin: ai-assisted`) affiche « Position de PPF, rédigée par l'IA, en attente de relecture » ; la partie « Ce que propose PPF » le rappelle dans le texte. Chaque fait porte sa source et sa date de consultation ; une qualification politique est attribuée à qui l'emploie, jamais présentée comme un fait.

## Textes légaux du site (`legal/`)

Mentions légales, politique de confidentialité, conditions d'utilisation, charte de contenu et procédure de signalement : **brouillons à faire relire par un avocat**, hors de la signature des contenus. Ils sont copiés dans les ressources du site à la compilation et servis aux adresses `/mentions-legales`, `/confidentialite`, `/conditions-utilisation`, `/charte` et `/signaler`. En-tête : `title`, `summary`, `origin`, `updated_at` ; pas de `slug`. Détail et blancs à remplir : `legal/README.md`.

## Fiches pédagogiques pour la classe (`enseignants/`, CO-13, D-096)

Une fiche fait vivre un parcours en 55 minutes avec un groupe (conception : `docs/integration/comprendre-enseignants.md`). Identifiant `classe-<identifiant du parcours>-<niveau>`, `type: fiche-pedagogique`, `duration: 55`, et en plus : `course` (identifiant du parcours lié), `level` (`lycee` ou `cycle-4`), `programme` (le programme officiel servi, en une phrase, avec le numéro et la date du Bulletin officiel et « vérifié le AAAA-MM-JJ » ; l'adresse du texte va dans les sources). Corps, dans cet ordre : `## Objectifs`, `## Prérequis`, `## Déroulé` (lignes `- 10 min : ce que fait la classe`, dont la somme fait la durée), `## Débat guidé` (un bloc `::: objections` d'au moins deux objections, une de chaque côté), `## Pour aller plus loin` (facultative), `## Sources`. Pas de bloc `::: quiz` : les questions à main levée sont celles du parcours. Le rappel de neutralité et la licence sont ajoutés par le site et l'application, jamais écrits dans la fiche.

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
| `title` | Le terme tel qu'il s'affiche (sans `[EXEMPLE]` pour un vrai contenu) |
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
| `from` | Facultatif : `mouvement` pour une revendication déjà portée par le mouvement et relevée par PPF (étiquette `ppf-from`, D-152) ; demande `carried_by` et une section `## Sources` |
| `carried_by` | Avec `from: mouvement` : qui la porte, où et quand, séparés par ` ; ` (étiquettes `ppf-carried-by`) |
| `supported_by` | Facultatif, pour toute revendication : les organisations qui la soutiennent publiquement, avec la date, séparées par ` ; ` (étiquettes `ppf-supported-by`, affichées « Également portée par ») ; demande une section `## Sources` |

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
- Une Constitution Citoyenne et Démocratique (type `pacte`) : `election`, `pacte-status`, `sent`, `deadline`, une ligne `measure: identifiant | titre | engagement précis | texte à modifier` par mesure, une ligne `candidate:` par candidat.
- Une réponse : `pacte`, `candidate`, `received`, une ligne `answer: identifiant | signe ou refuse | commentaire` par mesure répondue, `archive`. **N'écrivez jamais l'empreinte du texte** : l'outil la calcule à partir du Pacte.

La liste complète est dans `docs/FORMATS.md`, section 4 septies. L'outil refuse une pétition hors d'un site officiel, une Constitution Citoyenne et Démocratique sans date limite, une réponse d'une personne qui n'est pas candidate ou à une mesure inconnue.

**Vrais contenus et contenus des tests** (décision de la propriétaire du 9 novembre 2026, D-151) : les pétitions, le calendrier électoral, les modèles de courrier et les consultations de `decider/` sont réels, vérifiés le jour même sur les sources officielles, chaque source avec sa date de consultation, et marqués `origin: ai-assisted` (« Rédigé par l'IA »). Restent [EXEMPLE] dans `decider/` : la Constitution Citoyenne et Démocratique, les réponses des candidats, le comparateur et ses positions (jusqu'à la liste officielle du Conseil constitutionnel), et la pétition locale (aucune source officielle commune ne recense les pétitions locales). Les contenus fictifs que lisent les tests sont dans `tests/decider/` : l'outil les signe à part dans `shared/ui/src/jvmTest/resources/contents/decider-tests.json`, jamais embarqué dans l'application.

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

### Format d'une caisse de solidarité et d'une mission de bénévolat (`solidarite/caisses/*.md`, `solidarite/missions/*.md`)

M35 (AG-10, AG-12), décision D-085 ; formats signés dans `docs/FORMATS.md`, section 4 terdecies.

- `solidarite/caisses/*.md` : `slug`, `title`, `summary`, `fund_type` (`greve`, `juridique` ou `autre`), `territory`, `beneficiary`, `verified` (date de vérification par l'équipe, `AAAA-MM-JJ`), `no_party` (`oui` obligatoire : ni candidat ni parti), `manager` (nom) et/ou `manager_entry` (identifiant d'une fiche de `organisations/`, transformé en adresse `30023:<clé de l'outil>:<slug>` ; l'outil vérifie que la fiche existe), `donation_link` (`https://` seulement), `sector`, `strike`, `status` (`ouverte`, `close`, `retiree`), `origin`, `geohash`, `published_at`, `updated_at` (`AAAA-MM-JJ`, midi UTC comme les autres contenus). Avant de publier une vraie caisse : `docs/guides/verification-caisses-solidarite.md`.
- `solidarite/missions/*.md` : `slug`, `title`, `summary`, `skills` (séparées par des virgules, dans la liste fermée de `docs/FORMATS.md`), `territory`, `remote` (`oui` pour une mission à distance), `location`, `commitment`, `people`, `deadline` (`AAAA-MM-JJ`, fin de journée à l'heure de Paris), `status` (`ouverte`, `pourvue`, `close`), `origin`, `published_at`, `updated_at`.
- Aucun champ `type` dans ces en-têtes. Résultat : `solidarite.json`, à côté de `organisations.json`. Le corps est la présentation (Markdown simple, sans lien cliquable).

### Format d'une suite de mobilisation (`suites/*.md`)

AG-62 « Et après ? » (conception : `docs/integration/agir-et-apres.md` ; format signé : `docs/FORMATS.md`, section 4 septdecies). Une suite dit ce qui a suivi une mobilisation passée de l'agenda ; elle s'affiche sous la description de cette mobilisation, dans l'onglet « Passés ». **Les suites actuelles sont des exemples [EXEMPLE]** : elles suivent la projection-débat de Rennes de `agenda/`, et l'agenda reste fait d'exemples (décision de la propriétaire du 9 novembre 2026), donc ses suites aussi ; l'outil refuse un titre sans [EXEMPLE] et une source hors des domaines réservés aux exemples (`example.org`, `example.com`, `example.net`, `.invalid`).

| Champ | Rôle |
|---|---|
| `slug`, `title`, `summary` | Identifiant stable (préfixe `suite-` conseillé), titre avec [EXEMPLE], résumé obligatoire (la phrase montrée sous le titre) |
| `type` | `suite` |
| `mobilization` | La mobilisation suivie : `agenda:<slug d'un fichier de agenda/>` ; une ligne par mobilisation si la suite en suit plusieurs |
| `suite-kind` | `reponse-elu`, `annonce`, `recul`, `texte-depose`, `decision-justice` ou `autre` |
| `date` | Date de la suite, `AAAA-MM-JJ`, jamais avant la mobilisation |
| `fact-status` | `etabli`, `allegue` ou `enquete`, comme un fait de « Voir » |
| `source` | Au moins une : `type \| nom \| adresse https \| date`, comme dans « Voir » ; **l'adresse est obligatoire** |
| `person` | Facultatif, obligatoire pour une réponse d'élu : `voir:<slug d'une personne de voir/personnes>` (le droit de réponse s'applique) |
| `demand`, `call`, `chronology` | Facultatifs : `revendication:<slug>`, `appel:<slug>`, `voir:<slug d'un fait de la chronologie>` |
| `correction` | Facultatif : `date \| résumé public \| correction ou mise-a-jour` |
| `origin`, `published_at`, `updated_at` | Comme pour une fiche |

Le corps est un texte simple (pas de Markdown), 4 000 caractères au plus. L'outil écrit chaque lien sous la forme d'une adresse complète (`31923:<clé>:<slug>` pour une mobilisation, `30023:<clé>:<slug>` pour le reste) ; n'écrivez jamais l'adresse vous-même. Il refuse un champ inconnu, une suite sans source ou dont une source n'a pas d'adresse, un type ou un statut inconnus, un lien vers un identifiant qui n'existe pas ou du mauvais genre, une réponse d'élu sans `person`. Résultat : `suites.json`.

## Signer et intégrer les contenus

```bash
./gradlew :tools:content-signer:run
```

L'outil lit `content/fiches`, `content/parcours`, `content/decryptages`, `content/histoire`, `content/qui-decide`, `content/methode`, `content/glossaire`, `content/revendications`, `content/fil`, `content/voir`, `content/agenda`, `content/appels`, `content/organisations`, `content/solidarite` et `content/suites`, signe chaque fichier en événement (fiches, parcours, contenus de « Comprendre », glossaire, revendications, appels, organisations, caisses et missions, suites : NIP-23, type 30023 ; fil : type 1 ; agenda : type 31923 ; voir `docs/FORMATS.md`) et écrit `fiches.json`, `parcours.json`, `comprendre.json`, `glossaire.json`, `revendications.json`, `fil.json`, `voir.json`, `agenda.json`, `appels.json`, `organisations.json`, `solidarite.json` et `suites.json` dans `shared/ui/src/commonMain/composeResources/files/contents/`, que l'application charge au premier démarrage. La signature BIP-340 utilise un aléa : relancer l'outil change les signatures de tous les fichiers (les identifiants restent les mêmes), ce qui crée des différences inutiles entre branches ; ne garder que les fichiers réellement modifiés. La clé de signature des exemples est lue dans le fichier indiqué par `PPF_CONTENT_KEY_FILE` (par défaut `~/.ppf/content-example.nsec`, hors dépôt) ; si le fichier n'existe pas, l'outil crée une nouvelle clé et affiche la clé publique à inscrire dans `EditorialTrust`.
