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

## Non confirmé

- `PARPOL` et `GP` présents dans AMO30 pour 1997 à 2012 ; complétude de la 14e législature ; tables des votes dans Dosleg ; votes nominatifs du Parlement européen avant juillet 2019 ; registre actuel du Conseil ; champs exacts d'`Agenda.json` ; licence des flux du Sénat.
- Relever ces structures demande de télécharger les fichiers (accord de la propriétaire, comme pour la première fois).
