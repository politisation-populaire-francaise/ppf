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

## Non confirmé

1. Champs JSON réels des scrutins de l'Assemblée (documentation vide) : demande à la propriétaire de télécharger `Scrutins.json.zip` (30 Mo) pour les relever.
2. Clés JSON réelles des fichiers AMO (documentation écrite pour le XML).
3. Fréquence de mise à jour de l'Assemblée.
4. Tables des votes dans `dosleg.zip` (Sénat).
5. Clés du JSON AGORA (HATVP) et de `declarations.xml`.
6. Texte de la décision du Bureau du Parlement européen C/2025/341 (lu dans une copie tierce).
7. Articles exacts visés par l'article 26 III de la loi 2013-907.
