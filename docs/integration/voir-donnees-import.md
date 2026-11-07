# « Voir », données ouvertes : l'outil d'import et la route du serveur

Bloc 1 du contrat `voir-donnees-ouvertes.md` (section 7, « Import et serveur »), écrit le 7 novembre 2026 sur la branche `feature/voir-donnees-import`. Sources et structure réelle des fichiers : `voir-donnees-sources.md`.

## En bref

- Un **outil séparé** (`tools/open-data-importer`) lit les fichiers des institutions, ne garde que les champs permis par le contrat, écrit les paquets et **signe l'index** avec la clé d'import. Il tourne une fois puis s'arrête (le minuteur systemd le relance).
- Le **serveur** ne fait que lire le dossier des paquets, en lecture seule, et ne détient aucune clé.
- **Données fictives** (`example`, par défaut) écrites dans les formats officiels, lues par les mêmes analyseurs que les vrais fichiers. Aucune personne réelle, aucun vote réel dans le dépôt.
- Les paquets d'exemple embarqués dans l'application sont dans `shared/ui/src/commonMain/composeResources/files/parlement/` (signés par la clé d'exemple `OpenDataKeys.EXAMPLE`).

## L'outil d'import

**Jar** : `ppf-open-data-importer.jar`, construit par `gradlew :tools:open-data-importer:buildFatJar` dans `tools/open-data-importer/build/libs/` (environ 31 Mo : bibliothèque de signature native comprise). Lancement : `java -jar ppf-open-data-importer.jar …`. En développement : `gradlew :tools:open-data-importer:run --args="--out <dossier> --key <fichier>"`.

| Argument | Variable d'environnement | Rôle |
|---|---|---|
| `--source example\|official` | `PPF_OPEN_DATA_SOURCE` | `example` par défaut (développement, tests, préproduction) ; `official` en production seulement |
| `--only an,senat,pe` | | sources à rafraîchir (toutes par défaut) ; les autres sont reprises telles quelles du dernier import réussi |
| `--out <dossier>` | `PPF_OPEN_DATA_DIR` | dossier des paquets (obligatoire) |
| `--key <fichier>` | `PPF_IMPORT_KEY_FILE` | fichier de la clé d'import (`nsec1…`), hors dépôt (obligatoire) |

L'argument l'emporte sur la variable. Exemples pour le minuteur : chaque jour `--only an,senat`, chaque semaine `--only pe`.

**Codes de sortie** :

| Code | Signification | Le dossier |
|---|---|---|
| 0 | import réussi | nouveaux paquets et nouvel index |
| 1 | arguments invalides | inchangé |
| 2 | téléchargement impossible (réseau, HTTP autre que 200, fichier trop gros) | inchangé, l'ancien import reste servi |
| 3 | refusé par la garde : une source perd plus de 20 % de ses membres ou de ses scrutins (fichier tronqué) | inchangé |
| 4 | données de la source illisibles (format changé, champ obligatoire absent, zip abîmé), ou dossier de sortie incohérent | inchangé |
| 5 | clé absente, illisible, ou clé d'exemple utilisée pour des données officielles | inchangé |

**Écriture** : `<out>/index.event.json` (événement NIP-01 signé, kind 30078, `d` = `ppf:parlement-index`) et `<out>/<paquet>.json` (`membres`, `scrutins-an-AAAA-MM`, `votes-pe-AAAA-MM`). Chaque fichier est écrit sous un nom provisoire puis renommé ; l'index est renommé en dernier ; les paquets que l'index ne cite plus sont supprimés ensuite. Un fichier identique n'est pas réécrit, et un index identique garde sa signature : deux imports identiques donnent exactement les mêmes octets.

**Journaux** : seulement des nombres et des noms de paquets (« an 577 member(s), an 9759 ballot(s) »), jamais un nom ni un vote ; un message d'erreur ne cite jamais une valeur des données.

## Hôtes contactés (mode `official` seulement)

- `data.assemblee-nationale.fr` : `AMO10_deputes_actifs_mandats_actifs_organes.json.zip`, `Scrutins.json.zip`, `Dossiers_Legislatifs.json.zip` (17e législature) ;
- `www.senat.fr` : `api-senat/senateurs.json` ;
- `data.europarl.europa.eu` : API v2 (`meps/show-current?country-of-representation=FR`, `meps/{id}`, `corporate-bodies/{id}`, `meetings?year=`, `meetings/{id}/decisions`), une requête toutes les 0,7 s au plus (limite : 500 par 5 minutes).

HTTPS seulement, redirections refusées, toute autre adresse refusée avant connexion. Rien n'est envoyé d'autre que la requête (ni cookie ni identifiant). `www.hatvp.fr` n'est **pas** contacté dans cette version (voir « Décisions »). Jamais depuis l'appareil ; jamais dans les tests.

## Mémoire (limite de 256 Mo en production)

- Les zips sont téléchargés sur disque (dossier temporaire privé, effacé à la fin) puis lus **entrée par entrée** (`java.util.zip.ZipFile`) : un scrutin, un député, un dossier à la fois ; une entrée de plus de 16 Mo est refusée (protection contre une « bombe zip »).
- Les identifiants des députés sont partagés en mémoire (une seule copie de chaque `an:PA…` pour les 9 759 scrutins) ; seuls les champs gardés restent en mémoire.
- Les réponses du Parlement européen sont décodées directement vers les quelques champs utilisés, sans arbre JSON complet ; 64 Mo au plus par réponse.
- Réglage conseillé du conteneur : `java -XX:MaxRAMPercentage=75 -jar …` (le tas suit la limite du conteneur). Non mesuré sur les vrais fichiers (pas de téléchargement sans accord) : à vérifier au premier import réel en préproduction.

## Ce qui est lu, et jamais gardé

- **Assemblée nationale** : identifiant, prénom et nom, circonscription (`numDepartement` puis `numCirco` sur deux chiffres : `0101`, `2A02`, `97101`, `09911` ; Saint-Barthélemy et Saint-Martin : `97701`), nom du département, début du mandat, groupe en cours. Scrutins : numéro, date, titre, type (`SPO`, `SPS`, `MOC`), résultat, décomptes, décompte par groupe, positions nominatives, `parDelegation`, `causePositionVote` (`MG`, `PAN`, `PSE`), mises au point et dysfonctionnements (corrections, sans date : le fichier n'en donne pas), lien du scrutin, dossier (`an-dossier:<uid>`) et adresse de sa page (`https://www.assemblee-nationale.fr/dyn/17/dossiers/<titreChemin>`).
- **Sénat** : matricule, prénom et nom, groupe, département, page du sénateur (seulement sur www.senat.fr, sinon la liste des sénateurs).
- **Parlement européen** : identifiant, nom, parti national et groupe en cours, votes par appel nominal (`VOTE_ELECTRONIC_ROLLCALL`) de la 10e législature (depuis le 16 juillet 2024), positions des seuls élus en France, résultat de tout le Parlement.
- **Jamais gardés** (vérifié par les tests) : naissance, décès, profession, catégorie socioprofessionnelle, sexe, civilité, adresses, courriels, téléphones, place dans l'hémicycle (`numPlace`), photo, collaborateurs, réseaux sociaux, couleur des groupes, références de mandat, citoyenneté.

## Le serveur

Fichier `server/src/main/kotlin/org/politisationpopulairefrancaise/server/ParlementRoutes.kt`, branché par **une ligne** dans `Application.module` (`parlementRoutes()`).

- `GET /api/v1/parlement` : l'index signé tel que l'outil l'a écrit ; `503 no_open_data` avant le premier import.
- `GET /api/v1/parlement/{name}` : un paquet **cité par l'index courant** (nom vérifié par `ParliamentFormat.isPackageName`, aucun autre chemin construit) ; `404 unknown_package` sinon.
- `ETag` = sha256 des octets ; `If-None-Match` (liste, étiquette faible ou `*`) répond `304` ; `Cache-Control: public, no-cache`.
- Compression : faite par le reverse proxy (`encode zstd gzip` de Caddy), le serveur n'en installe pas.
- `PPF_OPEN_DATA_DIR` absent : les routes n'existent pas. Aucune base de données, aucune migration.
- Serveur de développement (`gradlew :server:runDev`) : l'import d'exemple tourne au démarrage dans un dossier temporaire (avec `PPF_IMPORT_KEY_FILE`, ou `~/.ppf/open-data-example.nsec`), sinon la copie embarquée des paquets d'exemple est servie.

## Jeux d'exemple

- Fichiers fictifs dans `tools/open-data-importer/src/main/resources/example/` (liste dans `files.txt`, date d'import fixée par `manifest.json` : 2026-11-07), produits par `tools/open-data-importer/scripts/generate_example.py` : 12 députés (identifiants `PA9000xx`) dans 3 groupes d'exemple et un groupe de non-inscrits, un ancien groupe dissous, 4 sénateurs (matricules `99001X` à `99004X`), 3 eurodéputés (`9900xx`) avec partis et groupes fictifs, 6 scrutins sur septembre et octobre 2026 (mise au point, dysfonctionnement, délégations, présidente de l'Assemblée et membre du Gouvernement non votants, motion de censure, dossier inconnu), 2 séances du Parlement européen (dont un vote à main levée écarté). Toute personne est « [EXEMPLE] Prénom Nom ». Les champs interdits y figurent avec des valeurs fictives, pour vérifier qu'ils ne passent jamais.
- Régénérer les paquets embarqués après un changement : `java -jar tools/open-data-importer/build/libs/ppf-open-data-importer.jar --out shared/ui/src/commonMain/composeResources/files/parlement --key ~/.ppf/open-data-example.nsec`.

## Décisions prises (à consigner dans D-064)

1. **Jar unique sans greffon supplémentaire** : tâche Gradle `buildFatJar` (un `Jar` qui embarque les dépendances), aucune nouvelle dépendance ; JDK `HttpClient`, `java.util.zip`, kotlinx.serialization.
2. **Identifiants des groupes** : `an:PO…`, `senat:groupe:<code>`, `pe:groupe:<identifiant de l'organe>` (distincts des identifiants des membres).
3. **Groupes de l'Assemblée** : tous les groupes en cours d'AMO10 (y compris les non-inscrits, que les décomptes des scrutins citent), jamais les groupes terminés ; un député sans mandat de groupe en cours n'a pas de groupe.
4. **Garde** : appliquée par source rafraîchie (membres de la chambre, total des scrutins de la chambre), contre le dernier import réussi ; premier import toujours accepté.
5. **`--only`** : les membres, groupes, scrutins et la date de mise à jour d'une source non rafraîchie sont repris de l'import précédent (paquets vérifiés par leur empreinte, sinon code 4).
6. **Clé** : la clé d'exemple est refusée en mode `official` (code 5) ; un dossier contenant des paquets d'exemple refuse un import officiel, et l'inverse (code 4).
7. **Index identique, signature conservée** (les signatures Schnorr sont aléatoires) : sorties déterministes, dépôt stable.
8. **Parlement européen** : votes par appel nominal seulement ; numéro du vote = `activity_order` de la décision (ou son rang dans la séance) ; identifiant `pe:<date>:<numéro>` ; lien vers le procès-verbal des votes par appel nominal (`https://www.europarl.europa.eu/doceo/document/PV-10-<date>-RCV_FR.html`) ; décompte par groupe non calculé (il ne porterait que sur les élus en France) ; pages de 500 éléments.
9. **HATVP** (`interestsUrl`) : non rempli dans cette version (le rapprochement de `liste.csv` avec les identifiants des parlementaires reste à vérifier sur le fichier réel) ; le champ existe dans le format.
10. **Les nombres valant leur valeur par défaut ne sont pas écrits** (`ParliamentFormat.json`, `encodeDefaults = false`) : l'index ne porte donc pas `"format": 1`, que la lecture rétablit. Un futur format 2 devra l'écrire explicitement (à noter dans `docs/FORMATS.md` par la session « Voir »).

## À vérifier au premier import réel (préproduction, avec l'accord de la propriétaire)

- Forme exacte des réponses JSON-LD du Parlement européen (`hasMembership`, `memberDuring`, `activity_label`, pagination `offset`/`limit`) : écrite d'après la documentation, sans réponse réelle.
- Code de Saint-Barthélemy et Saint-Martin dans AMO10 (`977` attendu, `978` ramené à `977`), code des circonscriptions des sénateurs (`1` ramené à `01`).
- Consommation de mémoire et durée (Scrutins : 9 759 entrées).

## Tests

Lancés le 7 novembre 2026, tous verts, sans réseau :
- `gradlew :tools:open-data-importer:test` : **14 tests** (`ParsersTest` 6 : codes de circonscription, députés et groupes d'AMO10, scrutins avec décomptes, délégations, mises au point, dysfonctionnements et causes de non-vote, lecture d'un vrai zip entrée par entrée, sénateurs, eurodéputés et votes par appel nominal ; `ImporterTest` 8 : index signé vérifié par `ParliamentIndexReader` et empreintes des paquets, minimisation (aucun champ interdit dans la sortie), sorties identiques d'un import à l'autre, garde (code 3, ancien jeu intact), données illisibles et téléchargement en échec (codes 4 et 2, ancien jeu intact), `--only`, ligne de commande (codes 1 et 5, variables d'environnement), hôtes autorisés).
- `gradlew :server:test --tests "*ParlementRoutesTest*"` : **5 tests** (200 avec signature vérifiable, ETag et 304, paquets cités par l'index seulement, 404 `unknown_package`, chemins hors du dossier refusés, 503 avant le premier import, routes absentes sans `PPF_OPEN_DATA_DIR`) ; `ApplicationTest` (4) et `TilesRoutesTest` (6) restent verts.
- Jar unique construit et lancé avec `-Xmx256m` en mode `example` : code 0, deux lancements donnent les mêmes octets.

## Fichiers communs touchés

- `settings.gradle.kts` : une ligne (`include(":tools:open-data-importer")`).
- `server/build.gradle.kts` : dépendance de test vers l'outil d'import (serveur de développement et tests).
- `Application.kt` : une ligne (`parlementRoutes()`).
- `DevServer.kt` : une ligne (`parlementRoutes(devOpenData())`).
