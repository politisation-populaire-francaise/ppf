# Generates the FICTIONAL example files of the open data importer, in the formats of the official
# files (voir-donnees-sources.md). Every person is "[EXEMPLE] Prénom Nom", every identifier is in
# the 9xxxxx range. Usage, from the repository root:
#   python -I tools/open-data-importer/scripts/generate_example.py tools/open-data-importer/src/main/resources/example
import json
import os
import sys

root = sys.argv[1]
files = []


def write(rel, data):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    files.append(rel)


def xml_list(items):
    """XML converted to JSON: nothing is null, one is an object, several are a list."""
    if not items:
        return None
    return items[0] if len(items) == 1 else items


IMPORTED_ON = "2026-11-07"

# ---------------------------------------------------------------- Assemblée nationale (AMO10)
groups = [
    ("PO900001", "[EXEMPLE] Groupe des Communs", "GDC"),
    ("PO900002", "[EXEMPLE] Groupe de l'Horizon", "GDH"),
    ("PO900003", "[EXEMPLE] Groupe du Littoral", "GDL"),
]
# uid, first name, last name, department code, department, constituency number, current group
deputies = [
    ("PA900001", "Alix", "Martinaud", "01", "Ain", "1", "PO900001"),
    ("PA900002", "Bastien", "Quérel", "2A", "Corse-du-Sud", "2", "PO900001"),
    ("PA900003", "Coralie", "Venturini", "971", "Guadeloupe", "1", "PO900001"),
    ("PA900004", "Damien", "Lestrade", "099", "Français établis hors de France", "11", "PO900001"),
    ("PA900005", "Éloïse", "Ferrandi", "69", "Rhône", "1", "PO900002"),
    ("PA900006", "Fabien", "Morvanec", "33", "Gironde", "3", "PO900002"),
    ("PA900007", "Gaëlle", "Tournemire", "75", "Paris", "5", "PO900002"),
    ("PA900008", "Hugo", "Delambre", "59", "Nord", "12", "PO900002"),
    ("PA900009", "Inès", "Caradec", "13", "Bouches-du-Rhône", "4", "PO900003"),
    ("PA900010", "Jules", "Pradeilles", "977", "Saint-Barthélemy et Saint-Martin", "1", "PO900003"),
    ("PA900011", "Katell", "Roumégoux", "2B", "Haute-Corse", "1", "PO900003"),
    ("PA900012", "Lucien", "Abgrall", "44", "Loire-Atlantique", "7", None),
]

for n, (uid, first, last, dept, dept_name, circo, group) in enumerate(deputies, start=1):
    mandates = [{
        "@xsi:type": "MandatParlementaire_type",
        "uid": f"PM9{n:05d}",
        "acteurRef": uid,
        "legislature": "17",
        "typeOrgane": "ASSEMBLEE",
        "dateDebut": "2024-07-08",
        "datePublication": "2024-07-10",
        "dateFin": None,
        "mandature": {"datePriseFonction": "2024-07-08", "causeFin": None, "placeHemicycle": str(100 + n), "mandatRemplaceRef": None},
        "infosQualite": {"codeQualite": "membre", "libQualite": "membre", "libQualiteSex": "membre"},
        "organes": {"organeRef": "PO900000"},
        "election": {
            "lieu": {"region": "[EXEMPLE]", "regionType": "Métropolitain", "departement": dept_name, "numDepartement": dept, "numCirco": circo},
            "causeMandat": "élections générales",
            "refCirconscription": f"PO99{n:04d}",
        },
        "suppleants": {"suppleant": {"dateDebut": "2024-07-08", "dateFin": None, "suppleantRef": f"PA98{n:04d}"}},
        "collaborateurs": {"collaborateur": {"qualite": "M.", "prenom": "[EXEMPLE] Collaborateur", "nom": "Fictif"}},
    }]
    if group:
        mandates.append({
            "@xsi:type": "MandatSimple_type",
            "uid": f"PM8{n:05d}",
            "acteurRef": uid,
            "legislature": "17",
            "typeOrgane": "GP",
            "dateDebut": "2024-07-10",
            "dateFin": None,
            "infosQualite": {"codeQualite": "Membre", "libQualite": "Membre"},
            "organes": {"organeRef": group},
        })
    if n == 11:
        # A past group: ended, never shown.
        mandates.append({"uid": "PM890011", "acteurRef": uid, "legislature": "17", "typeOrgane": "GP", "dateDebut": "2024-07-10", "dateFin": "2025-06-30", "organes": {"organeRef": "PO900004"}})
    if n in (5, 9):
        # A committee with several bodies (organeRef as a list), not used by the import.
        mandates.append({"uid": f"PM7{n:05d}", "acteurRef": uid, "legislature": "17", "typeOrgane": "COMPER", "dateDebut": "2024-07-20", "dateFin": None, "organes": {"organeRef": ["PO900010", "PO900011"]}})
        mandates.append({"uid": f"PM6{n:05d}", "acteurRef": uid, "legislature": None, "typeOrgane": "PARPOL", "dateDebut": "2023-01-01", "dateFin": None, "organes": {"organeRef": "PO900020"}})
    actor = {"acteur": {
        "@xmlns": "http://schemas.assemblee-nationale.fr/referentiel",
        "uid": {"@xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance", "@xsi:type": "IdActeur_type", "#text": uid},
        "etatCivil": {
            "ident": {"civ": "M." if n % 2 == 0 else "Mme", "prenom": f"[EXEMPLE] {first}", "nom": last, "alpha": last, "trigramme": last[:3].upper()},
            "infoNaissance": {"dateNais": "1970-01-01", "villeNais": "[EXEMPLE] Ville fictive", "depNais": "[EXEMPLE]", "paysNais": "[EXEMPLE]"},
            "dateDeces": None,
        },
        "profession": {"libelleCourant": "[EXEMPLE] Profession fictive", "socProcINSEE": {"catSocPro": "[EXEMPLE]", "famSocPro": "[EXEMPLE]"}},
        "uri_hatvp": None,
        "adresses": {"adresse": [
            {"@xsi:type": "AdresseMail_type", "uid": f"AD9{n:05d}", "type": "15", "typeLibelle": "Mèl", "valElec": "exemple@example.invalid"},
            {"@xsi:type": "AdressePostale_type", "uid": f"AD8{n:05d}", "type": "0", "typeLibelle": "Adresse officielle", "numeroRue": "1", "nomRue": "[EXEMPLE] Rue fictive", "codePostal": "00000", "ville": "[EXEMPLE]"},
        ]},
        "mandats": {"mandat": xml_list(mandates)},
    }}
    write(f"an/amo10/json/acteur/{uid}.json", actor)

organes = [
    {"uid": "PO900000", "codeType": "ASSEMBLEE", "libelle": "[EXEMPLE] Assemblée nationale de la 17e législature", "libelleAbrege": "AN", "libelleAbrev": "AN", "viMoDe": {"dateDebut": "2024-07-08", "dateAgrement": None, "dateFin": None}, "legislature": "17"},
    *[{"@xsi:type": "GroupePolitique_type", "uid": uid, "codeType": "GP", "libelle": label, "libelleEdition": label, "libelleAbrege": label, "libelleAbrev": abbrev,
       "viMoDe": {"dateDebut": "2024-07-10", "dateAgrement": None, "dateFin": None}, "organeParent": None, "chambre": None, "regime": "5ème République", "legislature": "17",
       "secretariat": {"secretaire": None}, "couleurAssociee": "#123456", "positionPolitique": "[EXEMPLE]", "preseance": "1"} for uid, label, abbrev in groups],
    {"uid": "PO900004", "codeType": "GP", "libelle": "[EXEMPLE] Ancien groupe dissous", "libelleAbrev": "AGD", "viMoDe": {"dateDebut": "2024-07-10", "dateFin": "2025-06-30"}, "legislature": "17"},
    {"uid": "PO900010", "codeType": "COMPER", "libelle": "[EXEMPLE] Commission des affaires fictives", "libelleAbrev": "CAF", "viMoDe": {"dateDebut": "2024-07-20", "dateFin": None}, "legislature": "17"},
    {"uid": "PO900011", "codeType": "COMPER", "libelle": "[EXEMPLE] Commission des essais", "libelleAbrev": "CDE", "viMoDe": {"dateDebut": "2024-07-20", "dateFin": None}, "legislature": "17"},
    {"uid": "PO900020", "codeType": "PARPOL", "libelle": "[EXEMPLE] Parti de rattachement fictif", "libelleAbrev": "PRF", "viMoDe": {"dateDebut": "2023-01-01", "dateFin": None}},
    {"uid": "PO990001", "codeType": "CIRCONSCRIPTION", "libelle": "[EXEMPLE] 1re circonscription", "viMoDe": {"dateDebut": None, "dateFin": None}, "lieu": {"departement": {"code": "01", "libelle": "Ain"}}},
]
for organe in organes:
    write(f"an/amo10/json/organe/{organe['uid']}.json", {"organe": organe})

# A conflict-of-interest withdrawal (déport): present in AMO10, not used by the first version.
write("an/amo10/json/deport/DP900001.json", {"deport": {"uid": "DP900001", "acteurRef": "PA900006", "dateCreation": "2026-09-01", "portee": {"libelle": "[EXEMPLE]"}}})

# ---------------------------------------------------------------- Assemblée nationale (dossiers)
dossiers = [
    ("DLR5L17N90001", "[EXEMPLE] Projet de loi relatif au logement abordable", "exemple_logement_abordable", "Projet de loi ordinaire"),
    ("DLR5L17N90002", "[EXEMPLE] Proposition de loi sur la transparence des rendez-vous", "exemple_transparence_rendez_vous", "Proposition de loi ordinaire"),
]
for uid, title, path, procedure in dossiers:
    write(f"an/dossiers/json/dossierParlementaire/{uid}.json", {"dossierParlementaire": {
        "@xsi:type": "DossierLegislatif_Type", "uid": uid, "legislature": "17",
        "titreDossier": {"titre": title, "titreChemin": path, "senatChemin": None},
        "procedureParlementaire": {"code": "1", "libelle": procedure},
        "initiateur": {"acteurs": {"acteur": {"acteurRef": "PA900005", "mandatRef": "PM900005"}}},
        "actesLegislatifs": {"acteLegislatif": {"uid": "L17-EX-1", "codeActe": "AN1", "libelleActe": {"nomCanonique": "1ère lecture"}}},
    }})
write("an/dossiers/json/document/PRJLANR5L17B9001.json", {"document": {"uid": "PRJLANR5L17B9001", "titres": {"titrePrincipal": "[EXEMPLE] Texte fictif"}}})

# ---------------------------------------------------------------- Assemblée nationale (ballots)
group_of = {d[0]: (d[6] or "PO900005") for d in deputies}
# p for, c against, a abstention, n not voting, - absent; then corrections and delegations.
ballots = [
    dict(number=9001, date="2026-09-15", code="SPO", lib="scrutin public ordinaire", title="[EXEMPLE] l'article 1er du projet de loi relatif au logement abordable (première lecture).",
         dossier="DLR5L17N90001", votes="nppp ppcn a-cp", delegated={"PA900003"}, corrections=[], malfunctions=[]),
    dict(number=9002, date="2026-09-29", code="SPS", lib="scrutin public solennel", title="[EXEMPLE] l'ensemble du projet de loi relatif au logement abordable (première lecture).",
         dossier="DLR5L17N90001", votes="nppp pccn aapc", delegated={"PA900002", "PA900009"}, corrections=[("PA900005", "contres")], malfunctions=[]),
    dict(number=9003, date="2026-09-30", code="SPO", lib="scrutin public ordinaire", title="[EXEMPLE] l'amendement n° 12 de M. [EXEMPLE] Fabien Morvanec à l'article 3 du projet de loi de finances.",
         dossier=None, votes="ncca -ppn cc-a", delegated=set(), corrections=[("PA900011", "abstentions"), ("PA900004", "nonVotantsVolontaires")], malfunctions=[]),
    dict(number=9004, date="2026-10-08", code="MOC", lib="motion de censure", title="[EXEMPLE] la motion de censure déposée en application de l'article 49, alinéa 2, de la Constitution.",
         dossier=None, votes="nppp -n-n p---", delegated=set(), corrections=[], malfunctions=[("PA900007", "pours")]),
    dict(number=9005, date="2026-10-14", code="SPO", lib="scrutin public ordinaire", title="[EXEMPLE] l'ensemble de la proposition de loi sur la transparence des rendez-vous (première lecture).",
         dossier="DLR5L17N90002", votes="nppp ppnn pppa", delegated={"PA900006"}, corrections=[], malfunctions=[]),
    dict(number=9006, date="2026-10-21", code="SPO", lib="scrutin public ordinaire", title="[EXEMPLE] l'article unique de la proposition de résolution sur les services publics de proximité.",
         dossier="DLR5L17N90003", votes="nacc ccnn ppp-", delegated=set(), corrections=[], malfunctions=[]),
]
KEYS = {"p": "pours", "c": "contres", "a": "abstentions", "n": "nonVotants"}
# Not voting because of the office: the President of the Assemblée (PAN), a member of the Government (MG).
CAUSES = {"PA900001": "PAN", "PA900008": "MG"}
all_groups = [g[0] for g in groups] + ["PO900005"]


def counts(entries):
    c = {"nonVotants": "0", "pour": "0", "contre": "0", "abstentions": "0", "nonVotantsVolontaires": "0"}
    for key, _ in entries:
        name = {"pours": "pour", "contres": "contre", "abstentions": "abstentions", "nonVotants": "nonVotants"}[key]
        c[name] = str(int(c[name]) + 1)
    return c


def voter(uid, number, delegated, cause=None):
    v = {"acteurRef": uid, "mandatRef": "PM9" + uid[-5:], "numPlace": str(100 + int(uid[-2:])), "parDelegation": "true" if uid in delegated else "false"}
    if cause:
        v["causePositionVote"] = cause
        del v["parDelegation"]
    return v


for b in ballots:
    codes = b["votes"].replace(" ", "")
    entries = [(KEYS[code], d[0]) for code, d in zip(codes, deputies) if code != "-"]
    group_blocks = []
    for g in all_groups:
        mine = [(k, uid) for k, uid in entries if group_of[uid] == g]
        if not mine:
            continue
        c = counts(mine)
        nominal = {}
        for key in ["nonVotants", "pours", "contres", "abstentions"]:
            vs = [voter(uid, b["number"], b["delegated"], CAUSES.get(uid) if key == "nonVotants" else None) for k, uid in mine if k == key]
            nominal[key] = None if not vs else {"votant": xml_list(vs)}
        majority = max(["pour", "contre", "abstentions"], key=lambda k: int(c[k]))
        group_blocks.append({
            "organeRef": g,
            "nombreMembresGroupe": str(sum(1 for d in deputies if (d[6] or "PO900005") == g)),
            "vote": {"positionMajoritaire": {"pour": "pour", "contre": "contre", "abstentions": "abstention"}[majority], "decompteVoix": c, "decompteNominatif": nominal},
        })
    total = counts(entries)
    voters = int(total["pour"]) + int(total["contre"]) + int(total["abstentions"])
    expressed = int(total["pour"]) + int(total["contre"])
    required = expressed // 2 + 1
    adopted = int(total["pour"]) >= required
    if b["code"] == "MOC":
        adopted = False

    def block(items):
        out = {"nonVotants": None, "pours": None, "contres": None, "abstentions": None, "nonVotantsVolontaires": None}
        for uid, key in items:
            current = out[key]["votant"] if out[key] else None
            v = {"acteurRef": uid, "mandatRef": "PM9" + uid[-5:], "parDelegation": "false"}
            if current is None:
                out[key] = {"votant": v}
            elif isinstance(current, list):
                current.append(v)
            else:
                out[key] = {"votant": [current, v]}
        return out

    statement = block(b["corrections"]) if b["corrections"] or b["malfunctions"] else None
    if statement is not None:
        statement["dysfonctionnement"] = block(b["malfunctions"]) if b["malfunctions"] else None
    uid = f"VTANR5L17V{b['number']}"
    write(f"an/scrutins/json/{uid}.json", {"scrutin": {
        "@xmlns": "http://schemas.assemblee-nationale.fr/referentiel",
        "uid": uid,
        "numero": str(b["number"]),
        "organeRef": "PO900000",
        "legislature": "17",
        "sessionRef": "SCR5A2026O1",
        "seanceRef": f"RUANR5L17S2026IDS9{b['number']}",
        "dateScrutin": b["date"],
        "quantiemeJourSeance": "1",
        "typeVote": {"codeTypeVote": b["code"], "libelleTypeVote": b["lib"], "typeMajorite": "majorité absolue des suffrages exprimés"},
        "sort": {"code": "adopté" if adopted else "rejeté", "libelle": "[EXEMPLE] L'Assemblée nationale a " + ("adopté." if adopted else "n'a pas adopté.")},
        "titre": b["title"],
        "demandeur": {"texte": "[EXEMPLE] Président du groupe fictif", "referenceLegislative": None},
        "objet": {"libelle": b["title"], "dossierLegislatif": None if not b["dossier"] else {"dossierRef": b["dossier"], "libelle": "[EXEMPLE]"}},
        "modePublicationDesVotes": "DecompteNominatif",
        "syntheseVote": {"nombreVotants": str(voters), "suffragesExprimes": str(expressed), "nbrSuffragesRequis": str(required), "annonce": "[EXEMPLE]", "decompte": total},
        "ventilationVotes": {"organe": {"organeRef": "PO900000", "groupes": {"groupe": xml_list(group_blocks)}}},
        "miseAuPoint": statement,
        "lieuVote": "Hémicycle",
    }})

# A group of non-attached members exists in the ballots only (PO900005); add its organe so that the
# group count names a known group, as in the real files.
write("an/amo10/json/organe/PO900005.json", {"organe": {"uid": "PO900005", "codeType": "GP", "libelle": "[EXEMPLE] Députés non inscrits", "libelleAbrev": "NI", "viMoDe": {"dateDebut": "2024-07-10", "dateFin": None}, "legislature": "17"}})

# ---------------------------------------------------------------- Sénat
senators = [
    ("99001X", "Mathilde", "Garrigou", "EXA", "[EXEMPLE] Groupe des Vallées", "GDV", "N", "33", "Gironde", 1),
    ("99002X", "Nathan", "Esquerré", "EXA", "[EXEMPLE] Groupe des Vallées", "GDV", "A", "2A", "Corse-du-Sud", 2),
    ("99003X", "Océane", "Kerbiriou", "EXB", "[EXEMPLE] Groupe des Plateaux", "GDP", "N", "975", "Saint-Pierre-et-Miquelon", 1),
    ("99004X", "Pascal", "Vidalenc", "EXB", "[EXEMPLE] Groupe des Plateaux", "GDP", "N", "01", "Ain", 2),
]
write("senat/senateurs.json", [{
    "matricule": m, "civilite": "M." if i % 2 else "Mme", "feminise": i % 2 == 0, "nom": last, "prenom": f"[EXEMPLE] {first}",
    "groupe": {"code": code, "libelle": label, "libelleCourt": short, "codeTypeAppartenance": kind},
    "circonscription": {"code": cc, "libelle": cl},
    "categorieProfessionnelle": {"code": "99", "libelle": "[EXEMPLE] Catégorie fictive"},
    "serie": serie, "url": f"https://www.senat.fr/senateur/exemple_{m.lower()}.html", "urlAvatar": f"https://www.senat.fr/exemple/{m.lower()}.jpg",
    "facebook": None, "twitter": "https://example.invalid/exemple", "siege": str(200 + i),
    "organismes": [{"code": "EX", "libelle": "[EXEMPLE] Commission fictive"}],
} for i, (m, first, last, code, label, short, kind, cc, cl, serie) in enumerate(senators)])

# ---------------------------------------------------------------- Parlement européen (API v2, JSON-LD)
CONTEXT = "https://data.europarl.europa.eu/api/v2/context.jsonld"
meps = [
    ("990001", "Quitterie", "Bellocq", "990201", "990101"),
    ("990002", "Romain", "Taillefer", "990201", "990102"),
    ("990003", "Solène", "Marivaux", "990202", "990103"),
]
write("pe/meps/show-current.json", {"data": [{
    "id": f"person/{i}", "type": "Person", "identifier": i, "label": f"[EXEMPLE] {first} {last.upper()}", "familyName": last, "givenName": f"[EXEMPLE] {first}",
    "sortLabel": last.upper(), "api:country-of-representation": "FR", "api:political-group": "PONTS" if g == "990201" else "PHARES",
} for i, first, last, g, p in meps], "@context": [CONTEXT]})
for i, first, last, g, p in meps:
    memberships = [
        {"id": f"membership/{i}-1", "type": "Membership", "identifier": f"{i}-1", "membershipClassification": "def/ep-entities/EU_POLITICAL_GROUP", "organization": f"org/{g}",
         "memberDuring": {"type": "PeriodOfTime", "startDate": "2024-07-16"}, "role": "def/ep-roles/MEMBER", "membershipPrincipalCountry": "FR"},
        {"id": f"membership/{i}-2", "type": "Membership", "identifier": f"{i}-2", "membershipClassification": "def/ep-entities/NATIONAL_POLITICAL_GROUP", "organization": f"org/{p}",
         "memberDuring": {"type": "PeriodOfTime", "startDate": "2024-07-16"}, "role": "def/ep-roles/MEMBER"},
        {"id": f"membership/{i}-3", "type": "Membership", "identifier": f"{i}-3", "membershipClassification": "def/ep-entities/COMMITTEE_PARLIAMENTARY_STANDING", "organization": "org/990301",
         "memberDuring": {"type": "PeriodOfTime", "startDate": "2024-07-23"}, "role": "def/ep-roles/MEMBER"},
    ]
    if i == "990003":
        # An earlier national party: ended, never shown.
        memberships.append({"id": f"membership/{i}-4", "type": "Membership", "identifier": f"{i}-4", "membershipClassification": "def/ep-entities/NATIONAL_POLITICAL_GROUP",
                            "organization": "org/990104", "memberDuring": {"type": "PeriodOfTime", "startDate": "2019-07-02", "endDate": "2024-07-15"}})
    write(f"pe/meps/{i}.json", {"data": [{
        "id": f"person/{i}", "type": "Person", "identifier": i, "label": f"[EXEMPLE] {first} {last.upper()}", "familyName": last, "givenName": f"[EXEMPLE] {first}",
        "bday": "1970-01-01", "placeOfBirth": "[EXEMPLE] Ville fictive", "citizenship": "http://publications.europa.eu/resource/authority/country/FRA",
        "img": "https://example.invalid/exemple.jpg", "hasEmail": "mailto:exemple@example.invalid", "hasGender": "http://publications.europa.eu/resource/authority/human-sex/NAP",
        "contactPoint": [{"type": "ContactPoint", "hasTelephone": "tel:+0000000000"}],
        "hasMembership": memberships,
    }], "@context": [CONTEXT]})
bodies = [
    ("990101", "PDR", "[EXEMPLE] Parti des Rivières"),
    ("990102", "MDV", "[EXEMPLE] Mouvement des Villages"),
    ("990103", "UDC-EX", "[EXEMPLE] Union des Coteaux"),
    ("990104", "AP-EX", "[EXEMPLE] Ancien parti fictif"),
    ("990201", "PONTS", "[EXEMPLE] Groupe européen des Ponts"),
    ("990202", "PHARES", "[EXEMPLE] Groupe européen des Phares"),
]
for i, short, long in bodies:
    write(f"pe/corporate-bodies/{i}.json", {"data": [{
        "id": f"org/{i}", "type": "Organization", "identifier": i, "label": short,
        "prefLabel": {"fr": long, "en": long.replace("[EXEMPLE] ", "[EXEMPLE] (en) ")}, "altLabel": {"fr": short},
        "classification": "def/ep-entities/EU_POLITICAL_GROUP" if i.startswith("9902") else "def/ep-entities/NATIONAL_CHAMBER",
    }], "@context": [CONTEXT]})
write("pe/meetings-2024.json", {"data": [], "@context": [CONTEXT]})
write("pe/meetings-2025.json", {"data": [], "@context": [CONTEXT]})
write("pe/meetings-2026.json", {"data": [
    {"id": "eli/dl/event/MTG-PL-2026-09-16", "type": "Activity", "activity_id": "MTG-PL-2026-09-16", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2026-09-16"},
    {"id": "eli/dl/event/MTG-PL-2026-10-21", "type": "Activity", "activity_id": "MTG-PL-2026-10-21", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2026-10-21"},
    # After the day of the import: not imported.
    {"id": "eli/dl/event/MTG-PL-2026-11-23", "type": "Activity", "activity_id": "MTG-PL-2026-11-23", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2026-11-23"},
], "@context": [CONTEXT]})
FOREIGN = [f"person/88000{k}" for k in range(1, 6)]


def decision(sitting, order, title, method, outcome, favor, against, abst, counts):
    return {
        "id": f"eli/dl/event/{sitting}-DEC-{order}", "type": "Decision", "activity_id": f"{sitting}-DEC-{order}", "activity_date": sitting[7:],
        "activity_order": str(order), "activity_label": {"fr": title, "en": title.replace("[EXEMPLE] ", "[EXEMPLE] (en) ")},
        "decision_method": f"def/ep-decision-methods/{method}", "decision_outcome": f"def/ep-decision-outcomes/{outcome}",
        "number_of_votes_favor": counts[0], "number_of_votes_against": counts[1], "number_of_votes_abstention": counts[2],
        "had_voter_favor": favor, "had_voter_against": against, "had_voter_abstention": abst,
        "number_of_attendees": 650,
    }


write("pe/meetings/MTG-PL-2026-09-16/decisions.json", {"data": [
    decision("MTG-PL-2026-09-16", 1, "[EXEMPLE] Rapport sur la protection des petites rivières - vote unique", "VOTE_ELECTRONIC_ROLLCALL", "ADOPTED",
             ["person/990001", "person/990002", FOREIGN[0], FOREIGN[1]], ["person/990003", FOREIGN[2]], FOREIGN[3], (412, 201, 37)),
    decision("MTG-PL-2026-09-16", 2, "[EXEMPLE] Résolution sur les phares côtiers - amendement 4", "VOTE_ELECTRONIC_ROLLCALL", "REJECTED",
             [FOREIGN[0]], ["person/990001", "person/990003", FOREIGN[1]], ["person/990002"], (188, 389, 61)),
    decision("MTG-PL-2026-09-16", 3, "[EXEMPLE] Ordre du jour - vote à main levée", "VOTE_SHOW_OF_HANDS", "ADOPTED", [], [], [], (0, 0, 0)),
], "@context": [CONTEXT]})
write("pe/meetings/MTG-PL-2026-10-21/decisions.json", {"data": [
    decision("MTG-PL-2026-10-21", 1, "[EXEMPLE] Règlement sur les ponts transfrontaliers - accord provisoire", "VOTE_ELECTRONIC_ROLLCALL", "ADOPTED",
             ["person/990001", "person/990002", "person/990003", FOREIGN[4]], [FOREIGN[0]], [], (530, 98, 22)),
    decision("MTG-PL-2026-10-21", 2, "[EXEMPLE] Objection à un acte délégué sur les villages", "VOTE_ELECTRONIC_ROLLCALL", "REJECTED",
             "person/990003", ["person/990001", FOREIGN[1]], [], (120, 480, 40)),
], "@context": [CONTEXT]})

# ================================================================ HISTORY OF THE BALLOTS
# (voir-historique-sources.md, voir-historique-import.md) Same official formats, other fictional
# people (identifiers 91xxxx), a few ballots per legislature, session and term.


def write_text(rel, text, encoding):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding=encoding, newline="\n") as f:
        f.write(text)
    files.append(rel)


# ---------------------------------------------------------------- Assemblée nationale, AMO30
LEG_DATES = {14: ("2012-06-20", "2017-06-20"), 15: ("2017-06-21", "2022-06-21"), 16: ("2022-06-22", "2024-06-09"), 17: ("2024-07-18", None)}
# uid, first name, last name, legislatures, groups (organ, start, end), parties of attachment (organ, start, end)
history_deputies = [
    ("PA910001", "Armelle", "Kerjean", [14, 15], [("PO910101", "2012-06-26", "2022-06-21")], [("PO910201", "2012-12-01", None)]),
    ("PA910002", "Benoît", "Larrieu", [14, 15, 16, 17], [("PO910101", "2012-06-26", None)], [("PO910201", "2012-12-01", "2019-12-31"), ("PO910202", "2020-01-01", None)]),
    ("PA910003", "Capucine", "Roussel", [14, 15, 16, 17], [("PO910102", "2012-06-26", None)], [("PO910202", "2012-12-01", None)]),
    # Never declared a party of attachment: the group stands for it.
    ("PA910004", "Didier", "Marsan", [14, 15, 16, 17], [("PO910102", "2012-06-26", None)], []),
    ("PA910005", "Estelle", "Guérin", [16, 17], [("PO910101", "2022-06-28", None)], [("PO910201", "2022-06-28", None)]),
    ("PA910006", "Firmin", "Aubrac", [14], [("PO910102", "2012-06-26", "2017-06-20")], [("PO910202", "2012-12-01", "2017-06-20")]),
]
for n, (uid, first, last, legs, gps, parties) in enumerate(history_deputies, start=1):
    mandates = []
    for leg in legs:
        start, end = LEG_DATES[leg]
        mandates.append({
            "@xsi:type": "MandatParlementaire_type", "uid": f"PM91{leg}{n:03d}", "acteurRef": uid, "legislature": str(leg), "typeOrgane": "ASSEMBLEE",
            "dateDebut": start, "datePublication": start, "dateFin": end,
            "mandature": {"datePriseFonction": start, "causeFin": None, "placeHemicycle": str(300 + n), "mandatRemplaceRef": None},
            "infosQualite": {"codeQualite": "membre", "libQualite": "membre"},
            "organes": {"organeRef": "PO910000"},
            "election": {"lieu": {"region": "[EXEMPLE]", "departement": "[EXEMPLE] Département", "numDepartement": "29", "numCirco": str(n)}},
            "collaborateurs": {"collaborateur": {"qualite": "M.", "prenom": "[EXEMPLE] Collaborateur", "nom": "Fictif"}},
        })
    for k, (organ, start, end) in enumerate(gps, start=1):
        mandates.append({"@xsi:type": "MandatSimple_type", "uid": f"PM92{n:03d}{k}", "acteurRef": uid, "legislature": None, "typeOrgane": "GP",
                         "dateDebut": start, "dateFin": end, "infosQualite": {"codeQualite": "Membre"}, "organes": {"organeRef": organ}})
    for k, (organ, start, end) in enumerate(parties, start=1):
        mandates.append({"@xsi:type": "MandatSimple_type", "uid": f"PM93{n:03d}{k}", "acteurRef": uid, "legislature": None, "typeOrgane": "PARPOL",
                         "dateDebut": start, "dateFin": end, "infosQualite": {"codeQualite": "Membre"}, "organes": {"organeRef": organ}})
    # A committee: never read.
    mandates.append({"uid": f"PM94{n:03d}", "acteurRef": uid, "typeOrgane": "COMPER", "dateDebut": "2012-07-01", "dateFin": None, "organes": {"organeRef": "PO910301"}})
    write(f"an-historique/amo30/json/acteur/{uid}.json", {"acteur": {
        "uid": {"@xsi:type": "IdActeur_type", "#text": uid},
        "etatCivil": {"ident": {"civ": "Mme" if n % 2 else "M.", "prenom": f"[EXEMPLE] {first}", "nom": last, "alpha": last},
                      "infoNaissance": {"dateNais": "1970-01-01", "villeNais": "[EXEMPLE] Ville fictive"}},
        "profession": {"libelleCourant": "[EXEMPLE] Profession fictive"},
        "adresses": {"adresse": {"@xsi:type": "AdresseMail_type", "valElec": "exemple@example.invalid"}},
        "mandats": {"mandat": xml_list(mandates)},
    }})
# Someone who never sat in the Assemblée (a senator in AMO30): left out.
write("an-historique/amo30/json/acteur/PA910099.json", {"acteur": {
    "uid": {"#text": "PA910099"}, "etatCivil": {"ident": {"civ": "M.", "prenom": "[EXEMPLE] Gaston", "nom": "Sénateur"}},
    "mandats": {"mandat": {"uid": "PM919999", "acteurRef": "PA910099", "typeOrgane": "SENAT", "dateDebut": "2014-10-01", "dateFin": None, "organes": {"organeRef": "PO910400"}}},
}})
history_organs = [
    ("PO910000", "ASSEMBLEE", "[EXEMPLE] Assemblée nationale", "AN"),
    ("PO910101", "GP", "[EXEMPLE] Groupe des Collines", "GCO"),
    ("PO910102", "GP", "[EXEMPLE] Groupe des Vallées", "GVA"),
    ("PO910201", "PARPOL", "[EXEMPLE] Parti des Collines", "PCO"),
    ("PO910202", "PARPOL", "[EXEMPLE] Mouvement des Vallées", "MVA"),
    ("PO910301", "COMPER", "[EXEMPLE] Commission fictive", "CF"),
]
for uid, code, label, short in history_organs:
    write(f"an-historique/amo30/json/organe/{uid}.json", {"organe": {"uid": uid, "codeType": code, "libelle": label, "libelleAbrev": short,
                                                                     "viMoDe": {"dateDebut": "2012-06-20", "dateFin": None}, "couleurAssociee": "#123456"}})

# ---------------------------------------------------------------- Assemblée nationale, ballots of the 14th to 17th legislatures
KEYS = {"pour": "pours", "contre": "contres", "abst": "abstentions", "nv": "nonVotants"}


def history_scrutin(leg, number, date, title, code, adopted, groups, dossier=None):
    group_blocks = []
    total = {"nonVotants": 0, "pour": 0, "contre": 0, "abstentions": 0, "nonVotantsVolontaires": 0}
    for organ, votes in groups:
        counts = {"nonVotants": len(votes.get("nv", [])), "pour": len(votes.get("pour", [])), "contre": len(votes.get("contre", [])),
                  "abstentions": len(votes.get("abst", [])), "nonVotantsVolontaires": 0}
        for k in total:
            total[k] += counts[k]
        nominal = {}
        for short, key in KEYS.items():
            voters = [{"acteurRef": a, "mandatRef": f"PM9{a[-4:]}", "numPlace": str(300 + int(a[-1])), "parDelegation": "false"} for a in votes.get(short, [])]
            nominal[key] = {"votant": xml_list(voters)} if voters else None
        group_blocks.append({"organeRef": organ, "nombreMembresGroupe": "3", "vote": {
            "positionMajoritaire": "pour", "decompteVoix": {k: str(v) for k, v in counts.items()}, "decompteNominatif": nominal}})
    voters = total["pour"] + total["contre"] + total["abstentions"]
    return {
        "uid": f"VTANR5L{leg}V{number}", "numero": str(number), "organeRef": "PO910000", "legislature": str(leg),
        "dateScrutin": date, "typeVote": {"codeTypeVote": code, "libelleTypeVote": "[EXEMPLE]"},
        "sort": {"code": "adopté" if adopted else "rejeté", "libelle": "[EXEMPLE]"},
        "titre": title, "demandeur": {"texte": "[EXEMPLE] Président du groupe fictif"},
        "objet": {"libelle": title, "dossierLegislatif": None if not dossier else {"dossierRef": dossier, "libelle": "[EXEMPLE]"}},
        "modePublicationDesVotes": "DecompteNominatif",
        "syntheseVote": {"nombreVotants": str(voters), "suffragesExprimes": str(total["pour"] + total["contre"]), "nbrSuffragesRequis": "1",
                         "annonce": "[EXEMPLE]", "decompte": {k: str(v) for k, v in total.items()}},
        "ventilationVotes": {"organe": {"organeRef": "PO910000", "groupes": {"groupe": xml_list(group_blocks)}}},
    }


# The 14th legislature is one document with the list of its ballots, and stops on 24 November 2016.
write("an-historique/scrutins-14/Scrutins_XIV.json", {"scrutins": {"scrutin": [
    history_scrutin(14, 9001, "2012-07-10", "[EXEMPLE] l'ensemble du projet de loi relatif aux sentiers côtiers (première lecture).", "SPO", True,
                    [("PO910101", {"pour": ["PA910001", "PA910002"]}), ("PO910102", {"contre": ["PA910003"], "abst": ["PA910004"], "nv": ["PA910006"]})],
                    dossier="DLR5L14N91001"),
    history_scrutin(14, 9002, "2014-03-12", "[EXEMPLE] l'amendement n° 12 à l'article 3 du projet de loi relatif aux phares.", "SPS", False,
                    [("PO910101", {"pour": ["PA910001"], "contre": ["PA910002"]}), ("PO910102", {"contre": ["PA910003", "PA910006"], "abst": ["PA910004"]})]),
    history_scrutin(14, 9003, "2016-11-24", "[EXEMPLE] l'ensemble de la proposition de loi relative aux moulins.", "SPO", True,
                    [("PO910101", {"pour": ["PA910001", "PA910002"]}), ("PO910102", {"pour": ["PA910004"]})]),
]}})
history_ballots = {
    15: [
        (9001, "2018-02-06", "[EXEMPLE] l'article unique de la proposition de loi relative aux haies.", "SPO", True,
         [("PO910101", {"pour": ["PA910001", "PA910002"]}), ("PO910102", {"contre": ["PA910003"], "nv": ["PA910004"]})]),
        (9002, "2020-05-12", "[EXEMPLE] la motion de censure déposée en application de l'article 49, alinéa 3, de la Constitution.", "MOC", False,
         [("PO910101", {"pour": ["PA910002"], "contre": ["PA910001"]}), ("PO910102", {"pour": ["PA910003"], "abst": ["PA910004"]})]),
    ],
    16: [
        (9001, "2023-03-20", "[EXEMPLE] l'ensemble du projet de loi relatif aux ponts.", "SPO", True,
         [("PO910101", {"pour": ["PA910005"], "contre": ["PA910002"]}), ("PO910102", {"pour": ["PA910003", "PA910004"]})]),
    ],
    17: [
        (9001, "2025-01-15", "[EXEMPLE] l'ensemble du projet de loi relatif aux écluses.", "SPO", True,
         [("PO910101", {"pour": ["PA910002", "PA910005"]}), ("PO910102", {"abst": ["PA910003"], "contre": ["PA910004"]})]),
        (9002, "2026-10-14", "[EXEMPLE] la motion de censure déposée en application de l'article 49, alinéa 2, de la Constitution.", "MOC", False,
         [("PO910101", {"contre": ["PA910005"]}), ("PO910102", {"pour": ["PA910003"]})]),
    ],
}
for leg, ballots in history_ballots.items():
    for number, date, title, code, adopted, groups in ballots:
        write(f"an-historique/scrutins-{leg}/json/VTANR5L{leg}V{number}.json", {"scrutin": history_scrutin(leg, number, date, title, code, adopted, groups)})

# ---------------------------------------------------------------- Sénat, ODSEN_HISTOGROUPES.csv (Latin-1, ";")
histogroupes = [
    ("99101A", "Hélène", "Castagnet", "EXA", "[EXEMPLE] Groupe des Crêtes", "01/10/2004", ""),
    ("99102A", "Ignace", "Daubrée", "EXA", "[EXEMPLE] Groupe des Crêtes", "01/10/2004", "30/06/2018"),
    ("99102A", "Ignace", "Daubrée", "EXB", "[EXEMPLE] Groupe des Estuaires", "01/07/2018", ""),
    ("99103A", "Jeanne", "Escoffier", "EXB", "[EXEMPLE] Groupe des Estuaires", "01/10/2004", ""),
    ("99104A", "Kilian", "Fabrègue", "EXB", "[EXEMPLE] Groupe des Estuaires", "01/10/2011", "30/09/2020"),
    ("99105A", "Léonie", "Gaubert", "RASNAG", "[EXEMPLE] Réunion administrative des non-inscrits", "01/10/2020", ""),
]
csv_lines = ['"Matricule";"Nom usuel";"Prénom usuel";"Identifiant de l\'appartenance";"Code du groupe";"Nom court du groupe";'
             '"Date de début d\'appartenance";"Date de fin d\'appartenance";"Fonction";"Date de début de fonction";"Date de fin de fonction"']
for k, (m, first, last, code, short, start, end) in enumerate(histogroupes, start=1):
    csv_lines.append(f'"{m}";"{last}";"[EXEMPLE] {first}";"{k}";"{code}";"{short}";"{start}";"{end}";"Membre";"{start}";"{end}"')
write_text("senat-historique/ODSEN_HISTOGROUPES.csv", "\n".join(csv_lines) + "\n", "latin-1")

# ---------------------------------------------------------------- Sénat, dosleg.sql (PostgreSQL 8.4 dump, only what is needed)
scr_rows = [
    # sesann, scrnum, scrint, scrdat, pour, contre, votants, exprimés, majorité
    (2005, 12, "[EXEMPLE] Scrutin d'une session antérieure à 2006", "2005-12-01", 200, 100, 310, 300, 151),
    (2006, 9001, "[EXEMPLE] l'ensemble du projet de loi relatif aux sentiers côtiers", "2006-11-15 00:00:00", 200, 120, 330, 320, 161),
    (2018, 9001, "[EXEMPLE] l'amendement n° 7 à l'article 2 de la proposition de loi relative aux phares\\tdeuxième lecture", "2018-10-17", 150, 170, 330, 320, 161),
    (2018, 9002, "[EXEMPLE] l'ensemble de la proposition de loi relative aux moulins", "2019-02-05", 250, 80, 335, 330, 166),
    (2026, 9001, "[EXEMPLE] l'ensemble du projet de loi de finances pour 2027", "2026-10-20", 180, 140, 340, 320, 161),
]
votsen_rows = [
    (2005, 12, "99101A", "p", "\\N"),
    (2006, 9001, "99101A", "p", "\\N"), (2006, 9001, "99102A", "p", "\\N"), (2006, 9001, "99103A", "c", "\\N"),
    (2018, 9001, "99101A", "p", "\\N"), (2018, 9001, "99102A", "c", "\\N"), (2018, 9001, "99103A", "c", "\\N"), (2018, 9001, "99104A", "a", "\\N"),
    (2018, 9002, "99101A", "p", "\\N"), (2018, 9002, "99102A", "p", "\\N"), (2018, 9002, "99103A", "n", "\\N"), (2018, 9002, "99104A", "p", "99103A"),
    (2026, 9001, "99101A", "c", "\\N"), (2026, 9001, "99102A", "p", "\\N"), (2026, 9001, "99103A", "p", "\\N"), (2026, 9001, "99105A", "a", "\\N"),
]
sql = [
    "--",
    "-- [EXEMPLE] PostgreSQL database dump (fictional extract of the Dosleg copy: only the tables read)",
    "--",
    "",
    "SET client_encoding = 'LATIN1';",
    "SET standard_conforming_strings = off;",
    "",
    "CREATE TABLE scr (",
    "    sesann integer NOT NULL,",
    "    scrnum integer NOT NULL",
    ");",
    "",
    "COPY sen (senmat, sennomuse, senprenomuse, sendatnai) FROM stdin;",
]
for m, first, last, *_ in histogroupes:
    sql.append(f"{m}\t{last}\t[EXEMPLE] {first}\t1970-01-01")
sql += ["\\.", "", "COPY posvot (posvotcod, posvotlib) FROM stdin;", "p\tPour", "c\tContre", "a\tAbstention", "n\tN'a pas pris part au vote", "\\.", "",
        "COPY scr (sesann, scrnum, scrint, scrdat, scrpou, scrcon, scrvot, scrsuf, scrpousea, scrconsea, scrvotsea, scrsufsea, scrmaj, scrobs) FROM stdin;"]
for s, num, title, date, pour, contre, vot, suf, maj in scr_rows:
    sql.append(f"{s}\t{num}\t{title}\t{date}\t{pour}\t{contre}\t{vot}\t{suf}\t{pour}\t{contre}\t{vot}\t{suf}\t{maj}\t\\N")
sql += ["\\.", "", "COPY votsen (sesann, scrnum, senmat, posvotcod, senmatdel, stavotidt) FROM stdin;"]
for s, num, m, pos, dele in votsen_rows:
    sql.append(f"{s}\t{num}\t{m}\t{pos}\t{dele}\t1")
sql += ["\\.", "", "--", "-- PostgreSQL database dump complete", "--", ""]
write_text("senat-historique/dosleg/dosleg.sql", "\n".join(sql), "latin-1")

# ---------------------------------------------------------------- Parlement européen, 9th and 10th terms
history_meps = [
    ("990011", "Maëlle", "Ourcival", [("EU_POLITICAL_GROUP", "990201", "2019-07-02", "2024-07-15"),
                                      ("NATIONAL_POLITICAL_GROUP", "990101", "2019-07-02", "2021-01-31"),
                                      ("NATIONAL_POLITICAL_GROUP", "990102", "2021-02-01", "2024-07-15")]),
    # No national party.
    ("990012", "Noé", "Prunières", [("EU_POLITICAL_GROUP", "990202", "2019-07-02", "2024-07-15")]),
]


def mep_summary(i, first, last):
    return {"id": f"person/{i}", "type": "Person", "identifier": i, "label": f"[EXEMPLE] {first} {last.upper()}", "familyName": last,
            "givenName": f"[EXEMPLE] {first}", "sortLabel": last.upper(), "api:country-of-representation": "FR"}


write("pe/meps-term-9.json", {"data": [mep_summary("990003", "Solène", "Marivaux")] + [mep_summary(i, f, l) for i, f, l, _ in history_meps], "@context": [CONTEXT]})
write("pe/meps-term-10.json", {"data": [mep_summary(i, f, l) for i, f, l, _, _ in meps], "@context": [CONTEXT]})
for i, first, last, memberships in history_meps:
    write(f"pe/meps/{i}.json", {"data": [{
        "id": f"person/{i}", "type": "Person", "identifier": i, "label": f"[EXEMPLE] {first} {last.upper()}", "familyName": last, "givenName": f"[EXEMPLE] {first}",
        "bday": "1970-01-01", "placeOfBirth": "[EXEMPLE] Ville fictive", "hasEmail": "mailto:exemple@example.invalid",
        "hasMembership": [{"id": f"membership/{i}-{k}", "type": "Membership", "identifier": f"{i}-{k}", "membershipClassification": f"def/ep-entities/{kind}",
                           "organization": f"org/{org}", "memberDuring": {"type": "PeriodOfTime", "startDate": start, "endDate": end}, "role": "def/ep-roles/MEMBER"}
                          for k, (kind, org, start, end) in enumerate(memberships, start=1)],
    }], "@context": [CONTEXT]})
write("pe/meetings-2019.json", {"data": [
    # Before 15 July 2019: the vote of each member is not published, the sitting is not imported.
    {"id": "eli/dl/event/MTG-PL-2019-07-03", "type": "Activity", "activity_id": "MTG-PL-2019-07-03", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2019-07-03"},
], "@context": [CONTEXT]})
write("pe/meetings-2021.json", {"data": [
    {"id": "eli/dl/event/MTG-PL-2021-03-10", "type": "Activity", "activity_id": "MTG-PL-2021-03-10", "had_activity_type": "def/ep-activities/PLENARY_SITTING", "activity_date": "2021-03-10"},
], "@context": [CONTEXT]})
write("pe/meetings/MTG-PL-2019-07-03/decisions.json", {"data": [
    decision("MTG-PL-2019-07-03", 1, "[EXEMPLE] Élection du Président - vote non importé", "VOTE_ELECTRONIC_ROLLCALL", "ADOPTED", ["person/990011"], [], [], (400, 200, 50)),
], "@context": [CONTEXT]})
pe_2021 = [
    decision("MTG-PL-2021-03-10", 1, "[EXEMPLE] Rapport sur les sentiers européens - vote unique", "VOTE_ELECTRONIC_ROLLCALL", "ADOPTED",
             ["person/990011", "person/990003", FOREIGN[0]], ["person/990012", FOREIGN[1]], [], (455, 180, 50)),
    decision("MTG-PL-2021-03-10", 2, "[EXEMPLE] Résolution sur les moulins - amendement 3", "VOTE_ELECTRONIC_ROLLCALL", "REJECTED",
             ["person/990012"], ["person/990011", FOREIGN[2]], ["person/990003"], (150, 470, 60)),
    decision("MTG-PL-2021-03-10", 3, "[EXEMPLE] Ordre du jour - vote à main levée", "VOTE_SHOW_OF_HANDS", "ADOPTED", [], [], [], (0, 0, 0)),
]
pe_2021[0]["referenceText"] = "A9-0001/2021"
write("pe/meetings/MTG-PL-2021-03-10/decisions.json", {"data": pe_2021, "@context": [CONTEXT]})

# ---------------------------------------------------------------- manifest and listing
write("manifest.json", {"note": "[EXEMPLE] Données fictives au format des fichiers officiels. Aucune personne réelle.", "importedOn": IMPORTED_ON})
with open(os.path.join(root, "files.txt"), "w", encoding="utf-8", newline="\n") as f:
    f.write("# Every example file, relative to example/ (the class path cannot be listed). Generated.\n")
    for rel in sorted(files):
        f.write(rel + "\n")
print(len(files), "files")
