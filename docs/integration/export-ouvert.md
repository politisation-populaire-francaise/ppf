# Export ouvert, vérification et citation des contenus (M34 S-48, M33 S-141, M34 S-144) : conception, version 0.1

Rédigé le 8 novembre 2026 par le pôle « Voir » (« [Voir] Team Dev 2 »), à partir du cahier fonctionnel 2.1 (M33, M34, parcours « Vérifier un contenu reçu » et « Citer un chiffre ») et du cahier des charges 2.2 (sections 3.2 et 3.8, « Données ouvertes publiées par PPF »). Document de conception : rien n'est codé tant qu'une étape n'est pas marquée « faite ».

## 0. Ce qui existe déjà

- Le format d'export des contenus est l'événement signé NIP-01 (D-018, `docs/FORMATS.md` section 2) ; l'adresse stable d'un contenu modifiable est `kind:pubkey:d` (section 4).
- `GET /api/v1/events` et `GET /api/v1/events/<id>` servent déjà tous les événements stockés, sans compte (D-021).
- Les pages du site ont une adresse stable (`/voir/...`, `/comprendre/...`, D-055, D-091).

Ce qui manque : un point d'entrée qui dit ce qui est publié et sous quelle licence, des fichiers téléchargeables d'un coup (JSON et tableur), un schéma versionné pour chaque fichier, une page qui vérifie un contenu et une référence prête à citer.

## 1. Principes

1. **Seulement ce que PPF publie** : en version 1, l'export ne contient que les contenus signés par les clés de l'équipe éditoriale (`PublicationPolicy.isEditor`, vérifié de nouveau à la lecture). Les contenus des comptes citoyens (revendications, mobilisations, messages de salon) restent lisibles par l'API, mais ne sont pas rassemblés en un fichier : un fichier en vrac relierait des clés pseudonymes à des opinions politiques (RGPD, article 9 ; protéger par défaut).
2. **Vérifiable par tous** : le fichier `contenus` est fait des événements signés eux-mêmes, sans transformation ; chacun peut vérifier chaque signature sans faire confiance au serveur. Les tableaux (CSV) sont dérivés de ces événements et chaque ligne cite l'adresse stable et l'identifiant de la version d'où elle vient.
3. **Chaque donnée avec sa source** (S-48) : une ligne d'un tableau porte toujours le type, le nom, l'adresse et la date de sa source.
4. **Personnes et justice à part** : pas de tableau des personnes, des affaires ni des peines avant la relecture de l'avocat (cahier des charges 9.8 : « réutilisation des décisions de justice en données ouvertes », RGPD article 10). Ces contenus restent dans le fichier `contenus` comme ils le sont dans l'API.
5. **Schéma versionné** : chaque fichier a un schéma JSON (JSON Schema 2020-12) dont le nom porte sa version majeure (`voir-mesures-1.schema.json`) ; une colonne ajoutée à la fin garde la version, tout autre changement crée la version suivante, l'ancienne restant servie six mois.
6. **Aucune donnée sur qui télécharge** : rien n'est journalisé, aucun compte, aucun jeton ; mise en cache par `ETag`.

## 2. API et fichiers (étape A)

| Requête | Contenu |
|---|---|
| `GET /api/v1/export` | Catalogue : `{"format":1,"generated_at":…,"publisher":"Politisation Populaire Française","license":{…},"editorial_keys":["<hex>",…],"datasets":[…]}`. Chaque jeu : `id`, `title`, `description`, `space`, `schema` (adresse du schéma), `files` (`format` = `json` ou `csv`, `url`, `sha256`, `bytes`, `rows`). |
| `GET /api/v1/export/contenus.json` | Tableau JSON des événements signés : la dernière version de chaque adresse des clés éditoriales (types 30023 et suivants), triés par adresse. |
| `GET /api/v1/export/<jeu>.json` et `.csv` | Les tableaux dérivés (section 3). CSV : RFC 4180, UTF-8, virgule, première ligne d'en-têtes en anglais (noms des propriétés du schéma), dates ISO 8601, nombres avec un point décimal. |
| `GET /api/v1/export/schemas/<jeu>-<version>.schema.json` | Le schéma de chaque fichier. |

- Le catalogue et les fichiers sont recalculés au plus une fois par minute (comme `EditorialVoirSource`), en mémoire ; l'`ETag` est l'empreinte du fichier.
- **Licence** : tant que la propriétaire ne l'a pas choisie (question posée au chef d'équipe le 8 novembre 2026), `license` vaut `{"status":"a-decider","text":"Licence en cours de choix : réutilisation à demander à PPF."}` et la page `/donnees` le dit ; aucune donnée réelle n'est publiée avant ce choix.
- **Registre des jeux** : interface `OpenDataset` (identifiant, titre, espace, schéma, `rows(catalogue)`), sur le modèle des files du back-office ; chaque pôle enregistre les siens dans son propre fichier. « Voir » fournit les premiers ; les autres pôles ajoutent les leurs (pétitions et compteurs de « Décider », fiches de « Comprendre », mobilisations d'« Agir » publiées par l'équipe…).

## 3. Premiers tableaux de « Voir »

| Jeu | Une ligne par | Colonnes principales |
|---|---|---|
| `voir-mesures` | valeur d'un indicateur (M2) | `indicator` (adresse), `indicator_title`, `unit`, `measure_id`, `date`, `value`, `part`, `occasion`, `place`, `reliability`, `source_kind`, `source_name`, `source_url`, `version` (identifiant de l'événement) |
| `voir-partis-aides` | année d'aide publique d'un parti (VO-30) | `party`, `party_name`, `year`, `amount_eur`, `decree`, `source_url`, `version` |
| `voir-partis-sieges` | nombre de sièges d'un parti dans une chambre | `party`, `party_name`, `chamber`, `seats`, `date`, `source_kind`, `source_name`, `source_url`, `version` |

Pas de tableau du journal des corrections en version 1 : ses titres nomment des personnes (point 4 ci-dessus) ; le journal reste sur le site (`/voir/corrections`). Les chiffres du Parlement (votes, scrutins) sont déjà publiés en paquets signés par la clé d'import (D-064) : plus tard, le catalogue les annoncera comme jeux externes, sans les dupliquer.

## 4. Page « Données ouvertes » du site (étape A)

`/donnees`, lisible sans JavaScript : à quoi servent ces fichiers, la licence, la liste des jeux (titre, description, lignes, taille, liens JSON, CSV et schéma), comment vérifier un contenu (renvoi vers `/verifier`), la date de génération ; liée en bas de chaque page à côté de « Signaler une faille ».

## 5. Citer ce contenu (S-144, étape B)

Sous chaque contenu du site et de l'application, un bloc « Citer ce contenu » donne une référence prête à copier :

> Politisation Populaire Française, « Titre », publié le 10 décembre 2025, mis à jour le 6 novembre 2026, version `dc90c95f0994`, https://politisation-populaire-francaise.org/voir/faits/affaire-alex-relaxe, empreinte SHA-256 `dc90c95f…b5c962`, consulté le 8 novembre 2026.

- Sur le site : le texte dans un bloc `<details>` (sélectionnable, sans script) et la même référence en CSL-JSON (format des logiciels de bibliographie : Zotero) en lien.
- Dans l'application : bouton « Copier la référence » (presse-papiers), et « Partager ».
- Fonction commune `Citation.of(article, url, consultedOn)` dans `shared/core`, testée une fois pour le site et l'application.

## 6. Vérifier un contenu PPF (S-141, étape C)

- **Code de vérification** : les 16 premiers caractères de l'identifiant de la version (`dc90c95f09947507`, 64 bits), imprimés sur les images de partage avec l'adresse courte `…/v/<code>` ; le code sert à **retrouver** l'original, jamais à le prouver : la preuve est la signature de l'original retrouvé, que la personne compare à ce qu'elle a reçu.
- **Sur le site** : `/verifier` (formulaire envoyé en GET pour un code, un identifiant ou un lien ; en POST pour un fichier ou un JSON collé, jamais gardé) et `/v/<code>`. Réponses : « Authentique : publié par PPF le …, version actuelle » ; « Authentique, mais une version plus récente existe » (lien) ; « Signature valide, mais la clé n'est pas une clé de PPF » ; « Modifié ou abîmé : la signature ne correspond pas » ; « Inconnu de PPF ». Rien n'est journalisé.
- **Dans l'application, même hors ligne** : écran « Vérifier un contenu » (coller un lien, un code ou un JSON, ouvrir un fichier) qui vérifie avec les clés de confiance de l'appareil (`EditorialTrust`, puis `PlatformTrust`) et la copie locale ; en ligne, il demande en plus au serveur si une version plus récente existe.
- Vérification commune `ContentCheck.of(event, trust, latest)` dans `shared/core`.

## 7. Étapes

| Étape | Contenu | Tests |
|---|---|---|
| A (**faite** le 8 novembre 2026, D-101, branche `feature/voir-export`) | `OpenDataset`, catalogue, `contenus.json`, trois tableaux de « Voir » en JSON et CSV, schémas, page `/donnees`, `FORMATS.md` section 5 bis | contenus des seules clés éditoriales ; dernière version par adresse ; CSV conforme (guillemets, retours à la ligne, virgules) ; chaque ligne avec sa source et sa version ; schémas valides et lignes conformes ; `ETag` et `304` ; page sans script |
| B | `Citation`, bloc du site (Voir et Comprendre), bouton de l'application | référence identique sur le site et l'application ; échappement ; CSL-JSON valide |
| C | `ContentCheck`, `/verifier`, `/v/<code>`, écran de l'application | les cinq réponses ; hors ligne ; aucun journal ; JSON abîmé, trop gros |

## 8. Questions ouvertes

1. **Propriétaire, par le chef d'équipe** : licence des contenus et des données publiés par PPF (cahier des charges 3.8). Options et recommandation envoyées au chef d'équipe le 8 novembre 2026.
2. **Avocat, avant l'ouverture publique** : tableaux des personnes, des affaires et des peines (section 1, point 4).
3. **Autres pôles** : leurs jeux de données, à enregistrer par `OpenDataset` après l'étape A.
