package org.politisationpopulairefrancaise.server.agir

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallSlots
import org.politisationpopulairefrancaise.core.agir.calls.SlotApi
import org.politisationpopulairefrancaise.core.agir.calls.SlotCounts
import org.politisationpopulairefrancaise.core.agir.calls.SlotEnvelopes
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupAnswer
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupRequest
import org.politisationpopulairefrancaise.core.agir.calls.SlotRemoval
import org.politisationpopulairefrancaise.core.agir.calls.SlotWithdrawal
import org.politisationpopulairefrancaise.core.urne.Nip98
import org.politisationpopulairefrancaise.core.urne.Nip98ReplayGuard
import io.ktor.server.request.uri
import org.politisationpopulairefrancaise.core.agir.calls.slots
import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.slf4j.Logger
import java.security.MessageDigest
import java.sql.Connection
import java.time.Clock
import javax.sql.DataSource
import kotlin.time.Duration

/** Why a request about a slot was refused: its HTTP status and code. */
data class SlotRefusal(val status: Int, val code: String, val message: String)

/** What became of a signup on the server. */
sealed interface SlotSignupResult {
    data class Stored(val id: String, val taken: Int) : SlotSignupResult

    data class Refused(val refusal: SlotRefusal) : SlotSignupResult
}

/**
 * The signups to the slots of calls (AP-11, table `call_slot_signup`): envelopes readable by the author of the call
 * only, counted by slot, withdrawn with their token, erased [CallSlots.SERVER_KEPT_AFTER_END] after the slot. The
 * capacity is checked under a lock of the slot, so that two signups at the same moment never exceed it.
 */
class SlotSignupStore(private val dataSource: DataSource, private val events: EventRepository, private val clock: Clock = Clock.systemUTC()) {

    private fun now(): Long = clock.instant().epochSecond

    /** The published call at [address], or null. */
    private fun publishedCall(address: ContentAddress): Call? =
        events.query(EventFilter(kinds = listOf(Call.KIND), authors = listOf(address.pubkey), identifiers = listOf(address.identifier), limit = 1))
            .firstOrNull()?.let(Call::from)

    fun signUp(request: SlotSignupRequest, work: Int = SlotApi.WORK_BITS): SlotSignupResult {
        val now = now()
        val address = parseAddress(request.call) ?: return refused(BAD_CALL)
        if (!HEX64.matches(request.withdrawHash)) return refused(MALFORMED)
        CaseEnvelopes.check(request.envelope, address.pubkey, now, work)?.let { return refused(SlotRefusal(400, "bad_envelope", "The envelope is not a signup for the author of the call ($it).")) }
        val call = publishedCall(address) ?: return refused(UNKNOWN_CALL)
        if (!call.isOpen) return refused(CLOSED)
        val slot = call.slots.firstOrNull { it.id == request.slot } ?: return refused(UNKNOWN_SLOT)
        if (slot.end <= now) return refused(SLOT_OVER)
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                lockSlot(connection, request.call, slot.id)
                val taken = count(connection, request.call, slot.id, now)
                val known = connection.prepareStatement("SELECT 1 FROM call_slot_signup WHERE id = ?").use { statement ->
                    statement.setString(1, request.envelope.id)
                    statement.executeQuery().use { it.next() }
                }
                val result = when {
                    known -> SlotSignupResult.Stored(request.envelope.id, taken)
                    taken >= slot.places -> SlotSignupResult.Refused(FULL)
                    else -> {
                        connection.prepareStatement(
                            "INSERT INTO call_slot_signup (id, call_address, slot_id, envelope, withdraw_hash, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, request.envelope.id)
                            statement.setString(2, request.call)
                            statement.setString(3, slot.id)
                            statement.setString(4, request.envelope.toJson())
                            statement.setString(5, request.withdrawHash)
                            statement.setLong(6, slot.end + CallSlots.SERVER_KEPT_AFTER_END)
                            statement.executeUpdate()
                        }
                        SlotSignupResult.Stored(request.envelope.id, taken + 1)
                    }
                }
                connection.commit()
                result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** The places taken in each slot of [callAddress] (slots without a signup are absent). */
    fun counts(callAddress: String): Map<String, Int> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT slot_id, COUNT(*) FROM call_slot_signup WHERE call_address = ? AND expires_at >= ? GROUP BY slot_id").use { statement ->
            statement.setString(1, callAddress)
            statement.setLong(2, now())
            statement.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) }
            }
        }
    }

    /** The envelopes of [callAddress], readable by its author only. */
    fun envelopes(callAddress: String): List<SignedEvent> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT envelope FROM call_slot_signup WHERE call_address = ? AND expires_at >= ? ORDER BY id LIMIT ?").use { statement ->
            statement.setString(1, callAddress)
            statement.setLong(2, now())
            statement.setInt(3, MAX_ENVELOPES)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) runCatching { SignedEvent.fromJson(rows.getString(1)) }.getOrNull()?.let(::add) }
            }
        }
    }

    /** Withdraws the signup [SlotWithdrawal.id] when its token is the right one; false otherwise. */
    fun withdraw(withdrawal: SlotWithdrawal): Boolean {
        if (!HEX64.matches(withdrawal.id) || !HEX64.matches(withdrawal.token)) return false
        val hash = MessageDigest.getInstance("SHA-256").digest(withdrawal.token.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        return dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM call_slot_signup WHERE id = ? AND withdraw_hash = ?").use { statement ->
                statement.setString(1, withdrawal.id)
                statement.setString(2, hash)
                statement.executeUpdate() > 0
            }
        }
    }

    /** The call a signup belongs to, or null when the server no longer has it. */
    fun callOf(id: String): String? {
        if (!HEX64.matches(id)) return null
        return dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT call_address FROM call_slot_signup WHERE id = ?").use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }
    }

    /** Removes the signup [id] (the organizer's decision, checked by the route); false when it was already gone. */
    fun remove(id: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM call_slot_signup WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate() > 0
        }
    }

    /** Erases the signups of the slots ended more than two days ago; returns how many. */
    fun purge(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM call_slot_signup WHERE expires_at < ?").use { statement ->
            statement.setLong(1, now())
            statement.executeUpdate()
        }
    }

    fun startPurge(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val erased = purge()
                if (erased > 0) log.info("Slots of calls: $erased signup(s) erased after their slot.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Slots of calls: erasure failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }

    private fun lockSlot(connection: Connection, callAddress: String, slotId: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use { statement ->
            statement.setString(1, "slot:$callAddress:$slotId")
            statement.executeQuery().close()
        }
    }

    private fun count(connection: Connection, callAddress: String, slotId: String, now: Long): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM call_slot_signup WHERE call_address = ? AND slot_id = ? AND expires_at >= ?").use { statement ->
            statement.setString(1, callAddress)
            statement.setString(2, slotId)
            statement.setLong(3, now)
            statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }

    private fun refused(refusal: SlotRefusal) = SlotSignupResult.Refused(refusal)

    companion object {
        const val MAX_REQUEST_BYTES: Long = CaseEnvelopes.MAX_ENVELOPE_BYTES + 4L * 1024
        const val MAX_ENVELOPES: Int = CallSlots.MAX_SLOTS * CallSlots.MAX_PLACES
        private val HEX64 = Regex("[0-9a-f]{64}")

        val MALFORMED = SlotRefusal(400, "malformed", "The body is not a signup to a slot.")
        val BAD_CALL = SlotRefusal(400, "bad_call", "Not the address of a call.")
        val UNKNOWN_CALL = SlotRefusal(404, "unknown_call", "No published call at this address.")
        val UNKNOWN_SLOT = SlotRefusal(404, "unknown_slot", "The call has no such slot.")
        val CLOSED = SlotRefusal(409, "closed", "The call is closed.")
        val SLOT_OVER = SlotRefusal(409, "slot_over", "The slot is over.")
        val FULL = SlotRefusal(409, "full", "No place left in this slot.")

        fun parseAddress(value: String): ContentAddress? =
            runCatching { ContentAddress.parse(value) }.getOrNull()?.takeIf { it.kind == Call.KIND && HEX64.matches(it.pubkey) && it.identifier.isNotEmpty() && value.length <= 200 }
    }
}

/**
 * The routes of the slots (AP-11), without any account:
 *
 * - POST /api/v1/agir/slots: a [SlotSignupRequest]; `201 {"id","taken"}` (also for the same signup sent again);
 *   `400 malformed | bad_call | bad_envelope`, `404 unknown_call | unknown_slot`, `409 closed | slot_over | full`,
 *   `413 too_large`, `429 rate_limited`.
 * - GET /api/v1/agir/slots?call=<address>: `{"slots": {"<id>": <taken>}}`.
 * - GET /api/v1/agir/slots/signups?call=<address>: `{"envelopes": [...]}`, readable by the author of the call only.
 * - POST /api/v1/agir/slots/withdraw: a [SlotWithdrawal]; `204`, or `404 not_found` (unknown id or wrong token).
 * - POST /api/v1/agir/slots/remove: a [SlotRemoval] with `Authorization: Nostr` (NIP-98) signed by the author of the
 *   call; `204`, `401` with the code of the refusal (also when the signer is not the author), `404 not_found`.
 *
 * The ceilings are global (one-time keys sign the envelopes: no budget per key, nothing tied to an address).
 */
fun Route.slotApi(store: SlotSignupStore?, signups: RateLimiter = RateLimiter(60, maxTrackedKeys = 1), withdrawals: RateLimiter = RateLimiter(120, maxTrackedKeys = 1)) {
    if (store == null) return
    post(SlotApi.SLOTS) {
        val body = call.readLimited() ?: return@post
        if (signups.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
        val request = runCatching { SlotApi.json.decodeFromString(SlotSignupRequest.serializer(), body) }.getOrNull()
            ?: return@post call.refuse(SlotSignupStore.MALFORMED)
        when (val result = withContext(Dispatchers.IO) { store.signUp(request) }) {
            is SlotSignupResult.Stored -> call.respond(HttpStatusCode.Created, SlotSignupAnswer(result.id, result.taken))
            is SlotSignupResult.Refused -> call.refuse(result.refusal)
        }
    }
    get(SlotApi.SLOTS) {
        val address = call.request.queryParameters["call"]?.takeIf { SlotSignupStore.parseAddress(it) != null }
            ?: return@get call.refuse(SlotSignupStore.BAD_CALL)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(SlotCounts(withContext(Dispatchers.IO) { store.counts(address) }))
    }
    get(SlotApi.SIGNUPS) {
        val address = call.request.queryParameters["call"]?.takeIf { SlotSignupStore.parseAddress(it) != null }
            ?: return@get call.refuse(SlotSignupStore.BAD_CALL)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(SlotEnvelopes(withContext(Dispatchers.IO) { store.envelopes(address) }))
    }
    val replay = Nip98ReplayGuard()
    post(SlotApi.REMOVE) {
        val body = call.readLimited() ?: return@post
        if (withdrawals.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
        val removal = runCatching { SlotApi.json.decodeFromString(SlotRemoval.serializer(), body) }.getOrNull()
            ?: return@post call.refuse(SlotSignupStore.MALFORMED)
        val callAddress = withContext(Dispatchers.IO) { store.callOf(removal.id) }
            ?: return@post call.refuse(SlotRefusal(404, "not_found", "No signup with this id."))
        val author = SlotSignupStore.parseAddress(callAddress)?.pubkey ?: return@post call.refuse(SlotSignupStore.BAD_CALL)
        val path = call.request.uri
        val now = java.time.Instant.now().epochSecond
        val result = Nip98.verify(
            call.request.headers[HttpHeaders.Authorization],
            { u -> u.substringAfter("://").substringAfter('/', "").let { "/$it" } == path },
            "POST",
            body.encodeToByteArray(),
            now,
            acceptsSigner = { it == author },
        )
        when (result) {
            is Nip98.Result.Invalid -> call.refuse(SlotRefusal(401, result.reason, "Only the author of the call removes a signup."))
            is Nip98.Result.Valid -> {
                if (!synchronized(replay) { replay.firstUse(result.eventId, now) }) return@post call.refuse(SlotRefusal(401, "replayed_authorization", "This authorization was already used."))
                withContext(Dispatchers.IO) { store.remove(removal.id) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
    post(SlotApi.WITHDRAW) {
        val body = call.readLimited() ?: return@post
        if (withdrawals.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
        val withdrawal = runCatching { SlotApi.json.decodeFromString(SlotWithdrawal.serializer(), body) }.getOrNull()
            ?: return@post call.refuse(SlotSignupStore.MALFORMED)
        if (withContext(Dispatchers.IO) { store.withdraw(withdrawal) }) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.refuse(SlotRefusal(404, "not_found", "No signup with this id and token."))
        }
    }
}

private const val GLOBAL = "*"

private suspend fun RoutingCall.readLimited(): String? {
    val declared = request.contentLength()
    val tooLarge = SlotRefusal(413, "too_large", "A signup is at most ${SlotSignupStore.MAX_REQUEST_BYTES} bytes.")
    if (declared != null && declared > SlotSignupStore.MAX_REQUEST_BYTES) {
        refuse(tooLarge)
        return null
    }
    val bytes = receiveChannel().readRemaining(SlotSignupStore.MAX_REQUEST_BYTES + 1).readByteArray()
    if (bytes.size > SlotSignupStore.MAX_REQUEST_BYTES) {
        refuse(tooLarge)
        return null
    }
    return bytes.decodeToString()
}

private suspend fun RoutingCall.limited() {
    response.headers.append(HttpHeaders.RetryAfter, "60")
    respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many signups; try again later."))
}

private suspend fun RoutingCall.refuse(refusal: SlotRefusal) = respond(HttpStatusCode.fromValue(refusal.status), ApiError(refusal.code, refusal.message))
