# « Voir », données réelles et ouvertes : contrat commun

Contrat écrit le 7 novembre 2026 par la session « Section [Voir] Team dev », à relire par le chef d'équipe (« Team lead [Voir] ») avant de lancer le travail. Validation de la propriétaire relayée le 7 novembre 2026 : « Oui, commencez » (feuille de route de « Voir », point 1 ; doctrine « curseur au maximum », D-044). Sources vérifiées : `voir-donnees-sources.md`.

But : que « Voir » couvre **tous les parlementaires** (députés, sénateurs, eurodéputés élus en France) sans saisie à la main, avec leurs votes, à partir des données officielles, sourcées et datées partout. Les affaires, promesses et déclarations restent saisies et relues à la main : l'import n'en crée jamais.

## 0. Numéros, branches, règles

- Décisions : D-064 (import et intégrité), D-065 (affichage dans « Voir »), D-066 en réserve.
- Branche `feature/voir-donnees` (depuis `feature/voir`), worktree `.claude/worktrees/voir-donnees`. La session « Voir » fusionne et avance `main`.
- Base locale : une migration est nécessaire (paquets téléchargés) ; **numéro à obtenir** des autres sessions (Comprendre a `10.sqm`, Agir `11.sqm` : je demande `12.sqm`). Aucune migration Flyway : le serveur sert des fichiers.
- On **ajoute des fichiers** ; un fichier existant de `core/voir` ou `ui/voir` ne se modifie qu'après avoir prévenu la session « Voir » (seuls points d'entrée prévus : une carte sur l'axe « Le pouvoir » de `VoirScreen.kt`, et la rubrique « Scrutins » sur la fiche d'un texte et d'une personne).
- CLAUDE.md en entier (aucun tiers depuis l'appareil, iOS compatible, écrans adaptatifs, WCAG 2.2 AA, textes externalisés, pas de tiret cadratin). Gradle : `--no-daemon --max-workers=2`, jamais de build complet en parallèle d'un autre.
- **Aucune donnée réelle** dans le dépôt, les tests, les captures ou la préproduction : jeux **fictifs** au format officiel, toute personne nommée `[EXEMPLE] Prénom Nom`.

## 1. Sources de la première version

| Source | Fichier | Usage |
|---|---|---|
| Assemblée nationale | `AMO10_deputes_actifs_mandats_actifs_organes.json.zip` | Députés en exercice, groupes, circonscriptions |
| Assemblée nationale | `Scrutins.json.zip` (17e législature) | Scrutins publics et votes nominatifs |
| Assemblée nationale | `Dossiers_Legislatifs.json.zip` | Seulement le lien scrutin vers dossier (identifiant et adresse officielle) |
| Sénat | `senateurs.json` | Sénateurs en fonction, groupes, départements |
| Parlement européen | API v2 `meps/show-current?country-of-representation=FR`, `meps/{id}`, `corporate-bodies/{id}`, `meetings/{id}/decisions` | Eurodéputés élus en France, parti national, groupe, votes par appel nominal |
| HATVP | lien vers la page officielle de chaque déclaration (`liste.csv`, colonne `url_dossier`) | Déclarations d'intérêts : **lien seulement** dans la première version |

Hors première version, dit tel quel dans l'application :
- **votes nominatifs du Sénat** : publiés seulement dans une copie de base PostgreSQL 8.4 et en pages HTML ; la fiche d'un sénateur affiche « Votes du Sénat : bientôt » et le lien officiel ;
- **répertoire des représentants d'intérêts** : aucune fiche n'y nomme un élu ni un texte (la loi ne l'impose pas) ; un rapprochement automatique serait une interprétation. Les rencontres restent des faits « influence » saisis et relus à la main, avec le lien vers la fiche officielle. Une recherche par mots dans l'objet des fiches pourra être proposée plus tard, à faire relire par un avocat ;
- amendements (354 Mo).

**Jamais gardé** (minimisation) : date et lieu de naissance, date de décès, profession, catégorie socioprofessionnelle, sexe, civilité, adresses, courriels, téléphones, place dans l'hémicycle, photo, collaborateurs. Libellés neutres (« Membre de l'Assemblée nationale »).

**Jamais** : déclaration de patrimoine (publication interdite, loi 2013-907, article 26 III).

## 2. Import, hors du serveur principal (D-064)

- Un **outil d'import séparé** (`tools/open-data-importer`, processus ou conteneur à part) télécharge les sources, garde seulement les champs autorisés, écrit les paquets, calcule leurs sha256 et **signe l'index** avec une **clé d'import dédiée**. Le serveur principal ne détient pas cette clé : il sert les fichiers d'un dossier en lecture seule.
- **Clé d'import** : paire secp256k1 (Schnorr BIP-340, comme les contenus), distincte de la clé de la plateforme et des clés éditoriales ; fichier hors dépôt (`PPF_IMPORT_KEY_FILE`). L'application fait confiance à une liste de clés d'import (`OpenDataTrust`) **seulement pour les paquets de données officielles** : une clé d'import ne peut publier aucun contenu, et une clé éditoriale ne peut signer aucun paquet. Rotation : la liste accepte plusieurs clés ; révocation par mise à jour de l'application.
- `PPF_OPEN_DATA_SOURCE=example` par défaut (développement, tests, préproduction) : jeux fictifs embarqués au format officiel. `official` en production seulement.
- Fréquence : chaque jour (Assemblée, Sénat), chaque semaine (Parlement européen, sous la limite de 500 requêtes par 5 minutes). Le dernier import réussi reste servi tant qu'un nouveau échoue. **Import refusé** si le nombre de membres ou de scrutins baisse de plus de 20 % par rapport au précédent (fichier tronqué).
- Hôtes contactés en mode `official` seulement : `data.assemblee-nationale.fr`, `www.senat.fr`, `data.europarl.europa.eu`, `www.hatvp.fr` ; listés dans la note du bloc et dans `tools/check-no-trackers.sh` si elle les bloque. Jamais depuis l'appareil.
- Journaux sans donnée personnelle (nombres et noms de paquets seulement).

## 3. API du serveur (lecture seule, sans compte, compressée)

`GET /api/v1/parlement` : l'index signé.

```json
{
  "format": 1,
  "example": true,
  "importedOn": "2026-11-07",
  "sources": [
    { "id": "an", "name": "Assemblée nationale, données ouvertes", "url": "https://data.assemblee-nationale.fr/", "licence": "Licence Ouverte", "updatedOn": "2026-11-07" },
    { "id": "senat", "name": "Sénat, données ouvertes", "url": "https://data.senat.fr/", "licence": "Licence Ouverte 2.0", "updatedOn": "2026-11-07" },
    { "id": "pe", "name": "Parlement européen, données ouvertes", "url": "https://data.europarl.europa.eu/", "licence": "CC BY 4.0", "updatedOn": "2026-11-07" }
  ],
  "packages": [
    { "name": "membres", "sha256": "<64 hex>", "bytes": 151234 },
    { "name": "scrutins-an-2026-10", "sha256": "<64 hex>", "bytes": 61234 },
    { "name": "votes-pe-2026-10", "sha256": "<64 hex>", "bytes": 12345 }
  ],
  "signature": { "pubkey": "<64 hex>", "sig": "<128 hex>" }
}
```

La signature porte sur le sha256 des octets de l'index **sans** le champ `signature` (même objet, clés dans cet ordre, sérialisation sans espace). `GET /api/v1/parlement/{name}` : un paquet ; `ETag` = sha256 ; `If-None-Match` répond 304 ; `404 unknown_package` sinon. L'appareil refuse un index mal signé ou un paquet dont l'empreinte ne correspond pas.

**Paquet `membres`** : `members` (identifiant officiel préfixé `an:PA…`, `senat:19591F`, `pe:22858` ; nom ; chambre ; groupe ; territoire : circonscription `6901` pour un député (code département sur deux ou trois caractères suivi du numéro sur deux chiffres, clé commune avec l'annuaire des élus de « Comprendre »), département pour un sénateur, `FR` pour un eurodéputé ; début du mandat ; adresse de la page officielle ; pour un eurodéputé, son parti national) et `groups` (identifiant, chambre, libellé, sigle).

**Paquet `scrutins-an-AAAA-MM`** : pour chaque scrutin, numéro, date, titre officiel, type (solennel, ordinaire, motion…), résultat (adopté ou rejeté), décomptes (votants, exprimés, pour, contre, abstentions), décompte par groupe, listes d'identifiants par position (`pour`, `contre`, `abstention`, `nonVotant`), lien officiel du scrutin et du dossier législatif quand il existe. **Paquet `votes-pe-AAAA-MM`** : même forme, positions des seuls eurodéputés élus en France, avec le résultat de l'ensemble du Parlement.

Listes triées (deux imports identiques donnent les mêmes octets). Les champs exacts des scrutins de l'Assemblée sont à confirmer sur un vrai fichier : **demande à la propriétaire** de pouvoir télécharger `Scrutins.json.zip` (30 Mo) pour relever ses champs ; d'ici là, le jeu fictif suit la page HTML officielle d'un scrutin et sera ajusté.

## 4. Sur l'appareil (D-065)

- `core/voir/parlement/` (nouveaux fichiers) : modèles, lecture stricte et tolérante (un paquet illisible est ignoré), vérification de la signature et des empreintes, `ParliamentCatalog` (membres, groupes, scrutins, participation calculée sur l'appareil : part des scrutins du mandat où le membre a voté).
- Téléchargement : `RemoteEvents` gagne une méthode à implémentation par défaut « indisponible » (comme les salons et l'annuaire), implémentée par `ServerPublicationPort`. Le paquet `membres` se télécharge à la première ouverture de « Parlementaires » après avoir montré sa taille ; les scrutins mois par mois, à la demande. Rien n'est envoyé : le serveur sait seulement qu'un appareil a téléchargé un paquet, sans garder son adresse IP.
- Base locale : table des paquets (nom, sha256, JSON, date de la source, date du téléchargement), migration à numéroter (section 0).
- Lien avec les fiches de « Voir » : une personne publique peut porter `ppf-official-id` (`an:PA…`, `senat:…`, `pe:…`) ; un texte de loi peut porter `ppf-official-id` = `an-dossier:<uid>`. Lu dans un nouveau fichier, sans modifier `PublicPerson` ni `LegislativeText`.

## 5. Écrans

- Axe « Le pouvoir » : une carte « Tous les parlementaires, données officielles » (seul ajout dans `VoirScreen.kt`, après accord de la session « Voir »).
- **Parlementaires** : recherche sans accents, filtres chambre et groupe, une, deux ou trois colonnes.
- **Fiche d'un parlementaire** : chambre, groupe, territoire, début du mandat, participation, derniers votes avec la position écrite en toutes lettres (« Pour », « Contre », « Abstention », « Non-votant ») **sans couleur de jugement**, lien vers sa fiche « Voir » si elle existe, vers sa déclaration d'intérêts sur le site de la HATVP (après la mise en garde « Vous quittez PPF »), vers sa page officielle.
- **Fiche d'un scrutin** : résultat, décomptes, tableau par groupe (aussi lisible par un lecteur d'écran), membres par position, lien officiel.
- **Fiche d'un texte de loi** : ses scrutins quand le dossier est relié (rubrique ajoutée après accord de la session « Voir »).
- Partout : la source, sa licence et la date de mise à jour ; un bandeau « Données fictives [EXEMPLE] » quand `example` est vrai.

## 6. Découpage

1. **Import et serveur** (`tools/open-data-importer`, route de lecture dans `server`, jeux fictifs, tests) ;
2. **Appareil et écrans** (`core/voir/parlement`, `ui/voir/parlement`, migration, tests) ;
3. **Intégration** (session principale) : points d'entrée dans « Voir », docs (DECISIONS, FORMATS, REGISTRE, THREAT_MODEL, ROADMAP, CHANGELOG, GUIDE), APK.

## 7. À faire relire par un avocat

- Publication des votes nominatifs réels (exactitude, mise à jour, présentation neutre).
- Tout rapprochement avec le répertoire des représentants d'intérêts.
- Présentation des déclarations d'intérêts (lien seulement, non altéré).
