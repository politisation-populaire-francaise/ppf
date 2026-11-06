package org.politisationpopulairefrancaise.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.groups.MailboxRetention
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the user's mailbox setting stands. */
enum class MailboxStatus {
    /** Never chosen: the server applies its default, three months. */
    DEFAULT,

    /** Accepted by the server. */
    SAVED,

    /** Signed and kept on the device; it leaves with the next synchronization. */
    PENDING,

    /** The server refused the last choice; the previous one still applies. */
    REJECTED,

    /** No local database and no server: nothing can be kept. */
    UNAVAILABLE,
}

/**
 * How long the server keeps the encrypted envelopes addressed to the user (decision D-028): a
 * signed NIP-78 event (kind 30078, `d` = `ppf:mailbox`) that the user publishes and the server
 * reads. Protection by default: without a choice the server keeps them three months, the minimum.
 * The setting is a publication like any other: signed on the device, kept there until the server
 * accepts it.
 */
class MailboxSetting(
    private val store: LocalEventStore?,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** The account whose setting is shown (public key, lowercase hex), or null without an account. */
    var account: String? by mutableStateOf(null)
        private set

    var retention: MailboxRetention by mutableStateOf(MailboxRetention.DEFAULT)
        private set

    var status: MailboxStatus by mutableStateOf(MailboxStatus.DEFAULT)
        private set

    var saving: Boolean by mutableStateOf(false)
        private set

    /** Shows the setting of [publicKeyHex] (null: no account) and fetches it from the server once. */
    fun showAccount(publicKeyHex: String?) {
        if (publicKeyHex == account) return
        account = publicKeyHex
        retention = MailboxRetention.DEFAULT
        status = if (store == null && remote == null) MailboxStatus.UNAVAILABLE else MailboxStatus.DEFAULT
        val key = publicKeyHex ?: return
        scope.launch {
            loadLocal(key)
            pull(key)
        }
    }

    /**
     * Signs and publishes [choice] for the account of [signer]. Without an answer from the server it
     * waits on the device and leaves with the next synchronization; when the server refuses it,
     * the previous setting is kept.
     */
    suspend fun choose(signer: EventSigner, choice: MailboxRetention) {
        val key = signer.publicKey.hex
        if (key != account || saving) return
        saving = true
        try {
            val previous = store?.let { local -> withContext(Dispatchers.Default) { latest(local, key) } }
            // Strictly newer than the setting it replaces, even when changed twice in one second.
            val createdAt = maxOf(clock(), (previous?.event?.createdAt ?: 0L) + 1)
            val event = signer.sign(MailboxRetention.build(signer.publicKey, choice, createdAt))
            store?.let { local -> withContext(Dispatchers.Default) { local.store(event, pending = true) } }
            retention = choice
            status = MailboxStatus.PENDING
            val remote = remote ?: return
            when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> {
                    store?.let { local -> withContext(Dispatchers.Default) { local.markPublished(event.id) } }
                    status = MailboxStatus.SAVED
                }
                is PublicationResult.Unavailable -> status = MailboxStatus.PENDING
                is PublicationResult.Rejected -> {
                    store?.let { local ->
                        withContext(Dispatchers.Default) {
                            local.delete(event.id)
                            previous?.let { local.store(it.event, pending = it.pending) }
                        }
                    }
                    retention = previous?.let { MailboxRetention.from(it.event) } ?: MailboxRetention.DEFAULT
                    status = MailboxStatus.REJECTED
                }
            }
        } finally {
            saving = false
        }
    }

    private data class Stored(val event: SignedEvent, val pending: Boolean)

    /** The newest setting of [key] on the device, and whether it still waits for the server. */
    private fun latest(store: LocalEventStore, key: String): Stored? {
        val event = store.byTag(listOf(MailboxRetention.KIND), "d", MailboxRetention.IDENTIFIER, limit = SETTINGS_SCAN)
            .filter { it.pubkey == key && MailboxRetention.from(it) != null }
            .maxWithOrNull(compareBy<SignedEvent> { it.createdAt }.thenByDescending { it.id })
            ?: return null
        return Stored(event, pending = store.pending().any { it.id == event.id })
    }

    private suspend fun loadLocal(key: String) {
        val store = store ?: return
        val stored = withContext(Dispatchers.Default) { latest(store, key) } ?: return
        if (account != key) return
        retention = MailboxRetention.from(stored.event) ?: MailboxRetention.DEFAULT
        status = if (stored.pending) MailboxStatus.PENDING else MailboxStatus.SAVED
    }

    /** Sends a setting that waits on the device, then reads the one the server holds (set on another device, perhaps). */
    private suspend fun pull(key: String) {
        val remote = remote ?: return
        val store = store
        if (store != null) {
            val waiting = withContext(Dispatchers.Default) { latest(store, key) }?.takeIf { it.pending }
            if (waiting != null) {
                when (remote.publish(waiting.event)) {
                    is PublicationResult.Accepted -> withContext(Dispatchers.Default) { store.markPublished(waiting.event.id) }
                    is PublicationResult.Rejected -> withContext(Dispatchers.Default) { store.delete(waiting.event.id) }
                    is PublicationResult.Unavailable -> return
                }
            }
        }
        val filter = RemoteFilter(kinds = listOf(MailboxRetention.KIND), authors = listOf(key), identifiers = listOf(MailboxRetention.IDENTIFIER), limit = 1)
        val result = remote.fetch(filter) as? FetchResult.Events ?: return
        val fetched = result.events.firstOrNull { it.pubkey == key && it.verify() && MailboxRetention.from(it) != null } ?: return
        if (store != null) {
            withContext(Dispatchers.Default) { store.store(fetched) }
            loadLocal(key)
        } else if (account == key) {
            retention = MailboxRetention.from(fetched) ?: MailboxRetention.DEFAULT
            status = MailboxStatus.SAVED
        }
    }

    private companion object {
        /** Settings of every account that used this device; a handful at most. */
        const val SETTINGS_SCAN = 50
    }
}

/** The mailbox setting of the current account, for the whole run of the application. */
@Composable
fun rememberMailboxSetting(dependencies: AppDependencies, accountKey: String?): MailboxSetting {
    val scope = rememberCoroutineScope()
    val setting = remember(dependencies) { MailboxSetting(dependencies.contentStore, dependencies.remote, scope) }
    LaunchedEffect(setting, accountKey) { setting.showAccount(accountKey) }
    return setting
}
