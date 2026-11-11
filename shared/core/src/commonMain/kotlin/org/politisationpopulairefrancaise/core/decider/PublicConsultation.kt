package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * The kinds of official public consultations relayed by PPF (DE-27, `docs/integration/consultations-publiques.md`). PPF
 * never collects an answer itself: each consultation links to the official page where the citizen answers. [key] is
 * stored in the signed contents and must not change; [minimumDays] is the shortest legal duration, used only to warn
 * the team at signing (the dates always come from the official notice).
 */
enum class ConsultationKind(val tagValue: String, val minimumDays: Int?) {
    /**
     * A consultation opened on a website by an administration before a regulatory act, in place of an advisory
     * commission (code des relations entre le public et l'administration, articles L132-1 to L132-3: at least fifteen days).
     */
    OPEN_ONLINE("consultation-ouverte", 15),

    /**
     * The participation of the public by electronic means in a decision with an incidence on the environment (code de
     * l'environnement, articles L123-19 to L123-19-2; the legal minimum depends on the decision: 15, 21 or 30 days).
     */
    ENVIRONMENT_ONLINE("participation-environnement", 15),

    /**
     * A public inquiry, led by an inquiry commissioner (code de l'environnement, articles L123-1 and following; L123-9: at
     * least thirty days for a project with an environmental assessment, fifteen otherwise).
     */
    PUBLIC_INQUIRY("enquete-publique", 15),

    /**
     * A public debate or a prior consultation under the Commission nationale du débat public (code de l'environnement,
     * articles L121-1 and following).
     */
    PUBLIC_DEBATE("debat-public", null),

    /**
     * A consultation of the voters or a local referendum decided by a local authority (code général des collectivités
     * territoriales, articles LO1112-1 and L1112-15 and following): the day of the vote, open and closed the same day.
     */
    LOCAL_VOTE("consultation-locale", null),
    ;

    companion object {
        fun fromTag(value: String?): ConsultationKind? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/**
 * One official public consultation (DE-27, `ppf-type` = `consultation`), written by the team from its official notice:
 * who organizes it, its kind, its first and last days, the official page where the citizen answers, its territory for a
 * local one. The body is the guide to answer it. Whether the user follows it and its reminders stay on the device.
 */
data class PublicConsultation(
    val article: Article,
    val kind: ConsultationKind,
    /** Who organizes it, as the official notice names it: a ministry, a prefecture, the CNDP, a commune. */
    val organizer: String,
    /** The official page where one answers; PPF collects nothing. */
    val officialUrl: String,
    val opensOn: CivilDate,
    /** The last day one can answer, included. */
    val closesOn: CivilDate,
    /** The code of the territory of a local consultation (référentiel des territoires); null for a national one. */
    val territory: String?,
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    fun isOpen(today: CivilDate): Boolean = today >= opensOn && today <= closesOn

    fun isUpcoming(today: CivilDate): Boolean = today < opensOn

    fun isOver(today: CivilDate): Boolean = today > closesOn

    /** Days left until the last day (0 on the last day), never negative. */
    fun daysLeft(today: CivilDate): Long = (closesOn.toEpochDays() - today.toEpochDays()).coerceAtLeast(0)

    /** How many days one can answer, the first and the last included. */
    val durationDays: Long get() = closesOn.toEpochDays() - opensOn.toEpochDays() + 1

    /** The host of the official page, shown before leaving PPF. */
    val officialHost: String get() = hostOf(officialUrl)

    /**
     * What makes the signing tool hesitate, in plain words for the team: never a refusal of the law by the code, only a
     * check that the dates written look like the general rule (the dates themselves come from the official notice).
     */
    fun warnings(): List<String> = buildList {
        kind.minimumDays?.let { minimum ->
            if (durationDays < minimum) add("${kind.tagValue} open $durationDays day(s), less than the legal minimum of $minimum: check the notice")
        }
        if (kind == ConsultationKind.PUBLIC_INQUIRY && durationDays in 15L until 30L) {
            add("public inquiry of $durationDays days: thirty at least when the project has an environmental assessment (L123-9), check the notice")
        }
        // Many inquiries are held on a register of a private provider or on the site of a commune: a hint, never a refusal.
        val host = officialHost
        if (!host.endsWith(".gouv.fr")) add("official page on $host, outside .gouv.fr: check that the notice of the organizer names it")
    }

    companion object {
        /** The address of the official page: https only, no user name, no port (`https://x.gouv.fr@evil.example` must not pass). */
        fun isOfficialPage(url: String): Boolean {
            if (!url.startsWith("https://")) return false
            val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
            return host.isNotEmpty() && '@' !in host && ':' !in host && '.' in host && host.none { it.isWhitespace() }
        }

        internal fun hostOf(url: String): String = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()

        /** The consultation of [article], or null when one of its required fields is missing or wrong: it is then not shown at all. */
        fun from(article: Article): PublicConsultation? {
            if (article.type != ArticleType.CONSULTATION) return null
            val event = article.event
            val kind = ConsultationKind.fromTag(event.value(DeciderTags.CONSULTATION_KIND)) ?: return null
            val organizer = event.value(DeciderTags.ORGANIZER) ?: return null
            val url = event.value(DeciderTags.OFFICIAL_URL)?.takeIf(::isOfficialPage) ?: return null
            val opens = CivilDate.parseIso(event.value(DeciderTags.OPENED)) ?: return null
            val closes = CivilDate.parseIso(event.value(DeciderTags.CLOSES)) ?: return null
            if (closes < opens) return null
            return PublicConsultation(
                article = article,
                kind = kind,
                organizer = organizer,
                officialUrl = url,
                opensOn = opens,
                closesOn = closes,
                territory = event.value(DeciderTags.TERRITORY_CODE),
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }
    }
}

/** The reminders of a consultation (DE-27): its opening, and three days before its last day, at 10 h, Paris time. */
object ConsultationReminders {
    const val OWNER_PREFIX: String = "decider:consultation:"

    /** The reminder comes three days before the last day: the time to read the file and write an answer. */
    const val CLOSING_DAYS_BEFORE: Int = 3

    const val OPENING: String = "ouverture"
    const val CLOSING: String = "cloture"

    fun owner(consultation: PublicConsultation): String = OWNER_PREFIX + consultation.article.slug

    fun reminderId(consultation: PublicConsultation, key: String): String = owner(consultation) + "/" + key

    /** `key` to due time: the opening (only while it is to come) and the closing reminder, those after [now] only. */
    fun plan(consultation: PublicConsultation, now: Long): List<Pair<String, Long>> = buildList {
        add(OPENING to ParisTime.toEpochSeconds(consultation.opensOn, ElectionReminders.HOUR, 0))
        // A consultation of three days or less: the reminder of the closing would come before its opening.
        val closing = CivilDate.fromEpochDays(maxOf(consultation.closesOn.toEpochDays() - CLOSING_DAYS_BEFORE, consultation.opensOn.toEpochDays() + 1))
        if (closing <= consultation.closesOn) add(CLOSING to ParisTime.toEpochSeconds(closing, ElectionReminders.HOUR, 0))
    }.filter { it.second > now }.distinctBy { it.second }.sortedBy { it.second }
}
