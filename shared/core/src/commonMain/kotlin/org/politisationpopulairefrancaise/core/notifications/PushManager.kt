package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The platform's push system (UnifiedPush on Android; nothing on desktop yet; APNs wake-ups on
 * iOS later). One "instance" per [PushScope], so the two registrations use different addresses.
 * The answers come back asynchronously through [PushManager.onNewEndpoint] and friends.
 */
interface PushRegistrar {
    /** False when no distributor is installed (the user is then told how to get one, or stays without). */
    fun isAvailable(): Boolean

    fun register(scope: PushScope, vapidPublicKey: String)

    fun unregister(scope: PushScope)
}

/** Where the device is with one push registration, for the settings screen. */
enum class PushState { OFF, WAITING_FOR_ADDRESS, ACTIVE, NO_DISTRIBUTOR, SERVER_REFUSED, SERVER_UNREACHABLE }

/**
 * Keeps the device's push registrations in line with the [NotificationSettings]: asks the platform
 * for an address when a wake-up is enabled, gives it to the server, renews it at each start, and
 * deletes it everywhere when the wake-up is disabled. An empty wake-up only triggers [onWake]
 * (synchronization then [NotificationEngine.check]); its content is ignored.
 */
class PushManager(
    private val database: PpfDatabase,
    private val client: PushApi,
    private val registrar: PushRegistrar,
    private val signer: suspend () -> EventSigner?,
    private val onWake: suspend () -> Unit,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val queries get() = database.notificationsQueries
    private val states = mutableMapOf<PushScope, PushState>()

    fun state(scope: PushScope): PushState = states[scope] ?: if (registration(scope) != null) PushState.ACTIVE else PushState.OFF

    /** Applies [settings]: registers the wanted scopes (again, to renew them), unregisters the others. */
    suspend fun apply(settings: NotificationSettings) {
        for (scope in PushScope.entries) {
            val wanted = when (scope) {
                PushScope.PUBLIC -> settings.publicWake
                PushScope.MAILBOX -> settings.mailboxWake && signer() != null
            }
            if (wanted) start(scope) else stop(scope)
        }
    }

    private suspend fun start(scope: PushScope) {
        if (!registrar.isAvailable()) {
            states[scope] = PushState.NO_DISTRIBUTOR
            return
        }
        when (val key = client.vapidPublicKey()) {
            is PushResult.Done -> {
                states[scope] = if (registration(scope) != null) PushState.ACTIVE else PushState.WAITING_FOR_ADDRESS
                registrar.register(scope, key.value)
            }
            is PushResult.Refused -> states[scope] = PushState.SERVER_REFUSED
            is PushResult.Unavailable -> states[scope] = PushState.SERVER_UNREACHABLE
        }
    }

    private suspend fun stop(scope: PushScope) {
        val existing = registration(scope)
        registrar.unregister(scope)
        if (existing != null) {
            // Forgotten here even if the server cannot be reached: it deletes the address after 30 days anyway.
            client.unsubscribe(existing.subscription_id)
            queries.deleteRegistration(scope.key)
        }
        states[scope] = PushState.OFF
    }

    /** The platform gave a (new) address for [scope]: hand it to the server. */
    suspend fun onNewEndpoint(scope: PushScope, endpoint: PushEndpoint) {
        val account = if (scope == PushScope.MAILBOX) signer() ?: return else null
        val previous = registration(scope)
        when (val result = client.subscribe(scope, endpoint, account)) {
            is PushResult.Done -> {
                if (previous != null && previous.subscription_id != result.value) client.unsubscribe(previous.subscription_id)
                queries.setRegistration(scope.key, result.value, endpoint.url, clock())
                states[scope] = PushState.ACTIVE
            }
            is PushResult.Refused -> states[scope] = PushState.SERVER_REFUSED
            is PushResult.Unavailable -> states[scope] = PushState.SERVER_UNREACHABLE
        }
    }

    /** The platform dropped the registration (distributor removed, user unregistered elsewhere). */
    suspend fun onUnregistered(scope: PushScope) {
        registration(scope)?.let { client.unsubscribe(it.subscription_id) }
        queries.deleteRegistration(scope.key)
        states[scope] = PushState.OFF
    }

    fun onRegistrationFailed(scope: PushScope) {
        states[scope] = PushState.NO_DISTRIBUTOR
    }

    /** An empty wake-up arrived: the content is never read, the device simply synchronizes. */
    suspend fun onMessage() = onWake()

    /** Forgets every registration (the account is forgotten, or the user turned everything off). */
    suspend fun clear() {
        PushScope.entries.forEach { stop(it) }
    }

    private fun registration(scope: PushScope) = queries.getRegistration(scope.key).executeAsOneOrNull()
}
