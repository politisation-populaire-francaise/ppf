package org.politisationpopulairefrancaise.app

import android.content.Context
import org.politisationpopulairefrancaise.core.notifications.PushRegistrar
import org.politisationpopulairefrancaise.core.notifications.PushScope
import org.unifiedpush.android.connector.UnifiedPush

/**
 * UnifiedPush on Android: the registrations go through the "distributor" application the user
 * installed (ntfy, set to our own server); no Google service is involved. One UnifiedPush instance
 * per [PushScope], so the anonymous registration and the mailbox one never share an address.
 */
class AndroidPushRegistrar(private val context: Context) : PushRegistrar {

    override fun isAvailable(): Boolean = UnifiedPush.getDistributors(context).isNotEmpty()

    override fun register(scope: PushScope, vapidPublicKey: String) {
        // Keeps the distributor already chosen, or takes the only / default one without asking.
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
            if (success) UnifiedPush.register(context, instanceOf(scope), null, vapidPublicKey)
        }
    }

    override fun unregister(scope: PushScope) {
        if (UnifiedPush.getAckDistributor(context) != null) UnifiedPush.unregister(context, instanceOf(scope))
    }

    companion object {
        fun instanceOf(scope: PushScope): String = "ppf-" + scope.key

        fun scopeOf(instance: String): PushScope? = PushScope.entries.firstOrNull { instanceOf(it) == instance }
    }
}
