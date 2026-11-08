package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef
import org.politisationpopulairefrancaise.core.voir.parlement.Position

/*
 * The history of the ballots of the Assemblée nationale, 14th to 17th legislatures
 * (voir-historique-sources.md). The party of a deputy at the date of a ballot is the `PARPOL`
 * mandate (party of attachment) covering that date in AMO30; without one (before December 2012,
 * or a deputy who declared none), the political group (`GP`) stands for it, marked `groupe`.
 * Only counts are kept: no name, no nominal vote.
 */

/** The dated mandates of one deputy in AMO30: seats in the Assemblée, groups, parties. */
class DeputyHistory(val seats: List<Span>, val groups: List<Span>, val parties: List<Span>)

/** A group or a party of AMO30. */
data class AnOrgan(val id: String, val label: String, val abbreviation: String?)

/** What AMO30 tells of every deputy since 1997, reduced to dated memberships. */
class AnHistoryReference(val deputies: Map<String, DeputyHistory>, val organs: Map<String, AnOrgan>) {
    private val membersByDay = HashMap<String, Map<Line, Int>>()

    /** The line of a deputy at [day]: the party when one is published, else the group ([ballotGroup] when AMO30 has none). */
    fun lineOf(actor: String, day: String, ballotGroup: String?): Line? {
        val deputy = deputies[actor]
        deputy?.parties?.at(day)?.let { return Line("an:${it.organ}", PartyBasis.PARTY) }
        val group = ballotGroup ?: deputy?.groups?.at(day)?.organ ?: return null
        return Line("an:$group", PartyBasis.GROUP)
    }

    /** The deputies in office at [day], per line. */
    fun members(day: String): Map<Line, Int> = membersByDay.getOrPut(day) {
        val counts = HashMap<Line, Int>()
        deputies.forEach { (actor, deputy) ->
            if (deputy.seats.none { it.covers(day) }) return@forEach
            val line = lineOf(actor, day, null) ?: return@forEach
            counts[line] = (counts[line] ?: 0) + 1
        }
        counts
    }

    fun partyRef(line: Line): PartyRef {
        val organ = organs[line.id.removePrefix("an:")]
        return partyRef(line, organ?.label ?: "Organe ${line.id.removePrefix("an:")}", organ?.abbreviation)
    }
}

object AssembleeHistory {
    /** The legislatures with open data of their ballots (nothing before the 14th). */
    val LEGISLATURES: IntRange = 14..17

    private val ACTOR = Regex("^PA\\d{1,10}$")
    private val ORGAN = Regex("^PO\\d{1,10}$")
    private val DOSSIER = Regex("^DLR[0-9A-Z]{1,30}$")
    private val KEPT = setOf("ASSEMBLEE", "GP", "PARPOL")

    /** AMO30 (`json/acteur/`, `json/organe/`): only the mandates in the Assemblée, groups and parties, with their dates. */
    fun reference(entries: EntrySource): AnHistoryReference {
        val ids = Interner()
        val deputies = HashMap<String, DeputyHistory>()
        val organs = HashMap<String, AnOrgan>()
        entries.forEach { entry ->
            when {
                entry.name.contains("acteur/") && entry.name.endsWith(".json") -> actor(entry, ids)?.let { (uid, history) -> deputies[uid] = history }
                entry.name.contains("organe/") && entry.name.endsWith(".json") -> organ(entry)?.let { organs[it.id] = it }
            }
        }
        if (deputies.isEmpty()) throw UnreadableSourceException("AMO30: no deputy")
        return AnHistoryReference(deputies, organs)
    }

    private fun actor(entry: SourceEntry, ids: Interner): Pair<String, DeputyHistory>? {
        val root = entry.json()["acteur"] ?: throw UnreadableSourceException("${entry.name}: no acteur")
        val uid = root["uid"].text()?.takeIf { ACTOR.matches(it) } ?: throw UnreadableSourceException("${entry.name}: no PA identifier")
        // Names, birth, profession, addresses: never read.
        val spans = HashMap<String, MutableList<Span>>()
        root.at("mandats", "mandat").items().forEach { mandate ->
            val type = mandate["typeOrgane"].text()?.takeIf { it in KEPT } ?: return@forEach
            val organ = mandate.at("organes", "organeRef").items().firstNotNullOfOrNull { it.text()?.takeIf(ORGAN::matches) } ?: return@forEach
            spans.getOrPut(type) { ArrayList() } += Span(ids.of(organ), mandate["dateDebut"].day(), mandate["dateFin"].day())
        }
        val seats = spans["ASSEMBLEE"].orEmpty()
        if (seats.isEmpty()) return null
        return ids.of(uid) to DeputyHistory(seats, spans["GP"].orEmpty(), spans["PARPOL"].orEmpty())
    }

    private fun organ(entry: SourceEntry): AnOrgan? {
        val root = entry.json()["organe"] ?: throw UnreadableSourceException("${entry.name}: no organe")
        if (root["codeType"].text() !in setOf("GP", "PARPOL")) return null
        val uid = root["uid"].text()?.takeIf { ORGAN.matches(it) } ?: throw UnreadableSourceException("${entry.name}: no PO identifier")
        val label = root["libelle"].text() ?: throw UnreadableSourceException("${entry.name}: no label")
        return AnOrgan(uid, label, root["libelleAbrev"].text() ?: root["libelleAbrege"].text())
    }

    private val NOMINAL = listOf(
        "pours" to Position.FOR,
        "contres" to Position.AGAINST,
        "abstentions" to Position.ABSTENTION,
        "nonVotants" to Position.NOT_VOTING,
        "nonVotantsVolontaires" to Position.NOT_VOTING,
    )

    /**
     * The ballots of one legislature: one `json/VTANR5L<lég.>V<numéro>.json` per ballot (15th to
     * 17th) or a single document with a list `scrutins` (14th), both read as a stream.
     */
    fun ballots(legislature: Int, entries: EntrySource, reference: AnHistoryReference, example: Boolean): HistoryPackage {
        val ballots = ArrayList<HistoryBallot>()
        val lines = HashSet<Line>()
        entries.forEach { entry ->
            if (!entry.name.endsWith(".json")) return@forEach
            entry.open().bufferedReader(Charsets.UTF_8).use { reader ->
                JsonObjectStream(reader, entry.name, objectKeys = setOf("scrutin"), arrayKeys = setOf("scrutin", "scrutins")).forEach { root ->
                    val (ballot, used) = ballot(root, entry.name, legislature, reference)
                    ballots += ballot
                    lines += used
                }
            }
        }
        if (ballots.isEmpty()) throw UnreadableSourceException("Scrutins of legislature $legislature: no ballot")
        if (ballots.distinctBy { it.id }.size != ballots.size) throw UnreadableSourceException("Scrutins of legislature $legislature: duplicate ballot")
        return buildHistoryPackage(example, Chamber.AN, legislature, ballots, lines.map(reference::partyRef))
    }

    private fun ballot(root: JsonElement, name: String, expected: Int, reference: AnHistoryReference): Pair<HistoryBallot, Set<Line>> {
        fun fail(what: String): Nothing = throw UnreadableSourceException("$name: $what")
        val number = root["numero"].int() ?: fail("no number")
        val legislature = root["legislature"].int() ?: fail("no legislature")
        if (legislature != expected) fail("a ballot of another legislature")
        val date = root["dateScrutin"].day() ?: fail("no date")
        val title = root["titre"].text() ?: root.at("objet", "libelle").text() ?: fail("no title")
        val total = root.at("syntheseVote", "decompte") ?: fail("no count")

        val distribution = Distribution()
        root.at("ventilationVotes", "organe").items().flatMap { it.at("groupes", "groupe").items() }.forEach { group ->
            val groupRef = group["organeRef"].text()?.takeIf(ORGAN::matches) ?: fail("group without identifier")
            val vote = group["vote"]
            var nominal = 0
            NOMINAL.forEach { (key, position) ->
                vote.at("decompteNominatif", key, "votant").items().forEach { voter ->
                    val actor = voter["acteurRef"].text()?.takeIf(ACTOR::matches) ?: fail("voter without identifier")
                    val line = reference.lineOf(actor, date, groupRef) ?: fail("voter without group")
                    distribution.add(line, position)
                    nominal++
                }
            }
            if (nominal == 0) {
                // No nominal list for this group: its counts stand for its members, as a group.
                val count = vote["decompteVoix"]
                val line = Line("an:$groupRef", PartyBasis.GROUP)
                listOf("pour" to Position.FOR, "contre" to Position.AGAINST, "abstentions" to Position.ABSTENTION, "nonVotants" to Position.NOT_VOTING).forEach { (key, position) ->
                    val n = count[key].int() ?: 0
                    if (n > 0) distribution.add(line, position, n)
                }
            }
        }

        val dossierRef = root.at("objet", "dossierLegislatif").let { it["dossierRef"].text() ?: it.text() }?.takeIf(DOSSIER::matches)
        val outcome = root.at("sort", "code").text()?.lowercase()
        val members = reference.members(date)
        // Every party (or group) with deputies that day has a line, even when none of them voted.
        members.keys.forEach { distribution.add(it, Position.NOT_VOTING, 0) }
        val ballot = HistoryBallot(
            id = "an:$legislature:$number",
            date = date,
            title = title,
            kind = root.at("typeVote", "codeTypeVote").text(),
            dossier = dossierRef?.let { "an-dossier:$it" },
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
            parties = distribution.build { members[it] ?: 0 },
            url = "${Assemblee.SITE}/dyn/$legislature/scrutins/$number",
        )
        return ballot to distribution.lines
    }
}
