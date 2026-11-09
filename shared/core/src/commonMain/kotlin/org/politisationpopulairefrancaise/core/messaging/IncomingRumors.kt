package org.politisationpopulairefrancaise.core.messaging

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.signing.EventKinds
import kotlin.concurrent.Volatile

/**
 * The rumors of authentic gift wraps addressed to the user that are not chat messages (decision
 * D-088): today the timers of AG-55 « Sans nouvelles de moi » ([EventKinds.TIMER]). A space
 * registers a handler for its kind ([register]); [MessagingService.receive] sets these rumors
 * aside instead of dropping them, and [MessagingService.pull] hands each one to its handler.
 *
 * A rumor of an [expected] kind that no handler can take right now (an Android wake-up that pulls
 * the messages before any screen of the application was opened in this process) is "held": the
 * pull then keeps its cursor where it was, so that the next pull fetches that wrap again, once the
 * handler is registered. A handler that throws holds its rumor the same way. Any other kind is
 * ignored, as before D-088.
 *
 * Registration happens on the main thread, dispatch on a worker: the map is replaced, never
 * modified in place.
 */
class IncomingRumors(private val expected: Set<Int> = emptySet()) {

    @Volatile
    private var handlers: Map<Int, suspend (Rumor, String) -> Unit> = emptyMap()

    /**
     * Hands the rumors of [kind] to [handler] (the rumor, already checked by [GiftWrap.unwrap], and
     * the user's public key in hexadecimal) until the returned function is called. A later
     * registration for the same kind replaces this one.
     */
    fun register(kind: Int, handler: suspend (rumor: Rumor, recipient: String) -> Unit): () -> Unit {
        require(kind != EventKinds.CHAT_MESSAGE) { "Chat messages are stored by MessagingService itself" }
        handlers = handlers + (kind to handler)
        return {
            if (handlers[kind] === handler) handlers = handlers - kind
        }
    }

    /** Whether a rumor of [kind] is set aside by [MessagingService.receive] rather than dropped. */
    fun takes(kind: Int): Boolean = kind in expected || kind in handlers

    /**
     * Gives each rumor to the handler of its kind. Returns how many were held: no handler for an
     * expected kind, or a handler that failed. Rumors of other kinds are simply dropped.
     */
    suspend fun dispatch(rumors: List<Rumor>, recipient: String): Int {
        var held = 0
        for (rumor in rumors) {
            val handler = handlers[rumor.kind]
            if (handler == null) {
                if (rumor.kind in expected) held++
                continue
            }
            try {
                handler(rumor, recipient)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                held++
            }
        }
        return held
    }

    companion object {
        /**
         * The registry of the application process, used by every [MessagingService] by default: the
         * screens register their handlers here, the background round of the notifications pulls with it.
         */
        val Process: IncomingRumors = IncomingRumors(expected = setOf(EventKinds.TIMER))
    }
}
