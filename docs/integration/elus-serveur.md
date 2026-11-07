# Note d'intégration : l'annuaire des élus sur le serveur (bloc elus-serveur, D-047)

Bloc de la vague 3 de « Comprendre » (contrat `docs/integration/comprendre-vague-3.md`, section 1). Périmètre : `server/` et cette note. Rédigée le 7 novembre 2026 pour la session principale, qui fusionne et met à jour CHANGELOG, ROADMAP, DECISIONS, FORMATS, REGISTRE_DONNEES, THREAT_MODEL et GUIDE_PROPRIETAIRE.

**État** : terminé. Code, jeu fictif et tests commités ; `:server:test --tests "*.elus.*"` vert (42 tests, dont la vérification manuelle ignorée sans fichiers) ; mode `official` vérifié une fois sur les vrais fichiers (section 5). Reste à la session principale : fusion, docs générales, décisions de la section 8.

## 1. Sources vérifiées le 7 novembre 2026

Toutes les adresses ci-dessous ont été lues sur l'API publique de data.gouv.fr (`https://www.data.gouv.fr/api/1/datasets/<id>/`) et les fichiers téléchargés une fois dans un dossier temporaire hors du dépôt pour lire leurs en-têtes (jamais copiés dans le dépôt : ce sont des données personnelles réelles).

### 1.1 Répertoire national des élus (RNE)

- Jeu `5c34c4d1634f4173183a64f1`, ministère de l'Intérieur, page `https://www.data.gouv.fr/datasets/repertoire-national-des-elus-1`.
- **Licence** : Licence Ouverte 2.0 (`lov2`). Réutilisation libre, y compris commerciale, à condition de citer la source et la date de mise à jour.
- **Fréquence annoncée** : trimestrielle ; dernière mise à jour lue : 11 août 2026 (champ `last_update` du jeu).
- **Format** : CSV, séparateur point-virgule, UTF-8 **sans** BOM, fins de ligne CRLF, guillemets à la mode RFC 4180 (rares : seulement des noms d'intercommunalités comme `"Cc ""Creuse Confluence"""`). Dates au format `AAAA-MM-JJ`. Un en-tête contient une double espace (`Libellé du  département` dans le fichier des assemblées) : le lecteur compare les noms de colonnes après avoir réduit les espaces.
- **Adresses stables** : chaque fichier a un identifiant de ressource qui ne change pas d'une mise à jour à l'autre (les fichiers sont remplacés sur place) ; l'adresse `https://www.data.gouv.fr/api/1/datasets/r/<ressource>` redirige vers la dernière version sur `static.data.gouv.fr`.

| Fichier (titre de la ressource) | Ressource | Lignes (août 2026) | Mandat PPF |
|---|---|---|---|
| `elus-conseillers-municipaux-cm.csv` | `d5f400de-ae3f-4966-8cb6-a85c70c6c24a` | 511 225 (65 Mo) | `municipal` (maires et adjoints compris : colonne « Libellé de la fonction ») |
| `elus-conseillers-communautaires-epci.csv` | `41d95d7d-b172-4636-ac44-32656367cdc7` | 62 129 (10 Mo) | `communautaire` |
| `elus-conseillers-departementaux-cd.csv` | `601ef073-d986-4582-8e1a-ed14dc857fba` | 4 037 | `departemental` |
| `elus-conseillers-regionaux-cr.csv` | `430e13f9-834b-4411-a1a8-da0b4b6e715c` | 1 745 | `regional` |
| `elus-membres-assemblee-ma.csv` | `a595be27-cfab-4810-b9d4-22e193bffe35` | 557 | `collectivite` |
| `elus-deputes-dep.csv` | `1ac42ff4-1336-44f8-a221-832039dbc142` | 577 | `depute` |
| `elus-senateurs-sen.csv` | `b78f8945-509f-4609-a4a7-3048b8370479` | 348 | `senateur` |
| `elus-representants-Parlement-européen-rpe.csv` | `70957bb0-f19f-40c5-b97b-90b3d4d71f9e` | 81 | `europeen` |

Non importés : `elus-maires-mai.csv` (les maires sont déjà dans le fichier des conseillers municipaux, même nombre : 34 826), conseillers d'arrondissement (`elus-conseillers-darrondissements-ca.csv`), conseillers et assemblée des Français de l'étranger (hors contrat).

Colonnes **lues** (repérées par leur nom, jamais par leur position) : codes et libellés du département, de la collectivité à statut particulier, de la commune, de l'intercommunalité (`N° SIREN`, `Libellé de l'EPCI`, `Code de la commune de rattachement`), du canton, de la région et de la section départementale, de la circonscription législative ; `Nom de l'élu`, `Prénom de l'élu`, `Date de début du mandat`, `Libellé de la fonction`, `Date de début de la fonction`.

Colonnes **ignorées** (jamais lues dans une variable, jamais stockées) : `Code sexe`, `Date de naissance`, `Code de la catégorie socio-professionnelle`, `Libellé de la catégorie socio-professionnelle`, `Code nationalité`, et pour les assemblées les sections et circonscriptions métropolitaines (voir limites).

Particularités relevées dans les vrais fichiers :
- Paris : le Conseil de Paris est dans le fichier municipal (commune `75056`, 163 élus) ; pas de conseillers départementaux à Paris.
- Métropole de Lyon : ses conseillers sont dans le fichier des assemblées (collectivité `69M`), pas dans celui des intercommunalités ; les communes de la Métropole n'ont ni intercommunalité ni canton.
- Corse (`94`), Martinique (`972`), Guyane (`973`), Saint-Pierre-et-Miquelon (`975`), Saint-Barthélemy (`977`), Saint-Martin (`978`), Wallis-et-Futuna (`986`), Polynésie française (`987`), Nouvelle-Calédonie (`988`) : assemblées dans le fichier des assemblées ; dans les fichiers municipaux, le code du département est vide pour les collectivités d'outre-mer et la Martinique ou la Guyane (seul le code de la collectivité est rempli).
- Collectivité européenne d'Alsace : code `6AE` dans le fichier départemental, cantons `67xx` et `68xx`.
- Fichier régional : la section départementale perd parfois son zéro initial (`2` pour l'Aisne, `8` pour les Ardennes) ; le Rhône a deux sections (`69` hors Métropole et `69M`).
- Députés et sénateurs des Français établis hors de France : département `ZZ` (non importés, aucun paquet ne les accueille dans le contrat) ; Saint-Barthélemy et Saint-Martin partagent une circonscription (`ZX`, code `97701`).

### 1.2 Codes postaux

- **Base officielle des codes postaux**, La Poste, jeu `545b55e1c751df52de9b6045`, page `https://www.data.gouv.fr/datasets/base-officielle-des-codes-postaux`.
- **Licence** : Licence Ouverte 2.0 (`lov2`) : réutilisation permise. Fréquence annoncée : semestrielle ; fichier daté du 8 octobre 2026.
- Fichier CSV : ressource `008a2dda-2c60-4b63-b910-998f6f818089`, qui redirige vers `https://data.laposte.fr/data-fair/api/v1/datasets/laposte-hexasmal/raw` (1,5 Mo, 39 192 lignes).
- Format : séparateur point-virgule, **Windows-1252** (et non UTF-8, bien que le serveur annonce `charset=utf-8`), fins de ligne LF, en-tête `#Code_commune_INSEE;Nom_de_la_commune;Code_postal;Libellé_d_acheminement;Ligne_5`. Colonnes lues : `#Code_commune_INSEE`, `Code_postal`.
- Paris, Lyon et Marseille n'y figurent que par arrondissement (`75101` à `75120`, `69381` à `69389`, `13201` à `13216`) : le serveur rattache ces codes à la commune (`75056`, `69123`, `13055`) grâce au Code officiel géographique.

### 1.3 Code officiel géographique (Insee)

- Jeu `58c984b088ee386cdb1261f3`, Insee, page `https://www.data.gouv.fr/datasets/code-officiel-geographique-1`. **Licence** : Licence Ouverte (`fr-lo`). Mise à jour annuelle (millésime 2026 publié le 24 février 2026).
- Les ressources changent chaque année (une par millésime) : le serveur les retrouve par leur titre dans l'API du jeu (le millésime le plus récent) ; elles redirigent vers `https://www.insee.fr/fr/statistiques/fichier/...`.
- Format : CSV, séparateur virgule, UTF-8 sans BOM, tous les champs entre guillemets.
- Fichiers utilisés : communes (`v_commune_AAAA.csv` : `TYPECOM`, `COM`, `DEP`, `CTCD`, `CAN`, `LIBELLE`, `COMPARENT`), cantons (`v_canton_AAAA.csv` : `CAN`, `DEP`, `LIBELLE`, `TYPECT`), départements (`v_departement_AAAA.csv` : `DEP`, `REG`, `TNCC`, `LIBELLE`), régions (`v_region_AAAA.csv` : `REG`, `LIBELLE`), collectivités d'outre-mer (`v_comer_AAAA.csv` : `COMER`, `TNCC`, `LIBELLE`) et leurs communes (`v_commune_comer_AAAA.csv` : `COM_COMER`, `LIBELLE`, `COMER`), événements des communes (`v_mvt_commune_AAAA.csv` : `DATE_EFF`, `TYPECOM_AV`, `COM_AV`, `TYPECOM_AP`, `COM_AP`).
- **Commune vers canton** : la colonne `CAN` donne le canton de chaque commune située dans un seul canton (vérifié : identique aux résultats officiels des départementales de 2021 pour les 34 096 communes comparées). Pour les 276 communes partagées entre plusieurs cantons, `CAN` est un « pseudo-canton » (`TYPECT` = `V`) qui ne dit pas lesquels : voir 1.5.

### 1.4 Commune vers circonscription législative

- Aucune table à jour n'est publiée telle quelle : la table du ministère de l'Intérieur (jeu `5369a160a3a729239d2065bc`, licence `fr-lo`) date de 2017 et ne connaît pas les communes nouvelles créées depuis ; les résultats des législatives de 2024 par commune ne donnent pas la circonscription.
- **Source retenue** : résultats du 1er tour des législatives de 2022 au niveau des communes, ministère de l'Intérieur, jeu `62a6eb8598f12fafcba7ba7c`, **licence Ouverte (`fr-lo`)**, ressource `87d3701e-4d5d-4963-89f4-b402cb1c4403` (`resultats-par-niveau-subcom-t1-france-entiere.txt`, 18 Mo, `static.data.gouv.fr`). Les circonscriptions n'ont pas changé depuis 2012 (élections de 2012, 2017, 2022 et 2024 sur le même découpage). Format : séparateur point-virgule, Windows-1252. Colonnes lues : `Code du département`, `Code de la circonscription`, `Code de la commune` (les six premières seulement ; les colonnes des candidats ne sont jamais lues).
- Codes du ministère convertis : départements d'outre-mer `ZA` (971), `ZB` (972), `ZC` (973), `ZD` (974), `ZM` (976), collectivités `ZS` (975), `ZX` (977 et 978), `ZW` (986), `ZP` (987), `ZN` (988) ; le code Insee d'une commune d'outre-mer est `97` ou `98` suivi des trois chiffres du ministère.
- Les communes fusionnées depuis juin 2022 sont suivies grâce aux événements du Code officiel géographique (92 cas résolus sur 182 codes disparus ; les 90 autres sont des communes d'outre-mer hors du fichier des communes de l'Insee, retrouvées dans celui des collectivités d'outre-mer, et 4 communes de métropole rétablies ou modifiées, qui restent sans circonscription).
- 125 communes sont partagées entre plusieurs circonscriptions (Paris : 18, Marseille : 7, Lyon : 4...) : elles gardent la liste de toutes.

### 1.5 Communes partagées entre plusieurs cantons

- **Source retenue** : résultats du 1er tour des départementales de 2021 au niveau des communes, ministère de l'Intérieur, jeu `60d1fa73986a6e7da8017808`, ressource `f659a06c-c8a2-4226-aa85-2607abe2c1e1` (`resultats-par-niveau-subcom-t1-france-entiere.txt`, 12 Mo). Même format que 1.4 ; colonnes lues : `Code du département`, `Code du canton`, `Code de la commune`.
- Utilisée **seulement** pour les communes dont le canton Insee est un pseudo-canton : elle donne la liste exacte des cantons (Lille : 6, Rennes : 6, Marseille : 12). La règle approchée par le « bureau centralisateur » du fichier des cantons se trompait pour 45 des 276 communes : elle n'est pas utilisée.
- **Licence** : le jeu ne précise pas sa licence sur data.gouv.fr (`notspecified`). Ce sont des informations publiques d'une administration, librement réutilisables sans licence lorsqu'elles sont gratuites (code des relations entre le public et l'administration, articles L321-1, L322-1 et L323-2), à condition de ne pas les altérer et de citer la source et la date. À confirmer par la propriétaire ; sans cette source, ces 276 communes n'auraient pas de canton et l'application afficherait tous les conseillers départementaux du département (règle de repli du contrat, 1.1).

## 2. Hôtes contactés (mode `official` seulement)

`www.data.gouv.fr` (API et redirections), `static.data.gouv.fr` (fichiers du RNE et des élections), `www.insee.fr` (Code officiel géographique), `data.laposte.fr` (codes postaux). Le serveur suit lui-même les redirections et refuse tout autre hôte. Aucun de ces hôtes n'est bloqué par `tools/check-no-trackers.sh` (liste de motifs interdits, pas de liste blanche) : script non modifié.

En mode `example` (par défaut : développement, tests, préproduction), le serveur ne contacte personne.

## 3. Ce qui est fait

Paquet `server/src/main/kotlin/org/politisationpopulairefrancaise/server/elus/` :

| Fichier | Rôle |
|---|---|
| `Csv.kt` | Lecteur CSV en flux (un enregistrement à la fois, jamais tout le fichier) ; tolère BOM, CRLF ou LF, guillemets RFC 4180, lignes vides, guillemet isolé ; refuse un enregistrement de plus d'un million de caractères. `CsvInput` détecte l'encodage sur les 64 premiers Ko (UTF-8 strict, sinon Windows-1252). `CsvTable` repère les colonnes par leur nom (sans accents, casse, espaces doublés ni `#` initial), refuse un fichier auquel manque une colonne attendue et ignore les autres. Les messages d'erreur nomment le fichier, la colonne ou le numéro de ligne, jamais une valeur. |
| `Rne.kt` | Lecture des huit fichiers du RNE en `StagedOfficial` : seules les colonnes du contrat sont déclarées (section 1.1) ; nom affiché `Prénom Nom` ; dates `AAAA-MM-JJ` (ou `JJ/MM/AAAA` des anciens fichiers) ; rang dans le conseil (maire ou président 0, n-ième adjoint ou vice-président n, autres fonctions, puis membres) pour le tri seulement. |
| `Geography.kt` | Code officiel géographique, codes postaux, circonscriptions (2022) et cantons des communes partagées (2021), avec suivi des fusions de communes ; noms des circonscriptions (« 1re circonscription du Rhône »). |
| `ElusBuilder.kt` | Construction déterministe des paquets et de l'index (ordre fixe, sans paramètre régional ni ordre de table de hachage). |
| `ElusStore.kt` | Tables de `V6__elus.sql` ; verrou consultatif PostgreSQL (un seul import à la fois, même entre processus) ; élus écrits dans la table de travail par `COPY` en flux. |
| `ElusImporter.kt` | Un import : géographie, puis les huit fichiers, puis un paquet à la fois (corps compressé en base, seule sa description reste en mémoire), garde des 20 %, activation en une transaction. |
| `ElusService.kt` | Configuration (`ElusConfig`, `ElusSetup`), import servi en mémoire (index et empreintes), planification. |
| `ElusApi.kt` | Routes. |
| `ElusSources.kt`, `OfficialSource.kt` | Sources : `ExampleSource` (ressources du serveur), `DirectorySource` (dossier local), `OfficialSource` (data.gouv.fr, client HTTPS qui suit lui-même les redirections et n'accepte que les hôtes de la section 2, 400 Mo au plus par fichier). |

Autres fichiers : `server/src/main/resources/db/migration/V6__elus.sql` (numéro réservé) ; le jeu fictif dans `server/src/main/resources/elus/example/` ; branchement dans `Application.kt` (paramètre `elus` de `module`, routes) ; `server/build.gradle.kts` (`PPF_RNE_REFRESH_DAYS=0` pendant les tests, comme le ménage des revendications) ; `TestDatabase.reset()` vide aussi les tables de l'annuaire ; une ligne dans `DevServer.kt`.

Aucune dépendance nouvelle : JDK (`java.net.http`, `java.util.zip`), pilote PostgreSQL (`COPY`), kotlinx.serialization et Ktor déjà présents. `tools/check-no-trackers.sh` et `tools/check-dependencies.sh` ne sont pas modifiés.

### 3.1 Règles de construction des paquets

- **Paquets** : chaque département du Code officiel géographique (101), chaque collectivité d'outre-mer qui a au moins un élu dans le RNE (975, 977, 978, 986, 987, 988), et `FR` (membres du Parlement européen). Index trié par code (`01`... `19`, `21`... `29`, `2A`, `2B`, `30`... `976`, `977`... `988`, `FR`).
- **Communes** : celles du Code officiel géographique (et, outre-mer, celles des collectivités d'outre-mer), triées par nom sans accents ni casse, puis par code. Codes postaux de La Poste (ceux des arrondissements pour Paris, Lyon et Marseille). Intercommunalité : celle où siègent les conseillers communautaires de la commune (fichier du RNE). Cantons : celui du Code officiel géographique, ou la liste des départementales de 2021 pour une commune partagée ; aucun canton là où il n'y a pas de conseil départemental (Corse, Martinique, Guyane, Paris, Métropole de Lyon). Circonscriptions : toutes celles de la commune.
- **Qui va dans quel paquet** : chaque élu va dans le paquet de son département ou de sa collectivité, avec ces regroupements : l'Assemblée de Corse dans `2A` et `2B` ; la Collectivité européenne d'Alsace (conseil départemental `6AE`, section régionale `6AE`) entière dans `67` et `68` ; la Métropole de Lyon (`69M`) dans `69` ; le député de Saint-Barthélemy et Saint-Martin (`ZX`, circonscription `97701`) dans `977` et `978`. Une intercommunalité à cheval sur deux départements a tous ses conseillers dans les paquets des deux. Le président du conseil régional est dans le paquet de tous les départements de sa région (les autres conseillers régionaux restent dans le paquet de leur section). Les députés et sénateurs des Français établis hors de France (`ZZ`) ne sont dans aucun paquet.
- **Territoires** (`territory`, contrat 1.4) : commune (code Insee), intercommunalité (SIREN, avec `commune` = commune de rattachement donnée par le RNE), canton, région, collectivité (code du RNE : `69M`, `94` pour la Corse, `972`, `973`, `975`, `977`, `978`, `986`, `987`, `988`), circonscription (`6901`, `2A01`, `97101`, `97701`), département pour les sénateurs (`69`, `2A`, `972`, `977`), `FR`.
- **Tri des élus** : mandat (ordre du contrat), territoire, rang dans le conseil, nom sans accents ni casse, puis tous les autres champs : deux imports des mêmes fichiers donnent les mêmes octets (testé).
- **JSON** : compact, UTF-8, champs dans l'ordre du contrat ; `function`, `functionSince`, `epci` et `region` écrits `null` quand il n'y en a pas ; `commune` absent sauf pour `communautaire`. `region` vaut `null` pour les collectivités d'outre-mer et pour `FR`.

### 3.2 Mise à jour et sécurité (contrat 1.5)

- `PPF_RNE_SOURCE` : `example` (défaut) ou `official`. **`official` n'est suivi que si `PPF_ENVIRONMENT=production` ou `preproduction`** (décision de la propriétaire du 7 novembre 2026 : « on reste dans la vraie info même en preprod ») ; ailleurs le serveur sert le jeu fictif et l'écrit dans son journal (aucune donnée réelle en développement ni en test, même par erreur de configuration).
- `PPF_RNE_REFRESH_DAYS` : jours entre deux imports (7 par défaut). Import au démarrage si rien n'est servi ou si la source a changé ; après un échec, nouvel essai six heures plus tard ; `0` coupe tout import automatique (tests).
- Le dernier import réussi reste servi tant qu'un nouvel import n'a pas réussi : un fichier introuvable, une colonne manquante, un encodage cassé, une coupure du téléchargement font échouer l'import sans rien changer.
- Garde des 20 % : un import est refusé si, pour **un seul** mandat ou au total, le nombre d'élus baisse de plus de 20 % par rapport à l'import servi de la même source (un fichier tronqué, même petit, est ainsi repéré). Le motif (`drop:municipal:55:40`) est gardé dans `elus_import.failure`.
- Jeu fictif : l'import refuse toute personne dont le prénom ne commence pas par `[EXEMPLE] `.
- Journaux : nombres, codes de fichiers et de départements, dates. Jamais un nom, jamais une adresse IP (aucune journalisation des requêtes, comme partout).
- Authenticité : HTTPS et empreinte de l'index pour la phase 1 ; signature des paquets par le service de signature à venir (à noter dans THREAT_MODEL).

## 4. API (contrat 1.4)

- `GET /api/v1/elus` : l'index ; `GET /api/v1/elus/{code}` : un paquet, `404 {"error": "unknown_package", ...}` pour tout autre code.
- `ETag: "<sha256>"` : **entre guillemets**, comme le veut HTTP (RFC 9110). La valeur entre les guillemets est exactement le `sha256` de l'index. `If-None-Match` accepte la valeur avec ou sans guillemets, faible (`W/`) ou non, une liste, ou `*` : réponse `304` sans corps.
- `Cache-Control: public, no-cache` (toute mémoire cache peut garder la réponse, à condition de revalider), `Vary: Accept-Encoding`.
- Compression : un client qui envoie `Accept-Encoding: gzip` reçoit le corps compressé tel qu'il est stocké (`Content-Encoding: gzip`) ; les autres reçoivent le JSON exact. Le reverse proxy Caddy (`encode zstd gzip`) ne recompresse pas une réponse déjà compressée.
- `503 {"error": "elus_unavailable", ...}` avec `Retry-After: 60` tant qu'aucun import n'a réussi (quelques secondes après le premier démarrage en mode `example`).
- Sans base de données, la réponse `503 database_unavailable` commune à `/api/v1/` s'applique.

**Pour le bloc elus-app** : l'empreinte à comparer est celle des octets reçus après décompression ; si le client lit l'en-tête `ETag`, il retire les guillemets.

## 4 bis. ElusLookup : recherche en lecture seule pour le back-office

Pour la file `elus.certifications` du pôle « Décider » (contrat du back-office, section 7) : le certificateur voit, à côté d'une demande de certification d'un compte d'élu, les mandats en cours de l'annuaire qui lui correspondent. Forme validée par « Décider » le 7 novembre 2026. Fichiers : `server/.../server/elus/ElusLookup.kt` (recherche, statut, identifiant) et `ElusLookupHtml.kt` (fragment kotlinx.html) ; tests `ElusLookupTest`.

```kotlin
class ElusLookup(service: ElusService) {
    fun find(name: String, mandate: Mandate? = null, territory: String? = null, limit: Int = 50): List<ElusMatch>
    fun mandateStatus(id: String): MandateStatus
    fun sourceUpdatedOn(): LocalDate?            // null tant qu'aucun import n'est servi
    companion object {
        fun mandateId(mandate: Mandate, territory: String, name: String, since: String): String   // "rne1:" + 32 hex
        fun isValidId(id: String): Boolean
    }
}
data class ElusMatch(val id: String, val name: String, val mandate: Mandate, val function: String?,
                     val territory: String, val territoryName: String, val since: String, val sourceUpdatedOn: LocalDate)
sealed interface MandateStatus {
    data class Current(val sourceUpdatedOn: LocalDate)   // figure dans le dernier import servi
    data class NotFound(val sourceUpdatedOn: LocalDate)  // absent du dernier import servi (fini, corrigé, ou jamais présent)
    data object Unavailable                              // aucun import servi (premier démarrage) : ne rien conclure
}
fun FlowContent.elusMatches(matches: List<ElusMatch>, sourceUpdatedOn: LocalDate?, limit: Int = 50)
```

- **Identifiant stable** `rne1:` + 32 caractères hexadécimaux : format dans `docs/FORMATS.md`, section 4 octies. Il est calculé sur les données servies (mandat, territoire, nom « Prénom Nom » sans accents ni majuscules, date de début du mandat), jamais stocké : deux imports des mêmes fichiers donnent les mêmes identifiants, et un changement de fonction (un conseiller qui devient maire) ne le change pas. Le paquet ne garde que le nom affiché « Prénom Nom » (D-047) : l'empreinte porte sur ce nom réuni et non sur deux champs séparés ; deux personnes qui ne différeraient que par la frontière entre prénom et nom, sur le même mandat, le même territoire et le même jour, auraient le même identifiant (cas jamais vu, accepté). Deux homonymes parfaits dans ce cas partageraient aussi l'identifiant : le certificateur les voit tous les deux.
- **`find`** : tous les mots du nom cherché (deux lettres au moins ; accents, casse, traits d'union, apostrophes et ordre des mots ignorés) doivent être des mots du nom de l'élu ; filtres facultatifs par mandat et par code de territoire exact ; résultats dans l'ordre des paquets, sans doublon (un président de région ou une intercommunalité à cheval sur deux départements sont dans plusieurs paquets), 50 au plus par défaut. La recherche lit les paquets du dernier import servi (la table de travail est vidée après chaque import) : seulement le paquet `FR` pour le Parlement européen, seulement celui du département pour un mandat municipal ou sénatorial dont le territoire est donné, tous les paquets sinon (de l'ordre d'une seconde sur le vrai répertoire ; « Décider » passe le mandat et le territoire quand la demande les donne).
- **`mandateStatus`** : `Current` ou `NotFound`, avec la date de mise à jour de la source. Le RNE ne donne ni date de fin ni identifiant : on répond seulement « en cours selon la source mise à jour le … ». `Unavailable` (ajouté à la forme validée) évite de prendre un serveur qui vient de démarrer pour un mandat terminé. Le statut s'appuie sur un ensemble compact des identifiants de l'import servi (16 octets par mandat, une dizaine de Mo pour tout le répertoire), construit à la première question après chaque import.
- **`elusMatches`** : tableau accessible (légende, en-têtes de colonnes et de lignes) : nom, mandat (« Conseil municipal », « Assemblée nationale »…, libellés neutres), fonction, territoire (nom et code), début du mandat, identifiant ; la date de la source et l'avertissement « le répertoire ne donne ni date de fin ni identifiant officiel » ; un message quand rien ne correspond, quand l'annuaire n'est pas encore chargé, ou quand la liste atteint la limite.
- **Vie privée** : seulement les données publiques de l'annuaire (section 1.2 du contrat) ; aucun journal (ni nom cherché, ni résultat) ; rien n'est écrit en base. Le journal d'audit du socle trace la décision du certificateur, pas la recherche.
- **Branchement** : « Décider » crée `ElusLookup(elusService)` dans `Application.module` à côté de sa file et appelle `find` et `elusMatches` dans `render`, `mandateStatus` pour une certification déjà accordée (contrôle périodique ou à l'affichage).

## 5. Vérification sur les vrais fichiers (7 novembre 2026, RNE du 11 août 2026)

Faite une fois par `ElusOfficialCheck` sur les fichiers téléchargés dans un dossier temporaire hors du dépôt (chiffres seulement, aucun nom) :
- import réussi en 37,5 s sur la machine de développement chargée ; pic de mémoire du tas 310 Mo (tas maximal de 512 Mo, PostgreSQL embarqué dans le même processus de test) ;
- élus par mandat : municipal 511 225, communautaire 62 129, départemental 4 037, régional 1 745, collectivité 557, député 566 (577 moins les 11 des Français de l'étranger), sénateur 336 (348 moins 12), européen 81 ;
- 108 paquets (101 départements, 975, 977, 978, 986, 987, 988, `FR`) ; 85,7 Mo de JSON en tout, 8,0 Mo compressés ; index de 66 Ko, 6 342 codes postaux ; plus gros paquet 2,1 Mo (200 Ko compressé environ) ;
- Nord (59) : 1,96 Mo (187 Ko compressé), 647 communes, 41 cantons, 21 circonscriptions, 13 672 élus ; Rhône (69) : 909 Ko (85 Ko), 266 communes, 150 membres de la Métropole de Lyon ; Paris (75) : 66 Ko (6 Ko), 18 circonscriptions ; `FR` : 10 Ko ;
- 34 963 communes : toutes ont au moins un code postal ; 9 sans circonscription ; 563 sans canton (Corse, Martinique, Guyane, Paris et Métropole de Lyon, qui n'ont pas de conseil départemental, et quelques communes partagées absentes des résultats de 2021) ; 28 882 avec une intercommunalité.

## 5 bis. Format des codes de circonscription (`territory` des députés, `communes[].circonscriptions`)

Code du RNE = code du département (ou de la collectivité) suivi du numéro sur **deux chiffres** :
- métropole : `0101`, `6901`, `7518`, `5921` (quatre caractères) ;
- Corse : `2A01`, `2A02`, `2B01`, `2B02` ;
- départements d'outre-mer : `97101` à `97104`, `97201`..., `97301`, `97401` à `97407`, `97601`, `97602` (cinq caractères) ;
- collectivités d'outre-mer : `97501` (Saint-Pierre-et-Miquelon), `97701` (Saint-Barthélemy et Saint-Martin, circonscription commune, présente dans les paquets `977` et `978`), `98601` (Wallis-et-Futuna), `98701` à `98703` (Polynésie française), `98801`, `98802` (Nouvelle-Calédonie) ;
- Français établis hors de France : `ZZ01` à `ZZ11` dans le RNE, **dans aucun paquet** (aucun paquet `ZZ` dans le contrat).

Le numéro est toujours les deux derniers caractères ; le département ou la collectivité, tout ce qui précède.

## 6. Tests

`gradlew :server:test --tests "*.elus.*"` (PostgreSQL embarqué, comme les autres tests du serveur) :
- `CsvTest` : BOM, CRLF, lignes vides, guillemets doublés, séparateurs et sauts de ligne entre guillemets, colonne manquante refusée par son nom sans citer de valeur, colonnes trouvées malgré accents, casse et espaces, enregistrements trop courts ignorés et comptés, détection UTF-8 et Windows-1252, séquence UTF-8 cassée refusée.
- `RneTest` : colonnes conservées (et jamais déclarées : sexe, naissance, profession, nationalité), dates, rangs, sections régionales, Corse, Français de l'étranger, intercommunalités, codes du ministère, suivi des fusions de communes, noms des circonscriptions.
- `ElusImportTest` : import du jeu fictif (nombres par mandat, paquets, empreintes et tailles de l'index), contenu du paquet du Rhône (commune partagée entre quatre circonscriptions, Métropole de Lyon, président de région d'un autre département), champs exacts du JSON, minimisation, déterminisme (mêmes octets à deux imports), garde des 20 %, import qui échoue sans effacer le précédent, refus d'une personne non marquée `[EXEMPLE]`, planification.
- `ElusLookupTest` : identifiant (empreinte des champs canoniques, insensible aux accents et à la casse), aucune réponse avant le premier import (`Unavailable`), recherche insensible aux accents, à la casse et à l'ordre des mots, filtres par mandat et territoire, dédoublonnage, identifiants identiques entre deux imports identiques, mandat retiré par l'import suivant (`NotFound`, les autres restent `Current`), fragment HTML.
- `ElusApiTest` : 503 avant le premier import, 200 avec `ETag` et type, 304, 404 `unknown_package`, gzip, drapeau `example`, configuration (`official` en production et en préproduction seulement).
- `OfficialSourceTest` : choix du millésime complet le plus récent du Code officiel géographique, import « officiel » sans le drapeau `example` (data.gouv.fr simulé, aucun réseau), ressource disparue, hôtes et schémas refusés.
- `ElusOfficialCheck` : vérification manuelle sur les vrais fichiers (section 5), ignorée sans `ELUS_OFFICIAL_DIR`.

`gradlew :server:runDev` sert le jeu fictif : http://localhost:8080/api/v1/elus et http://localhost:8080/api/v1/elus/69.

## 7. Le jeu fictif

`server/src/main/resources/elus/example/` : les huit fichiers du RNE avec leurs en-têtes exacts (y compris les colonnes de sexe, naissance, profession et nationalité, remplies de valeurs fictives pour vérifier qu'elles ne sortent jamais), 148 personnes `[EXEMPLE] Prénom Nom` (prénoms épicènes, noms visiblement inventés : Exemplaire, Fictif, Maquette...), et les extraits réels des fichiers géographiques (lignes copiées telles quelles ; pour les résultats électoraux, seules les six colonnes territoriales, sans aucun nom de candidat).

Communes réelles : Lyon, Villeurbanne, Villefranche-sur-Saône (69) ; Lille, Roubaix, Villeneuve-d'Ascq (59) ; Marseille, Aix-en-Provence (13) ; Rennes, Bruz (35) ; Paris (75). Mandats couverts : maire, adjoints et conseillers municipaux de chaque commune ; intercommunalités réelles (Métropole européenne de Lille, Aix-Marseille-Provence, Rennes Métropole, Grand Paris, Villefranche Beaujolais Saône) avec président et vice-président ; conseillers départementaux de deux cantons par commune avec un président par département ; conseillers régionaux de chaque section, présidents de région rattachés à un autre département ; assemblée de la Métropole de Lyon ; un député par circonscription (deux au plus par commune : Lyon est partagée entre quatre circonscriptions et en a deux), un député des Français de l'étranger (écarté) ; deux sénateurs par département ; trois eurodéputés.

## 8. Limites connues et suites

- **Format à compléter (proposition, non faite)** : rien dans le paquet ne dit à quelle collectivité appartient une commune. Pour la Métropole de Lyon, ses 59 communes n'ont ni intercommunalité ni canton, et les élus `collectivite` de territoire `69M` sont dans le paquet `69`, mais l'appareil ne peut pas savoir qu'une commune comme Villefranche-sur-Saône n'en fait pas partie (sinon qu'elle a une intercommunalité et un canton). Même question pour la Corse, la Martinique et la Guyane (là, tout le paquet est concerné). Proposition pour un format 2 : un champ facultatif `collectivite` par commune (`69M`, `20R`, `972R`...) et une liste `collectivites` (code, nom) dans le paquet. Je ne l'ai pas ajouté pour ne pas casser la lecture stricte du bloc elus-app.
- Les élus de la Métropole de Lyon ont une circonscription métropolitaine dans le RNE, et ceux de Guyane et de Martinique une section : non transmises (pas de champ dans le format).
- Libellés des intercommunalités : ceux du RNE, avec sa casse (« Metropole Du Grand Paris ») ; l'Insee publie des libellés propres mais seulement en tableur.
- Les communes partagées entre plusieurs cantons ou circonscriptions listent tous leurs cantons ou circonscriptions : sans adresse (que l'application ne demande pas), on ne peut pas faire mieux.
- 4 communes de métropole (rétablies ou modifiées depuis 2022) n'ont pas de circonscription, ce qui déclenche la règle de repli de l'appareil pour elles seules.
- Un nouveau découpage des circonscriptions (pas avant les prochaines législatives) demandera une nouvelle table : à surveiller.
- Les Français établis hors de France (11 députés, 12 sénateurs) n'ont pas de paquet : un paquet `ZZ` serait facile à ajouter si le contrat le prévoit.
- La licence des résultats des départementales de 2021 (section 1.5) est à confirmer par la propriétaire.
