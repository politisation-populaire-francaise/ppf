package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/** Where a local petition stands (DE-26). [tagValue] is stored in the signed contents and must not change. */
enum class LocalPetitionStatus(val tagValue: String) {
    /** The signatures are being collected. */
    COLLECTING("en-collecte"),

    /** Filed with the mayor or the president of the assembly. */
    FILED("deposee"),

    /** Put on the agenda of the assembly, which decides freely. */
    ON_AGENDA("inscrite"),

    /** The assembly decided to organize the consultation. */
    CONSULTATION_DECIDED("consultation-decidee"),

    /** The assembly decided not to organize it, or the request was not admissible. */
    REFUSED("refusee"),
    ;

    val isCollecting: Boolean get() = this == COLLECTING

    companion object {
        fun fromTag(value: String?): LocalPetitionStatus? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/** The number of registered voters of the collectivity, as the team read it from an official source: `count | date | source`. */
data class RegisteredVoters(val count: Long, val readOn: CivilDate, val source: String)

/**
 * A local petition (DE-26, `ppf-type` = `petition-locale`, `docs/integration/petitions-locales.md`): voters ask their
 * collectivity to put a consultation on the agenda of its assembly (Constitution, article 72-1; code général des
 * collectivités territoriales, article L1112-16, version of the law n° 2022-217 of 21 February 2022): a tenth of the
 * registered voters of a commune, a twentieth in the other collectivities; each quarter, a voter signs only one such
 * request to the same collectivity; the assembly decides freely. PPF collects no signature: the content says where to
 * sign (the collectivity's page, or on paper with the organizers), and the team reads the counts from the organizers.
 */
data class LocalPetition(
    val article: Article,
    /** The code of the collectivity (référentiel des territoires: INSEE code of a commune, code of a department…). */
    val territory: String,
    val collectivity: String,
    /** True for a commune (a tenth), false for another collectivity (a twentieth). */
    val commune: Boolean,
    val voters: RegisteredVoters,
    /** Where one signs online, when the collectivity offers a page; null for a collection on paper. */
    val signUrl: String?,
    /** How to sign when there is no page (on paper, at a stand), written by the team. */
    val howToSign: String?,
    val status: LocalPetitionStatus,
    val counts: List<SignatureCount>,
    val milestones: List<Milestone>,
    val opensOn: CivilDate?,
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title
    val latestCount: SignatureCount? get() = counts.firstOrNull()

    /** The signatures required by L1112-16, rounded up: a tenth of the registered voters of a commune, a twentieth elsewhere. */
    val required: Long get() = ceilDiv(voters.count, if (commune) COMMUNE_FRACTION else OTHER_FRACTION)

    /** How far the last count reached, from 0 to 1 (more than 1 when the threshold is passed). */
    val progress: Double? get() = latestCount?.let { it.count.toDouble() / required.coerceAtLeast(1) }

    /** What makes the signing tool hesitate: a signing page and a collection on paper both missing, a count older than the voters. */
    fun warnings(): List<String> = buildList {
        if (status.isCollecting && signUrl == null && howToSign == null) add("collecting without a signing page nor how to sign on paper")
        latestCount?.let { if (it.count > voters.count) add("more signatures than registered voters: check the counts") }
    }

    companion object {
        /** L1112-16: a tenth of the registered voters in a commune. */
        const val COMMUNE_FRACTION: Long = 10

        /** L1112-16: a twentieth in the other collectivities. */
        const val OTHER_FRACTION: Long = 20

        private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

        fun from(article: Article): LocalPetition? {
            if (article.type != ArticleType.PETITION_LOCALE) return null
            val event = article.event
            val territory = event.value(DeciderTags.TERRITORY_CODE) ?: return null
            val collectivity = event.fields(DeciderTags.COLLECTIVITY).firstOrNull() ?: return null
            val name = collectivity.at(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val isCommune = when (collectivity.at(1)?.trim()) {
                "commune" -> true
                "autre" -> false
                else -> return null
            }
            val voters = event.fields(DeciderTags.VOTERS).firstOrNull()?.let { f ->
                val count = f.at(0)?.replace(" ", "")?.replace(" ", "")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
                val date = CivilDate.parseIso(f.at(1)) ?: return null
                val source = f.at(2)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                RegisteredVoters(count, date, source)
            } ?: return null
            val status = LocalPetitionStatus.fromTag(event.value(DeciderTags.LOCAL_STATUS)) ?: return null
            val url = event.value(DeciderTags.OFFICIAL_URL)
            // A signing page, when there is one, must be a clean https address; anything else makes the content unreadable.
            if (url != null && !PublicConsultation.isOfficialPage(url)) return null
            return LocalPetition(
                article = article,
                territory = territory,
                collectivity = name,
                commune = isCommune,
                voters = voters,
                signUrl = url,
                howToSign = event.value(DeciderTags.HOW_TO_SIGN),
                status = status,
                counts = event.fields(DeciderTags.COUNT).mapNotNull { f ->
                    val count = f.at(0)?.replace(" ", "")?.replace(" ", "")?.toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val date = CivilDate.parseIso(f.at(1)) ?: return@mapNotNull null
                    SignatureCount(count, date, f.at(2))
                }.sortedByDescending { it.readOn },
                milestones = event.fields(DeciderTags.MILESTONE).mapNotNull { f ->
                    val date = CivilDate.parseIso(f.at(0)) ?: return@mapNotNull null
                    val label = f.at(1) ?: return@mapNotNull null
                    Milestone(date, label)
                }.sortedBy { it.date },
                opensOn = CivilDate.parseIso(event.value(DeciderTags.OPENED)),
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }
    }
}
