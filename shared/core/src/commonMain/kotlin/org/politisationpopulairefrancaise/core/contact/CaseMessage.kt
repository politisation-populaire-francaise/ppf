package org.politisationpopulairefrancaise.core.contact

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Why a text is not a `ppf-case` message this reader accepts; the message is dropped, never shown. */
class CaseFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** What a `ppf-case` message is (docs/integration/contact-reclamations.md, section 5). */
@Serializable
enum class CaseMessageKind(val wire: String) {
    /** The first message of the person: the nature of the case, its subject and text. */
    @SerialName("opening")
    OPENING("opening"),

    /** A follow-up message of the person. */
    @SerialName("message")
    MESSAGE("message"),

    /** A reply of the team, shown as signed « l'équipe PPF » (never by the member who wrote it). */
    @SerialName("reply")
    REPLY("reply"),

    /** A change of status explained to the person (motivated extension, closing). */
    @SerialName("status")
    STATUS("status"),
}

/** The contested moderation decision of a claim: its identifier and its date (seconds since 1970). */
@Serializable
data class ContestedDecision(val id: String, val at: Long)

/**
 * A message of a case, format `ppf-case` version 1: the JSON text of a NIP-17 rumor (kind 14), sealed then
 * gift wrapped (NIP-59) for the inbox of the team (messages of the person) or for the key of the case
 * (replies of the team). The server never sees it. Absent optional fields are omitted, never `null`.
 *
 * A reader refuses a format or a version it does not know ([fromJson]); unknown fields of version 1 are
 * ignored. Replies are never modified: a correction is a new reply.
 */
@Serializable
data class CaseMessage(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** The number of the case, display form `PPF-XXXXX-XXXXX`. */
    @SerialName("case") val caseNumber: String,
    val kind: CaseMessageKind,
    val text: String,
    // opening
    val type: CaseType? = null,
    val subtype: TicketSubtype? = null,
    val category: ContactCategory? = null,
    val motif: ClaimMotif? = null,
    val subject: String? = null,
    /** The technical report joined with the person's consent (S-101). */
    val technical: String? = null,
    /** The stable address of the content concerned (`kind:pubkey:d`, or an event id). */
    val content: String? = null,
    val decision: ContestedDecision? = null,
    /** For a right of reply or rights on one's data: what the person agrees to say about who they are. */
    @SerialName("declared_identity") val declaredIdentity: String? = null,
    // reply
    /** The motives of the decision on a contestation (DSA, article 17). */
    val motives: String? = null,
    /** Whether an automated tool took part in the decision (DSA, article 17, paragraph 3, c). */
    val automated: Boolean? = null,
    // status
    val status: CaseStatus? = null,
    /** The new deadline, after a motivated extension. */
    @SerialName("deadline_at") val deadlineAt: Long? = null,
) {
    /** The nature of an opening; null for the other kinds. */
    val nature: CaseNature? get() = if (kind == CaseMessageKind.OPENING) CaseNature.of(type, subtype, category, motif) else null

    /** Every rule this message breaks (empty when it is valid), in English for logs and tests. */
    fun problems(): List<String> = buildList {
        if (format != FORMAT) add("format must be $FORMAT")
        if (version != VERSION) add("version must be $VERSION")
        if (CaseNumber.fromDisplay(caseNumber) == null) add("case must be a number PPF-XXXXX-XXXXX")
        if (text.isBlank()) add("text is empty")
        if (text.length > MAX_TEXT) add("text has more than $MAX_TEXT characters")
        val openingFields = listOf(type, subtype, category, motif, subject, technical, content, decision, declaredIdentity)
        val replyFields = listOf(motives, automated)
        val statusFields = listOf(status, deadlineAt)
        when (kind) {
            CaseMessageKind.OPENING -> {
                val nature = CaseNature.of(type, subtype, category, motif)
                if (nature == null) add("an opening has a type and exactly the qualifier of that type")
                if (subject.isNullOrBlank()) add("an opening has a subject")
                if ((subject?.length ?: 0) > MAX_SUBJECT) add("subject has more than $MAX_SUBJECT characters")
                if ((technical?.length ?: 0) > MAX_TECHNICAL) add("technical has more than $MAX_TECHNICAL characters")
                if (content != null && (content.isBlank() || content.length > MAX_CONTENT_ADDRESS)) add("content is an address of 1 to $MAX_CONTENT_ADDRESS characters")
                if (decision != null) {
                    if (nature?.motif != ClaimMotif.MODERATION_DECISION) add("decision belongs to a claim against a moderation decision")
                    if (decision.id.isBlank() || decision.id.length > MAX_DECISION_ID || decision.at < 0) add("decision has an id of 1 to $MAX_DECISION_ID characters and a positive date")
                }
                if (declaredIdentity != null) {
                    if (nature?.motif != ClaimMotif.RIGHT_OF_REPLY && nature?.motif != ClaimMotif.DATA_RIGHTS) add("declared_identity belongs to a right of reply or a request on one's data")
                    if (declaredIdentity.isBlank() || declaredIdentity.length > MAX_DECLARED_IDENTITY) add("declared_identity has 1 to $MAX_DECLARED_IDENTITY characters")
                }
                if ((replyFields + statusFields).any { it != null }) add("an opening has no field of a reply or of a status")
            }
            CaseMessageKind.MESSAGE -> if ((openingFields + replyFields + statusFields).any { it != null }) add("a follow-up message has a text only")
            CaseMessageKind.REPLY -> {
                if ((openingFields + statusFields).any { it != null }) add("a reply has a text, and motives and automated only")
                if (motives != null && (motives.isBlank() || motives.length > MAX_MOTIVES)) add("motives has 1 to $MAX_MOTIVES characters")
            }
            CaseMessageKind.STATUS -> {
                if ((openingFields + replyFields).any { it != null }) add("a status has a text, a status and a deadline only")
                if (status == null) add("a status message names the new status")
                if (deadlineAt != null && deadlineAt < 0) add("deadline_at is positive")
            }
        }
    }

    val isValid: Boolean get() = problems().isEmpty()

    /** The JSON text of the rumor. Throws [CaseFormatException] when the message breaks a rule. */
    fun toJson(): String {
        val problems = problems()
        if (problems.isNotEmpty()) throw CaseFormatException(problems.joinToString("; "))
        return CaseJson.encodeToString(serializer(), this)
    }

    companion object {
        const val FORMAT: String = "ppf-case"
        const val VERSION: Int = 1
        const val MAX_SUBJECT: Int = 200
        const val MAX_TEXT: Int = 20_000
        const val MAX_TECHNICAL: Int = 16_000
        const val MAX_CONTENT_ADDRESS: Int = 300
        const val MAX_DECISION_ID: Int = 200
        const val MAX_DECLARED_IDENTITY: Int = 2_000
        const val MAX_MOTIVES: Int = 4_000

        /** The first message of the person. */
        fun opening(
            number: CaseNumber,
            nature: CaseNature,
            subject: String,
            text: String,
            technical: String? = null,
            content: String? = null,
            decision: ContestedDecision? = null,
            declaredIdentity: String? = null,
        ): CaseMessage = CaseMessage(
            caseNumber = number.display,
            kind = CaseMessageKind.OPENING,
            text = text,
            type = nature.type,
            subtype = nature.subtype,
            category = nature.category,
            motif = nature.motif,
            subject = subject,
            technical = technical,
            content = content,
            decision = decision,
            declaredIdentity = declaredIdentity,
        ).checked()

        /** A follow-up message of the person. */
        fun followUp(number: CaseNumber, text: String): CaseMessage =
            CaseMessage(caseNumber = number.display, kind = CaseMessageKind.MESSAGE, text = text).checked()

        /** A reply of the team; [motives] and [automated] for a contested moderation decision. */
        fun reply(number: CaseNumber, text: String, motives: String? = null, automated: Boolean? = null): CaseMessage =
            CaseMessage(caseNumber = number.display, kind = CaseMessageKind.REPLY, text = text, motives = motives, automated = automated).checked()

        /** A change of status explained to the person. */
        fun statusChange(number: CaseNumber, status: CaseStatus, text: String, deadlineAt: Long? = null): CaseMessage =
            CaseMessage(caseNumber = number.display, kind = CaseMessageKind.STATUS, text = text, status = status, deadlineAt = deadlineAt).checked()

        /**
         * Reads a message: refuses another format, a version it does not know, and any message that breaks a
         * rule of version 1 ([CaseFormatException]).
         */
        fun fromJson(json: String): CaseMessage {
            val root = runCatching { CaseJson.parseToJsonElement(json) as? JsonObject }.getOrNull()
                ?: throw CaseFormatException("Not a JSON object")
            if ((root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content != FORMAT) throw CaseFormatException("Not a $FORMAT message")
            val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (version != VERSION) throw CaseFormatException("Unknown version of $FORMAT: ${root["version"]}")
            val message = try {
                CaseJson.decodeFromJsonElement(serializer(), root)
            } catch (e: Exception) {
                throw CaseFormatException("Malformed $FORMAT message", e)
            }
            val problems = message.problems()
            if (problems.isNotEmpty()) throw CaseFormatException(problems.joinToString("; "))
            return message
        }

        private fun CaseMessage.checked(): CaseMessage {
            val problems = problems()
            require(problems.isEmpty()) { problems.joinToString("; ") }
            return this
        }
    }
}

private val CaseJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
