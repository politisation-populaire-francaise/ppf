# « Voir » : Cette semaine au Parlement (VO-31)

Écrit le 7 novembre 2026 sur la branche `feature/voir-semaine` (cahier fonctionnel 2.0, M20, VO-31, repris dans « En direct », M19). Sources vérifiées : `voir-historique-sources.md`.

## En bref

- L'outil d'import écrit un nouveau paquet **`semaine`**, cité par l'index signé comme les autres : l'ordre du jour du jour de l'import et des quatorze jours suivants, à l'heure de Paris, pour l'Assemblée nationale, le Sénat et le Parlement européen.
- L'application montre une page « Cette semaine au Parlement » (depuis l'écran « Parlementaires » de « Voir ») et un bloc compact dans « En direct ».
- Rien de personnel n'est gardé : ni participants, ni demandeurs, ni organisateurs, ni contacts, ni lieux. Seuls l'heure, l'assemblée, le type de réunion, le nom de la commission, l'objet du point, son type, le dossier législatif, l'état et le lien officiel.

## Le paquet `semaine` (format 1)

`ParliamentWeekPackage` (`shared/core/.../voir/parlement/ParliamentWeek.kt`) : `example`, `from` et `to` (AAAA-MM-JJ), `items` triés par heure, assemblée puis identifiant. Chaque point (`WeekItem`) :

| Champ | Contenu |
|---|---|
| `id` | stable : `an:<uid de la réunion>:<rang du point>` (ou `an:<uid>` sans points publiés), `senat:<UID>:<AAAAMMJJHHMM>`, `pe:<séance>:<ordre>` |
| `chamber` | `an`, `senat`, `pe` |
| `start`, `end` | heure de Paris, `AAAA-MM-JJTHH:MM` |
| `kind` | `seance-publique`, `commission`, `autre` |
| `body` | nom de la commission (organes d'AMO10) |
| `title`, `pointKind`, `detail` | objet du point, type (« Suite de la discussion », « Débat », « Votes »), description du Sénat ; 2 000 caractères au plus |
| `dossier`, `dossierUrl` | `an-dossier:DLR5L17N…` et `https://www.assemblee-nationale.fr/dyn/17/dossiers/<uid>` |
| `status` | `confirme`, `previsionnel`, `annule`, ou absent quand la source ne dit rien |
| `url` | page officielle de l'ordre du jour |

Lecture stricte sur le format, tolérante sur les champs inconnus (`ParliamentWeekFormat.read`) ; un paquet illisible est ignoré.

## L'import (`tools/open-data-importer`)

Fichier nouveau `Week.kt` :
- **Assemblée nationale** (`AnAgenda`) : `repository/17/vp/reunions/Agenda.json.zip`, `json/reunion/*.json`. Séances publiques (`organeReuniRef` = `PO838901` ou `@xsi:type` `seance…`) avec chaque point de `ODJ.pointsODJ.pointODJ` (`objet`, `typePointODJ`, `dossiersLegislatifsRefs.dossierRef`), ou `ODJ.resumeODJ` si les points ne sont pas publiés ; réunions de commission (`@xsi:type` `…Commission…`) **seulement pour leurs points qui portent un dossier législatif** ; tout autre type écarté (groupes d'amitié…). État de `cycleDeVie.etat` : « Confirmé », « Éventuel », « Annulé » ou « Supprimé ». Les noms des commissions viennent d'AMO10, téléchargé une seconde fois seulement quand une commission est à nommer.
- **Sénat** (`SenatICalendar`) : `https://www.senat.fr/aglae/Seance/ical.ics` (hôte déjà autorisé). Lignes repliées, `DTSTART`/`DTEND` (UTC, `TZID` ou heure locale), `SUMMARY`, `DESCRIPTION` (déséchappée), `CATEGORIES`, `UID`, `URL` (gardée seulement sur www.senat.fr, sinon la page de l'ordre du jour), `STATUS` ; répétitions simples : `RRULE` (`FREQ=DAILY` ou `WEEKLY`, `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY`), `RDATE`, `EXDATE`. `ORGANIZER`, `ATTENDEE`, `CONTACT`, `LOCATION` jamais lus.
- **Parlement européen** (`EuroparlAgenda`) : `meetings?year=` puis `meetings/{MTG-PL-AAAA-MM-JJ}/foreseen-activities` (`activity_label` en français d'abord, `activity_start_date`, `activity_end_date`, `activity_order`, `had_activity_type`) ; toujours « Prévisionnel ».
- **Robustesse** : une source de la semaine illisible ou non téléchargée ne bloque jamais l'import des membres et des scrutins ; son assemblée garde les points du paquet précédent (dans la fenêtre), et le journal le dit. Avec `--only`, les assemblées non rafraîchies gardent aussi leurs points. Avec `--from` (fichiers officiels sur disque), la semaine n'est pas relue.
- Jeux fictifs : `src/main/resources/example/semaine/` (réunions au format d'`Agenda.json.zip`, un `ical.ics`, des réponses de l'API européenne), produits par `scripts/generate_week_example.py`. Paquets embarqués régénérés avec la clé d'exemple (`shared/ui/src/commonMain/composeResources/files/parlement/`).

## Sur l'appareil

- `ParliamentWeekLoader` (nouveau fichier de `core/voir/parlement/`) : à côté de `ParliamentRepository`, sans le modifier ; même stockage, même route du serveur, mêmes vérifications (empreinte et taille de l'index signé avant de garder quoi que ce soit). Le jeu d'exemple embarqué est vérifié de la même façon.
- **Économie de données** : le paquet ne se télécharge que sur un geste (bouton avec sa taille), ou à l'ouverture d'« En direct » (et de la page) **quand une version est déjà sur l'appareil** et que le mode économie de données est coupé. À l'ouverture d'« En direct », seul l'index signé (quelques centaines d'octets) est demandé s'il n'est pas encore connu.
- `ui/voir/parlement/ParliamentWeekState.kt` et `ParliamentWeekScreen.kt` (nouveaux) : page par jour (titre du jour à gauche sur grand écran, au-dessus sur téléphone), filtre par assemblée, heure, assemblée, type de réunion, objet, type de point et état en badges neutres, description du Sénat ; liens : fiche du texte dans « Voir » quand son `ppf-official-id` est le dossier du point, dossier législatif et ordre du jour officiel (après « Vous quittez PPF »), « Qui décide de ça ? » (espace Comprendre), « Interpeller mes élus » (espace Décider, faute de page dédiée DE-09 à ce jour) ; source et date comme les autres pages du Parlement, bandeau « Données fictives [EXEMPLE] » ; bloc « En direct » (`ParliamentWeekSlot`) : quatre prochains points et « Voir toute la semaine ».
- Formulation neutre : on décrit l'ordre du jour, sans aucun jugement.

## Points d'accroche dans des fichiers communs

| Fichier | Modification |
|---|---|
| `tools/.../opendata/Importer.kt` | 3 lignes : import de `ParliamentWeekFormat`, `files[ParliamentWeekFormat.PACKAGE] = WeekImport(...).bytes()` avant `val refreshedChambers`, et `&& ref.name !in files` dans le filtre de `keptRefs` |
| `tools/.../ImporterTest.kt` | 1 ligne : `"semaine"` ajouté à la liste attendue des paquets |
| `ui/voir/parlement/ParliamentState.kt` | `data object Week : ParliamentPage` et la propriété `var week: ParliamentWeekState?` |
| `ui/voir/parlement/ParliamentScreen.kt` | 2 lignes : la page `ParliamentPage.Week` et la carte `ParliamentWeekEntry` après la provenance de la liste |
| `ui/endirect/EnDirectScreen.kt` | paramètre facultatif `parliamentWeek` (comme `upcoming`) et son appel dans les deux mises en page |
| `ui/shell/AdaptiveShell.kt` | `rememberParliamentWeekState(...)` après `rememberParliamentState`, et `parliamentWeek = { ParliamentWeekSlot(parliamentState, it) }` sur `EnDirectScreen` (plus 2 imports) |
| `composeResources/files/parlement/` | paquets d'exemple régénérés (`index.event.json` et `semaine.json`) |

Aucune modification de `infra/`, `.github/`, `server/`, `ui/comprendre/`, `MarkdownText.kt`. Le serveur sert déjà tout paquet cité par l'index (`ParliamentFormat.isPackageName("semaine")` est vrai) : rien à changer.

## Décisions (techniques, consignées dans D-100)

1. Fenêtre : du jour de l'import (heure de Paris) au quatorzième jour suivant inclus ; l'appareil masque les jours passés.
2. Un paquet unique `semaine`, réécrit à chaque import ; deux imports identiques donnent les mêmes octets.
3. Une source de la semaine en échec ne fait pas échouer l'import (code 0) : points précédents gardés, journal explicite.
4. Réunions de commission gardées seulement pour leurs points liés à un dossier législatif (le reste est hors du sujet « textes en discussion »).
5. État absent quand la source ne dit rien (le flux du Sénat n'a souvent pas de `STATUS`) : on n'affiche alors aucun état plutôt que d'en supposer un.
6. Liens officiels : `https://www.assemblee-nationale.fr/dyn/seance-publique` (séance publique), `https://www2.assemblee-nationale.fr/agendas/les-agendas` (commissions), `https://www.senat.fr/ordre-du-jour/ordre-du-jour.html` (Sénat, si l'événement n'a pas d'adresse sur www.senat.fr) : vérifiés le 7 novembre 2026 (réponse 200). `https://www.europarl.europa.eu/plenary/fr/home.html` pour le Parlement européen : vérifié le 8 novembre 2026 (page officielle « Accueil | Plénière | Parlement européen », avec ses « Ordres du jour »).
7. « Interpeller mes élus » mène à l'espace « Décider » tant qu'aucune page DE-09 n'existe.

## À vérifier au premier import réel

- Forme exacte des réunions d'`Agenda.json.zip` hors séance publique (`@xsi:type` des commissions), `resumeODJ`, valeurs de `cycleDeVie.etat`.
- Présence de `STATUS`, de `URL` et des répétitions dans `ical.ics` ; licence de ce flux (non précisée, attribution prudente affichée).
- Réponse réelle de `foreseen-activities` (dates, libellés multilingues).

## Tests

Lancés le 8 novembre 2026 après l'intégration de `main` (bdae4c3), tous au vert :
- `gradlew :tools:open-data-importer:test` : **24 tests** (`WeekTest` 8 : fenêtre du jour de l'import aux quatorze jours suivants, heure de Paris, agenda de l'Assemblée (séances publiques et commissions sur un dossier législatif seulement), calendrier du Sénat avec ses répétitions et sans aucune donnée personnelle, règle hebdomadaire sans jours, activités prévues du Parlement européen, paquet `semaine` cité par l'index signé, assemblée non rafraîchie ou illisible qui garde ses points ; `ImporterTest` 8 avec `semaine` dans la liste des paquets ; `ParsersTest` 6 ; `KeyGenTest` 2).
- `gradlew :shared:core:jvmTest --tests "*voir.parlement*"` : **35 tests** dont `ParliamentWeekTest` 5 (lecture stricte du format, jours dans l'ordre, paquet gardé seulement s'il correspond à l'index, index plus récent qui le rend périmé, jeu embarqué vérifié contre son index).
- `gradlew :shared:ui:jvmTest --tests "*ParliamentWeek*" --tests "*EnDirect*" --tests "*voir.parlement*"` : **23 tests** dont `ParliamentWeekScreenTest` 6 (téléphone : téléchargement sur un geste puis jours et liens ; ordinateur : jours à côté de leurs points, filtre par assemblée ; bloc d'« En direct » qui attend un geste et respecte l'économie de données ; quatre prochains points et ouverture de la semaine dans « Voir » ; mise à jour à l'ouverture seulement sans économie de données ; bloc présent dans l'application avec le jeu d'exemple embarqué).
- Paquets embarqués régénérés avec `java -jar tools/open-data-importer/build/libs/ppf-open-data-importer.jar --source example --out shared/ui/src/commonMain/composeResources/files/parlement --key ~/.ppf/open-data-example.nsec` : seuls `index.event.json` et `semaine.json` (nouveau) changent ; deux imports de suite donnent les mêmes octets.
- Le jeu d'exemple couvre du 7 au 21 novembre 2026 : après le 21 novembre, la page affiche « Aucune séance prévue » tant que les paquets d'exemple ne sont pas régénérés avec une nouvelle date d'import.
