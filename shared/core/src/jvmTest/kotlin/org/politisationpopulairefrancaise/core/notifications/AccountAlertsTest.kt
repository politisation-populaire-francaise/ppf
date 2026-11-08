package org.politisationpopulairefrancaise.core.notifications

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The category « Mon compte » (S-146, decision of the team lead of 8 November 2026). */
class AccountAlertsTest {

    private val database = JvmDatabase.inMemory()
    private var now = 1_800_000_000L

    private class Recorder : SystemNotifier {
        val posted = mutableListOf<SystemNotification>()
        override fun post(notification: SystemNotification) {
            posted += notification
        }
    }

    private class Fixed(override val name: String, val items: MutableList<NotificationCandidate> = mutableListOf()) : NotificationSource {
        override suspend fun collect(after: Long?): List<NotificationCandidate> = items.toList()
    }

    private fun account(id: String, createdAt: Long) = NotificationCandidate(id, NotificationCategory.ACCOUNT, AccountAlerts.PHRASE_CHECK, createdAt = createdAt)

    @Test
    fun theAccountCategoryIsOffByDefaultButAlwaysReachesTheCentre() = runTest {
        assertFalse(NotificationSettings.MOST_PROTECTIVE.isEnabled(NotificationCategory.ACCOUNT))
        assertTrue(NotificationCategory.ACCOUNT.alwaysInCentre)
        assertTrue(NotificationCategory.entries.filter { it != NotificationCategory.ACCOUNT }.none { it.alwaysInCentre })

        val settings = NotificationSettingsStore(database)
        val center = NotificationCenterStore(database, clock = { now })
        val recorder = Recorder()
        // Old, but a current matter of the account: read from the start the first time.
        val source = Fixed("account-test", mutableListOf(account("a1", createdAt = now - 20 * 24 * 3600)))
        val engine = NotificationEngine(settings::load, center, listOf(NotificationCategory.ACCOUNT to source), recorder, clock = { now })

        assertEquals(1, engine.check().added)
        assertEquals(listOf("a1"), center.entries().map { it.id })
        assertTrue(recorder.posted.isEmpty(), "off: the centre only, nothing shown by the system")

        // Seen once: never again, even when the user deleted it from the centre.
        center.delete("a1")
        now += 60
        assertEquals(0, engine.check().added)

        // Once enabled, a new alert is shown by the system, with its fixed code as title even without details.
        settings.save(NotificationSettings(enabled = setOf(NotificationCategory.ACCOUNT)))
        engine.skipToNow(NotificationCategory.ACCOUNT)
        source.items += account("a2", createdAt = now - 1)
        engine.check()
        assertEquals(listOf(SystemNotification(NotificationCategory.ACCOUNT, count = 1, title = AccountAlerts.PHRASE_CHECK)), recorder.posted)
    }

    @Test
    fun aDeliveredAlertIsKeptOnceAndShownOnlyWhenEnabled() {
        val settings = NotificationSettingsStore(database)
        val center = NotificationCenterStore(database, clock = { now })
        val recorder = Recorder()
        val engine = NotificationEngine(settings::load, center, emptyList(), recorder, clock = { now })
        val key = IdentityKeys.generate().publicKey.hex

        assertEquals(1, engine.deliver(listOf(AccountAlerts.ownCompromised(key, now))).added)
        assertEquals(0, engine.deliver(listOf(AccountAlerts.ownCompromised(key, now + 30))).added)
        assertTrue(recorder.posted.isEmpty())
        val entry = center.entries().single()
        assertEquals(AccountAlerts.OWN_COMPROMISED, entry.title)
        assertEquals(AccountAlerts.MY_SPACE_TARGET, entry.target)

        // Another category is never forced into the centre while it is off.
        val vote = NotificationCandidate("v1", NotificationCategory.VOTES, "[EXEMPLE] Vote", createdAt = now)
        assertEquals(0, engine.deliver(listOf(vote)).added)

        settings.save(NotificationSettings(enabled = setOf(NotificationCategory.ACCOUNT)))
        engine.deliver(listOf(AccountAlerts.ownCompromised(IdentityKeys.generate().publicKey.hex, now)))
        assertEquals(NotificationCategory.ACCOUNT, recorder.posted.single().category)
    }

    @Test
    fun thePhraseReminderComesOnceItsDateHasCome() = runTest {
        val phrase = RecoveryPhrase.generate()
        val keys = phrase.deriveKeys()
        val due = now + 3600
        val store = InMemoryIdentityStore(StoredIdentity.of(keys, phrase, verified = false, checkDueAt = due))
        val source = PhraseReminderSource(store, clock = { now })

        assertTrue(source.collect(null).isEmpty(), "not yet due")
        now = due
        val reminder = source.collect(null).single()
        assertEquals("${AccountAlerts.PHRASE_CHECK}:$due", reminder.id)
        assertEquals(NotificationCategory.ACCOUNT, reminder.category)
        assertEquals(AccountAlerts.MY_SPACE_TARGET, reminder.target)
        assertEquals(due, reminder.createdAt)

        // Checked, or a record without a date (written before S-146): no reminder here.
        store.save(StoredIdentity.of(keys, phrase, verified = true))
        assertTrue(source.collect(null).isEmpty())
        store.save(StoredIdentity.of(keys, phrase, verified = false))
        assertTrue(source.collect(null).isEmpty())
        store.clear()
        assertTrue(source.collect(null).isEmpty())
    }

    @Test
    fun theNoticesKeptOnTheDeviceBecomeAlerts() = runTest {
        val me = IdentityKeys.generate().publicKey
        val contactPhrase = RecoveryPhrase.generate()
        val contact = contactPhrase.deriveKeys()
        val events = LocalEventStore(database, clock = { now })
        // A conversation where the contact wrote: the alert opens it.
        val messages = MessageStore(database, clock = { now })
        messages.store(ChatMessage.compose(contact.publicKey, listOf(me), "[EXEMPLE] Bonjour", createdAt = now - 100))
        val (commitment, notice) = noticeOf(contactPhrase, createdAt = now - 50)

        // The notice alone, without the commitment it names, is not trusted.
        events.store(notice)
        val source = CompromiseNoticesSource(database, me = { me.hex }, clock = { now })
        assertTrue(source.collect(null).isEmpty())

        events.store(commitment)
        val alert = source.collect(null).single()
        assertEquals("${AccountAlerts.CONTACT_COMPROMISED}:${notice.id}", alert.id)
        assertEquals(AccountAlerts.CONTACT_COMPROMISED, alert.title)
        assertEquals(contact.publicKey.hex, alert.detail)
        assertTrue(alert.target!!.startsWith("ppf:messages/"))
        assertEquals(now, alert.createdAt, "dated by its reception on the device")
        assertTrue(source.collect(now).isEmpty(), "already read")
        assertTrue(CompromiseNoticesSource(database, me = { null }).collect(null).isEmpty(), "no account, no alert")

        // A notice about the device's own account is its own alert, the same as the one a screen delivers.
        val own = CompromiseNoticesSource(database, me = { contact.publicKey.hex }, clock = { now }).collect(null).single()
        assertEquals(AccountAlerts.ownCompromised(contact.publicKey.hex, now).id, own.id)
        assertEquals(AccountAlerts.OWN_COMPROMISED, own.title)
        assertNull(own.detail)
    }

    @Test
    fun theNotificationsOfTheDeviceReadTheAccountWhileEverythingIsOff() = runTest {
        val phrase = RecoveryPhrase.generate()
        // Notifications runs on the real clock: a reminder due a minute ago.
        val identity = InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase, verified = false, checkDueAt = currentEpochSeconds() - 60))
        val recorder = Recorder()
        val notifications = Notifications(database, identity, remote = null, notifier = recorder)

        notifications.onWake()
        assertEquals(listOf(AccountAlerts.PHRASE_CHECK), notifications.center.entries().map { it.title })
        assertTrue(recorder.posted.isEmpty())
    }

    private suspend fun noticeOf(phrase: RecoveryPhrase, createdAt: Long): Pair<SignedEvent, SignedEvent> {
        val account = LocalEventSigner(phrase.deriveKeys())
        val recoveryKeys = phrase.deriveRecoveryKeys()
        val commitment = AccountRecovery.signCommitment(account, recoveryKeys.publicKey, createdAt = createdAt - 1000)
        val notice = AccountRecovery.signNotice(LocalEventSigner(recoveryKeys), AccountRecovery.parseCommitment(commitment)!!, createdAt = createdAt)
        return commitment to notice
    }
}
