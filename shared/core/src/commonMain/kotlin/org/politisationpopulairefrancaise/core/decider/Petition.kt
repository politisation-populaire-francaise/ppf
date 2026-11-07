package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * The official platforms whose petitions PPF relays (cahier fonctionnel, M10). A petition is only
 * shown with a page on one of the platform's own sites: PPF never collects a signature itself and
 * never sends anyone to a look-alike site.
 */
enum class PetitionPlatform(val tagValue: String, val officialHosts: Set<String>) {
    /** Petitions to the Assemblée nationale (Règlement de l'Assemblée nationale). */
    ASSEMBLEE("assemblee", setOf("petitions.assemblee-nationale.fr")),
    /** Petitions to the Sénat (Règlement du Sénat). */
    SENAT("senat", setOf("petitions.senat.fr")),
    /** Petitions to the Conseil économique, social et environnemental (Constitution, article 69). */
    CESE("cese", setOf("petitions.lecese.fr")),
    /** Supports of a shared-initiative referendum (Constitution, article 11). */
    RIP("rip", setOf("www.referendum.interieur.gouv.fr", "referendum.interieur.gouv.fr")),
    /** European citizens' initiative (Regulation (EU) 2019/788). */
    ICE("ice", setOf("citizens-initiative.europa.eu")),
    ;

    /** True when [url] is an https page of one of this platform's own sites. */
    fun isOfficial(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        // No user name, no port: "https://petitions.senat.fr@evil.example" must not pass.
        if ('@' in host || ':' in host) return false
        return host in officialHosts
    }

    companion object {
        fun fromTag(value: String?): PetitionPlatform? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Whether the collection is still open on the official platform. */
enum class PetitionStatus(val tagValue: String) {
    OPEN("ouverte"),
    CLOSED("close"),
    ;

    companion object {
        fun fromTag(value: String?): PetitionStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** A reading of the official counter: the number, the day it was read and where. */
data class SignatureCount(val count: Long, val readOn: CivilDate, val source: String?)

/** How many departments the signatories come from, as published by the platform. */
data class DepartmentsReading(val count: Int, val readOn: CivilDate)

/** Something that happened to the petition: referred to a committee, a debate decided, closed... */
data class Milestone(val date: CivilDate, val label: String)

/**
 * An official petition relayed by PPF (M10, DE-07). The counter readings are kept as a history,
 * the latest one first; the thresholds and their real effects come from [PetitionRules].
 *
 * PPF never knows who signs: signing happens on the official site, after an identification that
 * the platform runs itself. What the app keeps (following a petition) stays on the device.
 */
data class Petition(
    val article: Article,
    val platform: PetitionPlatform,
    val officialUrl: String,
    val reference: String?,
    val openedOn: CivilDate?,
    val closesOn: CivilDate?,
    val status: PetitionStatus,
    /** Every reading of the counter, newest first. */
    val counts: List<SignatureCount>,
    val departments: DepartmentsReading?,
    /** The signatures required by this procedure, when they are set procedure by procedure ([DeciderTags.REQUIRED]). */
    val required: Long?,
    /** Oldest first. */
    val milestones: List<Milestone>,
    /** The bills of "Voir" the petition is about. */
    val texts: List<ContentAddress>,
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title
    val latestCount: SignatureCount? get() = counts.firstOrNull()

    /** Still open on [today]: not closed by the editors, and its last day not passed. */
    fun isOpen(today: CivilDate): Boolean = status == PetitionStatus.OPEN && (closesOn == null || today <= closesOn)

    /** Days left until the last day of the collection (0 on the last day), null without a last day. */
    fun daysLeft(today: CivilDate): Long? = closesOn?.let { (it.toEpochDays() - today.toEpochDays()).coerceAtLeast(0) }

    companion object {
        fun from(article: Article): Petition? {
            if (article.type != ArticleType.PETITION) return null
            val event = article.event
            val platform = PetitionPlatform.fromTag(event.value(DeciderTags.PLATFORM)) ?: return null
            // Only the official site: a petition without it, or pointing elsewhere, is never relayed.
            val url = event.value(DeciderTags.OFFICIAL_URL)?.takeIf(platform::isOfficial) ?: return null
            val status = PetitionStatus.fromTag(event.value(DeciderTags.PETITION_STATUS)) ?: return null
            val counts = event.fields(DeciderTags.COUNT).mapNotNull { f ->
                val count = f.at(0)?.replace(" ", "")?.replace(" ", "")?.toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                val date = CivilDate.parseIso(f.at(1)) ?: return@mapNotNull null
                SignatureCount(count, date, f.at(2))
            }.sortedByDescending { it.readOn }
            return Petition(
                article = article,
                platform = platform,
                officialUrl = url,
                reference = event.value(DeciderTags.REFERENCE),
                openedOn = CivilDate.parseIso(event.value(DeciderTags.OPENED)),
                closesOn = CivilDate.parseIso(event.value(DeciderTags.CLOSES)),
                status = status,
                counts = counts,
                departments = event.fields(DeciderTags.DEPARTMENTS).mapNotNull { f ->
                    val count = f.at(0)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val date = CivilDate.parseIso(f.at(1)) ?: return@mapNotNull null
                    DepartmentsReading(count, date)
                }.maxByOrNull { it.readOn },
                required = event.value(DeciderTags.REQUIRED)?.replace(" ", "")?.toLongOrNull()?.takeIf { it > 0 },
                milestones = event.fields(DeciderTags.MILESTONE).mapNotNull { f ->
                    val date = CivilDate.parseIso(f.at(0)) ?: return@mapNotNull null
                    val label = f.at(1) ?: return@mapNotNull null
                    Milestone(date, label)
                }.sortedBy { it.date },
                texts = event.links(VoirTags.ROLE_TEXT),
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }
    }
}
