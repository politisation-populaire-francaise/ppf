package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.isWebAddress
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/** Whether the dates of an election are only announced or set by the decree of convocation (DE-28). */
enum class ElectionStatus(val tagValue: String) {
    /** Announced (by the Government, the Ministry of the Interior); the decree is still to be published. */
    ANNOUNCED("annoncee"),

    /** Set by the decree of convocation published in the Journal officiel. */
    CONVENED("convoquee"),
    ;

    companion object {
        fun fromTag(value: String?): ElectionStatus? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/** The decree that convenes the voters: its number, the day of the Journal officiel, its page on Légifrance. */
data class Decree(val number: String, val date: CivilDate, val url: String)

/** What a deadline of an election is for (`ppf-deadline` = `kind | date | source`). */
enum class DeadlineKind(val tagValue: String) {
    /** The last day to ask for one's registration at the town hall (Code électoral, article L17: the sixth Friday before the vote). */
    REGISTRATION_TOWN_HALL("inscription-mairie"),

    /** The last day to ask for one's registration online, set by the regulatory part of the code. */
    REGISTRATION_ONLINE("inscription-en-ligne"),

    /** The day by which a proxy is best established, so that it reaches the polling station in time. */
    PROXY("procuration-conseillee"),

    /** The last day for the candidacies, when the election has one. */
    CANDIDACIES("candidatures"),
    ;

    companion object {
        fun fromTag(value: String?): DeadlineKind? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/** One deadline, as the team wrote it from the official source it cites: PPF never sets a legal date by itself. */
data class ElectionDeadline(val kind: DeadlineKind, val date: CivilDate, val source: String)

/**
 * An election of the personal electoral calendar (DE-28, `ppf-type` = `election`, design:
 * `docs/integration/calendrier-electoral.md`): its rounds (the same format as the comparator), its deadlines, the
 * decree once published, and its territory for a local election. Everything personal (the commune, the reminders)
 * stays on the device.
 */
data class ElectionCalendar(
    val article: Article,
    val election: Election,
    val status: ElectionStatus,
    val decree: Decree?,
    val rounds: List<ElectionRound>,
    val deadlines: List<ElectionDeadline>,
    /** The code of the territory of a local election (référentiel des territoires); null for a national one. */
    val territory: String?,
    val sources: List<Source>,
) {
    val address: ContentAddress get() = article.address

    /** The day of the first round in mainland France. */
    val firstDay: CivilDate? get() = rounds.firstOrNull()?.day

    /** Not over yet on [today]: its last round is today or later. */
    fun isUpcoming(today: CivilDate): Boolean = rounds.any { it.day >= today }

    /** The deadlines not passed on [today], the nearest first. */
    fun deadlinesAhead(today: CivilDate): List<ElectionDeadline> = deadlines.filter { it.date >= today }.sortedBy { it.date }

    fun deadline(kind: DeadlineKind): ElectionDeadline? = deadlines.firstOrNull { it.kind == kind }

    /**
     * What makes the signing tool hesitate, in plain words for the team: never a refusal of the law by the code,
     * only a check that the date written matches the general rule (the dates themselves come from the sources).
     */
    fun warnings(): List<String> = buildList {
        val first = rounds.firstOrNull() ?: return@buildList
        deadline(DeadlineKind.REGISTRATION_TOWN_HALL)?.let { d ->
            val expected = sixthFridayBefore(first.day)
            if (d.date != expected) add("registration at the town hall on ${d.date.toIso()}, not the sixth Friday before the first round (${expected.toIso()}): check the source")
        }
        deadlines.filter { it.date > first.day }.forEach { add("${it.kind.tagValue} after the first round") }
    }

    companion object {
        /** The general rule of Code électoral, article L17 (law n° 2016-1048): the sixth Friday before the vote. */
        fun sixthFridayBefore(day: CivilDate): CivilDate {
            val days = day.toEpochDays()
            // 1970-01-01 was a Thursday: ISO day of the week, Monday = 1 … Sunday = 7.
            val dayOfWeek = ((days + 3).mod(7L)) + 1
            val firstFridayBefore = days - ((dayOfWeek - 5 + 7 - 1).mod(7L) + 1)
            return CivilDate.fromEpochDays(firstFridayBefore - 5 * 7)
        }

        fun from(article: Article): ElectionCalendar? {
            if (article.type != ArticleType.ELECTION) return null
            val event = article.event
            val election = event.fields(DeciderTags.ELECTION).firstNotNullOfOrNull { f ->
                val id = f.at(0) ?: return@firstNotNullOfOrNull null
                val label = f.at(1) ?: return@firstNotNullOfOrNull null
                Election(id, label, CivilDate.parseIso(f.at(2)))
            } ?: return null
            val status = ElectionStatus.fromTag(event.value(DeciderTags.ELECTION_STATUS)) ?: return null
            val decree = event.fields(DeciderTags.DECREE).firstOrNull()?.let { f ->
                val number = f.at(0) ?: return null
                val date = CivilDate.parseIso(f.at(1)) ?: return null
                val url = f.at(2)?.takeIf { it.startsWith("https://") } ?: return null
                Decree(number, date, url)
            }
            // A convened election names its decree; an announced one has none yet.
            if ((status == ElectionStatus.CONVENED) != (decree != null)) return null
            val rounds = event.fields(DeciderTags.ROUND).map { ElectionRound.parse(it) ?: return null }.sortedBy { it.number }
            if (rounds.isEmpty() || rounds.map { it.number }.distinct().size != rounds.size) return null
            val deadlines = event.fields(DeciderTags.DEADLINE).map { f ->
                val kind = DeadlineKind.fromTag(f.at(0)) ?: return null
                val date = CivilDate.parseIso(f.at(1)) ?: return null
                val source = f.at(2)?.takeIf(::isWebAddress) ?: return null
                ElectionDeadline(kind, date, source)
            }
            if (deadlines.map { it.kind }.distinct().size != deadlines.size) return null
            return ElectionCalendar(
                article = article,
                election = election,
                status = status,
                decree = decree,
                rounds = rounds,
                deadlines = deadlines.sortedBy { it.date },
                territory = event.value(DeciderTags.TERRITORY_CODE),
                sources = event.sources(),
            )
        }
    }
}
