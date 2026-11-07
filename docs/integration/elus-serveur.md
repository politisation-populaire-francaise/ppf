# Note d'intégration : l'annuaire des élus sur le serveur (bloc elus-serveur, D-047)

Bloc de la vague 3 de « Comprendre » (contrat `docs/integration/comprendre-vague-3.md`, section 1). Périmètre : `server/` et cette note. Rédigée le 7 novembre 2026 pour la session principale, qui fusionne et met à jour CHANGELOG, ROADMAP, DECISIONS, FORMATS, REGISTRE_DONNEES, THREAT_MODEL et GUIDE_PROPRIETAIRE.

**État** : recherche des sources faite (section 1) ; code en cours.

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
