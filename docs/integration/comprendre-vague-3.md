# « Comprendre », vague 3 : contrat commun

Contrat écrit le 7 novembre 2026 par la session « Comprendre » avant de lancer le travail en parallèle. Chaque bloc s'y tient ; ce qui n'est pas écrit ici se décide dans le bloc et se documente dans `docs/integration/<bloc>.md`. La session principale fusionne et met à jour CHANGELOG, ROADMAP, DECISIONS, FORMATS, REGISTRE_DONNEES, THREAT_MODEL et GUIDE_PROPRIETAIRE.

Demandes de la propriétaire (7 novembre 2026) :
1. Annuaire des élus : option A validée (« Pour les élus oui ça serait parfait »), entrée « Annuaire officiel des élus » du brainstorming du cahier des charges : importer le Répertoire national des élus sur notre serveur, servi par département, trouvé hors ligne sur l'appareil à partir d'une commune qui ne quitte jamais l'appareil ; liens vers les annuaires officiels en attendant (option C).
2. Glossaire : consultable en entier, ET les mots compliqués de **chaque** contenu soulignés en pointillés discrets avec un petit « ? » dans un rond ; un geste ouvre une infobulle avec la définition rapide et un bouton « En savoir plus » vers la définition détaillée.
3. Davantage de contenus, en commençant par les sujets les plus importants.

## Numéros réservés

- Décisions : D-047 (annuaire des élus), D-048 (glossaire), D-049 (contenus de la vague 3), D-050 en réserve.
- Base locale : `migrations/10.sqm` (schéma 10 vers 11), réservée au bloc **elus-app**. Aucun autre bloc ne touche au schéma local.
- Serveur : Flyway `V6__elus.sql`, réservé au bloc **elus-serveur**.

## Règles pour tous les blocs

- CLAUDE.md s'applique en entier : aucun tiers, aucun traceur, iOS compatible (rien de propre à la JVM ou à Android dans le code commun), écrans adaptatifs, WCAG 2.2 AA, textes externalisés en français, modes clair et sombre, composants de `shared/ui/.../components` et jetons de `PpfTheme` (aucune couleur ni police en dur), pas de tiret cadratin.
- Code, identifiants, commentaires et commits en anglais (Conventional Commits, le crochet `commit-msg` refuse `merge:`), docs en français.
- Tests écrits avec le code. Ne lance que les tâches Gradle de ton module (`:server:test`, `:shared:core:jvmTest`, `:shared:ui:jvmTest`, `:tools:content-signer:run`), jamais `gradlew build` complet : plusieurs blocs compilent en même temps sur la même machine.
- Gradle depuis PowerShell : `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'` puis `cmd /c "<worktree>\gradlew.bat -p <worktree> <tâches> > <scratchpad>\log.txt 2>&1"` et lis le fichier. Un démon qui plante (EXCEPTION_ACCESS_VIOLATION) : relance.
- Messages de commit : écris-les dans un fichier puis `git commit -F <fichier>` (les guillemets cassent les arguments sous PowerShell). Termine chaque message par la ligne `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Ne touche pas aux JSON générés de `shared/ui/src/commonMain/composeResources/files/contents/` sauf le bloc qui en a besoin pour ses tests ; la session principale les régénère après fusion.

## 1. Annuaire des élus (D-047)

### 1.1 Sources (à vérifier par **elus-serveur**, avec l'adresse exacte et la licence relevées sur la page officielle)

- **Répertoire national des élus (RNE)**, ministère de l'Intérieur, data.gouv.fr (jeu `5c34c4d1634f4173183a64f1`) : un fichier par mandat. On importe : conseillers municipaux (avec maires et adjoints), conseillers communautaires, conseillers départementaux, conseillers régionaux, membres des assemblées des collectivités à statut particulier si le fichier est simple à lire, députés, sénateurs. Les représentants au Parlement européen (circonscription nationale) vont dans un paquet national.
- **Codes postaux** : base officielle des codes postaux (La Poste, data.gouv.fr, jeu `545b55e1c751df52de9b6045`), si sa licence permet la réutilisation (à vérifier). Sinon, l'appareil demande le département puis le nom de la commune.
- **Commune vers circonscription législative et vers canton** : table officielle à trouver (INSEE, Code officiel géographique, ou ministère de l'Intérieur). Une commune partagée entre plusieurs circonscriptions ou cantons garde la liste de tous. Sans table fiable, la commune n'a pas de circonscription ni de canton et l'application affiche tous les députés ou conseillers départementaux du département avec leur territoire.

### 1.2 Minimisation

On garde seulement : nom, prénom, mandat, fonction (libellé du RNE : « Maire », « 1er adjoint », « Président du conseil départemental »…), territoire, date de début du mandat, date de début de la fonction. **Jamais** : date de naissance, sexe, catégorie socioprofessionnelle, nationalité, aucune autre colonne. Les libellés de l'application sont neutres (« Membre de l'Assemblée nationale », « Membre du conseil municipal ») pour ne pas avoir besoin du sexe.

### 1.3 Données fictives par défaut

`PPF_RNE_SOURCE=example` est la valeur par défaut (développement, tests, préproduction) : le serveur charge un jeu **fictif** embarqué dans ses ressources, au format des fichiers du RNE, où toute personne s'appelle `[EXEMPLE] Prénom Nom` (prénoms et noms visiblement fictifs) ; la géographie est réelle (communes, codes INSEE et postaux, départements de Lyon (69), Lille (59), Marseille (13), Rennes (35) et Paris (75), quelques communes chacun). `PPF_RNE_SOURCE=official` (production seulement) télécharge le vrai répertoire. Aucune donnée réelle d'élu dans le dépôt, les tests ou la préproduction.

### 1.4 API du serveur (lecture seule, sans compte, compressée)

`GET /api/v1/elus` : l'index.

```json
{
  "format": 1,
  "example": true,
  "source": {
    "name": "Répertoire national des élus (ministère de l'Intérieur)",
    "url": "https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/",
    "licence": "Licence Ouverte 2.0",
    "updatedOn": "2026-10-01"
  },
  "importedOn": "2026-11-07",
  "packages": [
    { "code": "69", "name": "Rhône", "sha256": "<64 hex>", "bytes": 123456, "postalCodes": ["69001", "69002"] },
    { "code": "FR", "name": "France", "sha256": "<64 hex>", "bytes": 4567, "postalCodes": [] }
  ]
}
```

`GET /api/v1/elus/{code}` : le paquet d'un département (`01` à `95`, `2A`, `2B`, `971` à `976`, et les collectivités d'outre-mer présentes dans le RNE) ou `FR` (paquet national : membres du Parlement européen élus en France). `404 unknown_package` sinon. En-tête `ETag` = sha256 ; `If-None-Match` répond 304. Le sha256 est celui des octets exacts du corps JSON (UTF-8, non compressé). L'appareil refuse un paquet dont l'empreinte ne correspond pas à l'index.

```json
{
  "format": 1,
  "example": true,
  "code": "69",
  "name": "Rhône",
  "region": { "code": "84", "name": "Auvergne-Rhône-Alpes" },
  "sourceUpdatedOn": "2026-10-01",
  "communes": [
    { "code": "69123", "name": "Lyon", "postalCodes": ["69001", "69002"], "epci": "200046977", "cantons": [], "circonscriptions": ["6901", "6902"] }
  ],
  "epcis": [ { "code": "200046977", "name": "Métropole de Lyon" } ],
  "cantons": [ { "code": "6901", "name": "Canton fictif" } ],
  "circonscriptions": [ { "code": "6901", "name": "1re circonscription du Rhône" } ],
  "officials": [
    { "mandate": "municipal", "territory": "69123", "name": "[EXEMPLE] Prénom Nom", "function": "Maire", "since": "2026-03-22", "functionSince": "2026-03-28" },
    { "mandate": "communautaire", "territory": "200046977", "commune": "69123", "name": "…", "function": null, "since": "…", "functionSince": null }
  ]
}
```

Valeurs de `mandate` et territoire correspondant : `municipal` (code INSEE de la commune), `communautaire` (SIREN de l'intercommunalité, avec `commune` = commune représentée quand le RNE la donne), `departemental` (code du canton), `regional` (code de la région ; le paquet d'un département contient les conseillers régionaux de sa section départementale, ou à défaut de toute la région), `collectivite` (assemblée d'une collectivité à statut particulier, code de la collectivité), `depute` (code de la circonscription), `senateur` (code du département), `europeen` (`FR`, paquet national seulement). `function` et `functionSince` valent `null` quand le RNE n'en donne pas. Dates au format `AAAA-MM-JJ`. Listes triées (communes par nom, élus par mandat, territoire, ordre du tableau quand le RNE le donne, sinon nom) pour que deux imports identiques donnent les mêmes octets.

### 1.5 Mise à jour et sécurité côté serveur

- Import au démarrage s'il n'y a rien, puis tous les `PPF_RNE_REFRESH_DAYS` jours (7 par défaut) ; le dernier import réussi reste servi tant qu'un nouvel import échoue ; un import dont le nombre d'élus baisse de plus de 20 % par rapport au précédent est refusé (fichier tronqué).
- Hôtes contactés en mode `official` seulement : ceux de data.gouv.fr (à lister dans `docs/integration/elus-serveur.md` et dans la liste blanche de `tools/check-no-trackers.sh` si elle les bloque).
- Aucune donnée personnelle dans les journaux (seulement des nombres et des codes de département). Pas d'adresse IP, comme partout.
- Authenticité : HTTPS et empreinte de l'index pour la phase 1 ; la signature des paquets par le service de signature viendra avec lui (à noter dans THREAT_MODEL).

### 1.6 Sur l'appareil (bloc **elus-app**)

- `core/elus/` : modèles (sérialisation kotlinx), lecture stricte et tolérante (un paquet illisible est ignoré, jamais un plantage), `ElusFinder` qui, pour une commune, rend les élus par niveau de décision (`DecisionLevel` de `core/comprendre`) : commune (maire, adjoints dans l'ordre, puis membres du conseil), intercommunalité (nom, président, représentants de la commune), département (conseillers du ou des cantons de la commune, président du conseil départemental), région (président du conseil régional, conseillers de la section), État (députés de la ou des circonscriptions de la commune, sénateurs du département), Union européenne (membres élus en France, paquet national facultatif).
- Lecture du serveur : nouvelle méthode de `RemoteEvents` avec une implémentation par défaut qui répond « indisponible » (comme `groups`), implémentée par `ServerPublicationPort` ; le client vérifie l'empreinte.
- Base locale `migrations/10.sqm` : la commune choisie (code INSEE et département, une seule ligne, jamais envoyée) et les paquets téléchargés (code, sha256, JSON, date de la source, date du téléchargement).
- **Choix de la commune** : l'utilisateur tape son code postal ; l'appareil trouve le département dans l'index (déjà téléchargé, il ne contient aucune donnée personnelle), télécharge le paquet de ce département après avoir montré sa taille (avec confirmation en mode économie de données), puis propose les communes de ce code postal. Phrase visible : la commune reste sur l'appareil ; le serveur sait seulement qu'un appareil a téléchargé la liste d'un département et ne garde pas son adresse IP. Rien n'est téléchargé sans geste de l'utilisateur. Boutons « Changer de commune » et « Oublier ma commune » (efface la commune et les paquets).
- **Encart « Qui décide ? Les élus concernés »** (`ElusBox` de `ui/comprendre/ComprendreDetails.kt`) : sous chaque niveau, les élus de la commune choisie quand c'est connu ; sinon un bouton « Indiquer ma commune ». La phrase actuelle `comprendre_elus_local_pending` disparaît quand la commune est connue.
- **Page « Mes élus »** dans « Comprendre » (cahier fonctionnel M13.1) : tous les élus de la commune par niveau, la source et sa date, un bandeau « Données fictives [EXEMPLE] » quand `example` est vrai.
- **Option C en attendant** : quand il n'y a pas de serveur (`remote == null`, l'APK actuel) ou qu'il ne répond pas, liens vers les annuaires officiels (Assemblée nationale, Sénat, Annuaire de l'administration pour les mairies ; adresses exactes à vérifier), ouverts seulement après l'avertissement « Vous quittez PPF » existant (`LeaveDialog` de l'espace Agir, à rendre commun s'il le faut).

## 2. Glossaire (D-048)

### 2.1 Format d'une entrée (`content/glossaire/<slug>.md`)

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

- `title` : le terme tel qu'il s'affiche, précédé de `[EXEMPLE] ` comme tout contenu pour l'instant.
- `summary` : **la définition rapide de l'infobulle**, une ou deux phrases, 200 caractères au plus, compréhensible sans rien d'autre.
- `variants` : les formes repérées dans les textes, séparées par des virgules (singulier, pluriel, sigle et forme longue : `49.3, 49-3, article 49.3, article 49 alinéa 3`). Au moins une. Une forme n'appartient qu'à une entrée.
- `see_also` (facultatif) : slugs d'autres entrées du glossaire. `related` (facultatif) : contenus liés, `parcours:<slug>`, `fiche:<slug>`, `decryptage:<slug>`, `qui-decide:<slug>`, `histoire:<slug>`.
- `auto: non` (facultatif) : l'entrée n'est jamais repérée automatiquement dans les textes (mots trop courants), seulement listée dans le glossaire.
- Le corps se termine par `## Sources` (sources primaires : Constitution et codes sur Légifrance, conseil-constitutionnel.fr, assemblee-nationale.fr, senat.fr, vie-publique.fr, service-public.fr, europa.eu, insee.fr).

### 2.2 Événement

Article NIP-23 (kind 30023) comme les autres contenus, `ppf-type` = `glossaire` (nouvelle valeur de `ArticleType`), `title` = terme, `summary` = définition rapide, une étiquette `["ppf-variant", "<forme>"]` par forme, `["ppf-auto", "non"]` si besoin, `["a", "30023:<clé>:<slug>", "", "voir-aussi"]` pour `see_also` et `["a", "30023:<clé>:<slug>", "", "lie"]` pour `related`. Fichier `glossaire.json` produit par `tools/content-signer`, qui refuse : définition absente ou trop longue, aucune forme, forme en double entre deux entrées, `## Sources` absent, lien vers un slug inconnu.

### 2.3 Repérage dans les textes (code commun, testé)

- Comparaison sans tenir compte des majuscules ; apostrophes droite et courbe équivalentes ; accents respectés.
- Limites de mot : le caractère avant et après la forme n'est ni une lettre ni un chiffre (« loi » n'est pas repéré dans « emploi »).
- À une même position, la forme la plus longue gagne (« motion de censure » avant « motion ») ; pas de chevauchement.
- **Première occurrence seulement** de chaque entrée dans un même contenu (un appel de `MarkdownText`, ou une portée commune quand un écran affiche un contenu en plusieurs morceaux). Jamais dans les titres, les liens, la section des sources, ni dans la page de l'entrée elle-même pour son propre terme.
- S'applique à **tous** les contenus rendus par `MarkdownText` (Comprendre, Voir, Agir, revendications, fiches), sans balisage par les rédacteurs, grâce à un `CompositionLocal` fourni par le shell. Sans glossaire fourni (tests, aperçus), le texte s'affiche comme avant.

### 2.4 Interface

- **Dans le texte** : soulignement en pointillés discret (couleur de la marque atténuée, dessiné sous la forme, sur plusieurs lignes si besoin), suivi d'un petit « ? » dans un rond (icône Lucide `circle-question-mark` ou équivalent ajoutée à `tools/icons/lucide/`, régénérée par `python -I tools/icons/generate_icons.py`), à la taille du texte. Toucher le mot ou le « ? » ouvre l'infobulle. Clavier : atteignable par Tab, ouvert par Entrée. Lecteur d'écran : annoncé comme un lien « <terme>, définition ».
- **Infobulle** : ancrée sous le mot (au-dessus s'il manque de place), largeur 320 dp au plus : le terme, la définition rapide, le badge [EXEMPLE], « En savoir plus », un bouton fermer. Se ferme en touchant ailleurs, par Échap et par le retour du système.
- **« En savoir plus »** : la page de l'entrée s'ouvre par-dessus le contenu en cours (plein écran sur téléphone, fenêtre centrée sur tablette et ordinateur), sans perdre la lecture : terme, définition rapide, explication détaillée (elle-même repérée), « Voir aussi », contenus liés, sources, et « Ouvrir le glossaire complet ».
- **Glossaire complet** : une entrée « Glossaire » dans l'accueil de « Comprendre » ; recherche (sans tenir compte des accents ni des majuscules), entrées par ordre alphabétique avec les lettres en intertitres, liste et détail côte à côte sur grand écran comme les autres listes de « Comprendre ».

## 3. Contenus de la vague 3 (D-049)

Tous les contenus : titre précédé de `[EXEMPLE] `, `origin: ai-assisted`, sources primaires citées et vérifiées (CLAUDE.md, section 6 : toute règle juridique vérifiée dans le droit en vigueur), ton neutre et pluraliste (objections et réponses sourcées quand le sujet est débattu), aucun appel à la violence, phrases courtes, aucune personne réelle nommée en dehors de fonctions officielles, pas de tiret cadratin. Vérifier qu'un sujet n'existe pas déjà dans `content/` avant de l'écrire.

### 3.1 Glossaire (bloc **contenus-glossaire**) : 60 entrées environ, par ordre de priorité

Institutions et procédures : Constitution, Président de la République, Premier ministre, gouvernement, Parlement, Assemblée nationale, Sénat, député, sénateur, projet de loi, proposition de loi, amendement, navette parlementaire, commission mixte paritaire, 49.3, motion de censure, dissolution, ordonnance, décret, loi organique, loi de finances, loi de financement de la sécurité sociale, Conseil constitutionnel, question prioritaire de constitutionnalité, Conseil d'État, référendum, référendum d'initiative partagée, article 16, état d'urgence, cohabitation.
Élections : suffrage universel, scrutin majoritaire, scrutin proportionnel, circonscription, parrainage, liste électorale, procuration, vote blanc, abstention, suffrages exprimés, compte de campagne.
Territoires : collectivité territoriale, commune, intercommunalité, département, région, préfet, décentralisation.
Social et droits : grève, préavis de grève, syndicat, convention collective, cotisations sociales, garde à vue, Défenseur des droits, pétition, CESE.
Europe : Union européenne, Commission européenne, Parlement européen, Conseil de l'Union européenne, directive, règlement européen.

### 3.2 Parcours, « Qui décide de ça ? » et fiches (bloc **contenus-parcours**), par ordre de priorité

Le mouvement social en cours et la présidentielle d'avril 2027 guident l'ordre.
1. Parcours « S'inscrire sur les listes électorales et voter (procuration comprise) », avec les dates limites pour avril 2027 vérifiées sur service-public.fr.
2. Parcours « Le droit de grève » (privé et public, préavis, service minimum, ce qui est interdit).
3. Parcours « Le Conseil constitutionnel » (qui le saisit, QPC, ce qu'il peut annuler).
4. Parcours « Qui fait quoi : la séparation des pouvoirs » (Président, gouvernement, Parlement, juges).
5. Parcours « L'Union européenne : qui décide quoi ».
6. Parcours « Commune, département, région : qui s'occupe de quoi ».
7. Parcours « Se présenter à une élection » (conditions, parrainages pour la présidentielle, comptes de campagne).
8. Parcours « Les pétitions officielles » (Assemblée nationale, Sénat, CESE, collectivités).
9. « Qui décide de ça ? » : logement et loyers, hôpital et médecins, eau du robinet, salaires et SMIC, impôts locaux, retraites, ramassage des déchets, police municipale et nationale.
10. Fiches pratiques : « Faire grève : vos droits », « Voter par procuration ».

Formats : `content/README.md` (parcours, « Qui décide de ça ? », fiches), modèles dans `content/parcours/` et `content/qui-decide/`. Le bloc lance `gradlew :tools:content-signer:run` une seule fois à la fin pour vérifier ses fichiers (l'outil refuse un contenu mal formé), puis restaure les JSON générés qu'il n'a pas à changer.
