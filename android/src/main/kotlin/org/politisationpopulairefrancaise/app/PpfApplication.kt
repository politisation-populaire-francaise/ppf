package org.politisationpopulairefrancaise.app

import android.app.Application
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.identity.AndroidIdentityStore
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.local.AndroidDatabase
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.notifications.Notifications
import org.politisationpopulairefrancaise.core.notifications.PushClient
import org.politisationpopulairefrancaise.core.notifications.PushManager
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.urne.HttpUrneRemote

/**
 * What lives as long as the application process, not only its screen: the local database, the
 * connection to the server and the notifications, which an empty wake-up from the server may
 * start while no screen is open (D-130, docs/integration/notifications.md).
 */
class PpfApplication : Application() {

    lateinit var database: PpfDatabase
        private set
    lateinit var identityStore: IdentityStore
        private set

    /** The server address fixed at build time (see build.gradle.kts), HTTPS only; null without server. */
    val serverUrl: String? by lazy {
        BuildConfig.PPF_SERVER_URL.trim().takeIf { it.startsWith("https://") }?.trimEnd('/')
    }

    /**
     * Our own ntfy server, next to the platform's server: "push." before its name
     * (push.politisation-populaire-francaise.org, push.preprod.politisation-populaire-francaise.org).
     */
    val pushServer: String? by lazy {
        serverUrl?.let { "https://push." + it.removePrefix("https://").substringBefore('/') }
    }

    /** One connection for the whole process; null when the build has no server. */
    val httpClient: HttpClient? by lazy { serverUrl?.let { HttpClient() } }

    val remote: ServerPublicationPort? by lazy {
        val url = serverUrl ?: return@lazy null
        val client = httpClient ?: return@lazy null
        ServerPublicationPort(client, url)
    }

    /**
     * The ballot-box API (D-110): the account's requests on [httpClient], the anonymous ballots on a
     * second client of their own, never used with the account. Null without server.
     */
    val urneRemote: HttpUrneRemote? by lazy {
        val url = serverUrl ?: return@lazy null
        val client = httpClient ?: return@lazy null
        HttpUrneRemote(client, HttpClient(), url) { currentEpochSeconds() }
    }

    /** Work started by a wake-up or a registration answer, outliving the screen that asked it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var notifications: Notifications
        private set

    /** Null without a server: there is then nobody to wake the device. */
    var push: PushManager? = null
        private set

    override fun onCreate() {
        super.onCreate()
        database = AndroidDatabase.open(applicationContext)
        identityStore = AndroidIdentityStore(applicationContext)
        val notifier = AndroidSystemNotifier(applicationContext).also { it.createChannels() }
        notifications = Notifications(database, identityStore, remote, notifier)
        val url = serverUrl
        val client = httpClient
        if (url != null && client != null) {
            push = PushManager(
                database = database,
                client = PushClient(client, url),
                registrar = AndroidPushRegistrar(applicationContext),
                signer = { identityStore.load()?.keys()?.let { LocalMessagingSigner(it) } },
                onWake = { notifications.onWake() },
            )
        }
        // Renews the registrations the user enabled (the server forgets them after 30 days without renewal).
        scope.launch { runCatching { push?.apply(notifications.settings.load()) } }
    }
}
