# « Voir » : sources de l'historique des votes et de « Cette semaine au Parlement »

Vérifié le 7 novembre 2026 sur les pages officielles (requêtes HEAD et quelques appels limités à l'API du Parlement européen ; aucun fichier de données téléchargé). Sert à l'import historique des votes pour le comparateur de « Décider » (DE-16) et à M20 « Cette semaine au Parlement ».

## Jusqu'où remontent les votes nominatifs officiels

| Chambre | Depuis | Source | Rattachement au parti |
|---|---|---|---|
| Assemblée nationale | 14e législature (2012) ; rien avant en données ouvertes (404 pour la 13e et la 12e) | `repository/<lég.>/loi/scrutins/` : 17e `Scrutins.json.zip` (30,2 Mo, quotidien), 16e `Scrutins.json.zip` (10,1 Mo, figé 28/06/2024), 15e `Scrutins_XV.json.zip` (9,2 Mo, figé 09/06/2022), 14e `Scrutins_XIV.json.zip` (0,67 Mo, figé 21/03/2018, peut-être incomplet) | Oui : mandats `PARPOL` (parti de rattachement) et `GP` (groupe) avec dates, fichier AMO30 « tous acteurs, tous mandats » (13,7 Mo, députés depuis juin 1997) |
| Sénat | 1er octobre 2006 | Base Dosleg, copie PostgreSQL 8.4 (`data.senat.fr/data/dosleg/dosleg.zip`, 16,1 Mo, quotidienne) ; aucune autre source lisible par machine | Non : seulement les groupes, avec dates (`ODSEN_HISTOGROUPES.csv`, 568 Ko) |
| Parlement européen | vote de chaque député : 9e législature, depuis le 15 juillet 2019 ; totaux seuls depuis 2014 | API v2 `meetings/{séance}/decisions` (`had_voter_favor`…) ; comptes rendus d'appel nominal depuis le 16/07/2014 (chemin de téléchargement à trouver) ; europarl.europa.eu bloque les robots | Oui : `hasMembership` `NATIONAL_POLITICAL_GROUP` avec dates, depuis avant 1979 |
| Conseil de l'Union européenne (vote de la France) | 1er décembre 2009 dans l'ancien jeu « Council votes on legislative acts », **abandonné** (404) | Registre des votes publics du Conseil, une note PDF par acte (403 aux robots) | Sans objet : vote du gouvernement |

Licences : Licence Ouverte (Assemblée), Licence Ouverte 2.0 (Sénat), CC BY 4.0 (Parlement européen), décision 2011/833/UE (Conseil).

## « Cette semaine au Parlement »

- **Assemblée nationale** : `repository/17/vp/reunions/Agenda.json.zip` (8,7 Mo, plusieurs fois par jour : organisateur, date et heure, lieu, ordre du jour s'il est connu ; champs à relever) ; `repository/17/vp/seances/seances_publique_libre_office.csv` (20 Ko : date, heure, ordre du jour en texte libre, déjà jusqu'au 26/11/2026). Pas de fichier propre à la Conférence des présidents.
- **Sénat** : flux iCalendar officiels, `https://www.senat.fr/aglae/Seance/ical.ics` (séance publique) et un flux par instance (dont la Conférence des Présidents) : `DTSTART`, `DTEND`, `SUMMARY`, `DESCRIPTION` (textes et numéros), `CATEGORIES` ; aucun identifiant de dossier ; licence non précisée.
- **Parlement européen** : `meetings/{séance}/foreseen-activities` (points prévus, libellés, ordre, type), CC BY 4.0.

## Structure relevée sur les vrais fichiers (7 novembre 2026, soir)

Fichiers téléchargés une fois par le chef d'équipe avec l'accord de la propriétaire (« Oui pour télécharger tous les fichier publics nécessaires »), lus comme des données par un script qui n'affiche que des noms de champs et des codes, puis **supprimés**. Rien de ces fichiers n'entre dans le dépôt.

- **Scrutins des 16e et 15e législatures** : même structure que la 17e (un fichier `json/VTANR5L<lég.>V<numéro>.json` par scrutin ; 4 106 pour la 16e, 4 417 pour la 15e ; 11 groupes dans la ventilation). Le même analyseur sert.
- **Scrutins de la 14e législature** : **un seul fichier** `Scrutins_XIV.json` avec une liste `scrutins` de 1 354 scrutins, du 3 juillet 2012 au 24 novembre 2016, mêmes champs que les autres législatures, tous avec le décompte nominatif. Le fichier ne couvre donc pas la fin de la législature (décembre 2016 à juin 2017) : à dire dans l'application.
- **AMO30** (historique des députés) : `json/acteur/` (3 175 acteurs), `json/organe/` (10 822), `json/deport/` (59) ; mandats `ASSEMBLEE` des législatures 12 à 17 ; mandats `GP` (groupe) et **`PARPOL` (parti de rattachement) avec dates, de décembre 2012 à aujourd'hui seulement** (3 491) : avant décembre 2012, repli sur le groupe. Mêmes champs qu'AMO10 ; ne garder que le mandat.
- **Agenda de l'Assemblée** (`Agenda.json.zip`, 8 279 réunions `json/reunion/RUANR5L17…json`) : `uid`, `timeStampDebut` et `timeStampFin` (date et heure avec fuseau), `organeReuniRef` (`PO838901` = la séance publique), `@xsi:type` (`seance_type`, réunions de commission…), `cycleDeVie.etat` (« Confirmé »…), `lieu`, `ODJ.pointsODJ.pointODJ[]` (`typePointODJ` comme « Suite de la discussion », `objet`, `dossiersLegislatifsRefs.dossierRef` = `DLR5L17N…`, `textesAssocies`, `natureTravauxODJ`), `ODJ.resumeODJ` ; `participants` (à ne pas garder).
- **Séances publiques (CSV)** : date, heure, ordre du jour en texte libre ; l'agenda JSON suffit.
- **Sénat, Dosleg** (`dosleg.sql`, 127 Mo, copie PostgreSQL) : table `scr` (scrutin : `sesann` année de session, `scrnum` numéro, `scrint` intitulé, `scrdat` date, `scrpou` pour, `scrcon` contre, `scrvot` votants, `scrsuf` suffrages exprimés, versions « séance » `…sea`, `scrmaj` majorité), table `votsen` (vote de chaque sénateur : `sesann`, `scrnum`, `senmat` matricule, `posvotcod` position, `senmatdel` sénateur qui a voté par délégation, `stavotidt` statut), `posvot` et `stavot` (libellés des codes). Lecture possible directement dans le texte SQL (instructions `COPY`), sans base de données.
- **Sénat, groupes dans le temps** (`ODSEN_HISTOGROUPES.csv`, Latin-1, séparateur `;`, 3 758 lignes) : matricule, identifiant d'appartenance, code et nom court du groupe, dates de début et de fin d'appartenance, fonction avec ses dates (noms et prénoms à ne pas garder).
- **Sénat, flux de la séance** (`ical.ics`, 55 événements) : `DTSTART`, `DTEND`, `SUMMARY`, `DESCRIPTION`, `CATEGORIES`, `LOCATION`, `UID`, `URL`, `X-AGLAE-STARTTYPE`, avec des événements répétés (`RRULE`, `RDATE`).
- **Parlement européen** : `meetings?year=2019` (séances, `parliamentary_term`) ; `meetings/{séance}/decisions` : **les votes de chaque député ne figurent que pour les votes par appel nominal** (`decision_method` = `VOTE_ELECTRONIC_ROLLCALL` : 152 décisions sur 400 le 21 octobre 2026 ; les votes électroniques simples et à main levée n'ont que le résultat) ; champs `had_voter_favor`, `had_voter_against`, `had_voter_abstention`, `had_voter_intended_*`, `decision_outcome`, `referenceText`, `decisionAboutId` ; `meetings/{séance}/foreseen-activities` (ordre du jour prévu : `activity_label`, `activity_start_date`, `agendaLabel`, `had_activity_type`) ; `meps/{id}` (`hasMembership` avec les partis nationaux et les groupes datés ; `bday`, `placeOfBirth`, `citizenship`, `hasGender`, `img`, `hasEmail` à ne pas garder) ; `corporate-bodies/{id}` (`label`, `prefLabel`, `classification`, `temporal`).

## Non confirmé

- Votes nominatifs du Parlement européen avant juillet 2019 ; registre actuel du Conseil ; licence des flux du Sénat ; fin de la 14e législature (décembre 2016 à juin 2017) absente du fichier officiel.
