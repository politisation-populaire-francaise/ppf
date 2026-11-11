package org.politisationpopulairefrancaise.core.agir.calls

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * One slot of a call (AP-11): a stretch of time with a limited number of places (a picket, a desk, a welcome).
 * Written in the call as `["ppf-slot", <id>, <start>, <end>, <places>, <label>]`.
 */
data class CallSlot(val id: String, val start: Long, val end: Long, val places: Int, val label: String) {
    fun tag(): List<String> = listOf(CallSlots.TAG, id, start.toString(), end.toString(), places.toString(), label)
}

/** The slots of a call and their rules (AP-11, docs/integration/agir-creneaux.md). */
object CallSlots {
    const val TAG: String = "ppf-slot"
    const val MAX_SLOTS: Int = 24
    const val MAX_PLACES: Int = 999
    const val MAX_LABEL: Int = 80
    const val MAX_DURATION_SECONDS: Long = 24 * 60 * 60

    /** The server erases the signups of a slot two days after its end. */
    const val SERVER_KEPT_AFTER_END: Long = 2 * 24 * 60 * 60

    /** The device forgets its own signup seven days after the end of the slot. */
    const val DEVICE_KEPT_AFTER_END: Long = 7 * 24 * 60 * 60

    private val ID = Regex("[a-z0-9-]{1,16}")

    /** The types of calls that may be cut into slots: those where people come or help. */
    val TYPES: Set<CallType> = setOf(
        CallType.BENEVOLES, CallType.GREVE, CallType.BLOQUER, CallType.SE_REUNIR, CallType.S_ORGANISER, CallType.MANIFESTER,
    )

    /** Whether [slot] is well formed (the same rule on the device, the server and the site). */
    fun isValid(slot: CallSlot): Boolean =
        ID.matches(slot.id) && slot.start >= 0 && slot.end > slot.start && slot.end - slot.start <= MAX_DURATION_SECONDS &&
            slot.places in 1..MAX_PLACES && slot.label.isNotBlank() && slot.label.length <= MAX_LABEL && slot.label.none { it.isISOControl() }

    /** The valid slots of [event], in their order, the first of each id only, [MAX_SLOTS] at most. */
    fun read(event: SignedEvent): List<CallSlot> {
        val seen = HashSet<String>()
        return event.tags.asSequence()
            .filter { it.size >= 6 && it[0] == TAG }
            .mapNotNull { tag ->
                val slot = CallSlot(tag[1], tag[2].toLongOrNull() ?: return@mapNotNull null, tag[3].toLongOrNull() ?: return@mapNotNull null, tag[4].toIntOrNull() ?: return@mapNotNull null, tag[5].trim())
                slot.takeIf { isValid(it) && seen.add(it.id) }
            }
            .take(MAX_SLOTS)
            .toList()
    }

    /** A new id for the [index]th slot of a draft (stable once written: the signups name it). */
    fun idFor(index: Int): String = "c${index + 1}"
}

/** The slots of this call (AP-11). */
val Call.slots: List<CallSlot> get() = if (type in CallSlots.TYPES) CallSlots.read(event) else emptyList()

/** What the organizer reads in a signup, once decrypted: the slot and the name the person chose. */
@Serializable
data class SlotSignupMessage(val v: Int = 1, val call: String, val slot: String, val name: String)

/** The routes of the slots on the server. */
object SlotApi {
    const val SLOTS: String = "/api/v1/agir/slots"
    const val SIGNUPS: String = "$SLOTS/signups"
    const val WITHDRAW: String = "$SLOTS/withdraw"

    /** The organizer removes a signup (a fake one, a duplicate), authenticated by the key of the call (NIP-98). */
    const val REMOVE: String = "$SLOTS/remove"

    /** The proof of work asked for a signup unless the server says otherwise. */
    const val WORK_BITS: Int = 16

    const val MAX_NAME: Int = 40

    /** The name shown to the organizer when the person typed none. */
    const val NO_NAME: String = "(sans nom)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

/** `POST /api/v1/agir/slots`: a signup, encrypted for the organizer, with the hash of the token that withdraws it. */
@Serializable
data class SlotSignupRequest(
    val call: String,
    val slot: String,
    @SerialName("withdraw_hash") val withdrawHash: String,
    val envelope: SignedEvent,
)

/** `201` to a signup: its id (the id of the envelope) and the places now taken in the slot. */
@Serializable
data class SlotSignupAnswer(val id: String, val taken: Int)

/** `GET /api/v1/agir/slots?call=<address>`: the places taken in each slot of the call. */
@Serializable
data class SlotCounts(val slots: Map<String, Int>)

/** `GET /api/v1/agir/slots/signups?call=<address>`: the envelopes of the signups, readable by the organizer only. */
@Serializable
data class SlotEnvelopes(val envelopes: List<SignedEvent>)

/** `POST /api/v1/agir/slots/withdraw`: withdraws the signup [id] with its [token]. */
@Serializable
data class SlotWithdrawal(val id: String, val token: String)

/** `POST /api/v1/agir/slots/remove`: the organizer removes the signup [id], with `Authorization: Nostr` signed by the author of the call. */
@Serializable
data class SlotRemoval(val id: String)

/** A signup ready to send, and the token the device keeps to withdraw it. */
class PreparedSignup(val request: SlotSignupRequest, val token: String)

/** What became of a request about a slot. */
sealed interface SlotOutcome {
    data class Done(val id: String, val taken: Int) : SlotOutcome

    /** No place left in the slot. */
    data object Full : SlotOutcome

    /** No network, or the server could not be reached. */
    data object Unreachable : SlotOutcome

    data class Refused(val code: String?) : SlotOutcome
}

/** Where the device sends its signups and reads the counters (the server adapter). */
interface SlotClient {
    suspend fun counts(call: String): Map<String, Int>?

    suspend fun signUp(request: SlotSignupRequest): SlotOutcome

    /** [SlotOutcome.Done] once withdrawn, [SlotOutcome.Refused] when the server no longer knows it (erased, or wrong token). */
    suspend fun withdraw(withdrawal: SlotWithdrawal): SlotOutcome

    /** The envelopes of the signups of [call], for its organizer. */
    suspend fun signups(call: String): List<SignedEvent>?

    /** The organizer removes the signup [id] of one of their calls, signing the request with [organizer] (NIP-98). */
    suspend fun remove(id: String, organizer: EventSigner): SlotOutcome = SlotOutcome.Refused(null)
}

/**
 * Signups to the slots of a call (AP-11). A signup is a private message for the author of the call only: sealed by a
 * one-time key (the organizer never learns the account of the person, only the name typed for this slot), wrapped by
 * another one with a proof of work. The server keeps the envelope, the call and the slot (to count the places) and the
 * hash of a withdrawal token; it never knows who signed up, and erases everything two days after the slot.
 */
object SlotSignups {
    /** Prepares a signup to [slot] of [call]; the proof of work makes it slow on purpose (call it off the main thread). */
    suspend fun prepare(call: Call, slot: CallSlot, name: String, work: Int = SlotApi.WORK_BITS, now: Long = currentEpochSeconds()): PreparedSignup {
        require(slot in call.slots) { "Not a slot of this call" }
        val message = SlotSignupMessage(call = call.address.toString(), slot = slot.id, name = cleanName(name))
        val oneTime = IdentityKeys.generate()
        val envelope = try {
            CaseEnvelopes.buildText(SlotApi.json.encodeToString(SlotSignupMessage.serializer(), message), LocalMessagingSigner(oneTime), PublicKey.fromHex(call.author), work, now)
        } finally {
            oneTime.destroy()
        }
        val token = secureRandomBytes(32).toHex()
        return PreparedSignup(SlotSignupRequest(message.call, slot.id, hashToken(token), envelope), token)
    }

    /** The name sent to the organizer: trimmed, [SlotApi.MAX_NAME] characters at most, [SlotApi.NO_NAME] when empty. */
    fun cleanName(name: String): String = name.filterNot { it.isISOControl() }.trim().take(SlotApi.MAX_NAME).ifEmpty { SlotApi.NO_NAME }

    /** SHA-256 of the token, in lowercase hexadecimal: what the server keeps. */
    fun hashToken(token: String): String = Crypto.sha256(token.encodeToByteArray()).toHex()

    /** Opens a signup with the key of the organizer, with the id of its envelope; null when it is not a signup to [callAddress]. */
    fun openWithId(envelope: SignedEvent, organizer: MessagingSigner, callAddress: String): Pair<String, SlotSignupMessage>? =
        open(envelope, organizer, callAddress)?.let { envelope.id to it }

    /** Opens a signup with the key of the organizer; null when it is not a signup to [callAddress]. */
    fun open(envelope: SignedEvent, organizer: MessagingSigner, callAddress: String): SlotSignupMessage? = runCatching {
        val rumor = GiftWrap.unwrap(envelope, organizer)
        if (rumor.kind != EventKinds.CHAT_MESSAGE) return null
        SlotApi.json.decodeFromString(SlotSignupMessage.serializer(), rumor.content).takeIf { it.call == callAddress && it.name.length <= SlotApi.MAX_NAME }
    }.getOrNull()

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/**
 * A slot as typed (AP-11): a day, a start and an end hour (an end before the start is the next day: a night picket),
 * a number of places and a label.
 */
data class SlotDraft(val label: String = "", val date: String = "", val startTime: String = "", val endTime: String = "", val places: String = "") {
    fun start(): Long? = if (startTime.isBlank()) null else DateTimeText(date, startTime).toEpochSeconds()

    fun end(): Long? {
        val start = start() ?: return null
        if (endTime.isBlank()) return null
        val end = DateTimeText(date, endTime).toEpochSeconds() ?: return null
        return if (end > start) end else end + 24 * 60 * 60
    }
}
