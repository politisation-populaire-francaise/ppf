package org.politisationpopulairefrancaise.signer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** Why an event is refused: a stable code of [SigningRefusals] and, for some, the tag concerned. */
data class Refusal(val code: String, val detail: String? = null)

/** How many approvals a use asks for. */
enum class Approvals {
    /** Automatic use: none, and any approval sent is refused. */
    NONE,

    /** One member of the group, or two different ones when the queue asks for the double validation. */
    HUMAN,
}

/**
 * The rule of one use (contract, section 4; D-129): the approvals, the hourly ceiling, how old an event
 * may be when it is signed, and the shape the event must have. The kinds come from [SigningUse.kinds].
 */
class UsePolicy(
    val use: SigningUse,
    val approvals: Approvals,
    val maxPerHour: Int,
    /** An event dated more than this before the request is refused (bounds backdating). */
    val maxAgeSeconds: Long,
    private val shape: (UnsignedEvent) -> Refusal?,
) {
    /** The first rule [event] breaks, null when it has the shape of this use. */
    fun check(event: UnsignedEvent): Refusal? {
        if (event.kind !in use.kinds) return Refusal(SigningRefusals.KIND_NOT_ALLOWED)
        return shape(event)
    }
}

/**
 * The rules of version 1, one per use. Human uses check the frame of the content (the people who approve
 * read the rest); automatic uses accept exactly one form, tag by tag, since nobody reads them before.
 * Changing an automatic rule is a change of the contract (section 4).
 */
object SigningPolicies {
    /** Contents waiting for a double validation may wait 7 days in the back-office (D-123). */
    const val HUMAN_MAX_AGE_SECONDS: Long = 8L * 86_400
    const val AUTOMATIC_MAX_AGE_SECONDS: Long = 86_400

    /** An event may be dated at most this far in the future (clocks of the server and of the service). */
    const val FUTURE_TOLERANCE_SECONDS: Long = 300

    /** The size of the canonical form, the same limit as `POST /api/v1/events` of the server. */
    const val MAX_EVENT_BYTES: Int = 65_536
    const val MAX_TAGS: Int = 1_000

    val DEFAULT_MAX_PER_HOUR: Map<SigningUse, Int> = mapOf(
        SigningUse.EDITORIAL_COMPRENDRE to 60,
        SigningUse.EDITORIAL_VOIR to 60,
        SigningUse.REVENDICATIONS_STATUS to 120,
        SigningUse.AGIR_ORGANISATIONS to 30,
        SigningUse.DECIDER_PACTE to 60,
        SigningUse.ARCHIVES_TIMESTAMPS to 600,
        SigningUse.OPENDATA_INDEX to 6,
        SigningUse.DECIDER_URN_CLOSURE to 30,
    )

    /** The `ppf-type` values that belong to another use and that an editorial key never signs. */
    private val TYPES_OF_OTHER_USES = setOf("organisation", "pacte", "engagement")

    private val ORIGINS = setOf("human", "ai-assisted")
    private val DEMAND_STATUSES = setOf("nouvelle", "en-debat", "tres-soutenue", "retenue", "campagne")
    private val ORGANISATION_ID = Regex("^[a-z0-9._~-]{1,64}$")
    private val URN_CLOSURE_ID = Regex("^ppf:urne-cloture:([1-9][0-9]{0,17})$")
    private val JSON = Json

    fun of(use: SigningUse, maxPerHour: Map<SigningUse, Int> = emptyMap()): UsePolicy {
        val ceiling = maxPerHour[use] ?: DEFAULT_MAX_PER_HOUR.getValue(use)
        val age = if (use.automatic) AUTOMATIC_MAX_AGE_SECONDS else HUMAN_MAX_AGE_SECONDS
        val approvals = if (use.automatic) Approvals.NONE else Approvals.HUMAN
        val shape: (UnsignedEvent) -> Refusal? = when (use) {
            SigningUse.EDITORIAL_COMPRENDRE, SigningUse.EDITORIAL_VOIR -> ::editorial
            SigningUse.AGIR_ORGANISATIONS -> ::organisation
            SigningUse.DECIDER_PACTE -> ::pacte
            SigningUse.REVENDICATIONS_STATUS -> ::statusLabel
            SigningUse.ARCHIVES_TIMESTAMPS -> ::timestamp
            SigningUse.OPENDATA_INDEX -> ::openDataIndex
            SigningUse.DECIDER_URN_CLOSURE -> ::urnClosure
        }
        return UsePolicy(use, approvals, ceiling, age, shape)
    }

    /** A NIP-23 content of the editorial team: one `d`, one `title`, one `ppf-origin`, and no type of another use. */
    private fun editorial(event: UnsignedEvent): Refusal? {
        frame(event, requireOrigin = true)?.let { return it }
        val type = single(event, "ppf-type")
        if (type is Single.Several) return Refusal(SigningRefusals.BAD_TAG, "ppf-type")
        if (type is Single.One && type.value in TYPES_OF_OTHER_USES) return Refusal(SigningRefusals.BAD_TAG, "ppf-type")
        return null
    }

    /** A sheet of the directory of organisations (FORMATS.md, section 4 sexies). */
    private fun organisation(event: UnsignedEvent): Refusal? {
        frame(event, requireOrigin = false)?.let { return it }
        val d = (single(event, "d") as Single.One).value
        if (!ORGANISATION_ID.matches(d)) return Refusal(SigningRefusals.BAD_TAG, "d")
        if ((single(event, "ppf-type") as? Single.One)?.value != "organisation") return Refusal(SigningRefusals.BAD_TAG, "ppf-type")
        val keys = values(event, "p")
        if (keys.size > 1 || keys.any { !isHex64(it) }) return Refusal(SigningRefusals.BAD_TAG, "p")
        val status = single(event, "ppf-org-status")
        if (status is Single.Several || (status is Single.One && status.value !in setOf("verifiee", "revoquee"))) return Refusal(SigningRefusals.BAD_TAG, "ppf-org-status")
        return null
    }

    /** The Pact and the candidates' answers (FORMATS.md, section 4 septies). */
    private fun pacte(event: UnsignedEvent): Refusal? {
        frame(event, requireOrigin = true)?.let { return it }
        val type = (single(event, "ppf-type") as? Single.One)?.value
        if (type != "pacte" && type != "engagement") return Refusal(SigningRefusals.BAD_TAG, "ppf-type")
        return null
    }

    /** A NIP-32 status label of a demand: exactly `L`, `l` and `a`, as `StatusLabels` of Décider writes them. */
    private fun statusLabel(event: UnsignedEvent): Refusal? {
        onlyTags(event, setOf("L", "l", "a"))?.let { return it }
        val namespace = event.tags.filter { it[0] == "L" }
        if (namespace.size != 1 || namespace[0] != listOf("L", "ppf-status")) return Refusal(SigningRefusals.BAD_TAG, "L")
        val label = event.tags.filter { it[0] == "l" }
        if (label.size != 1 || label[0].size != 3 || label[0][1] !in DEMAND_STATUSES || label[0][2] != "ppf-status") return Refusal(SigningRefusals.BAD_TAG, "l")
        val target = event.tags.filter { it[0] == "a" }
        val address = target.singleOrNull()?.takeIf { it.size == 2 }?.let { runCatching { ContentAddress.parse(it[1]) }.getOrNull() }
        if (address == null || address.kind != EventKinds.LONG_FORM_ARTICLE || address.identifier.isEmpty()) return Refusal(SigningRefusals.BAD_TAG, "a")
        if (event.content.length > 2_000) return Refusal(SigningRefusals.BAD_CONTENT)
        return null
    }

    /** An OpenTimestamps attestation (NIP-03): one `e` (the timestamped id), at most one `k`, the proof in base64. */
    private fun timestamp(event: UnsignedEvent): Refusal? {
        onlyTags(event, setOf("e", "k"))?.let { return it }
        val e = event.tags.filter { it[0] == "e" }
        if (e.size != 1 || e[0].size !in 2..3 || !isHex64(e[0][1])) return Refusal(SigningRefusals.BAD_TAG, "e")
        val k = event.tags.filter { it[0] == "k" }
        if (k.size > 1 || k.any { it.size != 2 || it[1].toIntOrNull()?.takeIf { n -> n in 0..65535 && it[1] == n.toString() } == null }) return Refusal(SigningRefusals.BAD_TAG, "k")
        val content = event.content
        if (content.isEmpty() || content.length % 4 != 0 || !content.all { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' || it == '=' }) {
            return Refusal(SigningRefusals.BAD_CONTENT)
        }
        if (runCatching { java.util.Base64.getDecoder().decode(content) }.isFailure) return Refusal(SigningRefusals.BAD_CONTENT)
        return null
    }

    /** The index of the parliamentary open data (FORMATS.md, section 4 octies): exactly its `d`, a JSON object. */
    private fun openDataIndex(event: UnsignedEvent): Refusal? {
        if (event.tags != listOf(listOf("d", "ppf:parlement-index"))) return Refusal(SigningRefusals.BAD_TAG, "d")
        return jsonObjectContent(event)
    }

    /** The closing statement of a ballot box: its `d`, its number, the SHA-256 of the closing document. */
    private fun urnClosure(event: UnsignedEvent): Refusal? {
        onlyTags(event, setOf("d", "ppf-urne", "ppf-closing-sha256"))?.let { return it }
        val d = single(event, "d") as? Single.One ?: return Refusal(SigningRefusals.MISSING_TAG, "d")
        val number = URN_CLOSURE_ID.matchEntire(d.value)?.groupValues?.get(1) ?: return Refusal(SigningRefusals.BAD_TAG, "d")
        if ((single(event, "ppf-urne") as? Single.One)?.value != number) return Refusal(SigningRefusals.BAD_TAG, "ppf-urne")
        val digest = single(event, "ppf-closing-sha256") as? Single.One ?: return Refusal(SigningRefusals.MISSING_TAG, "ppf-closing-sha256")
        if (!isHex64(digest.value)) return Refusal(SigningRefusals.BAD_TAG, "ppf-closing-sha256")
        if (event.tags.any { it.size != 2 }) return Refusal(SigningRefusals.BAD_TAG)
        return jsonObjectContent(event)
    }

    /** `d`, `title` and, when asked, `ppf-origin`: once each, with a value. */
    private fun frame(event: UnsignedEvent, requireOrigin: Boolean): Refusal? {
        for (name in listOfNotNull("d", "title", if (requireOrigin) "ppf-origin" else null)) {
            when (val found = single(event, name)) {
                Single.None -> return Refusal(SigningRefusals.MISSING_TAG, name)
                Single.Several -> return Refusal(SigningRefusals.BAD_TAG, name)
                is Single.One -> if (found.value.isBlank() || found.value.length > 1_000 || found.value.any { it < ' ' }) return Refusal(SigningRefusals.BAD_TAG, name)
            }
        }
        val origin = single(event, "ppf-origin")
        if (origin is Single.Several || (origin is Single.One && origin.value !in ORIGINS)) return Refusal(SigningRefusals.BAD_TAG, "ppf-origin")
        return null
    }

    private fun onlyTags(event: UnsignedEvent, allowed: Set<String>): Refusal? {
        val other = event.tags.firstOrNull { it.isEmpty() || it[0] !in allowed }
        return other?.let { Refusal(SigningRefusals.TAG_NOT_ALLOWED, it.firstOrNull()) }
    }

    private fun jsonObjectContent(event: UnsignedEvent): Refusal? {
        val parsed = runCatching { JSON.parseToJsonElement(event.content) }.getOrNull()
        return if (parsed is JsonObject) null else Refusal(SigningRefusals.BAD_CONTENT)
    }

    private sealed interface Single {
        data object None : Single
        data object Several : Single
        data class One(val value: String) : Single
    }

    private fun single(event: UnsignedEvent, name: String): Single {
        val found = event.tags.filter { it.isNotEmpty() && it[0] == name }
        return when {
            found.isEmpty() -> Single.None
            found.size > 1 || found[0].size < 2 -> Single.Several
            else -> Single.One(found[0][1])
        }
    }

    private fun values(event: UnsignedEvent, name: String): List<String> =
        event.tags.filter { it.isNotEmpty() && it[0] == name }.map { it.getOrElse(1) { "" } }
}
