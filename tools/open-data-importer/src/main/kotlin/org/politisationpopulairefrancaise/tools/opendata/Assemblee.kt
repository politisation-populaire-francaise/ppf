package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.voir.parlement.Ballot
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.CorrectionKind
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.Group
import org.politisationpopulairefrancaise.core.voir.parlement.GroupCount
import org.politisationpopulairefrancaise.core.voir.parlement.Member
import org.politisationpopulairefrancaise.core.voir.parlement.NotVotingCause
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import org.politisationpopulairefrancaise.core.voir.parlement.Positions
import org.politisationpopulairefrancaise.core.voir.parlement.VoteCorrection

/** Members and groups of one assembly, sorted by identifier. */
data class Roster(val members: List<Member>, val groups: List<Group>)

/*
 * The Assemblée nationale (data.assemblee-nationale.fr, Licence Ouverte). Only the fields allowed by
 * the contract are read: identifier, first and last name, constituency, start of the mandate,
 * group. Birth, profession, civility, addresses, seat, collaborators are never read.
 */
object Assemblee {
    const val SITE: String = "https://www.assemblee-nationale.fr"

    private val ACTOR = Regex("^PA\\d{1,10}$")
    private val ORGAN = Regex("^PO\\d{1,10}$")
    private val DOSSIER = Regex("^DLR[0-9A-Z]{1,30}$")
    private val PATH_SEGMENT = Regex("^[A-Za-z0-9_-]{1,200}$")
    private val DEPARTMENT = Regex("^(\\d{2,3}|2A|2B)$")

    /**
     * The constituency code shared with the directory of elected officials of "Comprendre": the
     * department as written (two or three characters) then the number on two digits: `0101`,
     * `2A02`, `97101`, `09911` (French people living abroad). Saint-Barthélemy and Saint-Martin
     * share one constituency, coded `97701`.
     */
    fun constituencyCode(numDepartement: String?, numCirco: String?): String? {
        val raw = numDepartement?.trim()?.uppercase() ?: return null
        val department = (if (raw.length == 1 && raw[0].isDigit()) "0$raw" else raw).let { if (it == "978") "977" else it }
        if (!DEPARTMENT.matches(department)) return null
        val number = numCirco?.trim()?.toIntOrNull()?.takeIf { it in 1..99 } ?: return null
        return department + number.toString().padStart(2, '0')
    }

    /** `AMO10_deputes_actifs_mandats_actifs_organes.json.zip`: `json/acteur/PA….json` and `json/organe/PO….json`. */
    fun members(entries: EntrySource): Roster {
        val groups = HashMap<String, Group>()
        data class Pending(val member: Member, val groupRef: String?)
        val pending = ArrayList<Pending>()
        entries.forEach { entry ->
            when {
                entry.name.contains("acteur/") && entry.name.endsWith(".json") -> actor(entry)?.let { (member, groupRef) -> pending += Pending(member, groupRef) }
                entry.name.contains("organe/") && entry.name.endsWith(".json") -> group(entry)?.let { groups[it.id] = it }
            }
        }
        if (pending.isEmpty()) throw UnreadableSourceException("AMO10: no current deputy")
        val members = pending.map { (member, groupRef) ->
            member.copy(group = groupRef?.let { "an:$it" }?.takeIf { it in groups })
        }
        // Every current group (past groups, with an end date, were left out): the counts of a ballot
        // name the group of non-attached members too, which nobody holds a mandate in.
        return Roster(members.sortedBy { it.id }, groups.values.sortedBy { it.id })
    }

    private fun actor(entry: SourceEntry): Pair<Member, String?>? {
        val root = entry.json()["acteur"] ?: throw UnreadableSourceException("${entry.name}: no acteur")
        val uid = root["uid"].text()?.takeIf { ACTOR.matches(it) } ?: throw UnreadableSourceException("${entry.name}: no PA identifier")
        val ident = root.at("etatCivil", "ident")
        val first = ident["prenom"].text()
        val last = ident["nom"].text()
        if (first == null || last == null) throw UnreadableSourceException("${entry.name}: no name")
        val mandates = root.at("mandats", "mandat").items()
        fun current(type: String) = mandates.filter { it["typeOrgane"].text() == type && it["dateFin"].text() == null }
        // A deputy in office: a current mandate in the Assemblée; the latest legislature if several.
        val seat = current("ASSEMBLEE").maxByOrNull { it["legislature"].int() ?: 0 } ?: return null
        val place = seat.at("election", "lieu")
        val territory = constituencyCode(place["numDepartement"].text(), place["numCirco"].text())
            ?: throw UnreadableSourceException("${entry.name}: unreadable constituency")
        val groupRef = current("GP").firstNotNullOfOrNull { organRef(it) }
        val member = Member(
            id = "an:$uid",
            name = "$first $last",
            chamber = Chamber.AN,
            territory = territory,
            territoryName = place["departement"].text(),
            since = seat["dateDebut"].day(),
            url = "$SITE/dyn/deputes/$uid",
        )
        return member to groupRef
    }

    private fun organRef(mandate: JsonElement): String? = mandate.at("organes", "organeRef").items().firstNotNullOfOrNull { it.text()?.takeIf(ORGAN::matches) }

    private fun group(entry: SourceEntry): Group? {
        val root = entry.json()["organe"] ?: throw UnreadableSourceException("${entry.name}: no organe")
        if (root["codeType"].text() != "GP") return null
        if (root.at("viMoDe", "dateFin").text() != null) return null
        val uid = root["uid"].text()?.takeIf { ORGAN.matches(it) } ?: throw UnreadableSourceException("${entry.name}: no PO identifier")
        val label = root["libelle"].text() ?: throw UnreadableSourceException("${entry.name}: no label")
        // The colour of the group (couleurAssociee) is never read: votes are shown in neutral colours.
        return Group(id = "an:$uid", chamber = Chamber.AN, label = label, abbreviation = root["libelleAbrev"].text() ?: root["libelleAbrege"].text())
    }

    /** `Dossiers_Legislatifs.json.zip`: only `json/dossierParlementaire/`, uid to `titreChemin` (the address of its page). */
    fun dossierPaths(entries: EntrySource): Map<String, String> {
        val paths = HashMap<String, String>()
        entries.forEach { entry ->
            if (!entry.name.contains("dossierParlementaire/") || !entry.name.endsWith(".json")) return@forEach
            val root = entry.json()["dossierParlementaire"] ?: throw UnreadableSourceException("${entry.name}: no dossierParlementaire")
            val uid = root["uid"].text()?.takeIf { DOSSIER.matches(it) } ?: throw UnreadableSourceException("${entry.name}: no dossier identifier")
            val path = root.at("titreDossier", "titreChemin").text()?.takeIf { PATH_SEGMENT.matches(it) } ?: return@forEach
            paths[uid] = path
        }
        return paths
    }

    private val NOMINAL = listOf(
        "pours" to Position.FOR,
        "contres" to Position.AGAINST,
        "abstentions" to Position.ABSTENTION,
        "nonVotants" to Position.NOT_VOTING,
        "nonVotantsVolontaires" to Position.NOT_VOTING,
    )

    /** `Scrutins.json.zip`: one `json/VTANR5L17V<numéro>.json` per ballot. */
    fun ballots(entries: EntrySource, dossierPaths: Map<String, String>): List<Ballot> {
        val ids = Interner()
        val ballots = ArrayList<Ballot>()
        entries.forEach { entry ->
            if (!entry.name.endsWith(".json")) return@forEach
            ballots += ballot(entry, dossierPaths, ids)
        }
        if (ballots.isEmpty()) throw UnreadableSourceException("Scrutins: no ballot")
        return ballots
    }

    private fun ballot(entry: SourceEntry, dossierPaths: Map<String, String>, ids: Interner): Ballot {
        val root = entry.json()["scrutin"] ?: throw UnreadableSourceException("${entry.name}: no scrutin")
        fun fail(what: String): Nothing = throw UnreadableSourceException("${entry.name}: $what")
        val number = root["numero"].int() ?: fail("no number")
        val legislature = root["legislature"].int() ?: fail("no legislature")
        val date = root["dateScrutin"].day() ?: fail("no date")
        val title = root["titre"].text() ?: root.at("objet", "libelle").text() ?: fail("no title")
        val total = root.at("syntheseVote", "decompte") ?: fail("no count")

        val positions = HashMap<Position, MutableSet<String>>()
        val delegated = sortedSetOf<String>()
        val causes = HashMap<String, String>()
        val groups = ArrayList<GroupCount>()
        root.at("ventilationVotes", "organe").items().flatMap { it.at("groupes", "groupe").items() }.forEach { group ->
            val ref = group["organeRef"].text()?.takeIf(ORGAN::matches) ?: fail("group without identifier")
            val vote = group["vote"]
            val count = vote["decompteVoix"]
            groups += GroupCount(
                group = ids.of("an:$ref"),
                inFavour = count["pour"].int() ?: 0,
                against = count["contre"].int() ?: 0,
                abstention = count["abstentions"].int() ?: 0,
                notVoting = count["nonVotants"].int() ?: 0,
            )
            val nominal = vote["decompteNominatif"]
            NOMINAL.forEach { (key, position) ->
                nominal.at(key, "votant").items().forEach { voter ->
                    // numPlace (the seat) and mandatRef are never kept.
                    val actor = voter["acteurRef"].text()?.takeIf(ACTOR::matches) ?: fail("voter without identifier")
                    val member = ids.of("an:$actor")
                    positions.getOrPut(position) { sortedSetOf() } += member
                    if (voter["parDelegation"].flag()) delegated += member
                    voter["causePositionVote"].text()?.let { causes[member] = it }
                }
            }
        }

        val corrections = ArrayList<VoteCorrection>()
        val statement = root["miseAuPoint"]
        fun readCorrections(block: JsonElement?, kind: CorrectionKind) = NOMINAL.forEach { (key, position) ->
            block.at(key, "votant").items().forEach { voter ->
                val actor = voter["acteurRef"].text()?.takeIf(ACTOR::matches) ?: fail("correction without identifier")
                // The file gives no date of its own to a correction: the interface shows the ballot's.
                corrections += VoteCorrection(member = ids.of("an:$actor"), intended = position, kind = kind)
            }
        }
        readCorrections(statement, CorrectionKind.STATEMENT)
        readCorrections(statement["dysfonctionnement"] ?: root["dysfonctionnement"], CorrectionKind.MALFUNCTION)

        val dossierRef = root.at("objet", "dossierLegislatif").let { it["dossierRef"].text() ?: it.text() }?.takeIf(DOSSIER::matches)
        val outcome = root.at("sort", "code").text()?.lowercase()
        return Ballot(
            id = "an:$legislature:$number",
            number = number,
            date = date,
            title = title,
            kind = root.at("typeVote", "codeTypeVote").text(),
            adopted = when (outcome) {
                "adopté", "adopte" -> true
                "rejeté", "rejete" -> false
                else -> null
            },
            counts = Counts(
                voters = root.at("syntheseVote", "nombreVotants").int(),
                expressed = root.at("syntheseVote", "suffragesExprimes").int(),
                inFavour = total["pour"].int() ?: fail("no count for"),
                against = total["contre"].int() ?: fail("no count against"),
                abstention = total["abstentions"].int() ?: fail("no count of abstentions"),
                notVoting = total["nonVotants"].int(),
            ),
            groups = groups.sortedBy { it.group },
            positions = Positions(
                inFavour = positions[Position.FOR].orEmpty().toList(),
                against = positions[Position.AGAINST].orEmpty().toList(),
                abstention = positions[Position.ABSTENTION].orEmpty().toList(),
                notVoting = positions[Position.NOT_VOTING].orEmpty().toList(),
                delegated = delegated.toList(),
                corrections = corrections.sortedWith(compareBy({ it.member }, { it.kind }, { it.intended })),
                notVotingCauses = causes.entries.sortedBy { it.key }.map { NotVotingCause(it.key, it.value) },
            ),
            url = "$SITE/dyn/$legislature/scrutins/$number",
            dossier = dossierRef?.let { "an-dossier:$it" },
            dossierUrl = dossierRef?.let(dossierPaths::get)?.let { "$SITE/dyn/$legislature/dossiers/$it" },
        )
    }
}
