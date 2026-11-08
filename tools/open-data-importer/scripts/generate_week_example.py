# Writes the fictional example files of « Cette semaine au Parlement » (VO-31) in the formats of the official ones:
#   python -I tools/open-data-importer/scripts/generate_week_example.py tools/open-data-importer/src/main/resources/example/semaine
import json, os, sys
root = sys.argv[1]
os.chdir(root)

def reunion(uid, typ, organ, start, end, etat, points=None, resume=None, participants=True):
    r = {"@xmlns": "http://schemas.assemblee-nationale.fr/referentiel", "@xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
         "@xsi:type": typ, "uid": uid, "timeStampDebut": start, "timeStampFin": end,
         "lieu": {"code": "AN", "libelleCourt": "AN", "libelleLong": "[EXEMPLE] Salle fictive"},
         "cycleDeVie": {"etat": etat, "chrono": {"creation": "2026-10-30T10:00:00.000+01:00", "cloture": None}},
         "demandeurs": {"acteur": {"acteurRef": "PA900001", "nom": "[EXEMPLE] Élise Arnaud"}},
         "organeReuniRef": organ, "sessionRef": "SCR5A2027O1", "ouverturePresse": "false",
         "ODJ": {"convocationODJ": None, "resumeODJ": {"item": resume} if resume else None,
                 "pointsODJ": {"pointODJ": points} if points is not None else None},
         "identifiants": {"numSeanceJO": "99", "idJO": "EXEMPLE", "quantieme": "Première", "DateSeance": start[:10] + "+01:00"}}
    if participants:
        r["participants"] = {"participantsInternes": {"participantInterne": [
            {"acteurRef": "PA900002", "presence": "présent", "nom": "[EXEMPLE] Zoé Vasseur"},
            {"acteurRef": "PA900003", "presence": "excusé", "nom": "[EXEMPLE] Bruno Côté"}]},
            "personnesAuditionnees": {"personneAuditionnee": {"uid": "PE900001", "ident": {"civ": "Mme", "prenom": "[EXEMPLE] Prénom", "nom": "Nom"}, "dateNais": "1970-01-01"}}}
    return {"reunion": r}

counter = [0]
def point(objet, typ, dossier=None):
    counter[0] += 1
    return {"@xsi:type": "podjSeanceConfPres_type", "uid": "PODJEXEMPLE%04d" % counter[0], "cycleDeVie": {"etat": "Confirmé"},
            "objet": objet, "procedure": None, "typePointODJ": typ, "comiteSecret": "false", "natureTravauxODJ": "ODJPR",
            "textesAssocies": None, "dossiersLegislatifsRefs": {"dossierRef": dossier} if dossier else None}

files = [
 ("RUANR5L17S2026IDS99001.json", lambda: reunion("RUANR5L17S2026IDS99001", "seance_type", "PO838901", "2026-11-10T15:00:00.000+01:00", "2026-11-10T20:00:00.000+01:00", "Confirmé",
     [point("[EXEMPLE] Questions au Gouvernement.", "Questions au Gouvernement"),
      point("[EXEMPLE] Suite de la discussion du projet de loi fictif sur les tilleuls (n° 9001).", "Suite de la discussion", "DLR5L17N90001")], participants=False)),
 ("RUANR5L17S2026IDS99002.json", lambda: reunion("RUANR5L17S2026IDS99002", "seance_type", "PO838901", "2026-11-12T09:00:00.000+01:00", "2026-11-12T13:00:00.000+01:00", "Éventuel",
     None, resume=["[EXEMPLE] Suite de l'ordre du jour de la veille.", "[EXEMPLE] Discussion de propositions de loi fictives."], participants=False)),
 ("RUANR5L17S2026IDC99003.json", lambda: reunion("RUANR5L17S2026IDC99003", "reunionCommission_type", "PO900010", "2026-11-11T09:30:00.000+01:00", "2026-11-11T12:30:00.000+01:00", "Confirmé",
     [point("[EXEMPLE] Audition fictive.", "Audition"),
      point("[EXEMPLE] Examen de la proposition de loi fictive sur les acacias (n° 9002).", "Examen du texte", ["DLRINVALIDE", "DLR5L17N90002"])])),
 ("RUANR5L17S2026IDC99004.json", lambda: reunion("RUANR5L17S2026IDC99004", "reunionCommission_type", "PO900010", "2026-11-13T14:00:00.000+01:00", "2026-11-13T16:00:00.000+01:00", "Confirmé",
     point("[EXEMPLE] Audition fictive sans texte.", "Audition"))),
 ("RUANR5L17S2026IDS99005.json", lambda: reunion("RUANR5L17S2026IDS99005", "seance_type", "PO838901", "2026-11-03T15:00:00.000+01:00", "2026-11-03T20:00:00.000+01:00", "Confirmé",
     point("[EXEMPLE] Séance passée.", "Discussion"), participants=False)),
 ("RUANR5L17S2026IDS99006.json", lambda: reunion("RUANR5L17S2026IDS99006", "seance_type", "PO838901", "2026-11-19T21:30:00.000+01:00", "2026-11-20T00:30:00.000+01:00", "Annulé",
     point("[EXEMPLE] Séance de nuit annulée.", "Discussion"), participants=False)),
 ("RUANR5L17S2026IDI99007.json", lambda: reunion("RUANR5L17S2026IDI99007", "reunionInitParlementaire_type", "PO990001", "2026-11-14T10:00:00.000+01:00", "2026-11-14T12:00:00.000+01:00", "Confirmé",
     point("[EXEMPLE] Réunion d'un groupe d'amitié fictif.", "Réunion", "DLR5L17N90001"))),
]

def dump(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")

for name, make in files:
    dump("an/reunion/" + name, make())
with open("an-agenda.txt", "w", encoding="utf-8", newline="\n") as f:
    f.write("# [EXEMPLE] Réunions fictives au format de json/reunion/ d'Agenda.json.zip (Assemblée nationale), produites par generate_week_example.py.\n")
    for name, _ in files:
        f.write(name + "\n")

dump("pe/meetings-2026.json", {"data": [
  {"id": "eli/dl/event/MTG-PL-2026-11-18", "type": "Activity", "activity_id": "MTG-PL-2026-11-18", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2026-11-18"},
  {"id": "eli/dl/event/MTG-PL-2026-12-14", "type": "Activity", "activity_id": "MTG-PL-2026-12-14", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2026-12-14"}],
  "@context": ["https://data.europarl.europa.eu/api/v2/context.jsonld"]})
dump("pe/meetings/MTG-PL-2026-11-18/foreseen-activities.json", {"data": [
  {"id": "eli/dl/event/MTG-PL-2026-11-18-OJ-1", "type": "Activity", "activity_id": "MTG-PL-2026-11-18-OJ-1", "had_activity_type": "def/ep-activities/PLENARY_DEBATE",
   "activity_order": 1, "activity_start_date": "2026-11-18T09:00:00+01:00", "activity_end_date": "2026-11-18T11:50:00+01:00",
   "activity_label": {"fr": "[EXEMPLE] Débat fictif sur les forêts européennes", "en": "[EXAMPLE] Fictional debate on European forests"}},
  {"id": "eli/dl/event/MTG-PL-2026-11-18-OJ-2", "type": "Activity", "activity_id": "MTG-PL-2026-11-18-OJ-2", "had_activity_type": "def/ep-activities/PLENARY_VOTE",
   "activity_order": 2, "activity_start_date": "2026-11-18T12:00:00",
   "activity_label": [{"@language": "en", "@value": "[EXAMPLE] Voting time"}, {"@language": "fr", "@value": "[EXEMPLE] Heure des votes"}]},
  {"id": "eli/dl/event/MTG-PL-2026-11-18-OJ-3", "type": "Activity", "activity_id": "MTG-PL-2026-11-18-OJ-3", "had_activity_type": "def/ep-activities/PLENARY_OTHER", "activity_order": 3}],
  "@context": ["https://data.europarl.europa.eu/api/v2/context.jsonld"]})

ics = r"""BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//[EXEMPLE] Calendrier fictif au format du Sénat//FR
CALSCALE:GREGORIAN
X-WR-CALNAME:[EXEMPLE] Séance publique
BEGIN:VTIMEZONE
TZID:Europe/Paris
BEGIN:STANDARD
DTSTART:19701025T030000
TZOFFSETFROM:+0200
TZOFFSETTO:+0100
RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
END:STANDARD
BEGIN:DAYLIGHT
DTSTART:19700329T020000
TZOFFSETFROM:+0100
TZOFFSETTO:+0200
RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
END:DAYLIGHT
END:VTIMEZONE
BEGIN:VEVENT
UID:exemple-seance-99001@senat.fr
DTSTAMP:20261105T120000Z
DTSTART;TZID=Europe/Paris:20261110T143000
DTEND;TZID=Europe/Paris:20261110T200000
SUMMARY:[EXEMPLE] Projet de loi fictif sur les acacias
DESCRIPTION:[EXEMPLE] Discussion générale\, puis examen des articles.\nTexte
  n° 9002 (2026-2027).
CATEGORIES:Séance publique
LOCATION:[EXEMPLE] Hémicycle
URL:https://www.senat.fr/ordre-du-jour/ordre-du-jour.html
ORGANIZER;CN="[EXEMPLE] Prénom Nom":mailto:exemple@example.org
ATTENDEE;CN="[EXEMPLE] Autre Personne":mailto:autre@example.org
CONTACT:[EXEMPLE] Prénom Nom\, 01 00 00 00 00
STATUS:CONFIRMED
X-AGLAE-STARTTYPE:A
END:VEVENT
BEGIN:VEVENT
UID:exemple-qag-99002@senat.fr
DTSTAMP:20261105T120000Z
DTSTART;TZID=Europe/Paris:20261104T150000
DTEND;TZID=Europe/Paris:20261104T163000
RRULE:FREQ=WEEKLY;BYDAY=WE;COUNT=4
SUMMARY:[EXEMPLE] Questions d'actualité au Gouvernement
CATEGORIES:Séance publique
URL:https://exemple.invalid/hors-du-senat
END:VEVENT
BEGIN:VEVENT
UID:exemple-suite-99003@senat.fr
DTSTAMP:20261105T120000Z
DTSTART:20261113T093000Z
DTEND:20261113T120000Z
RDATE;TZID=Europe/Paris:20261116T143000,20261117T143000
EXDATE;TZID=Europe/Paris:20261117T143000
SUMMARY:[EXEMPLE] Suite de l'examen du projet de loi fictif sur les acacias
CATEGORIES:Séance publique
STATUS:TENTATIVE
END:VEVENT
BEGIN:VEVENT
UID:exemple-decembre-99004@senat.fr
DTSTAMP:20261105T120000Z
DTSTART;TZID=Europe/Paris:20261201T143000
DTEND;TZID=Europe/Paris:20261201T200000
SUMMARY:[EXEMPLE] Séance hors de la période
END:VEVENT
END:VCALENDAR
"""
with open("senat/ical.ics", "w", encoding="utf-8", newline="\r\n") as f:
    f.write(ics)
