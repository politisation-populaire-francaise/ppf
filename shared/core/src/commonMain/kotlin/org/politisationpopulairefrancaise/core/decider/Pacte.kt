package org.politisationpopulairefrancaise.core.decider

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ArchivedCopy
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags.archives
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/** Where a pact stands (cahier fonctionnel, M13.5). */
enum class PacteStatus(val tagValue: String) {
    /** A starting list, to be chosen by the citizens' vote: not sent yet. */
    PROPOSED("proposee"),
    /** Sent to every candidate, the same day, with the same deadline: the table fills in. */
    SENT("envoye"),
    /** The election is over: what each elected candidate really does is followed. */
    CLOSED("clos"),
    ;

    companion object {
        fun fromTag(value: String?): PacteStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The election a pact is sent for. */
data class Election(val id: String, val label: String, val firstRound: CivilDate?)

/**
 * One measure of a pact: a precise, checkable commitment (M13.5: « engagements précis et
 * vérifiables »), with the text it changes so that the commitment can be checked afterwards.
 */
data class PacteMeasure(val id: String, val title: String, val commitment: String, val textToChange: String?)

/**
 * A democratic pact (M13.5, DE-08): a short list of measures sent to every candidate of an
 * election, without exception, with the same text and the same deadline. Every candidate is a
 * link to their sheet in "Voir"; the table of answers is built by [PacteBoard].
 */
data class Pacte(
    val article: Article,
    val election: Election,
    val status: PacteStatus,
    val measures: List<PacteMeasure>,
    val candidates: List<ContentAddress>,
    val sentOn: CivilDate?,
    val deadline: CivilDate?,
    val sources: List<Source>,
    val archives: List<ArchivedCopy>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    /**
     * The fingerprint of the text sent (SHA-256, hexadecimal) of the election and of every measure,
     * in their order. Each answer names the fingerprint of the text it answers: an answer to another
     * text is never counted, so that every candidate is judged on exactly the same words.
     */
    val fingerprint: String by lazy { fingerprintOf(election, measures) }

    fun measure(id: String): PacteMeasure? = measures.firstOrNull { it.id == id }

    companion object {
        const val MIN_MEASURES: Int = 1
        /** The cahier fonctionnel asks for a short list (5 to 10 measures); more would dilute the pressure. */
        const val MAX_MEASURES: Int = 10

        fun from(article: Article): Pacte? {
            if (article.type != ArticleType.PACTE) return null
            val event = article.event
            val election = event.fields(DeciderTags.ELECTION).firstNotNullOfOrNull { f ->
                val id = f.at(0) ?: return@firstNotNullOfOrNull null
                val label = f.at(1) ?: return@firstNotNullOfOrNull null
                Election(id, label, CivilDate.parseIso(f.at(2)))
            } ?: return null
            val status = PacteStatus.fromTag(event.value(DeciderTags.PACTE_STATUS)) ?: return null
            val measures = event.fields(DeciderTags.MEASURE).mapNotNull { f ->
                val id = f.at(0) ?: return@mapNotNull null
                val title = f.at(1) ?: return@mapNotNull null
                val commitment = f.at(2) ?: return@mapNotNull null
                PacteMeasure(id, title, commitment, f.at(3))
            }
            // A measure read twice, or a list too long or empty, makes the whole pact unreadable:
            // the text sent must be exactly the one shown.
            if (measures.size !in MIN_MEASURES..MAX_MEASURES) return null
            if (measures.map { it.id }.toSet().size != measures.size) return null
            val sentOn = CivilDate.parseIso(event.value(DeciderTags.SENT))
            val deadline = CivilDate.parseIso(event.value(DeciderTags.DEADLINE))
            // Once sent, the day it left and its deadline are always shown: they are the same for all.
            if (status != PacteStatus.PROPOSED && (sentOn == null || deadline == null || deadline < sentOn)) return null
            return Pacte(
                article = article,
                election = election,
                status = status,
                measures = measures,
                candidates = event.links(DeciderTags.ROLE_CANDIDATE),
                sentOn = sentOn,
                deadline = deadline,
                sources = event.sources(),
                archives = event.archives(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }

        /**
         * The canonical text of a pact, as hashed: one line per field, fields separated by the
         * unit separator (U+001F), so that no text can be confused with another.
         */
        fun canonicalText(election: Election, measures: List<PacteMeasure>): String = buildString {
            append("ppf-pacte-v1\n")
            append(election.id).append('\u001f').append(election.label.trim()).append('\n')
            measures.forEach { m ->
                append(m.id).append('\u001f').append(m.title.trim()).append('\u001f').append(m.commitment.trim()).append('\u001f').append(m.textToChange?.trim().orEmpty()).append('\n')
            }
        }

        fun fingerprintOf(election: Election, measures: List<PacteMeasure>): String =
            Crypto.sha256(canonicalText(election, measures).encodeToByteArray()).joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

/** What a candidate answered on one measure. */
enum class AnswerStatus(val tagValue: String) {
    SIGNED("signe"),
    REFUSED("refuse"),
    ;

    companion object {
        fun fromTag(value: String?): AnswerStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The answer on one measure, with the candidate's comment when they gave one. */
data class Answer(val measureId: String, val status: AnswerStatus, val comment: String?)

/**
 * The answer of one candidate to a pact (« engagement »): measure by measure, signed or refused,
 * with the day it was received and the archive of the document received (its SHA-256 and its
 * timestamp proof: M13.5, « engagements archivés avec empreinte et horodatage »). A measure
 * without an answer stays « sans réponse ».
 */
data class Engagement(
    val article: Article,
    val pacte: ContentAddress,
    val candidate: ContentAddress,
    /** The fingerprint of the text answered; compared with [Pacte.fingerprint]. */
    val textFingerprint: String,
    val answers: List<Answer>,
    val receivedOn: CivilDate,
    val archives: List<ArchivedCopy>,
    val sources: List<Source>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address

    fun answer(measureId: String): Answer? = answers.firstOrNull { it.measureId == measureId }

    companion object {
        fun from(article: Article): Engagement? {
            if (article.type != ArticleType.ENGAGEMENT) return null
            val event = article.event
            val pacte = event.links(DeciderTags.ROLE_PACTE).singleOrNull() ?: return null
            val candidate = event.links(DeciderTags.ROLE_CANDIDATE).singleOrNull() ?: return null
            val fingerprint = event.value(DeciderTags.PACTE_TEXT)?.lowercase()?.takeIf(::isSha256Hex) ?: return null
            val received = CivilDate.parseIso(event.value(DeciderTags.RECEIVED)) ?: return null
            val answers = event.fields(DeciderTags.ANSWER).mapNotNull { f ->
                val id = f.at(0) ?: return@mapNotNull null
                val status = AnswerStatus.fromTag(f.at(1)) ?: return@mapNotNull null
                Answer(id, status, f.at(2))
            }
            // Two answers on the same measure would let a reader pick the one they like: unreadable.
            if (answers.map { it.measureId }.toSet().size != answers.size) return null
            return Engagement(
                article = article,
                pacte = pacte,
                candidate = candidate,
                textFingerprint = fingerprint,
                answers = answers,
                receivedOn = received,
                archives = event.archives(),
                sources = event.sources(),
                corrections = event.corrections(),
            )
        }

        internal fun isSha256Hex(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

/** A sourced fact of "Voir" that follows a measure after the election (a bill tabled, a vote...). */
data class PacteFollowUp(val pacte: ContentAddress, val measureId: String, val factAddress: ContentAddress, val persons: List<ContentAddress>, val date: CivilDate)

internal object PacteFollowUps {
    /** Reads the link of a fact of "Voir" to a measure of a pact; null when the fact follows none. */
    fun of(fact: Fact): PacteFollowUp? {
        val event = fact.article.event
        val pacte = event.links(DeciderTags.ROLE_PACTE).firstOrNull() ?: return null
        val measure = event.value(DeciderTags.PACTE_MEASURE) ?: return null
        return PacteFollowUp(pacte, measure, fact.address, fact.persons, fact.date)
    }
}
