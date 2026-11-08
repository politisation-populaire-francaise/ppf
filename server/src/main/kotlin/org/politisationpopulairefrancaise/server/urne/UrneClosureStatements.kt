package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.urne.UrneResults
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.SigningStatus
import org.politisationpopulairefrancaise.server.signing.SigningTicket
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * The closing statements of the ballot boxes, signed by the platform (contract of the signing service, use
 * `decider.urn_closure`, automatic, no approval; docs/FORMATS.md, section 4 decies). For each closed box:
 * ```
 * {"kind":30078,"tags":[["d","ppf:urne-cloture:<n>"],["ppf-urne","<n>"],["ppf-closing-sha256","<SHA-256 of the closing document>"]],
 *  "content":"{\"v\":1,\"urne\":<n>,\"id\":\"…\",\"subject\":\"…\",\"results\":{…}}"}
 * ```
 * The hash covers the exact bytes of `GET /api/v1/urnes/{n}/closing` ([UrneService.closingDocument]): the
 * applications check the document they download against the signature, then recount it.
 *
 * The signing service never signs a second statement for the same `d` (`urn_already_closed`), even years
 * later, and the applications keep the FIRST one (`UrnClosures.choose`). So the signed statement is kept in
 * the row of its box first ([UrneStore.recordClosureStatement]), then published through the events table,
 * as it is: it is never lost between the two. An `urn_already_closed` answer means it was signed before and
 * lost here: no retry, an alert for the team ([UrneStore.refusedClosures]). Run by the task of the boxes.
 */
class UrneClosureStatements(
    private val store: UrneStore,
    private val service: UrneService,
    private val signer: PlatformSigner,
    private val events: EventRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(UrneClosureStatements::class.java)

    /** The requests waiting for the signing service, by box. */
    private val pending = ConcurrentHashMap<Long, SigningTicket>()

    /** One pass: follows the requests, asks for the new ones, publishes the signed statements. Returns how many were published. */
    fun runDue(): Int = runBlocking {
        for (number in store.closuresToSign(BATCH)) {
            val ticket = pending[number]
            if (ticket == null) {
                request(number)?.let { pending[number] = it }
                continue
            }
            when (val status = signer.status(ticket)) {
                SigningStatus.Pending -> Unit
                is SigningStatus.Signed -> {
                    pending.remove(number)
                    if (accepts(number, status.event)) store.recordClosureStatement(number, status.event.toJson())
                }
                is SigningStatus.Refused -> {
                    pending.remove(number)
                    if (status.code == URN_ALREADY_CLOSED) {
                        store.markClosureRefused(number, status.code)
                        log.warn("Voting: the closing statement of box $number was signed before and is not here; the published one stands (alert for the team).")
                    } else if (status.code !in RETRIED) {
                        log.warn("Voting: the closing statement of box $number was refused (${status.code}); asked again later.")
                    }
                }
            }
        }
        var published = 0
        for ((number, json) in store.closuresToPublish(BATCH)) {
            val event = runCatching { SignedEvent.fromJson(json) }.getOrNull() ?: continue
            when (events.store(event)) {
                StoreOutcome.STORED, StoreOutcome.DUPLICATE -> {
                    store.markClosurePublished(number)
                    published++
                }
                // A newer statement of the same address cannot replace the first one for the applications (UrnClosures):
                // ours stays kept in the box's row, and it is the one the applications keep.
                StoreOutcome.SUPERSEDED -> store.markClosurePublished(number)
            }
        }
        published
    }

    /** The unsigned statement of the closed box [number], asked for signature; null when nothing can be asked now. */
    private suspend fun request(number: Long): SigningTicket? {
        val key = signer.currentKey(SigningUse.DECIDER_URN_CLOSURE) ?: return null
        val unsigned = statementOf(number, key) ?: return null
        return signer.request(unsigned, SigningUse.DECIDER_URN_CLOSURE)
    }

    /** The closing statement of [number] for the key [pubkey], or null when the box is not closed. */
    fun statementOf(number: Long, pubkey: String): UnsignedEvent? {
        val urne = store.urne(number)?.takeIf { it.closed } ?: return null
        val document = service.closingDocument(number)
        val results = service.finalResults(number) ?: return null
        val content = buildJsonObject {
            put("v", 1)
            put("urne", number)
            put("id", urne.id)
            put("subject", urne.subject)
            put("results", Json.encodeToJsonElement(UrneResults.serializer(), results))
        }.toString()
        return UnsignedEvent.create(
            org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(pubkey),
            EventKinds.APP_DATA,
            clock.instant().epochSecond,
            listOf(listOf("d", identifierOf(number)), listOf(TAG_URNE, number.toString()), listOf(TAG_SHA256, sha256Hex(document))),
            content,
        )
    }

    /** The signed statement is the one asked: same box, same document. */
    private fun accepts(number: Long, event: SignedEvent): Boolean =
        event.kind == EventKinds.APP_DATA && event.tagValues("d") == listOf(identifierOf(number)) &&
            event.tagValues(TAG_SHA256) == listOf(sha256Hex(service.closingDocument(number))) && event.verify()

    companion object {
        const val PREFIX: String = "ppf:urne-cloture:"
        const val TAG_URNE: String = "ppf-urne"
        const val TAG_SHA256: String = "ppf-closing-sha256"
        const val URN_ALREADY_CLOSED: String = "urn_already_closed"
        private const val BATCH = 20

        /** Passing refusals, asked again at the next pass without an alert. */
        private val RETRIED = setOf("rate_limited", "signer_unavailable", "unknown_ticket", "no_active_key")

        fun identifierOf(number: Long): String = "$PREFIX$number"

        fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }
}
