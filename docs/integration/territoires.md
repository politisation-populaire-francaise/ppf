# Note de conception : le référentiel des territoires (D-090, pôle « Comprendre »)

Rédigée le 7 novembre 2026 pour le chef d'équipe, le pôle « Décider » et la session « Comprendre ». **Phase 1 seulement : recherche et conception.** Aucun code de production n'est écrit ; rien ici n'est encore une spécification tant que la section 12 n'est pas validée. Branche de travail partie de `main` au commit `e0abc2b`.

Besoin (demande du pôle « Décider ») : un module partagé `org.politisationpopulairefrancaise.core.territoires` (code commun, compatible iOS, lecture seule pour les autres pôles) qui donne pour chaque territoire son code officiel, son nom, son type, ses parents, sa population municipale (millésime, source), ses électeurs inscrits (date, source), l'indicateur « compte pour la règle des 30 » des pétitions de l'Assemblée nationale, la recherche par nom et par code postal, et le hors ligne au moins pour les départements et les régions. Usages de « Décider » : objectifs de signatures proportionnels à la population ; seuils des pétitions locales.

## État (phase 2, 7 novembre 2026, au soir)

Conception validée par le chef d'équipe (« Décider » accepte l'API sans correction) et **codée** : `shared/core/.../core/territoires/` (`TerritoiresFormats.kt`, `Territoires.kt`, `TerritoryShares.kt`, `TerritoiresStore.kt`, `Territoires.sq`, `migrations/12.sqm`), `RemoteEvents.fetchTerritoires`, `shared/ui/.../ui/territoires/TerritoiresResources.kt` (`territoiresFor(dependencies)`, `bundledNationalTerritoires()`), copie embarquée `composeResources/files/territoires/national.json`, serveur `server/.../server/territoires/` et `V7__territoires.sql`. Formats : `docs/FORMATS.md`, section 4 nonies. Décision : D-090.

Écarts à la conception (sections 3 et suivantes), tous rétrocompatibles avec ce que « Décider » a accepté :
- `TerritoiresStorage` est synchrone (comme `ElusStorage`) : `load`, `save`, `codes`, `delete`, `deleteAll`. Ajouts : `planPackages(codes)` (télécharger les communes d'un département, ou l'index `communes`), `storedPackage`, `storedCommunesIndex`, `storedPackageCodes`, `hasServer` ; `Territory` gagne `collectivites` (d'un département), `members` et `nature` (d'un EPCI) ; `NationalTerritories` gagne `all` et `entry(code)` ; `TerritorySource` gagne `kind` (`SourceKind`) ; fabriques `TerritoryCode.of`, `collectivite`, `outreMer`, `arrondissement`. `forget()` n'est pas suspendue.
- Recherche : une requête de cinq chiffres est essayée comme code de commune (anciens codes compris) avant d'être lue comme code postal.
- Numéros pris à l'entrée dans `main` : `12.sqm` (schéma 13) et Flyway `V7` (suivants des plus hauts de `main`).
- Sources du serveur : `example` par défaut ; `official` en production, ou en préproduction avec `PPF_TERRITOIRES_REAL_IN_PREPRODUCTION=true`. Le jeu d'exemple contient de vrais chiffres publics (Rhône, Paris, Corse-du-Sud, Martinique, Saint-Pierre-et-Miquelon, Saint-Barthélemy, Saint-Martin, et tout le niveau national), marqué `example` et `partial`. Il a été construit le 7 novembre 2026 à partir des fichiers publics du Code officiel géographique 2026 et de La Poste et des réponses des API Melodi et Métadonnées de l'Insee.
- La copie embarquée est le niveau national du jeu d'exemple (chiffres nationaux complets et réels, index des paquets du jeu d'exemple). `TerritoiresBundleTest` vérifie qu'elle reste identique ; pour la régénérer, l'effacer et relancer ce test.
- Compléments outre-mer (`server/src/main/resources/territoires/outre-mer.csv`) : seuls les chiffres vérifiés sont entrés (Saint-Pierre-et-Miquelon, Saint-Barthélemy, Saint-Martin, millésime 2023). **Mayotte** (recensement de 2017, puis décret attendu fin 2026), la **Polynésie française** (2022), **Wallis-et-Futuna** (2023) et la **Nouvelle-Calédonie** (2025) n'ont pas encore de population : les pages de l'Insee consultées ne donnaient pas les totaux. `TerritoryShares` les range dans `missing`.

Tests : `gradlew :shared:core:jvmTest` (488 tests, dont `TerritoiresFormatsTest`, `TerritoiresTest`, `DatabaseMigrationTest`) et `gradlew :server:test --tests "*.territoires.*"` (`TerritoiresBuilderTest`, `OfficialTerritoiresSourceTest` avec un Insee simulé, `TerritoiresApiTest`, `TerritoiresBundleTest`) : verts.

## 0. En bref

- **Sources retenues** : populations de référence 2023 de l'Insee (en vigueur depuis le 1er janvier 2026) et électeurs inscrits du répertoire électoral unique (Insee, extraction du 11 février 2026 pour les municipales), tous deux par l'API Melodi de l'Insee, ouverte et sans clé, sous Licence Ouverte ; composition des intercommunalités par l'API Métadonnées géographiques de l'Insee ; Code officiel géographique 2026 et codes postaux de La Poste, déjà lus par l'annuaire des élus ; une petite table de compléments pour Mayotte et l'outre-mer, absents de Melodi.
- **Règle des 30 vérifiée** : Règlement de l'Assemblée nationale, **article 148, alinéa 6** (et non l'article 151), « plus de 500 000 pétitionnaires domiciliés dans trente départements ou collectivités d'outre-mer au moins ».
- **Transport recommandé** : un **paquet national léger** (régions, départements, collectivités, outre-mer, avec l'index des paquets ; environ 120 ko, **embarqué dans l'application**) et des **paquets par département séparés de ceux de l'annuaire des élus** (environ 55 ko en moyenne), mêmes codes de paquets et mêmes règles HTTP que l'annuaire.
- **`ppf-territory`** reste un libellé libre ; une nouvelle étiquette facultative **`ppf-territory-code`** porte le code du référentiel (`com:69123`, `dep:69`, `reg:84`…). Rien ne casse ; seules les revendications demandent un changement du serveur.
- **Migrations** : locale nécessaire (oui) ; Flyway nécessaire (oui, pour le mode « official » du serveur). Numéros à demander au chef d'équipe.
- **Jeu de développement** : réel (chiffres publics et agrégés, aucune donnée personnelle), à faire valider.

## 1. Sources vérifiées le 7 novembre 2026

Consultées sur les sites officiels, par lecture des pages et par des appels directs aux API ouvertes de l'Insee (réponses JSON lues, aucun fichier gardé dans le dépôt). Aucune de ces données n'est personnelle : ce sont des comptes agrégés publiés par l'Insee.

### 1.1 Population : « populations de référence » de l'Insee

- **Nom** : depuis les populations 2022, l'Insee dit « populations de référence » au lieu de « populations légales » (recommandation de l'Autorité de la statistique publique ; le cadre juridique reste la loi n° 2002-276 du 27 février 2002 et le décret n° 2003-485 du 5 juin 2003). Page : https://www.insee.fr/fr/statistiques/8681011.
- **Dernier millésime** : **2023**, publié le 29 décembre 2025, **en vigueur depuis le 1er janvier 2026**, authentifié par le **décret n° 2025-1362 du 26 décembre 2025**. Date de référence : 1er janvier 2023 ; **géographie : communes au 1er janvier 2025**. Le millésime 2024 devrait paraître fin décembre 2026 et entrer en vigueur le 1er janvier 2027 (rythme annuel, à vérifier le moment venu).
- **Grandeurs** : population municipale (`PMUN`), population comptée à part (`PCAP`), population totale (`PTOT`). Pour un ensemble de communes, on additionne la **population municipale** ; la somme des populations totales compte deux fois certaines personnes (étudiants, par exemple) : le référentiel n'additionne donc jamais `PTOT`.
- **Licence** : Licence Ouverte 2.0 (jeu data.gouv.fr `67e34386efc54deb6b388bac`, licence `lov2` ; le catalogue de l'Insee, https://www.insee.fr/fr/information/8184173, met ses jeux « sous les termes de la licence » Licence Ouverte). Citer « Insee, populations de référence 2023 » et la date.
- **Format et adresse stable** : jeu `DS_POPULATIONS_REFERENCE` de l'API Melodi.
  - Catalogue : `https://api.insee.fr/melodi/catalog/DS_POPULATIONS_REFERENCE` (niveaux `COM`, `ARM`, `ARR`, `DEP`, `REG`, `FRANCE` ; France hors Mayotte ; 106 065 observations).
  - Données : `https://api.insee.fr/melodi/data/DS_POPULATIONS_REFERENCE?GEO=COM&POPREF_MEASURE=PMUN&maxResult=10000&page=1` puis les pages suivantes (champ `paging.next`, `paging.isLast`) : **34 858 communes en 4 pages**. Même chose avec `GEO=DEP`, `GEO=REG`, `GEO=ARM`, `POPREF_MEASURE=PTOT`. Codes géographiques préfixés par l'année de la géographie : `2025-COM-69123`, `2025-DEP-2A`. Valeur dans `measures.OBS_VALUE_NIVEAU.value`.
  - Fichier complet : CSV dans une archive zip de 985 ko, `https://api.insee.fr/melodi/file/DS_POPULATIONS_REFERENCE/DS_POPULATIONS_REFERENCE_2023_CSV_FR` ; son identifiant **porte le millésime**, à relire dans le catalogue chaque année.
  - Accès libre sans souscription, **30 requêtes par minute** (catalogue de l'Insee).
- **Chiffres relevés** (pour les tests) : Lyon (`69123`) municipale 519 127, totale 525 314 ; Rhône (`69`) municipale 1 914 667 ; Corse-du-Sud (`2A`) 168 306 ; région Corse (`94`) 355 486 ; Paris 1er (`75101`) 15 114 ; France hors Mayotte 68 094 280.
- **Absents de Melodi** (vérifié : réponses vides pour `2025-DEP-976`, `2025-COM-97501`, `2025-COMER-977`) :
  - **Mayotte** : la population de référence en vigueur reste celle du recensement exhaustif de 2017 (décret n° 2017-1688 du 14 décembre 2017). Le recensement de 2025-2026 donne 323 153 habitants au 1er janvier 2026 (https://www.insee.fr/fr/information/9022279) ; le décret qui l'authentifiera doit paraître fin 2026.
  - **Saint-Pierre-et-Miquelon, Saint-Barthélemy, Saint-Martin** : millésime 2023, en vigueur au 1er janvier 2026, 5 790, 10 660 et 31 160 habitants (https://www.insee.fr/fr/statistiques/8680667).
  - **Nouvelle-Calédonie, Polynésie française, Wallis-et-Futuna** : recensements généraux tous les cinq ans, exploités par l'institut local (derniers : 2025, 2022, 2023 ; https://www.insee.fr/fr/information/2409261).
  - Ces chiffres tiennent dans une **table de compléments** d'une trentaine de lignes, tenue à la main dans le dépôt du serveur, chaque ligne avec sa source et sa date (section 9.2).

### 1.2 Électeurs inscrits : répertoire électoral unique (Insee)

- **Jeu** : `DS_ELECTORAL`, « Caractéristiques du corps électoral », Insee, source répertoire électoral unique (REU). Catalogue : `https://api.insee.fr/melodi/catalog/DS_ELECTORAL` ; publié le 8 octobre 2024, modifié le 1er août 2026 ; France y compris collectivités d'outre-mer, **sauf Nouvelle-Calédonie** ; niveaux `COM`, `ARM`, `DEP`, `REG`, `COMER`, `FRANCE` ; géographie au 1er janvier 2025.
- **Licence** : Licence Ouverte (catalogue de l'Insee, comme 1.1).
- **Dates** : une extraction à la clôture des inscriptions de chaque scrutin général : municipales 2020 (`2020-02-14`), départementales et régionales 2021 (`2021-05-24`), présidentielle 2022 (`2022-03-09`), européennes 2024 (`2024-05-08`), **municipales 2026 (`2026-02-11`, la plus récente)**. Prochaine attendue : présidentielle d'avril 2027. Fréquence : à chaque scrutin général, non annuelle.
- **Listes** : `REU_LIST_TYPE=LP` (liste principale, électeurs français, `NATIONALITY=250`) ; `LC` (listes complémentaires municipales des citoyens de l'Union européenne, total `NATIONALITY=150`, puis détail par pays). Mesure `REU_MEASURE=VOTERS` ; `SEX=_T`, `AGE=_T` pour les totaux.
- **Requête par commune** : `https://api.insee.fr/melodi/data/DS_ELECTORAL?GEO=COM&REU_LIST_TYPE=LP&REU_ELECTION_TYPE=MUN&TIME_PERIOD=2026-02-11&REU_MEASURE=VOTERS&SEX=_T&AGE=_T&maxResult=10000&page=1` : **34 921 communes en 4 pages** (Mayotte et les communes des collectivités d'outre-mer comprises). Fichier complet : zip de 1,7 Mo, `https://api.insee.fr/melodi/file/DS_ELECTORAL/DS_ELECTORAL_CSV_FR` (identifiant sans millésime, donc stable).
- **Chiffres relevés au 11 février 2026** : Lyon liste principale 318 916, complémentaire 2 445 ; Paris 1 379 084 et 21 162 ; Rhône 1 227 213 ; Auvergne-Rhône-Alpes 5 713 379 ; Mayotte 109 512 ; Saint-Pierre-et-Miquelon 5 059 ; Saint-Barthélemy 5 256 ; Saint-Martin 20 220 ; Wallis-et-Futuna 9 227 ; Polynésie française 215 471. Total national (« Chiffres-clés », https://www.insee.fr/fr/statistiques/2389209) : 50 187 093 électeurs inscrits sur une liste, toutes listes confondues.
- **Ce que le chiffre veut dire** : c'est une indication. Le seuil qui compte juridiquement est apprécié par le maire ou le président de l'assemblée sur les listes du moment ; l'application l'écrit (« environ N signatures, d'après les inscrits du 11 février 2026, Insee »).

### 1.3 Intercommunalités (EPCI à fiscalité propre)

- **Source retenue** : API Métadonnées géographiques de l'Insee, ouverte et sans clé (vérifié par appel direct : réponse 200 avec l'en-tête `Accept: application/json`, en-têtes `X-Rate-Limit-Limit: 10000`). Sa documentation (portail https://portail-api.insee.fr) reste à lire avant de coder.
  - Liste : `https://api.insee.fr/metadonnees/geo/intercommunalites?date=2026-01-01` : **1 253 entrées** (987 communautés de communes, 230 d'agglomération, 21 métropoles, 14 communautés urbaines, et la Métropole de Lyon, code `200046977`, classée « (Autre) Collectivité territoriale » : le référentiel la traite comme la collectivité `69M`, jamais comme un EPCI).
  - Composition : `https://api.insee.fr/metadonnees/geo/intercommunalite/{siren}/descendants?type=Commune&date=2026-01-01`. Exemple vérifié : la communauté d'agglomération Villefranche Beaujolais Saône (`200040590`) compte Jassans-Riottier (`01194`, Ain) et des communes du Rhône : un EPCI peut être à cheval sur deux départements.
  - Coût : 1 253 requêtes par import annuel (la liste, puis la composition des 1 252 EPCI), espacées (deux par seconde : une dizaine de minutes).
- **Repli** : l'archive `https://www.insee.fr/fr/statistiques/fichier/2510634/epci_au_01-01-2026.zip` (1 Mo, « Base des EPCI à fiscalité propre au 1er janvier 2026 », page mise à jour le 13 mars 2026 ; tableur, donc un petit lecteur de tableur à écrire avec le JDK seul, protégé contre les archives piégées et les entités XML externes) ; ou les tableaux de la DGCL (BANATIC, https://www.collectivites-locales.gouv.fr/etudes-et-statistiques/acces-aux-statistiques-par-thematique/perimetre-des-intercommunalites/liste-et-composition-des-epci-fiscalite-propre).
- **Non retenu** : le RNE, qu'utilise l'annuaire des élus pour l'intercommunalité d'une commune. Il est incomplet : 28 882 communes sur 34 963 ont une intercommunalité dans les paquets de l'annuaire (`elus-serveur.md`, section 5), alors que presque toutes les communes en ont une (sauf quatre îles, les 59 communes de la Métropole de Lyon et l'outre-mer du Pacifique). **À signaler à « Comprendre »** : le référentiel pourrait fournir la composition officielle à l'annuaire.
- **Population et inscrits d'un EPCI** : ni Melodi ni l'API ne les publient : **somme** des communes membres (population municipale, inscrits), marquée « calculée ».

### 1.4 Géographie : ce qui est déjà lu par l'annuaire des élus

Code officiel géographique 2026 (licence Ouverte, `elus-serveur.md`, section 1.3) et Base officielle des codes postaux de La Poste (Licence Ouverte 2.0, section 1.2), avec en plus :
- `v_ctcd_2026.csv` (même jeu data.gouv.fr `58c984b088ee386cdb1261f3`, ressource `2f280951-f937-46c3-a695-cefd72d1e687`) : les **100 collectivités ayant les compétences départementales** (colonnes `CTCD`, `REG`, `CHEFLIEU`, `TNCC`, `NCC`, `NCCENR`, `LIBELLE`) : 93 conseils départementaux (`01D`… dont `69D`, le Rhône hors Métropole) et 7 collectivités à statut particulier : `20R` Collectivité de Corse, `69M` Métropole de Lyon, `6AE` Collectivité européenne d'Alsace, `75C` Ville de Paris, `972R` Martinique, `973R` Guyane, `976R` Département-Région de Mayotte. La colonne `CTCD` de `v_commune_2026.csv` rattache chaque commune à l'une d'elles.
- `v_mvt_commune_2026.csv` : les fusions et changements de code survenus depuis la géographie 2025 de Melodi (section 2.5).

### 1.5 Droit

- **Pétitions de l'Assemblée nationale : règle des 30.** Règlement de l'Assemblée nationale, **article 148, alinéa 6** (édition consolidée de juillet 2025, PDF du site de l'Assemblée, https://www.assemblee-nationale.fr/dyn/17/divers/texte_reference/02_reglement_assemblee_nationale.pdf, lu le 7 novembre 2026 ; rédaction issue de l'article 53 de la résolution n° 281 du 4 juin 2019) : « Sur proposition du président de la commission compétente ou d'un président de groupe, un débat sur un rapport relatif à une pétition signée par plus de 500 000 pétitionnaires domiciliés dans trente départements ou collectivités d'outre-mer au moins peut être inscrit par la Conférence des présidents à l'ordre du jour. La condition de domiciliation prévue au présent alinéa est précisée par une décision du Bureau. » La décision du Bureau n'a pas été trouvée : non lue.
  - **À corriger chez « Décider »** (`docs/integration/decider-petitions-droit.md`, `PetitionRules`) : la règle est à l'article 148, alinéa 6, et non à l'article 151 (qui règle le déroulement du débat) ; les deux seuils sont **« plus de »** 100 000 (article 148, alinéa 1) et **« plus de »** 500 000 : 500 001 signatures au moins.
  - **Qui compte** : les 101 départements ; les collectivités d'outre-mer de l'article 74 de la Constitution, au nombre de cinq selon l'Insee (Saint-Pierre-et-Miquelon, Saint-Barthélemy, Saint-Martin, Wallis-et-Futuna, Polynésie française ; https://www.insee.fr/fr/metadonnees/definition/c1842). La **Nouvelle-Calédonie** relève du titre XIII de la Constitution et non de l'article 74 : **à confirmer**. Les Terres australes et antarctiques françaises et Clipperton, sans population domiciliée, et les Français établis hors de France ne comptent pas. Le Rhône compte une fois (la Métropole de Lyon est dans le département du Rhône) ; la Haute-Corse et la Corse-du-Sud, le Bas-Rhin et le Haut-Rhin comptent chacun.
- **Pétition locale.** Constitution, article 72-1, alinéa 1 : « La loi fixe les conditions dans lesquelles les électeurs de chaque collectivité territoriale peuvent, par l'exercice du droit de pétition, demander l'inscription à l'ordre du jour de l'assemblée délibérante de cette collectivité d'une question relevant de sa compétence. »
  - **CGCT, article L1112-16** (Légifrance, `LEGIARTI000045210334`, version en vigueur depuis le 23 février 2022, loi n° 2022-217 du 21 février 2022, article 14) : « un dixième des électeurs inscrits sur les listes électorales » dans une commune, « un vingtième des électeurs » dans les autres collectivités territoriales, peuvent demander l'inscription à l'ordre du jour de l'organisation d'une consultation ; un électeur ne signe qu'une demande **par trimestre** et par collectivité ; hors commune, les organisateurs remettent à l'exécutif une copie des listes électorales des communes des signataires ; le II permet de saisir l'assemblée pour qu'elle se prononce dans un sens déterminé, aux mêmes conditions. La décision reste à l'assemblée.
  - **CGCT, article L5211-49** (Légifrance, `LEGIARTI000045210340`, en vigueur depuis le 23 février 2022, même loi, article 15) : pour un EPCI, **un dixième** des électeurs inscrits sur les listes électorales des communes membres (et non plus un cinquième, ancienne rédaction encore citée par des sites secondaires) ; une demande par trimestre.
  - **Points de droit ouverts** (section 12) : les citoyens européens des listes complémentaires comptent-ils dans le dixième communal ? Paris (commune et collectivité à statut particulier) relève-t-il du dixième ou du vingtième ? Les collectivités d'outre-mer ont leurs propres lois organiques : le vingtième du CGCT ne s'y applique pas sans vérification.

## 2. Modèle de données

### 2.1 Types et codes

Le code d'un territoire est **le code officiel** (Code officiel géographique, ou SIREN pour un EPCI) précédé de son type : c'est aussi la valeur de l'étiquette `ppf-territory-code` (section 7).

| Type (`TerritoryType`) | Préfixe | Exemples | Nombre | Source du code |
|---|---|---|---|---|
| `FRANCE` | `fr` (seul) | `fr` | 1 | |
| `REGION` | `reg:` | `reg:84`, `reg:94` (Corse), `reg:06` (Mayotte) | 18 | COG `REG` |
| `DEPARTEMENT` | `dep:` | `dep:01`, `dep:2A`, `dep:69`, `dep:971` | 101 | COG `DEP` |
| `COLLECTIVITE` (compétences départementales, dont statut particulier) | `ctcd:` | `ctcd:01D`, `ctcd:69D`, `ctcd:69M`, `ctcd:20R`, `ctcd:75C`, `ctcd:6AE`, `ctcd:972R`, `ctcd:973R`, `ctcd:976R` | 100 | COG `CTCD` |
| `OUTRE_MER` (collectivités d'outre-mer et Nouvelle-Calédonie) | `comer:` | `comer:975`, `977`, `978`, `986`, `987`, `988` | 6 (avec population) | COG `COMER` |
| `EPCI` | `epci:` | `epci:200040590` | 1 252 | SIREN |
| `COMMUNE` | `com:` | `com:69123`, `com:2A004`, `com:97411`, `com:97502` | environ 34 900 | COG `COM`, `COM_COMER` |
| `ARRONDISSEMENT` (municipal) | `arm:` | `arm:75101`, `arm:69381`, `arm:13201` | 45 | COG `ARM` |

Le brief demandait un type « collectivité à statut particulier (Corse, Guyane, Martinique, Métropole de Lyon) » : c'est `COLLECTIVITE` avec `specialStatus = true` (code CTCD qui ne finit pas par `D`). Les garder dans le même type que les conseils départementaux simplifie « Décider » : une commune a toujours exactement une collectivité de niveau départemental (`ctcd`), ou une collectivité d'outre-mer.

**Correspondance avec les codes de l'annuaire des élus** (RNE, champ `collectivite` des paquets) : `69M` → `ctcd:69M` ; `94` → `ctcd:20R` ; `972` → `ctcd:972R` ; `973` → `ctcd:973R` ; `975`, `977`, `978`, `986`, `987`, `988` → `comer:…`.

### 2.2 Parents

Liste ordonnée, du plus proche au plus large, terminée par `fr` :

| Territoire | Parents |
|---|---|
| Commune ordinaire (Villefranche-sur-Saône) | `epci:200040590`, `ctcd:69D`, `dep:69`, `reg:84`, `fr` |
| Commune de la Métropole de Lyon (Lyon) | `ctcd:69M`, `dep:69`, `reg:84`, `fr` (aucun EPCI) |
| Paris | `epci:200054781` (Métropole du Grand Paris), `ctcd:75C`, `dep:75`, `reg:11`, `fr` |
| Ajaccio | `epci:…`, `ctcd:20R`, `dep:2A`, `reg:94`, `fr` |
| Fort-de-France | `epci:…`, `ctcd:972R`, `dep:972`, `reg:02`, `fr` |
| Saint-Pierre (`97502`) | `comer:975`, `fr` (ni département ni région) |
| Arrondissement (Lyon 1er) | `com:69123`, puis les parents de Lyon |
| EPCI | `fr` ; ses départements sont dans `departements` (un EPCI peut en toucher deux) |
| Collectivité | ses départements (`ctcd:6AE` : `dep:67`, `dep:68`), `reg:…`, `fr` |
| Département | `reg:…`, `fr` |
| Région | `fr` |

Une **région** dont les compétences sont exercées par une collectivité à statut particulier (`94` Corse → `20R`, `02` Martinique → `972R`, `03` Guyane → `973R`, `06` Mayotte → `976R`) porte `specialStatus = true` et le champ `heldBy` (le code de cette collectivité) : la pétition régionale s'adresse alors à elle.

### 2.3 Population et inscrits

- `Population` : municipale (toujours), totale (seulement quand la source la publie pour ce territoire, jamais pour une somme), source (nom, adresse, licence, millésime, date d'entrée en vigueur), `computed` (vrai pour une somme : EPCI, `ctcd:69D`, `ctcd:20R`, commune fusionnée depuis la géographie de la source).
- `Electorate` : inscrits sur la liste principale, inscrits sur les listes complémentaires municipales (ou `null` si inconnu), source (date d'extraction), `computed`.
- Communes, départements, régions, France, collectivités d'outre-mer : **publiés** par l'Insee. EPCI et collectivités : **sommes** des communes (une collectivité qui couvre exactement un département, comme `01D`, reprend le chiffre publié du département ; `69D` = Rhône moins Métropole de Lyon ; `6AE` = Bas-Rhin plus Haut-Rhin ; `20R` = 2A plus 2B, égal au chiffre publié de la région 94, ce que le serveur vérifie).
- Nouvelle-Calédonie : population (recensement local) mais **aucun inscrit** (`electorate = null`).
- France : chiffres publiés par l'Insee, mais sur deux périmètres différents (section 5.1) ; jamais utilisés comme dénominateur d'un objectif.

### 2.4 Règle des 30

`spread: SpreadRule?` sur les départements et les collectivités d'outre-mer seulement (`null` ailleurs) : `COUNTS` pour les 101 départements et les cinq collectivités de l'article 74, `TO_CONFIRM` pour la Nouvelle-Calédonie, `EXCLUDED` sinon. La liste est écrite dans le code du serveur, avec la référence de l'article et la date de vérification, et transmise dans le paquet national : l'application ne la devine pas.

### 2.5 Géographies différentes

Melodi décrit les communes au 1er janvier 2025 ; le référentiel suit le Code officiel géographique 2026 (comme l'annuaire). Le serveur applique les événements de `v_mvt_commune_2026.csv` postérieurs au 1er janvier 2025 (même logique que `Geography.follow` de l'annuaire, mais en additionnant) : une commune nouvelle reçoit la somme de ses anciennes communes (`computed = true`) ; une commune rétablie ou scindée n'a pas de chiffre (`null`) plutôt qu'un chiffre faux. Chaque commune garde ses **anciens codes** (`formerCodes`, fusions depuis 2024) pour lire les contenus étiquetés avant une fusion.

## 3. API Kotlin proposée au pôle « Décider »

Code commun (`shared/core/src/commonMain/.../core/territoires/`), sans bibliothèque propre à une plateforme : `CivilDate` (`core/time`), `FrenchText.fold` (`core/text`), kotlinx.serialization et `Crypto.sha256` de bitcoin-kmp, déjà présents. Aucune dépendance au paquet `core/elus` (autre cycle de vie, autre écran) : seules les conventions sont reprises.

```kotlin
package org.politisationpopulairefrancaise.core.territoires

import org.politisationpopulairefrancaise.core.time.CivilDate

/** The kinds of territories, with the prefix of their code ([TerritoryCode.tag], tag `ppf-territory-code`). */
enum class TerritoryType(val key: String) {
    FRANCE("fr"),
    REGION("reg"),
    DEPARTEMENT("dep"),
    /** Collectivity with the powers of a department (COG "CTCD"): `01D`... `69D`, and the special statuses `20R`, `69M`, `6AE`, `75C`, `972R`, `973R`, `976R`. */
    COLLECTIVITE("ctcd"),
    /** Overseas collectivity (article 74) or Nouvelle-Calédonie: `975`, `977`, `978`, `986`, `987`, `988`. */
    OUTRE_MER("comer"),
    /** EPCI à fiscalité propre, by SIREN. */
    EPCI("epci"),
    COMMUNE("com"),
    /** Municipal district of Paris, Lyon or Marseille. */
    ARRONDISSEMENT("arm"),
    ;

    companion object {
        fun fromKey(key: String): TerritoryType?
    }
}

/**
 * A stable code of the referential: `fr`, `reg:84`, `dep:2A`, `ctcd:69M`, `comer:987`,
 * `epci:200040590`, `com:69123`, `arm:69381`. Built only by [parse] and the factories, which check
 * the shape of the official code (never its existence: [Territoires.find] does).
 */
class TerritoryCode private constructor(val type: TerritoryType, val code: String) {
    /** The text of the tag and of the JSON. */
    val tag: String get() = if (type == TerritoryType.FRANCE) "fr" else "${type.key}:$code"

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String = tag

    companion object {
        val FRANCE: TerritoryCode

        /** `com:69123`, `COM:69123`, ` dep:2a ` (trimmed, prefix case ignored, code upper-cased); null for anything else. */
        fun parse(text: String?): TerritoryCode?

        fun commune(insee: String): TerritoryCode?
        fun departement(code: String): TerritoryCode?
        fun region(code: String): TerritoryCode?
        fun epci(siren: String): TerritoryCode?
    }
}

/** Where a figure comes from, shown next to it. */
data class TerritorySource(
    /** Short id used in the packages: `popref-2023`, `reu-2026-02-11`, `mayotte-2017`. */
    val id: String,
    val name: String,
    val url: String,
    val licence: String,
    /** Populations: the vintage (2023). Null for registered voters. */
    val vintage: Int?,
    /** Populations: the day they came into force (2026-01-01). Registered voters: the extraction (2026-02-11). */
    val date: CivilDate?,
)

data class Population(
    val municipale: Long,
    /** Null for a sum (it would count some people twice) or when the source gives none. */
    val totale: Long?,
    val source: TerritorySource,
    /** True when the server added up communes (EPCI, `ctcd:69D`, commune merged since the vintage). */
    val computed: Boolean,
)

data class Electorate(
    /** Liste principale: French voters. */
    val principale: Long,
    /** Listes complémentaires municipales: citizens of the European Union. Null when unknown. */
    val complementaires: Long?,
    val source: TerritorySource,
    val computed: Boolean,
)

/** Whether the residents of a department or overseas collectivity count among the 30 (Règlement de l'Assemblée nationale, article 148, alinéa 6). */
enum class SpreadRule { COUNTS, TO_CONFIRM, EXCLUDED }

data class Territory(
    val code: TerritoryCode,
    val name: String,
    /** « du Rhône », « de la Réunion », « des Bouches-du-Rhône » (TNCC of the COG). */
    val ofName: String,
    /** Nearest first, ending with [TerritoryCode.FRANCE] (section 2.2). Empty for France. */
    val parents: List<TerritoryCode>,
    val population: Population?,
    val electorate: Electorate?,
    /** Departments and overseas collectivities only; null for the other types. */
    val spread: SpreadRule?,
    /** A collectivity whose code does not end with D, or a region whose powers it holds. */
    val specialStatus: Boolean = false,
    /** Regions only: the collectivity that holds its powers (`ctcd:20R` for Corse), null when it has its own regional council. */
    val heldBy: TerritoryCode? = null,
    /** EPCI and collectivities: the departments they cover (an EPCI may cover two). */
    val departements: List<TerritoryCode> = emptyList(),
    /** Communes: their postal codes (those of their districts for Paris, Lyon and Marseille). */
    val postalCodes: List<String> = emptyList(),
    /** Communes: the codes they replaced (mergers since 2024), to read older tags. */
    val formerCodes: List<String> = emptyList(),
    /** The package that holds the details: a department or overseas collectivity; null for the national level. */
    val packageCode: String? = null,
) {
    fun parent(type: TerritoryType): TerritoryCode? = parents.firstOrNull { it.type == type }
    val departement: TerritoryCode? get() = parent(TerritoryType.DEPARTEMENT)
    val region: TerritoryCode? get() = parent(TerritoryType.REGION)
    val epci: TerritoryCode? get() = parent(TerritoryType.EPCI)
    /** The departmental collectivity or the overseas collectivity of a commune. */
    val collectivite: TerritoryCode? get() = parent(TerritoryType.COLLECTIVITE) ?: parent(TerritoryType.OUTRE_MER)
}

/** One package announced by the national level. */
data class TerritoryPackageEntry(
    val code: String,
    val name: String,
    /** Lowercase hex SHA-256 of the exact bytes (UTF-8 JSON, not compressed). */
    val sha256: String,
    val bytes: Long,
    val postalCodes: List<String>,
)

/** The national level: always on the device (copy shipped with the application, then the newest copy checked from the server). */
data class NationalTerritories(
    val format: Int,
    /** True for fictitious data ([EXEMPLE]). */
    val example: Boolean,
    /** True for a development set that holds only a few department packages. */
    val partial: Boolean,
    /** Day of the geography (COG): 2026-01-01. */
    val geography: CivilDate,
    val builtOn: CivilDate?,
    val sources: List<TerritorySource>,
    val france: Territory,
    /** What the published figures of [france] cover (they differ: section 5.1). */
    val francePopulationPerimeter: String?,
    val franceElectoratePerimeter: String?,
    val regions: List<Territory>,
    val departements: List<Territory>,
    val collectivites: List<Territory>,
    val outreMer: List<Territory>,
    val packages: List<TerritoryPackageEntry>,
) {
    fun find(code: TerritoryCode): Territory?

    /** Departments and overseas collectivities whose residents count for the 30 ([SpreadRule.COUNTS]; with [includeToConfirm], also [SpreadRule.TO_CONFIRM]). */
    fun spreadUnits(includeToConfirm: Boolean = false): List<Territory>

    /** The packages covering [postalCode] (several when it straddles a border), found without sending it. */
    fun packagesFor(postalCode: String): List<TerritoryPackageEntry>
}

/** Storage of the checked packages on the device (SQLDelight table, or memory in tests). */
interface TerritoiresStorage {
    suspend fun load(code: String): StoredTerritoires?
    suspend fun save(stored: StoredTerritoires)
    suspend fun codes(): List<String>
    suspend fun deleteAll()
}

data class StoredTerritoires(val code: String, val sha256: String, val json: String, val builtOn: String?, val downloadedAt: Long)

/** What a postal code needs: the packages missing or stale on the device, and their size to announce. */
data class TerritoiresPlan(val postalCode: String, val missing: List<TerritoryPackageEntry>, val bytes: Long)

sealed interface TerritoiresDownload {
    data class Done(val codes: List<String>) : TerritoiresDownload
    /** The bytes did not match the sha256 of the national level, or were not the package asked for: nothing kept. */
    data class Refused(val code: String) : TerritoiresDownload
    data object NoServer : TerritoiresDownload
    data object Unreachable : TerritoiresDownload
}

sealed interface TerritoiresUpdate {
    data class Updated(val national: Boolean, val packages: List<String>) : TerritoiresUpdate
    data object UpToDate : TerritoiresUpdate
    data object NoServer : TerritoiresUpdate
    data object Unreachable : TerritoiresUpdate
    data object Unreadable : TerritoiresUpdate
}

/**
 * The referential on the device. Nothing it reads is ever sent: searches and choices stay here;
 * the server only sees which package is downloaded, after a gesture of the user.
 */
class Territoires(
    storage: TerritoiresStorage,
    /** Null without a server (the current APK). */
    server: RemoteEvents?,
    /** The national JSON shipped with the application (composeResources of shared/ui), read by the host. */
    bundled: suspend () -> String?,
    clock: () -> Long,
) {
    /** The newest valid copy between the shipped one and the stored one; null only if neither can be read. */
    suspend fun national(): NationalTerritories?

    /**
     * One territory. France, regions, departments, collectivities and overseas collectivities: always.
     * Communes, districts and EPCI: when their package is on the device. Follows [Territory.formerCodes].
     */
    suspend fun find(code: TerritoryCode): Territory?

    /** The value of a `ppf-territory-code` tag, read tolerantly; null when invalid or unknown (the caller then uses the label). */
    suspend fun resolve(tagValue: String?): Territory?

    /**
     * On the device only: names folded with [FrenchText.fold] (accents, case, hyphens; "St" and
     * "Ste" read as "Saint" and "Sainte"), five-digit postal codes, official codes ("69", "2A",
     * "69123", a SIREN). National types always; communes among the packages on the device, and
     * every commune once the "communes" package is downloaded. Best matches first, at most [limit].
     */
    suspend fun search(text: String, types: Set<TerritoryType> = TerritoryType.entries.toSet(), limit: Int = 20): List<Territory>

    /**
     * The assemblies a resident of [commune] can petition, nearest first: the commune, its EPCI,
     * its departmental collectivity, its region (or the collectivity that holds its powers), or
     * its overseas collectivity. Null when the package of the commune is not on the device.
     */
    suspend fun assembliesFor(commune: TerritoryCode): List<Territory>?

    /** Communes of a department, collectivity or EPCI, by name; null when a needed package is missing. */
    suspend fun communesOf(code: TerritoryCode): List<Territory>?

    suspend fun plan(postalCode: String): TerritoiresPlan
    suspend fun download(plan: TerritoiresPlan): TerritoiresDownload

    /** "Rechercher une mise à jour": the national level, then the packages on the device whose sha256 changed. */
    suspend fun update(): TerritoiresUpdate

    /** Erases the downloaded packages; the shipped national copy remains. */
    suspend fun forget()
}

enum class ShareBasis { POPULATION, ELECTORATE }

data class TerritoryShare(val territory: Territory, val weight: Long, val target: Long)

data class Shares(
    val total: Long,
    val basis: ShareBasis,
    val parts: List<TerritoryShare>,
    /** Left out: no figure for the basis (Nouvelle-Calédonie for registered voters, a split commune...). */
    val missing: List<Territory>,
)

object TerritoryShares {
    /**
     * Splits [total] among [territories] in proportion to their population municipale (or
     * registered voters on the liste principale), by the largest remainder: the parts add up
     * exactly to [total]; ties go to the larger weight, then to the smaller code. Deterministic.
     */
    fun proportional(total: Long, territories: List<Territory>, basis: ShareBasis = ShareBasis.POPULATION): Shares
}
```

Lecture du serveur : une méthode de plus dans `RemoteEvents` (`core/publication`), avec une valeur par défaut comme `fetchElus` : `suspend fun fetchTerritoires(code: String? = null, etag: String? = null): ElusDownload` (les quatre réponses de `ElusDownload` conviennent telles quelles : corps, 304, 404, injoignable ; renommer ce type en `PackageDownload` plus tard, sans urgence). `code = null` : le paquet national ; `"communes"` : l'index des noms ; sinon un code de département ou de collectivité d'outre-mer.

### 3.1 Exemples d'appel pour « Décider »

```kotlin
// 1. Objectifs indicatifs pour une pétition de l'Assemblée nationale (plus de 500 000 dans 30 départements ou COM).
val national = territoires.national() ?: return
val shares = TerritoryShares.proportional(total = 500_001, territories = national.spreadUnits())
val rhone = shares.parts.first { it.territory.code == TerritoryCode.departement("69") }
// rhone.target vaut environ 14 000 ; rhone.territory.population!!.source.name : « Populations de référence 2023 (Insee) »

// 2. Seuils indicatifs d'une pétition locale (Constitution, art. 72-1 ; CGCT, art. L1112-16 et L5211-49).
val commune = TerritoryCode.commune("69264") ?: return
val assemblies = territoires.assembliesFor(commune) ?: return askToDownload(territoires.plan("69400"))
// [Villefranche-sur-Saône, CA Villefranche Beaujolais Saône, Département du Rhône (69D), Auvergne-Rhône-Alpes]
val thresholds = assemblies.mapNotNull { LocalPetitionRules.threshold(it) }

// 3. Lire l'étiquette d'un contenu.
val place = territoires.resolve(event.firstTagValue("ppf-territory-code"))?.name ?: event.firstTagValue("ppf-territory")
```

Les fractions et leurs références restent chez « Décider » (`core/decider`), à côté de `PetitionRules`, par exemple :

```kotlin
// Proposition, à écrire par « Décider » : le référentiel ne donne que les chiffres.
object LocalPetitionRules {
    /** Null when no verified rule applies (overseas collectivities, Nouvelle-Calédonie, Paris until confirmed) or no figure. */
    fun threshold(assembly: Territory): LocalThreshold?
}

data class LocalThreshold(
    val assembly: Territory,
    /** Rounded up: a tenth of 318 916 is 31 892. */
    val signatures: Long,
    /** 10 (commune, EPCI) or 20 (other collectivities). */
    val denominator: Int,
    val electorate: Electorate,
    /** "CGCT, article L1112-16" or "CGCT, article L5211-49". */
    val reference: String,
)
```

## 4. Transport : options et recommandation

| | A. Champs ajoutés aux paquets de l'annuaire des élus | B. Paquet national léger et paquets par département séparés (**recommandé**) |
|---|---|---|
| Compatibilité | Rétrocompatible : `ElusReader` ignore les champs inconnus (`ignoreUnknownKeys`) | Rien ne change pour l'annuaire |
| Taille pour « Décider » au niveau national | Aucun niveau national dans l'annuaire : il faudrait les 108 paquets (85,7 Mo) pour connaître la population des départements | Environ 120 ko, **embarqué** dans l'application |
| Taille d'un département | Nord : 1,96 Mo (élus compris) pour quelques kilo-octets utiles | Nord : environ 100 ko (25 ko compressés) |
| Hors ligne | Seulement le département de la commune choisie dans « Mes élus » ; « Oublier ma commune » efface tout et `choose()` supprime les autres départements | National toujours présent (copie embarquée) ; paquets gardés jusqu'à « Effacer » |
| Fréquence | Paquets reconstruits chaque semaine (RNE) | Une à trois fois par an (populations en janvier, inscrits après un scrutin, COG et EPCI au printemps) |
| Données en développement | L'annuaire sert un jeu fictif hors production (données personnelles) | Les chiffres sont publics : un jeu réel est possible partout (section 6) |
| Couplage | « Décider », « Agir », « Messages » dépendraient du pipeline du RNE et du code de « Comprendre » | Indépendant ; mêmes conventions |
| Panne | Un RNE tronqué bloque aussi la population | Chaque import échoue seul |

**Recommandation : B.** Ce qui est repris de l'annuaire : codes des paquets (les mêmes départements et collectivités d'outre-mer, sans `FR`), règles HTTP (`ETag` entre guillemets égal au sha256, `If-None-Match`, `304`, gzip, `Cache-Control: public, no-cache`, `404 unknown_package`, `503` tant qu'aucun import n'a réussi), lecture stricte du document et tolérante des entrées, refus d'un paquet dont l'empreinte ne correspond pas, et côté serveur `Csv.kt`, `Geography` (COG et La Poste) et le client HTTPS à liste d'hôtes de `OfficialSource`.

Trois documents (formats en section 5) :
1. **`GET /api/v1/territoires`** : le niveau national, qui sert aussi d'index (empreinte, taille et codes postaux de chaque paquet). Embarqué dans l'application ; demandé au serveur seulement lors d'un geste (« Rechercher une mise à jour », ou téléchargement d'un département).
2. **`GET /api/v1/territoires/{code}`** : les communes, arrondissements et EPCI d'un département ou d'une collectivité d'outre-mer.
3. **`GET /api/v1/territoires/communes`** : l'index des noms de toutes les communes (code, nom, département), pour chercher une commune n'importe où sans rien envoyer. Facultatif, téléchargé sur geste avec sa taille annoncée (ou embarqué : question à la propriétaire).

## 5. Formats JSON proposés (pour FORMATS, future section « 4 nonies »)

Règles communes : format 1 ; JSON compact, UTF-8, champs dans l'ordre ci-dessous, listes triées (codes, puis noms sans accents ni majuscules) pour que deux imports des mêmes sources donnent les mêmes octets ; nombres entiers positifs ; `null` écrit seulement là où le tableau le dit ; **aucune donnée personnelle**.

### 5.1 Niveau national

```json
{
  "format": 1,
  "example": false,
  "partial": false,
  "geography": "2026-01-01",
  "builtOn": "2026-11-07",
  "sources": [
    { "id": "popref-2023", "name": "Populations de référence 2023 (Insee)", "url": "https://www.insee.fr/fr/statistiques/8681011", "licence": "Licence Ouverte 2.0", "vintage": 2023, "date": "2026-01-01" },
    { "id": "reu-2026-02-11", "name": "Répertoire électoral unique, électeurs inscrits (Insee)", "url": "https://www.insee.fr/fr/statistiques/8888278", "licence": "Licence Ouverte 2.0", "vintage": null, "date": "2026-02-11" }
  ],
  "france": { "name": "France", "population": 68094280, "populationPerimeter": "France hors Mayotte et hors collectivités d'outre-mer", "inscrits": 47900000, "inscritsPerimeter": "France et collectivités d'outre-mer, hors Nouvelle-Calédonie" },
  "regions": [ { "code": "84", "name": "Auvergne-Rhône-Alpes", "of": "d'Auvergne-Rhône-Alpes", "population": 8100000, "populationTotale": 8300000, "inscrits": 5700000, "inscritsComplementaires": 41000, "heldBy": null } ],
  "departements": [ { "code": "69", "name": "Rhône", "of": "du Rhône", "region": "84", "collectivites": ["69D", "69M"], "population": 1914667, "populationTotale": 1940015, "inscrits": 1227213, "inscritsComplementaires": 7992, "spread": "counts" } ],
  "collectivites": [ { "code": "69M", "name": "Métropole de Lyon", "of": "de la Métropole de Lyon", "special": true, "departements": ["69"], "region": "84", "population": 1450000, "inscrits": 900000, "inscritsComplementaires": 6000, "computed": ["population", "inscrits", "inscritsComplementaires"] } ],
  "outreMer": [ { "code": "975", "name": "Saint-Pierre-et-Miquelon", "of": "de Saint-Pierre-et-Miquelon", "population": 5790, "inscrits": 5059, "inscritsComplementaires": 4, "spread": "counts", "sources": { "population": "insee-com-2023" } } ],
  "packages": [ { "code": "69", "name": "Rhône", "sha256": "<64 hex>", "bytes": 61000, "postalCodes": ["69001", "69002"] } ]
}
```

(Seuls les chiffres du Rhône, de Saint-Pierre-et-Miquelon et la population de la France sont des relevés ; les autres sont des ordres de grandeur pour montrer le format.)

- `france` : les deux chiffres publiés n'ont pas le même périmètre (populations Melodi : hors Mayotte et hors collectivités d'outre-mer ; inscrits : y compris l'outre-mer sauf la Nouvelle-Calédonie) ; le paquet le dit dans `populationPerimeter` et `inscritsPerimeter`, et l'application l'affiche. Pour des objectifs, « Décider » additionne les départements et collectivités qu'il retient (`spreadUnits`), jamais le chiffre de la France.

- `population`, `populationTotale`, `inscrits`, `inscritsComplementaires` : entiers ou absents (absent = inconnu ; `0` est une valeur). Sources par défaut : la première source de population et la première source d'inscrits de `sources` ; un objet `sources` sur une entrée remplace l'une ou l'autre (Mayotte, outre-mer). `computed` : la liste des champs calculés par somme.
- `spread` : `counts`, `to-confirm` ou `excluded` (départements et outre-mer seulement).
- `heldBy` : collectivité qui exerce les compétences de la région (`20R`, `972R`, `973R`, `976R`), sinon `null`.
- `packages` : un par département et par collectivité d'outre-mer qui a des communes, plus `communes` (index des noms, `postalCodes` vide). `sha256` et `bytes` des octets exacts non compressés.

### 5.2 Paquet d'un département ou d'une collectivité d'outre-mer

```json
{
  "format": 1,
  "example": false,
  "code": "69",
  "name": "Rhône",
  "geography": "2026-01-01",
  "sources": [ "…mêmes objets que le niveau national…" ],
  "communes": [
    { "code": "69264", "name": "Villefranche-sur-Saône", "postalCodes": ["69400"], "epci": "200040590", "collectivite": "69D", "population": 37000, "populationTotale": 37600, "inscrits": 21000, "inscritsComplementaires": 300, "formerCodes": [] }
  ],
  "arrondissements": [ { "code": "69381", "name": "Lyon 1er Arrondissement", "commune": "69123", "population": 29000, "inscrits": 18000 } ],
  "epcis": [ { "code": "200040590", "name": "CA Villefranche Beaujolais Saône", "nature": "CA", "departements": ["01", "69"], "communes": ["01194", "69013"], "population": 76000, "inscrits": 47000, "computed": ["population", "inscrits"] } ]
}
```

(Chiffres d'exemple de format, non relevés.)

- Une commune : `epci` (SIREN ou `null`), `collectivite` (code CTCD, ou code de la collectivité d'outre-mer) ; département et région se déduisent du paquet et du niveau national.
- `epcis` : les EPCI qui ont au moins une commune dans le paquet, avec **toutes** leurs communes (aussi celles d'un autre département) et leurs totaux calculés sur toutes. `nature` : `CC`, `CA`, `CU`, `METRO`.
- Lecture par l'appareil : stricte pour le document (format 1, code valide, nom), tolérante pour les entrées (une commune illisible est écartée) ; 8 Mo au plus par paquet.

### 5.3 Index des noms (`communes`)

`{"format":1,"example":false,"code":"communes","geography":"2026-01-01","communes":[["01001","L'Abergement-Clémenciat","01"],…]}` : un tableau `[code, nom, paquet]` par commune, trié par code.

## 6. Jeu de développement : réel

Les populations et les électeurs inscrits ne sont **pas des données personnelles** : ce sont des comptes agrégés que l'Insee publie lui-même sous Licence Ouverte, réutilisables avec mention de la source. Le jeu de développement **peut donc être réel**, et il est préférable qu'il le soit : des seuils calculés sur de faux chiffres tromperaient les tests de « Décider ».

Proposition :
- **Serveur, mode `bundled` (par défaut)** : `server/src/main/resources/territoires/` contient des **extraits réels** des sources : tout le niveau national (régions, départements, collectivités, outre-mer), et les communes complètes des départements du jeu de l'annuaire (69, 59, 13, 35, 75) plus l'Ain (01, pour l'EPCI à cheval). Le paquet national porte `"partial": true` (six paquets seulement) ; l'application affiche alors « Données de développement : quelques départements seulement ». `example` reste faux (rien d'inventé). Taille des extraits : environ 300 ko.
- **Application** : la copie embarquée du niveau national est complète et réelle (environ 120 ko), produite par le serveur en mode `official` puis copiée une fois par an dans `shared/ui/src/commonMain/composeResources/files/territoires/national.json` (aucun téléchargement pendant la compilation, qui reste reproductible).
- **À faire valider** : CLAUDE.md dit de la préproduction « jamais de données réelles, uniquement des données fictives marquées [EXEMPLE] ». Je propose de lire « réelles » comme « personnelles » (règle de la section 7 de CLAUDE.md : « Aucune donnée personnelle réelle dans les tests, les jeux de données, la préproduction ») et donc d'autoriser ces chiffres publics en préproduction. Sinon : jeu fictif marqué [EXEMPLE] (chiffres arrondis inventés) en préproduction seulement.

## 7. Règle `ppf-territory` (proposition pour FORMATS)

**Aujourd'hui** (lu dans le code et les contenus) : texte libre, obligatoire pour les salons, mobilisations, appels et organisations, facultatif pour les revendications ; 80 caractères dans l'application (salons, appels), 100 sur le serveur ; comparé une fois replié (accents, majuscules, ponctuation) ; « près de chez moi » (`Territories.isNear`) reconnaît un numéro de département écrit dans le texte (« Lille (59) ») ou lu dans un code postal. Valeurs rencontrées dans les contenus embarqués : « France », « Lyon », « Lyon (69) », « Nord », « Nord (59) », « Métropole de Lyon », « Bretagne », « Seine-Saint-Denis », « Exempleville ». Le serveur indexe chaque étiquette par son nom et sa première valeur (`event_tags`).

**Proposition, compatible avec l'existant** :
1. `ppf-territory` **ne change pas** : un libellé lisible, toujours écrit là où il l'est aujourd'hui. Forme recommandée quand le territoire vient du référentiel : « Nom (dd) » pour une commune ou un département (convention de l'annuaire des organisations), le nom seul pour une région, une collectivité d'outre-mer ou « France ».
2. Nouvelle étiquette **facultative `ppf-territory-code`**, au plus une, valeur = code du référentiel :
   `^(fr|reg:[0-9]{2}|dep:(0[1-9]|1[0-9]|2[1-9AB]|[3-8][0-9]|9[0-5]|97[1-6])|ctcd:([0-9]{2}D|97[14]D|20R|69M|6AE|75C|972R|973R|976R)|comer:9[78][0-9]|epci:[0-9]{9}|com:([0-9]{5}|2[AB][0-9]{3})|arm:[0-9]{5})$`
   (forme seulement : l'existence se vérifie dans le référentiel). Elle désigne **le plus petit territoire du référentiel qui contient** ce que dit le libellé (« Quartier de la Guillotière » → `arm:69387`).
3. **Lecture** : code présent et connu du référentiel → il sert aux comparaisons (filtres, « près de chez moi », doublons) ; ancien code d'une commune fusionnée → suivi par `formerCodes` ; code absent, invalide ou inconnu → comportement d'aujourd'hui sur le libellé. Un lecteur qui ignore l'étiquette continue de fonctionner.
4. **Écriture** : l'application écrit le code quand l'utilisateur choisit un territoire proposé par le référentiel ; un texte tapé librement n'a pas de code. Jamais de code deviné à partir d'un libellé.
5. **Serveur** : vérifie seulement la forme (`400 bad_territory_code`), jamais l'existence, pour qu'une publication ne dépende jamais d'un import. Une étiquette séparée (et non une troisième valeur de `ppf-territory`) est indexée telle quelle : `GET /api/v1/events?tag=ppf-territory-code:dep:69` fonctionne sans changer l'API (le premier deux-points sépare le nom de la valeur).
6. **Changements à prévoir chez les autres pôles** (aucun ici) : revendications : `DemandDrafts.ALLOWED_TAGS` refuse aujourd'hui toute étiquette hors formulaire (`400 malformed_demand`) : le serveur doit l'accepter **avant** que l'application l'écrive ; Agir (mobilisations, appels, organisations) et salons : acceptée sans changement par le serveur (pas de liste fermée ; longueurs bornées) ; constructeurs de l'application (`Mobilization`, `CallDraft`, `Organisation`, `Group`, formulaire des revendications) ; `content/README.md` et l'outil de signature (champ facultatif `territoryCode`).

Option écartée : écrire le code dans `ppf-territory` (« Lyon (69123) ») : confusion avec la convention « (dd) » et libellés illisibles. Option reportée : étiquette `i` de NIP-73 (`iso3166:FR-69`), utile pour l'interopérabilité Nostr mais limitée aux départements et régions.

## 8. Migrations

- **Base locale (SQLDelight) : migration nécessaire : oui.** Une table `territoire_package(code TEXT PRIMARY KEY, sha256 TEXT NOT NULL, json TEXT NOT NULL, built_on TEXT, downloaded_at INTEGER NOT NULL)` pour les copies vérifiées (niveau national plus récent que la copie embarquée, départements téléchargés, index des noms). Raison : le hors-ligne des communes ne tient pas en mémoire d'un lancement à l'autre, et la table de l'annuaire (`elus_package`) a d'autres règles d'effacement (« Oublier ma commune »). Numéro à demander au chef d'équipe.
- **Serveur (Flyway) : migration nécessaire : oui, pour le mode `official`.** Deux tables sur le modèle de `V6__elus.sql` : `territoires_import` (un essai par ligne : source, statut, dates des sources, nombres pour la garde, document national exact et son empreinte, motif d'échec) et `territoires_package` (code, nom, `sha256`, `bytes`, codes postaux, corps compressé). Le mode `bundled` n'en a pas besoin (paquets construits en mémoire au démarrage). Numéro à demander au chef d'équipe.

## 9. Serveur

### 9.1 Import

`server/.../server/territoires/` (nouveau paquet) : lecture des sources, construction déterministe, stockage, routes. Réutilise `elus/Csv.kt`, `elus/Geography.kt` (à déplacer plus tard dans un paquet commun `server/geo` si « Comprendre » le souhaite) et le client de `OfficialSource`.

Ordre : Code officiel géographique 2026 et codes postaux (comme l'annuaire) ; populations Melodi (communes, arrondissements, départements, régions, France) ; inscrits Melodi (mêmes niveaux et `COMER`) ; compléments outre-mer ; EPCI (liste, puis composition, deux requêtes par seconde au plus) ; passage de la géographie 2025 à 2026 ; sommes ; vérifications ; construction ; activation en une transaction.

Vérifications (refus de tout l'import, l'ancien reste servi) :
- forme : codes géographiques `^2025-(COM|ARM|DEP|REG|COMER|FRANCE)-…$`, nombres entiers positifs inférieurs à 100 millions, pages Melodi de 10 000 observations au plus, 10 pages au plus par requête, réponse de 20 Mo au plus ;
- cohérence : somme des communes de chaque département égale au chiffre publié du département (à la géographie 2025, avant tout passage) ; `20R` égale la région 94 ;
- garde : refus si le nombre de communes varie de plus de 2 %, la population de la France de plus de 3 %, ou celle d'un département de plus de 15 % par rapport à l'import servi (motif court gardé, jamais une valeur lue) ;
- millésime : le plus récent dont la date d'entrée en vigueur est passée.

### 9.2 Compléments outre-mer

`server/src/main/resources/territoires/outre-mer.csv` : une ligne par chiffre absent de Melodi (Mayotte et ses 17 communes au recensement de 2017, jusqu'au décret attendu fin 2026 ; Saint-Pierre-et-Miquelon et ses deux communes, Saint-Barthélemy, Saint-Martin au millésime 2023 ; Wallis-et-Futuna, Polynésie française, Nouvelle-Calédonie à leur dernier recensement), avec code, grandeur, valeur, identifiant de source, adresse et date. Tenue à la main, relue à chaque mise à jour (janvier 2027 : Mayotte).

### 9.3 Variables

| Variable | Rôle | Défaut |
|---|---|---|
| `PPF_TERRITOIRES_SOURCE` | `bundled` (extraits du dépôt, ne contacte personne) ou `official` (sources de la section 1) | `bundled` |
| `PPF_TERRITOIRES_REFRESH_DAYS` | Jours entre deux vérifications des sources en mode `official` ; nouvel essai six heures après un échec ; `0` coupe l'import automatique (tests) | 30 |

Contrairement à l'annuaire, `official` n'est pas réservé à la production (rien de personnel) ; il reste coupé dans les tests (aucun réseau).

### 9.4 Hôtes contactés (mode `official` seulement)

- **Nouveau** : `api.insee.fr` (Melodi : catalogue et données ; API Métadonnées géographiques).
- Déjà listés pour l'annuaire : `www.data.gouv.fr`, `static.data.gouv.fr`, `www.insee.fr` (Code officiel géographique, et l'archive des EPCI en repli), `data.laposte.fr` (codes postaux).
- Le client suit lui-même les redirections et refuse tout autre hôte. L'appareil ne contacte que notre serveur. À reporter dans `THREAT_MODEL.md` et `REGISTRE_DONNEES.md` par la session principale.

## 10. Tests prévus

**Code commun** (`gradlew :shared:core:jvmTest`, et les autres cibles du code commun) :
- `TerritoryCodeTest` : chaque type, `fr`, majuscules et espaces, Corse (`2A004`), outre-mer (`97411`, `97502`), codes invalides (`dep:20`, `com:2C001`, `epci:12`, préfixe inconnu), aller-retour `parse(tag)`.
- `TerritoiresReaderTest` : lecture stricte du document (format, code, nom), tolérante des entrées (commune sans nom écartée, champs inconnus ignorés, nombre négatif ou texte à la place d'un nombre lu comme absent), sources par défaut et remplacées, `computed`, tailles maximales.
- `TerritoryParentsTest` : Villefranche-sur-Saône, Lyon (sans EPCI, `69M`), Paris (`75C`, Métropole du Grand Paris), Ajaccio (`20R`, `heldBy`), Fort-de-France (`972R`), Saint-Pierre (`comer:975`, sans département), Jassans-Riottier (EPCI du paquet 69 vu depuis le paquet 01), arrondissement de Lyon.
- `TerritoiresSearchTest` : « saint etienne », « St-Étienne », « SAINT-ÉTIENNE » ; « 69400 » ; « 69 », « 2a » ; un SIREN ; homonymes classés (département avant commune, puis population) ; limite ; types filtrés ; rien n'est envoyé (faux serveur qui échoue à tout appel).
- `SpreadRuleTest` : 106 `COUNTS`, Nouvelle-Calédonie `TO_CONFIRM`, Rhône compté une fois.
- `TerritorySharesTest` : somme exacte des parts (500 001), égalités départagées (poids puis code), territoire sans chiffre dans `missing`, total nul, liste vide, base « inscrits ».
- `TerritoiresDirectoryTest` : copie embarquée seule (APK sans serveur), copie plus récente gardée, copie abîmée ignorée, empreinte refusée, paquet d'un autre code refusé, `304`, mise à jour d'un paquet, `formerCodes`, effacement, redémarrage hors ligne.
- `DatabaseMigrationTest` (comme pour `10.sqm`) : base neuve et base migrée identiques.

**Serveur** (`gradlew :server:test --tests "*.territoires.*"`) :
- `MelodiTest` : pagination (`next`, `isLast`), préfixe `2025-COM-`, grandeurs `PMUN`, `PTOT`, listes `LP` et `LC`, réponses vides (Mayotte), réponse trop grosse refusée.
- `InseeGeoTest` : liste des EPCI, Métropole de Lyon écartée des EPCI, composition, EPCI à cheval.
- `TerritoiresBuilderTest` : passage 2025 vers 2026 (fusion additionnée, scission sans chiffre), sommes des EPCI et des collectivités (`69D`, `6AE`, `20R`), cohérence avec les chiffres publiés, compléments outre-mer, déterminisme (mêmes octets deux fois), aucune population totale additionnée.
- `TerritoiresImportTest` : garde, import qui échoue sans effacer le précédent, mode `bundled` sans réseau, liste d'hôtes (`api.insee.fr` accepté, tout autre refusé).
- `TerritoiresApiTest` : `200`, `ETag`, `304`, gzip, `404 unknown_package`, `503` avant le premier import.
- Pour `ppf-territory-code`, quand les pôles concernés l'adopteront : forme acceptée et refusée (`bad_territory_code`) dans `AgirRules`, `GroupDirectory` et `DemandDrafts`.
- `TerritoiresOfficialCheck` : vérification manuelle sur les vraies sources, ignorée sans variable (comme `ElusOfficialCheck`).

## 11. Tailles estimées

Mesures faites sur des entrées types (147 à 180 octets par commune en JSON) ; compression gzip estimée au quart pour ces données peu répétitives, à mesurer au premier import réel.

| Document | Contenu | JSON | gzip (estimé) |
|---|---|---|---|
| National | 18 régions, 101 départements, 100 collectivités, 6 outre-mer, sources, index de 108 paquets avec environ 6 340 codes postaux | environ 120 ko | environ 30 ko |
| Département moyen | 346 communes, leurs EPCI | environ 55 ko | environ 14 ko |
| Plus gros (Pas-de-Calais, environ 890 communes) | | environ 140 ko | environ 35 ko |
| Nord (647 communes) | | environ 100 ko | environ 25 ko |
| Paris | 1 commune, 20 arrondissements, 1 EPCI de 131 communes | environ 5 ko | environ 2 ko |
| Tous les départements | environ 34 900 communes | environ 5,5 Mo | environ 1,4 Mo |
| Index des noms (`communes`) | 41 octets par commune | environ 1,4 Mo | environ 400 ko |

L'application ne demande pas encore la compression (`elus-app.md`, section 2) : les tailles annoncées à l'utilisateur sont celles du JSON. Sur le serveur, les paquets compressés tiennent en moins de 2 Mo ; l'import garde en mémoire moins de 50 Mo (35 000 communes, quelques nombres chacune), compatible avec le serveur de 4 Go.

## 12. Points à faire valider

1. **Transport B** (paquets séparés de l'annuaire) et **niveau national embarqué** dans l'application (environ 120 ko).
2. **Index des noms des communes** (environ 1,4 Mo, 400 ko compressés) : téléchargé sur geste avec sa taille annoncée (ma recommandation), ou embarqué dans l'APK pour chercher une commune sans réseau ?
3. **Jeu de développement réel** et lecture de la règle de préproduction « jamais de données réelles » comme « jamais de données personnelles » (section 6).
4. **Nouvelle-Calédonie** et règle des 30 : laissée « à confirmer » tant que la décision du Bureau sur la domiciliation n'est pas lue ; on peut écrire au service des pétitions de l'Assemblée (envoi à décider par la propriétaire).
5. **Droit des pétitions locales**, à vérifier avant que « Décider » affiche un seuil : listes complémentaires (citoyens européens) dans le dixième communal ; Paris (dixième ou vingtième) ; collectivités d'outre-mer (lois organiques propres) ; Métropole de Lyon (vingtième, comme collectivité à statut particulier). D'ici là, « Décider » n'affiche que les cas sûrs (commune ordinaire, EPCI, conseil départemental, région) avec la mention « environ ».
6. **Source des EPCI** : API Métadonnées (JSON, 1 253 requêtes par an, recommandé) ou archive tableur de l'Insee (une requête, petit lecteur de tableur à écrire).
7. **Nouvelle étiquette `ppf-territory-code`** (section 7) et ordre de déploiement pour les revendications (serveur d'abord).
8. **Numéros de migration** (locale et Flyway) : à attribuer par le chef d'équipe.
9. **Corrections pour « Décider »** : article 148, alinéa 6 (et non 151) ; « plus de » 100 000 et « plus de » 500 000.
10. **Pour « Comprendre »** : 6 081 communes sans intercommunalité dans les paquets de l'annuaire ; le référentiel pourrait leur donner la composition officielle des EPCI.
11. **Décision D-090** à consigner par la session principale dans `DECISIONS.md` une fois ces points tranchés.

## 13. Sources consultées

- Insee, Populations de référence 2023 : https://www.insee.fr/fr/statistiques/8681011 ; collectivités d'outre-mer : https://www.insee.fr/fr/statistiques/8680667 ; Mayotte et outre-mer : https://www.insee.fr/fr/information/2409261 et https://www.insee.fr/fr/information/9022279 ; jeu data.gouv.fr : https://www.data.gouv.fr/datasets/populations-de-reference
- Insee, catalogue des données et API Melodi : https://www.insee.fr/fr/information/8184173 ; `https://api.insee.fr/melodi/catalog/DS_POPULATIONS_REFERENCE` ; `https://api.insee.fr/melodi/catalog/DS_ELECTORAL`
- Insee, électeurs : https://www.insee.fr/fr/statistiques/2389209 ; https://www.insee.fr/fr/statistiques/8888278 ; https://www.insee.fr/fr/statistiques/8276873
- Insee, intercommunalités : https://www.insee.fr/fr/information/2510634 ; `https://api.insee.fr/metadonnees/geo/intercommunalites?date=2026-01-01`
- Insee, Code officiel géographique 2026 : https://www.data.gouv.fr/datasets/code-officiel-geographique-1 (dont `v_ctcd_2026.csv`) ; définition des collectivités d'outre-mer : https://www.insee.fr/fr/metadonnees/definition/c1842
- Assemblée nationale, Règlement (édition de juillet 2025) : https://www.assemblee-nationale.fr/dyn/17/divers/texte_reference/02_reglement_assemblee_nationale.pdf ; résolution n° 281 du 4 juin 2019 : https://www.assemblee-nationale.fr/dyn/opendata/RIONANR5L15BTA0281.html
- Constitution, article 72-1 : https://www.conseil-constitutionnel.fr/le-bloc-de-constitutionnalite/texte-integral-de-la-constitution-du-4-octobre-1958-en-vigueur
- CGCT, article L1112-16 : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045210334 ; article L5211-49 : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000045210340
