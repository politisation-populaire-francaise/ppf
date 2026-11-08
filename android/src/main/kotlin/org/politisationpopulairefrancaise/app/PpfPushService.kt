package org.politisationpopulairefrancaise.app

import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.notifications.PushEndpoint
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushMessage
import org.unifiedpush.android.connector.data.PushEndpoint as UnifiedPushEndpoint

/**
 * Receives what the distributor forwards. A message is an empty wake-up: its content is never
 * read, the device synchronizes and builds its notifications itself (D-130).
 */
class PpfPushService : PushService() {

    private val app: PpfApplication get() = application as PpfApplication

    override fun onMessage(message: PushMessage, instance: String) {
        val push = app.push ?: return
        app.scope.launch { runCatching { push.onMessage() } }
    }

    override fun onNewEndpoint(endpoint: UnifiedPushEndpoint, instance: String) {
        val scope = AndroidPushRegistrar.scopeOf(instance) ?: return
        val keys = endpoint.pubKeySet ?: return // Without RFC 8291 keys the server could not encrypt the wake-up.
        val push = app.push ?: return
        app.scope.launch { runCatching { push.onNewEndpoint(scope, PushEndpoint(endpoint.url, keys.pubKey, keys.auth)) } }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val scope = AndroidPushRegistrar.scopeOf(instance) ?: return
        app.push?.onRegistrationFailed(scope)
    }

    override fun onUnregistered(instance: String) {
        val scope = AndroidPushRegistrar.scopeOf(instance) ?: return
        val push = app.push ?: return
        app.scope.launch { runCatching { push.onUnregistered(scope) } }
    }
}
