package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import org.politisationpopulairefrancaise.core.voir.parlement.Ballot
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.Group
import org.politisationpopulairefrancaise.core.voir.parlement.Member
import org.politisationpopulairefrancaise.core.voir.parlement.Positions
import java.io.InputStream

/*
 * The European Parliament (open data API v2, data.europarl.europa.eu, CC BY 4.0): the members
 * elected in France, their national party and their group, and the roll-call votes of the plenary
 * with the positions of these members only. Never read: birth, citizenship, photograph, e-mail,
 * gender, contact points.
 */

/** A member elected in France, as `meps/show-current` lists them. */
data class EpMep(val id: String, val name: String)

/** A membership of a member (`meps/{id}`, `hasMembership`): the body and its kind, with its dates. */
data class EpMembership(val organization: String, val classification: String, val start: String?, val end: String?)

/** A corporate body (`corporate-bodies/{id}`): a party or a political group. */
data class EpBody(val id: String, val label: String, val shortLabel: String?)

/** A roll-call decision of a plenary sitting (`meetings/{id}/decisions`). */
data class EpDecision(
    val id: String,
    val date: String,
    val order: Int?,
    val title: String?,
    val method: String?,
    val outcome: String?,
    val inFavour: Int?,
    val against: Int?,
    val abstention: Int?,
    val votersInFavour: List<String>,
    val votersAgainst: List<String>,
    val votersAbstaining: List<String>,
)

/** The few calls of the API used by the import; the example implementation reads bundled files. */
interface EuroparlApi {
    fun currentFrenchMeps(): List<EpMep>
    fun memberships(mepId: String): List<EpMembership>
    fun body(bodyId: String): EpBody?
    /** Identifiers of the plenary sittings of a year (`MTG-PL-AAAA-MM-JJ`). */
    fun plenarySittings(year: Int): List<String>
    fun decisions(sittingId: String): List<EpDecision>
}

/**
 * The API read as JSON-LD, through [transport]: a relative path (`meps/show-current?…`) and the page
 * asked (offset, limit) to the bytes of the answer, null when there is none (404). Each answer is
 * decoded into the few fields used, unknown fields skipped while reading (never a whole tree).
 */
class JsonEuroparlApi(private val transport: (path: String, offset: Int, limit: Int) -> InputStream?) : EuroparlApi {
    @Serializable
    private class Page<T>(val data: List<T> = emptyList())

    @Serializable
    private class MepSummary(val identifier: JsonElement? = null, val label: JsonElement? = null, val givenName: JsonElement? = null, val familyName: JsonElement? = null)

    @Serializable
    private class MepDetail(val hasMembership: JsonElement? = null)

    @Serializable
    private class Body(val identifier: JsonElement? = null, val label: JsonElement? = null, val prefLabel: JsonElement? = null, val altLabel: JsonElement? = null)

    @Serializable
    private class Meeting(@SerialName("activity_id") val activityId: JsonElement? = null, val id: JsonElement? = null)

    @Serializable
    private class Decision(
        val id: JsonElement? = null,
        @SerialName("activity_date") val date: JsonElement? = null,
        @SerialName("activity_order") val order: JsonElement? = null,
        @SerialName("activity_label") val label: JsonElement? = null,
        @SerialName("decision_method") val method: JsonElement? = null,
        @SerialName("decision_outcome") val outcome: JsonElement? = null,
        @SerialName("number_of_votes_favor") val inFavour: JsonElement? = null,
        @SerialName("number_of_votes_against") val against: JsonElement? = null,
        @SerialName("number_of_votes_abstention") val abstention: JsonElement? = null,
        @SerialName("had_voter_favor") val votersInFavour: JsonElement? = null,
        @SerialName("had_voter_against") val votersAgainst: JsonElement? = null,
        @SerialName("had_voter_abstention") val votersAbstaining: JsonElement? = null,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> pages(path: String, all: Boolean = true): List<T> {
        val result = ArrayList<T>()
        var offset = 0
        while (true) {
            val stream = transport(path, offset, PAGE) ?: break
            val page = try {
                stream.use { lenientJson.decodeFromStream<Page<T>>(it) }
            } catch (e: Exception) {
                throw UnreadableSourceException("europarl ${path.substringBefore('?')}: unexpected answer")
            }
            result += page.data
            if (!all || page.data.size < PAGE) break
            offset += PAGE
            if (offset > MAX_ITEMS) throw UnreadableSourceException("europarl ${path.substringBefore('?')}: too many items")
        }
        return result
    }

    override fun currentFrenchMeps(): List<EpMep> = pages<MepSummary>("meps/show-current?country-of-representation=FR").map { mep ->
        val id = mep.identifier.text()?.takeIf(ID::matches) ?: throw UnreadableSourceException("europarl meps: member without identifier")
        val name = listOfNotNull(mep.givenName.text(), mep.familyName.text()).joinToString(" ").ifEmpty { null } ?: mep.label.text()
            ?: throw UnreadableSourceException("europarl meps: member without name")
        EpMep(id, name)
    }

    override fun memberships(mepId: String): List<EpMembership> =
        pages<MepDetail>("meps/$mepId", all = false).flatMap { it.hasMembership.items() }.mapNotNull { membership ->
            val organization = membership["organization"].text()?.substringAfterLast('/') ?: return@mapNotNull null
            val classification = membership["membershipClassification"].text()?.substringAfterLast('/') ?: return@mapNotNull null
            val during = membership["memberDuring"]
            EpMembership(organization, classification, during["startDate"].day(), during["endDate"].day())
        }

    override fun body(bodyId: String): EpBody? = pages<Body>("corporate-bodies/$bodyId", all = false).firstOrNull()?.let { body ->
        val short = body.label.text()
        val long = languageText(body.prefLabel) ?: short ?: return null
        EpBody(id = body.identifier.text() ?: bodyId, label = long, shortLabel = short?.takeIf { it != long })
    }

    override fun plenarySittings(year: Int): List<String> = pages<Meeting>("meetings?year=$year").mapNotNull { meeting ->
        (meeting.activityId.text() ?: meeting.id.text()?.substringAfterLast('/'))?.takeIf(SITTING::matches)
    }

    override fun decisions(sittingId: String): List<EpDecision> = pages<Decision>("meetings/$sittingId/decisions").map { d ->
        EpDecision(
            id = d.id.text()?.substringAfterLast('/') ?: throw UnreadableSourceException("europarl decisions: decision without identifier"),
            date = d.date.day() ?: sittingDate(sittingId),
            order = d.order.int(),
            title = languageText(d.label),
            method = d.method.items().firstNotNullOfOrNull { it.text() }?.substringAfterLast('/'),
            outcome = d.outcome.items().firstNotNullOfOrNull { it.text() }?.substringAfterLast('/'),
            inFavour = d.inFavour.int(),
            against = d.against.int(),
            abstention = d.abstention.int(),
            votersInFavour = voters(d.votersInFavour),
            votersAgainst = voters(d.votersAgainst),
            votersAbstaining = voters(d.votersAbstaining),
        )
    }

    private fun voters(element: JsonElement?): List<String> = element.items().mapNotNull { it.text()?.substringAfterLast('/') }

    /** A label given in several languages: French first, then English, then any. */
    private fun languageText(element: JsonElement?): String? {
        element.text()?.let { return it }
        val values = element.items()
        values.firstNotNullOfOrNull { it["fr"].text() }?.let { return it }
        values.firstNotNullOfOrNull { it["en"].text() }?.let { return it }
        // JSON-LD language maps can also be lists of {"@language", "@value"}.
        values.firstOrNull { it["@language"].text() == "fr" }?.get("@value").text()?.let { return it }
        return values.firstNotNullOfOrNull { v -> (v as? kotlinx.serialization.json.JsonObject)?.values?.firstNotNullOfOrNull { it.text() } }
    }

    companion object {
        const val PAGE: Int = 500
        private const val MAX_ITEMS: Int = 100_000
        private val ID = Regex("^\\d{1,10}$")
        val SITTING = Regex("^MTG-PL-\\d{4}-\\d{2}-\\d{2}$")
        fun sittingDate(sittingId: String): String = sittingId.removePrefix("MTG-PL-")
    }
}

/** The members elected in France and the roll-call votes, from the API (any implementation). */
object Europarl {
    const val SITE: String = "https://www.europarl.europa.eu"
    /** The tenth term began on 16 July 2024: earlier sittings are not imported. */
    const val TERM_START: String = "2024-07-16"
    const val TERM: Int = 10

    private const val NATIONAL = "NATIONAL_POLITICAL_GROUP"
    private const val EU_GROUP = "EU_POLITICAL_GROUP"

    fun members(api: EuroparlApi, today: String): Roster {
        val bodies = HashMap<String, EpBody?>()
        fun body(id: String) = bodies.getOrPut(id) { api.body(id) }
        val groups = HashMap<String, Group>()
        val members = api.currentFrenchMeps().distinctBy { it.id }.map { mep ->
            val current = api.memberships(mep.id).filter { (it.start == null || it.start <= today) && (it.end == null || it.end >= today) }
            val party = current.firstOrNull { it.classification == NATIONAL }?.let { body(it.organization) }
            val group = current.firstOrNull { it.classification == EU_GROUP }?.let { membership ->
                body(membership.organization)?.let { b ->
                    val id = "pe:groupe:${membership.organization}"
                    groups.getOrPut(id) { Group(id = id, chamber = Chamber.PE, label = b.label, abbreviation = b.shortLabel) }
                    id
                }
            }
            Member(
                id = "pe:${mep.id}",
                name = mep.name,
                chamber = Chamber.PE,
                group = group,
                territory = "FR",
                since = current.filter { it.classification == EU_GROUP || it.classification == NATIONAL }.mapNotNull { it.start }.minOrNull(),
                url = "$SITE/meps/fr/${mep.id}",
                nationalParty = party?.label,
            )
        }
        if (members.isEmpty()) throw UnreadableSourceException("europarl: no member elected in France")
        return Roster(members.sortedBy { it.id }, groups.values.sortedBy { it.id })
    }

    /** The roll-call votes of every plenary sitting of the term up to [today], positions of [french] members only. */
    fun ballots(api: EuroparlApi, french: Set<String>, today: String): List<Ballot> {
        val ids = Interner()
        val firstYear = TERM_START.take(4).toInt()
        val lastYear = today.take(4).toInt()
        val sittings = (firstYear..lastYear).flatMap { api.plenarySittings(it) }.distinct().sorted()
            .filter { JsonEuroparlApi.sittingDate(it) in TERM_START..today }
        return sittings.flatMap { sitting ->
            val rollCalls = api.decisions(sitting).filter { it.method == "VOTE_ELECTRONIC_ROLLCALL" }.sortedWith(compareBy({ it.order ?: Int.MAX_VALUE }, { it.id }))
            rollCalls.mapIndexed { index, d ->
                val number = d.order ?: (index + 1)
                fun only(list: List<String>) = list.filter { it in french }.map { ids.of("pe:$it") }.distinct().sorted()
                Ballot(
                    id = "pe:${d.date}:$number",
                    number = number,
                    date = d.date,
                    title = d.title ?: "Vote par appel nominal n° $number",
                    kind = "appel-nominal",
                    adopted = when {
                        d.outcome == null -> null
                        d.outcome.endsWith("ADOPTED") -> true
                        d.outcome.endsWith("REJECTED") -> false
                        else -> null
                    },
                    // The result of the whole Parliament; the positions of the members elected in France only.
                    counts = Counts(
                        inFavour = d.inFavour ?: d.votersInFavour.size,
                        against = d.against ?: d.votersAgainst.size,
                        abstention = d.abstention ?: d.votersAbstaining.size,
                    ),
                    positions = Positions(inFavour = only(d.votersInFavour), against = only(d.votersAgainst), abstention = only(d.votersAbstaining)),
                    url = "$SITE/doceo/document/PV-$TERM-${d.date}-RCV_FR.html",
                )
            }
        }.distinctBy { it.id }
    }
}
