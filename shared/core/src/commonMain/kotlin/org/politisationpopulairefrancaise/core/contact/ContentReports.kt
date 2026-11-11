package org.politisationpopulairefrancaise.core.contact

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * Why a content is reported (S-38, D-234). The code travels in clear with the case so that the team can sort the
 * reports without reading them; the person's comment stays encrypted for the inbox of the team.
 */
enum class ReportReason(val wire: String) {
    /** Illegal content, or a call to violence. */
    ILLEGAL("illicite"),

    /** Hate, harassment, threats. */
    HATE("haine"),

    /** Someone else's personal data exposed. */
    PERSONAL_DATA("donnees-personnelles"),

    /** False information, a scam, an impersonation. */
    DECEPTION("tromperie"),

    /** Spam, a duplicate. */
    SPAM("spam"),

    OTHER("autre"),
    ;

    companion object {
        fun fromWire(value: String?): ReportReason? = entries.firstOrNull { it.wire == value }
    }
}

/** A report ready to be sent: the case to create, and the secrets the person keeps to follow it. */
class PreparedReport(val secrets: CaseSecrets, val code: CaseCode, val creation: CaseCreation)

/** What became of a report. */
sealed interface ReportOutcome {
    /** The team received it: its case [number]. */
    data class Sent(val number: String) : ReportOutcome

    /** No network, or the server could not be reached: nothing was received. */
    data object Unreachable : ReportOutcome

    /** The server refused it ([code] of its answer: too many reports, cases closed…). */
    data class Refused(val code: String?) : ReportOutcome
}

/** The destination that creates cases (the server); implemented by the server adapter. */
interface CaseSender {
    suspend fun createCase(creation: CaseCreation): ReportOutcome
}

/**
 * « Signaler ce contenu » (S-38, D-234): a report is a case of M30 (`ticket` / `report`) opened without an account,
 * its comment encrypted for the inbox of the team (docs/integration/contact-reclamations.md). For this subtype only,
 * the address of the content (already public) and the code of the reason also travel in clear, so that the back
 * office can group the reports by content before the team client exists; the server never learns who reported.
 */
object ContentReports {
    /** The longest comment: short on purpose, the team reads many. */
    const val MAX_COMMENT: Int = 500

    /** The text of the case when the person wrote nothing (a case has a text). */
    const val NO_COMMENT: String = "(aucun commentaire)"

    /**
     * The inbox of the team this build writes to: the preproduction's once it exists, the example one meanwhile
     * (development only, readable by anybody: said in docs/integration/contact-reclamations.md).
     */
    fun inbox(): PublicKey = ContactInbox.preproduction() ?: ContactInbox.EXAMPLE

    /** Whether [address] reads as the stable address of a published content: `<kind>:<64 hex>:<identifier>`. */
    fun isContentAddress(address: String): Boolean {
        if (address.length > CaseMessage.MAX_CONTENT_ADDRESS) return false
        val parts = address.split(':', limit = 3)
        return parts.size == 3 &&
            parts[0].isNotEmpty() && parts[0].all { it.isDigit() } &&
            parts[1].length == 64 && parts[1].all { it in '0'..'9' || it in 'a'..'f' } &&
            parts[2].isNotEmpty()
    }

    /** Prepares the case of a report on [address]; the proof of work makes this slow on purpose (call it off the main thread). */
    suspend fun prepare(
        address: String,
        reason: ReportReason,
        comment: String,
        inbox: PublicKey = inbox(),
        now: Long = currentEpochSeconds(),
    ): PreparedReport {
        require(isContentAddress(address)) { "Not the address of a content" }
        val text = comment.trim().take(MAX_COMMENT).ifEmpty { NO_COMMENT }
        val number = CaseNumber.generate()
        val code = CaseCode.generate()
        val secrets = CaseSecrets.derive(number, code)
        val nature = CaseNature.Ticket(TicketSubtype.REPORT)
        val message = CaseMessage.opening(number, nature, subject = reason.wire, text = text, content = address)
        val envelope = CaseEnvelopes.opening(message, secrets.signer(), inbox, now = now)
        val creation = CaseCreation.of(secrets, nature, envelope).copy(reportContent = address, reportReason = reason.wire)
        return PreparedReport(secrets, code, creation)
    }
}
