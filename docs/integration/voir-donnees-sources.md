# « Voir », données ouvertes : sources officielles vérifiées le 7 novembre 2026

Relevé fait le 7 novembre 2026 sur les pages officielles (requêtes HEAD seulement pour les tailles : **aucun fichier de données n'a été téléchargé**, un téléchargement demande l'accord de la propriétaire). Sert de base au contrat `voir-donnees-ouvertes.md`.

## Assemblée nationale (data.assemblee-nationale.fr)

- Licence : « Licence Ouverte / Open Licence », mention de la source obligatoire (https://data.assemblee-nationale.fr/licence-ouverte-open-licence). Fréquence non écrite ; fichiers datés du jour (mise à jour quotidienne probable, non confirmée).
- **Députés en exercice (AMO10)** : https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip (4,9 Mo, 2026-11-07). Variante `AMO40_..._divises`. **Ne pas utiliser** `AMO50` (figé au 2024-07-11).
  - CSV simple : https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_csv_opendata/liste_deputes_excel.csv (identifiant sans « PA »).
  - Identifiants : député `PA` + chiffres ; organe ou groupe `PO` + chiffres.
  - Schéma (v0.9.8, http://www.assemblee-nationale.fr/opendata/Schemas_Entites/AMO/Schemas_Acteurs.html) : acteur `uid`, `etatCivil/ident` (`civ`, `prenom`, `nom`, `alpha`) ; mandat `uid`, `acteurRef`, `legislature`, `typeOrgane`, `dateDebut`, `dateFin`, `infosQualite/codeQualite`, `organes` ; circonscription dans le mandat `ASSEMBLEE`, `election/lieu` (`departement`, `numDepartement`, `numCirco`) ; groupe = mandat `GP` vers un organe `PO…` (`libelle`, `libelleAbrege`, `viMoDe`).
  - **À ne jamais garder** : `infoNaissance`, `dateDeces`, `profession`, `adresses`, `placeHemicycle`, collaborateurs.
- **Scrutins** : https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip (30,2 Mo, 2026-11-07) ; positions de vote de chaque député. **Structure JSON non documentée** (section « Votes » vide) : noms de champs à confirmer sur un fichier réel.
- Dossiers législatifs : https://data.assemblee-nationale.fr/static/openData/repository/17/loi/dossiers_legislatifs/Dossiers_Legislatifs.json.zip (11,2 Mo). Amendements : `Amendements.json.zip` (354 Mo, hors champ de la première version).

## Sénat (data.senat.fr)

- Licence : termes de la Licence Ouverte (Etalab 2.0) ; mention de la source et de la date de mise à jour, ne pas suggérer un caractère officiel (https://data.senat.fr/licence/).
- **Sénateurs en fonction** : https://www.senat.fr/api-senat/senateurs.json (402 Ko, 2026-11-07) : `matricule` (ex. `19591F`), `nom`, `prenom`, `civilite`, `serie`, `url`, `urlAvatar`, `groupe` {`code`, `libelle`, `libelleCourt`}, `circonscription` {`code`, `libelle`}, `categorieProfessionnelle`, `organismes`. À ne pas garder : `urlAvatar`, `categorieProfessionnelle`, `civilite`.
- Historique : https://data.senat.fr/data/senateurs/ODSEN_GENERAL.csv (date de naissance, courriel, profession : à ne pas garder).
- **Votes nominatifs : aucun fichier CSV ou JSON**. Ils ne sont que dans la copie PostgreSQL 8.4 de la base Dosleg (https://data.senat.fr/data/dosleg/dosleg.zip, 16 Mo, tables non documentées) et en pages HTML (https://www.senat.fr/scrutin-public/2025/scr2025-{N}.html).

## Parlement européen (API v2)

- Documentation : https://data.europarl.europa.eu/en/developer-corner/opendata-api. Licence **CC BY 4.0** (attribution obligatoire) ; 500 requêtes par 5 minutes.
- Eurodéputés français : `GET https://data.europarl.europa.eu/api/v2/meps/show-current?country-of-representation=FR&format=application%2Fld%2Bjson` (81 élus) : `identifier`, `label`, `familyName`, `givenName`, `api:political-group`. Parti national et groupe européen par `GET /meps/{id}` (`hasMembership`, `NATIONAL_POLITICAL_GROUP`, `EU_POLITICAL_GROUP`) puis `GET /corporate-bodies/{id}`. À ne pas garder : `bday`, `placeOfBirth`, `citizenship`, `img`, `hasEmail`, `hasGender`, `contactPoint`.
- Votes : `GET /meetings/{MTG-PL-AAAA-MM-JJ}/decisions` : `decision_method` (`VOTE_ELECTRONIC_ROLLCALL`), `decision_outcome`, `number_of_votes_favor`, `number_of_votes_against`, `number_of_votes_abstention`, listes `had_voter_favor`, `had_voter_against`, `had_voter_abstention` (`person/{id}`).

## HATVP

- **Répertoire des représentants d'intérêts** : https://www.hatvp.fr/agora/opendata/agora_repertoire_opendata.json (139 Mo, mis à jour chaque nuit, Licence Ouverte 2.0). Fiche d'activités **annuelle** : objet en texte libre, domaines, types de décisions, **catégorie** de responsable public. **Aucun lien par nom ou identifiant vers un élu ou un texte** : la loi ne l'impose pas. Seul rattachement possible : le libellé libre de l'objet.
- **Déclarations d'intérêts et d'activités** : liste https://www.hatvp.fr/livraison/opendata/liste.csv (`type_mandat`, `type_document`, `url_dossier`, `id_origine`…). Réutilisation permise (code électoral, LO 135-2 IV ; loi 2013-907, article 5 IV ; CRPA L. 321-1 à L. 322-2 ; délibération HATVP 2017-111) : ne pas altérer ni dénaturer, citer la source et la date, respecter le RGPD. Pas d'interdiction d'usage commercial.
- **Patrimoine des parlementaires** : consultable en préfecture seulement ; publier est puni d'un an de prison et 45 000 € d'amende (loi 2013-907, article 26 III ; code pénal, 226-1). **Jamais dans PPF.**

## Répertoire national des élus

- https://www.data.gouv.fr/datasets/repertoire-national-des-elus-1 : ministère de l'Intérieur, Licence Ouverte 2.0, trimestriel (dernière mise à jour le 11 août 2026), 12 CSV. Utilisé par « Comprendre » (D-047).

## Structure relevée sur les vrais fichiers (7 novembre 2026, 14 h)

Avec l'accord de la propriétaire donné le 7 novembre 2026 dans la conversation, les quatre fichiers ont été téléchargés une fois (46,7 Mo), chacun dans son propre dossier hors du dépôt, analysés par un script qui n'affiche que les noms de champs, leurs types et quelques valeurs de codes (jamais un nom ni un vote), puis **supprimés**. Rien de ces fichiers n'entre dans le dépôt ni dans les tests.

Règles communes aux fichiers de l'Assemblée (JSON issus du XML) : **tous les nombres sont des chaînes** ; un élément unique est un **objet**, plusieurs sont une **liste** (il faut accepter les deux partout) ; une liste vide vaut `null` ; certains identifiants sont des objets (`uid` = `{"#text": "PA…"}` dans les acteurs).

### Scrutins (`Scrutins.json.zip`)

- 9 759 fichiers `json/VTANR5L17V<numéro>.json`, un par scrutin ; tous en `modePublicationDesVotes` = `DecompteNominatif`.
- `scrutin` : `uid` (`VTANR5L17V2657`), `numero` (`"2657"`), `legislature` (`"17"`), `dateScrutin` (`AAAA-MM-JJ`), `organeRef` (l'Assemblée, `PO838901`), `seanceRef`, `titre`, `objet.libelle`, `objet.dossierLegislatif` (`dossierRef` = `DLR5L17N54083`, `libelle`, souvent `null`), `demandeur.texte`.
- `typeVote` : `codeTypeVote` (`SPO` scrutin public ordinaire : 9 661 ; `SPS` solennel : 75 ; `MOC` motion de censure : 23), `libelleTypeVote`, `typeMajorite`.
- `sort` : `code` (`adopté`, `rejeté`), `libelle`.
- `syntheseVote` : `nombreVotants`, `suffragesExprimes`, `nbrSuffragesRequis`, `annonce`, `decompte` (`pour`, `contre`, `abstentions`, `nonVotants`, `nonVotantsVolontaires`).
- `ventilationVotes.organe.groupes.groupe[]` : `organeRef` (groupe `PO…`), `nombreMembresGroupe`, `vote.positionMajoritaire` (`pour`, `contre`, `abstention`), `vote.decompteVoix` (mêmes cinq nombres), `vote.decompteNominatif` avec `pours`, `contres`, `abstentions`, `nonVotants`, chacun `{"votant": objet ou liste}` ; un `votant` : `acteurRef` (`PA…`), `mandatRef`, `numPlace` (place dans l'hémicycle : **ne pas garder**), `parDelegation` (`"true"`/`"false"`), et pour les non-votants `causePositionVote` (`MG` membre du Gouvernement, `PAN` président de l'Assemblée, `PSE` président de séance ; à afficher, c'est la raison du non-vote).
- `miseAuPoint` : `pours`, `contres`, `abstentions`, `nonVotants`, `nonVotantsVolontaires` (mêmes `votant`) et `dysfonctionnement` (mêmes rubriques : le vote a mal fonctionné) ; **aucune date** propre à la mise au point dans le fichier (on affiche la date du scrutin et la mention « mise au point »).

### Députés (`AMO10`)

- `json/acteur/PA….json` (574 acteurs), `json/organe/PO….json` (7 123 organes), `json/deport/` (30 déports : un député qui s'abstient de prendre part à un sujet à cause d'un conflit d'intérêts ; à étudier en version 2).
- `acteur.uid.#text` = `PA841605` ; `etatCivil.ident` (`prenom`, `nom` à garder ; `civ`, `alpha`, `trigramme` à ne pas garder) ; `profession` à ne pas garder.
- `mandats.mandat[]` : `uid`, `acteurRef`, `legislature`, `typeOrgane` (`ASSEMBLEE`, `GP` groupe, `PARPOL` parti de rattachement, `COMPER` commission permanente, `GE`, `GA`, `ORGEXTPARL`…), `dateDebut`, `dateFin` (`null` si en cours), `infosQualite.codeQualite`, `organes.organeRef` (chaîne ou liste), et pour `ASSEMBLEE` : `election.lieu` (`numDepartement` sur deux ou trois caractères : `01`, `2A`, `099` pour les Français établis hors de France, `971` à `988` ; `numCirco` **sans zéro** : `1`, `11`), `election.refCirconscription` (organe `CIRCONSCRIPTION`), `election.causeMandat`.
- `organe` : `uid` (`PO845401`), `codeType` (`GP`, `CIRCONSCRIPTION`…), `libelle`, `libelleAbrev` (sigle du groupe), `libelleAbrege`, `viMoDe.dateDebut`/`dateFin`, `couleurAssociee` (**ne pas utiliser** : couleurs neutres), et pour une circonscription `lieu.departement.code`.
- Code de circonscription de nos paquets : `numDepartement` puis `numCirco` sur deux chiffres (`0101`, `2A02`, `09911`, `97101`), **à aligner** avec l'annuaire des élus de « Comprendre » avant de le figer.

### Dossiers législatifs (`Dossiers_Legislatifs.json.zip`)

- `json/dossierParlementaire/DLR5L17N….json` (3 402) et `json/document/` (7 622) ; on garde seulement `uid`, `titreDossier.titre`, `titreDossier.titreChemin` (pour l'adresse de la page sur assemblee-nationale.fr) et `procedureParlementaire.libelle`.

### Sénateurs (`senateurs.json`)

- Une liste : `matricule`, `nom`, `prenom`, `groupe` (`code`, `libelle`, `libelleCourt`, `codeTypeAppartenance` : `A` apparenté, `N` membre), `circonscription` (`code` : `33`, `975`…, `libelle`), `serie`, `url`. **À ne pas garder** : `civilite`, `feminise`, `categorieProfessionnelle`, `urlAvatar`, `facebook`, `twitter`, `organismes`, `siege`.

## Non confirmé

1. ~~Champs des scrutins~~ et ~~clés des fichiers AMO~~ : relevés (section précédente).
2. Date propre d'une mise au point : absente du fichier.
3. Fréquence de mise à jour de l'Assemblée (fichiers datés du jour de la lecture).
4. Tables des votes dans `dosleg.zip` (Sénat).
5. Clés du JSON AGORA (HATVP) et de `declarations.xml`.
6. Texte de la décision du Bureau du Parlement européen C/2025/341 (lu dans une copie tierce).
7. Articles exacts visés par l'article 26 III de la loi 2013-907.
