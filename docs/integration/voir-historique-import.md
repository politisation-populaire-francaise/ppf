# « Voir » : l'import de l'historique des votes (comparateur de « Décider », DE-16)

**État à la reprise (7 novembre 2026, soir)**
- **Fait** : format `ParliamentHistory.kt` (core) et son test ; analyseurs AN (AMO30, 14e à 17e en flux), Sénat (Dosleg par blocs `COPY`, `ODSEN_HISTOGROUPES.csv`), Parlement européen (9e et 10e) ; `--history` et `--only …-history` ; `data.senat.fr` autorisé ; fichiers d'exemple de l'historique ; `HistoryTest` (13 tests) ; cette documentation (brouillon).
- **Reste** : lancer `:shared:core:jvmTest --tests *Parliament*` (jamais lancé) ; construire le jar et régénérer les paquets embarqués de `shared/ui/.../files/parlement/` avec la clé d'exemple, puis les committer ; remplacer `TESTS_PLACEHOLDER` ci-dessous ; reporter dans `docs/FORMATS.md` et `DECISIONS.md`.
- **Dernier résultat de test** : `gradlew :tools:open-data-importer:test` vert, 29 tests (HistoryTest 13, ImporterTest 8, ParsersTest 6, KeyGenTest 2), commit 6dd43d3.

Écrit le 7 novembre 2026 sur la branche `feature/voir-historique`. Sources vérifiées et structure réelle des fichiers : `voir-historique-sources.md`. Outil d'import de départ : `voir-donnees-import.md`.

Demande de la propriétaire, relayée par « Décider » : « Depuis aussi loin que possible sur le parti portant le candidat à la présidentielle, tant que les données officielles existent, tous les votes en france et à l'union européenne ».

## En bref

- De **nouveaux paquets**, un par chambre et par période, cités dans **le même index signé** que les autres : `historique-an-14` à `historique-an-17`, `historique-senat-<année de session>` (depuis 2006), `historique-pe-9` et `historique-pe-10`.
- Pour chaque scrutin public : identifiant, date, titre officiel, texte ou dossier quand la source le donne, résultat, totaux, et la **répartition par parti** (pour, contre, abstention, non-votants, nombre de membres du parti à cette date).
- **Aucun nom de personne, aucun vote nominatif** dans ces paquets (les votes nominatifs restent dans les paquets mensuels de la législature en cours). **Aucune « position majoritaire »** calculée : ce serait une lecture éditoriale.
- L'application télécharge **tous les paquets d'historique d'un coup, pour tous les partis** : jamais une requête par parti, qui révélerait au serveur le parti qui intéresse la personne (opinions politiques, RGPD article 9).
- L'import de l'historique tourne **à part** de l'import quotidien (`--history`) ; l'import quotidien garde les paquets d'historique tels quels, et l'inverse.

## Format des paquets (à reporter dans `docs/FORMATS.md`)

Classes Kotlin dans `shared/core/src/commonMain/kotlin/org/politisationpopulairefrancaise/core/voir/parlement/ParliamentHistory.kt` : `HistoryPackage`, `HistoryBallot`, `PartyDistribution`, `PartyRef`, `PartyBasis`, `HistoryCoverage` ; nom d'un paquet : `ParliamentFormat.historyPackage(chamber, period)` ; lecture vérifiée (nom, chambre, période, format) : `ParliamentHistory.read(name, json)`. Même JSON que les autres paquets (`ParliamentFormat.json` : champs inconnus ignorés, valeurs par défaut non écrites).

```json
{
  "example": true,
  "chamber": "an",
  "period": 14,
  "coverage": { "first": "2012-07-10", "last": "2016-11-24" },
  "parties": [
    { "id": "an:PO910102", "label": "[EXEMPLE] Groupe des Vallées", "abbreviation": "GVA", "basis": "groupe", "basisLabel": "groupe, rattachement au parti non publié" },
    { "id": "an:PO910201", "label": "[EXEMPLE] Parti des Collines", "abbreviation": "PCO", "basis": "parti" }
  ],
  "ballots": [
    {
      "id": "an:14:9002",
      "date": "2014-03-12",
      "title": "[EXEMPLE] l'amendement n° 12 …",
      "kind": "SPS",
      "adopted": false,
      "counts": { "voters": 4, "expressed": 3, "pour": 1, "contre": 3, "abstention": 1, "nonVotant": 0 },
      "parties": [
        { "party": "an:PO910102", "basis": "groupe", "pour": 0, "contre": 0, "abstention": 1, "nonVotant": 0, "members": 1 },
        { "party": "an:PO910201", "basis": "parti", "pour": 1, "contre": 1, "abstention": 0, "nonVotant": 0, "members": 2 }
      ],
      "url": "https://www.assemblee-nationale.fr/dyn/14/scrutins/9002"
    }
  ]
}
```

| Champ | Contenu |
|---|---|
| `chamber`, `period` | `an` et la législature ; `senat` et l'année de session (octobre à septembre : 2025 = session 2025-2026) ; `pe` et la législature européenne |
| `coverage` | premier et dernier jour des scrutins du paquet (absent pour un paquet vide) : l'application peut dire jusqu'où va le fichier officiel |
| `parties` | chaque parti ou groupe cité, trié par identifiant : `id`, `label` (tel que la source l'écrit), `abbreviation`, `basis`, et `basisLabel` pour un groupe |
| `ballots[].id` | `an:<lég.>:<numéro>`, `senat:<année de session>:<numéro>`, `pe:<date>:<numéro du vote>` (le même numéro que les paquets mensuels : `activity_order`) |
| `ballots[].kind` | code de la source : Assemblée `SPO`, `SPS`, `MOC` ; Parlement européen `appel-nominal` ; absent pour le Sénat |
| `ballots[].text` | référence du texte voté (Parlement européen : `referenceText`, par exemple `A9-0001/2021`) |
| `ballots[].dossier` | dossier législatif de l'Assemblée (`an-dossier:DLR…`) |
| `ballots[].committee` | commission saisie : prévu, **vide pour l'instant** (aucun des champs lus ne la donne) |
| `ballots[].adopted` | résultat ; absent quand la source n'en donne pas |
| `ballots[].counts` | totaux de toute l'assemblée (pour le Parlement européen : tout le Parlement, pas seulement les élus en France) |
| `ballots[].parties[]` | `party`, `basis` (toujours écrit), `pour`, `contre`, `abstention`, `nonVotant`, `members` ; trié par `party` |

Identifiants des partis : `an:PO…` (parti de rattachement ou groupe de l'Assemblée), `senat-grp:<code>` (groupe du Sénat), `pe-org:<identifiant>` (parti national, Parlement européen). Deux lignes spéciales : `senat-grp:aucun` (« Sans groupe connu à cette date ») et `pe-org:aucun` (« Sans parti national connu à cette date »).

`basis` : `parti` quand la source publie le parti ; `groupe` quand le groupe de l'assemblée en tient lieu, avec le libellé « groupe, rattachement au parti non publié » (`ParliamentHistory.GROUP_BASIS_LABEL`).

`members` : les membres de ce parti (ou de ce groupe) dans l'assemblée à la date du scrutin, jamais moins que les votes comptés. Les absents ne sont pas comptés dans `nonVotant` pour l'Assemblée et le Sénat (ils ne figurent pas dans les listes) : absents = `members` − pour − contre − abstention − nonVotant. Pour le Parlement européen, la source ne distingue pas l'absent du présent qui ne vote pas : `nonVotant` = `members` − pour − contre − abstention. Un parti dont aucun membre n'a voté garde sa ligne (zéros).

## Le parti à la date du vote

| Chambre | Règle | `basis` |
|---|---|---|
| Assemblée nationale | mandat `PARPOL` (parti de rattachement) d'AMO30 couvrant la date (le plus récent s'il y en a plusieurs) ; sinon le groupe (`GP`) que le scrutin donne pour ce député | `parti`, sinon `groupe` |
| Sénat | groupe à la date (`ODSEN_HISTOGROUPES.csv`) : le Sénat ne publie aucun parti | toujours `groupe` |
| Parlement européen | parti national (`hasMembership`, `NATIONAL_POLITICAL_GROUP`) couvrant la date, élus en France seulement | `parti` |

Membres en fonction à une date : Assemblée, un mandat `ASSEMBLEE` d'AMO30 couvrant la date ; Sénat, une appartenance à un groupe couvrant la date ; Parlement européen, une appartenance datée à un groupe européen ou à un parti national couvrant la date.

## Couverture

| Paquets | Couverture | Remarque |
|---|---|---|
| `historique-an-14` | 3 juillet 2012 au 24 novembre 2016 (1 354 scrutins) | le fichier officiel s'arrête au 24 novembre 2016 : décembre 2016 à juin 2017 manquent (à dire dans l'application, `coverage.last`) ; partis publiés seulement depuis décembre 2012 : avant, tout est en `groupe` |
| `historique-an-15`, `-16` | législatures complètes (4 417 et 4 106 scrutins) | fichiers figés |
| `historique-an-17` | depuis juillet 2024, mis à jour à chaque import de l'historique | les paquets mensuels gardent en plus les votes nominatifs |
| `historique-senat-2006` et suivants | depuis la session 2006-2007 | groupes seulement |
| `historique-pe-9` | 15 juillet 2019 au 15 juillet 2024, votes par appel nominal seulement | avant le 15 juillet 2019, pas de vote de chaque député en données ouvertes |
| `historique-pe-10` | depuis le 16 juillet 2024, votes par appel nominal seulement | |

Rien avant 2012 pour l'Assemblée (pas de données ouvertes des 12e et 13e législatures), rien avant 2006 pour le Sénat. Le Conseil de l'Union européenne n'est pas importé (jeu de données abandonné, voir `voir-historique-sources.md`).

## L'outil d'import

Nouveaux identifiants pour `--only` : `an-history`, `senat-history`, `pe-history` ; nouvelle option **`--history`** (sans valeur) = les trois. Par défaut : en mode `official`, les sources quotidiennes seulement (`an,senat,pe`), jamais l'historique ; en mode `example`, tout, historique compris.

```
java -jar ppf-open-data-importer.jar --source official --history --out <dossier> --key <clé>
java -jar ppf-open-data-importer.jar --source official --only senat-history --out <dossier> --key <clé>
```

Le minuteur conseillé : l'historique une fois par semaine (ou par mois), à une autre heure que l'import quotidien (les deux écrivent dans le même dossier : ne jamais les lancer en même temps). Codes de sortie inchangés (0 à 5) ; la garde (20 %) s'applique à chaque source d'historique (total de ses scrutins). L'index cite trois sources de plus (`an-history`, `senat-history`, `pe-history`) avec leur date de mise à jour.

Hôtes contactés en mode `official` : ceux de l'import quotidien, plus **`data.senat.fr`** (ajouté à `ALLOWED_HOSTS`). Adresses :

- Assemblée : `repository/14/loi/scrutins/Scrutins_XIV.json.zip`, `repository/15/loi/scrutins/Scrutins_XV.json.zip`, `repository/16/loi/scrutins/Scrutins.json.zip`, `repository/17/loi/scrutins/Scrutins.json.zip` ; AMO30 : `repository/17/amo/tous_acteurs_mandats_organes_xi_legislature/AMO30_tous_acteurs_tous_mandats_tous_organes_historique.json.zip` (chemin d'après le dépôt de la 17e législature, **à confirmer** au premier import réel) ;
- Sénat : `https://data.senat.fr/data/dosleg/dosleg.zip`, `https://data.senat.fr/data/senateurs/ODSEN_HISTOGROUPES.csv` ;
- Parlement européen (API v2, une requête toutes les 0,7 s) : `meps?parliamentary-term=<9|10>&country-of-representation=FR` (**paramètres à confirmer**), `meps/{id}`, `corporate-bodies/{id}`, `meetings?year=<2019…>`, `meetings/{séance}/decisions`. Environ 600 requêtes, soit 7 à 8 minutes.

Avec `--from <dossier>` (fichiers déjà sur le disque) : `amo30/`, `scrutins-14/` à `scrutins-17/` (zips), `dosleg/` (zip), `histogroupes/` (CSV).

### Mémoire (256 Mo)

- **14e législature** : un seul document JSON (`Scrutins_XIV.json`) lu **en flux** par `JsonObjectStream` : chaque scrutin est extrait puis analysé seul (16 Mo au plus par scrutin), l'arbre complet n'est jamais construit. Les 15e à 17e : un fichier par scrutin, lus de la même façon.
- **AMO30** : de chaque acteur, seuls ses mandats `ASSEMBLEE`, `GP` et `PARPOL` avec leurs dates ; des organes, seuls les groupes et les partis (libellé et sigle).
- **Dosleg** (127 Mo de texte SQL) : lu **deux fois en flux, ligne par ligne**, sans base de données et sans rien exécuter : d'abord les blocs `COPY scr` et `COPY posvot` (scrutins et libellés des positions), puis `COPY votsen` (vote de chaque sénateur), compté aussitôt par groupe et jamais gardé. Le codage est lu dans `SET client_encoding`. Les autres tables sont sautées.
- Chaque paquet est encodé dès qu'il est construit. Non mesuré sur les vrais fichiers (pas de téléchargement sans accord) : à vérifier au premier import réel en préproduction, avec `-Xmx256m`.

## Ce qui est lu, et jamais gardé

- **Gardé** : identifiant et numéro du scrutin, date, titre, type, résultat, totaux, dossier ou texte ; identifiant, libellé et sigle des partis et des groupes ; les nombres par parti.
- **Lu pour compter, jamais écrit** : identifiants des députés (`acteurRef`), matricules des sénateurs (`senmat`), identifiants des eurodéputés, leurs positions et leurs appartenances datées.
- **Jamais lu** : noms et prénoms (AMO30, `ODSEN_HISTOGROUPES.csv`, table `sen` de Dosleg), naissance, profession, adresses, place dans l'hémicycle, délégations (`senmatdel`), statut du vote (`stavotidt`), photo, courriel.

## Décisions prises (à consigner dans `DECISIONS.md`)

1. **Un paquet par chambre et par période**, dans l'index commun, téléchargé en entier : la confidentialité de l'intérêt pour un parti l'emporte sur le volume (quelques Mo par législature, compressés par le reverse proxy).
2. **Le groupe tient lieu de parti** quand aucun parti n'est publié, toujours marqué `groupe` avec le libellé « groupe, rattachement au parti non publié » ; jamais de correspondance groupe → parti écrite par nous (ce serait une interprétation).
3. **Pas de position majoritaire** ni de « consigne » calculée : seulement les nombres.
4. **Sénat, abstentions** = votants − suffrages exprimés ; **adopté** quand les voix « pour » atteignent la majorité absolue donnée par le fichier (`scrmaj`). Les colonnes « séance » (`…sea`) ne sont pas utilisées. Position d'un sénateur d'après le libellé de `posvot` (« Pour », « Contre », « Abstention », tout autre = non-votant).
5. **Parlement européen** : élus en France seulement dans la répartition (les autres partis nationaux ne servent pas au comparateur), totaux de tout le Parlement ; même numéro de vote que les paquets mensuels.
6. **Un groupe sans liste nominative** dans un scrutin de l'Assemblée (cas non vu dans les fichiers, tous nominatifs) : ses décomptes sont portés sur la ligne du groupe (`groupe`).
7. **Lien du scrutin** de l'Assemblée : `https://www.assemblee-nationale.fr/dyn/<lég.>/scrutins/<numéro>` pour toutes les législatures (**à vérifier** pour les 14e et 15e) ; Sénat : `https://www.senat.fr/scrutin-public/<session>/scr<session>-<numéro>.html` ; Parlement européen : procès-verbal des votes par appel nominal.
8. **`--history` à part** : l'historique (gros fichiers, environ 600 requêtes au Parlement européen) ne ralentit jamais l'import quotidien.

## Jeux d'exemple

Fichiers fictifs aux formats officiels dans `tools/open-data-importer/src/main/resources/example/`, produits par `scripts/generate_example.py` (même script que le reste, les fichiers existants ne changent pas) :

- `an-historique/amo30/` : 6 députés fictifs (`PA9100xx`) sur les 14e à 17e législatures, 2 groupes, 2 partis de rattachement, un changement de parti au 1er janvier 2020, un député sans parti (repli sur le groupe), un sénateur à écarter ;
- `an-historique/scrutins-14/Scrutins_XIV.json` (un seul document, liste `scrutins`, 3 scrutins de juillet 2012 au 24 novembre 2016) ; `scrutins-15/` à `scrutins-17/` (un fichier par scrutin, 5 scrutins dont 2 motions de censure) ;
- `senat-historique/dosleg/dosleg.sql` (Latin-1 : `SET client_encoding`, une table `sen` à sauter, `posvot`, `scr` avec un scrutin de 2005 écarté, `votsen` avec une délégation) et `senat-historique/ODSEN_HISTOGROUPES.csv` (Latin-1, `;`, 5 sénateurs, un changement de groupe, un départ) ;
- `pe/meps-term-9.json`, `pe/meps-term-10.json`, `pe/meps/990011.json` et `990012.json`, séances `MTG-PL-2019-07-03` (avant le 15 juillet 2019 : écartée) et `MTG-PL-2021-03-10` ; la 10e législature reprend les séances de septembre et octobre 2026.

Paquets embarqués régénérés dans `shared/ui/src/commonMain/composeResources/files/parlement/` avec la clé d'exemple : 9 paquets d'historique de plus (`historique-an-14` à `-17`, `historique-senat-2006`, `-2018`, `-2026`, `historique-pe-9`, `-10`) ; les paquets existants sont identiques à l'octet près, seul l'index change (nouvelles sources et nouveaux paquets).

## Tests

TESTS_PLACEHOLDER

## À vérifier au premier import réel (préproduction, avec l'accord de la propriétaire)

- Adresse exacte d'AMO30 et paramètres `parliamentary-term` / `country-of-representation` de `meps` (API européenne).
- En-têtes réels de `ODSEN_HISTOGROUPES.csv` (colonnes trouvées par leur nom : matricule, « code … groupe », nom court, début et fin d'appartenance hors fonction) et format des dates.
- Codes et libellés de `posvot`, format de `scrdat`, encodage déclaré du dump.
- Forme de `referenceText` dans les décisions du Parlement européen.
- Mémoire et durée réelles (Dosleg : 127 Mo de texte ; 15e législature : 4 417 scrutins).

## Fichiers communs touchés

- `shared/core/.../voir/parlement/ParliamentHistory.kt` (nouveau) et son test `ParliamentHistoryTest.kt` (nouveau) ; `ParliamentFormat.kt` inchangé.
- `shared/ui/src/commonMain/composeResources/files/parlement/` : paquets d'exemple régénérés.
- Aucun changement dans `server/`, `infra/`, `.github/`, ni dans le code de `shared/ui`. L'application ignore pour l'instant les paquets `historique-*` (`ParliamentRepository.readPackage` ne les reconnaît pas) : leur lecture et leur téléchargement sont à brancher par « Décider » ou « Voir » avec `ParliamentHistory.read`.
