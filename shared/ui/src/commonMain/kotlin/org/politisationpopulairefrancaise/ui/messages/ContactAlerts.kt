package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.AccountRecoveryClient
import org.politisationpopulairefrancaise.core.identity.CompromiseNotice
import org.politisationpopulairefrancaise.core.identity.NoticeCheck
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The notices of compromise (kind 1777, S-146) that concern the user's contacts: asked from the
 * server in small mixed batches (the server never receives the list of contacts at once), checked
 * on the device against the first commitment of each account, and kept on the device with the
 * other public events ([store]), so that the alert survives a restart and works offline.
 * The successor a notice names is only ever proposed, never followed automatically.
 */
class ContactAlerts(
    private val store: LocalEventStore?,
    private val remote: RemoteEvents?,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** The server is asked again at most this often for the same contacts. */
    private val minIntervalSeconds: Long = DEFAULT_INTERVAL_SECONDS,
) {
    /** The checked notices, by compromised key (lowercase hex). */
    var notices: Map<String, CompromiseNotice> by mutableStateOf(emptyMap())
        private set

    private val round = Mutex()
    private var loaded: Set<String> = emptySet()
    private var lastAsked: Long? = null
    private var askedKeys: Set<String> = emptySet()

    fun noticeFor(key: String): CompromiseNotice? = notices[key]

    /**
     * Whether [message] arrived from a compromised key and is dated after "compromised since": it
     * may come from the person who holds the key now.
     */
    fun isSuspect(message: ChatMessage): Boolean = notices[message.author]?.let { message.createdAt >= it.since } == true

    /** Reads the notices kept on the device for [keys]. */
    suspend fun load(keys: Collection<String>) {
        val store = store ?: return
        val unread = keys.toSet() - loaded
        if (unread.isEmpty()) return
        val found = withContext(Dispatchers.Default) {
            unread.mapNotNull { key -> localNotice(store, key)?.let { key to it } }.toMap()
        }
        loaded = loaded + unread
        if (found.isNotEmpty()) notices = notices + found
    }

    /**
     * Asks the server for the notices of [keys], unless it was asked for the same keys less than
     * [minIntervalSeconds] ago; returns the keys newly found compromised.
     */
    suspend fun refresh(keys: Collection<String>, force: Boolean = false): Set<String> {
        val remote = remote ?: return emptySet()
        val wanted = keys.toSet()
        if (wanted.isEmpty()) return emptySet()
        return round.withLock {
            val now = clock()
            val recent = lastAsked?.let { now - it < minIntervalSeconds } == true
            if (!force && recent && askedKeys.containsAll(wanted)) return@withLock emptySet()
            val found = try {
                AccountRecoveryClient(remote, clock).noticesFor(wanted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return@withLock emptySet()
            lastAsked = now
            askedKeys = wanted
            store?.let { local ->
                withContext(Dispatchers.Default) {
                    found.values.forEach { checked ->
                        local.store(checked.commitment)
                        local.store(checked.event)
                    }
                }
            }
            val added = found.keys - notices.keys
            notices = notices + found.mapValues { it.value.notice }
            added
        }
    }

    /** Forgets everything (the account was erased from the device). */
    fun clear() {
        notices = emptyMap()
        loaded = emptySet()
        lastAsked = null
        askedKeys = emptySet()
    }

    private fun localNotice(store: LocalEventStore, key: String): CompromiseNotice? {
        val events = store.byTag(listOf(AccountRecovery.NOTICE_KIND), "p", key, limit = 10)
        val now = clock()
        return events.sortedBy { it.createdAt }.firstNotNullOfOrNull { event ->
            val commitmentId = event.firstTagValue("e") ?: return@firstNotNullOfOrNull null
            val commitment = store.find(commitmentId) ?: return@firstNotNullOfOrNull null
            (AccountRecovery.checkNotice(event, commitment, now) as? NoticeCheck.Valid)?.notice?.takeIf { it.compromised == key }
        }
    }

    companion object {
        /** Ten minutes: the conversations screen refreshes every 20 seconds, the notices need not. */
        const val DEFAULT_INTERVAL_SECONDS: Long = 10L * 60
    }
}
