package org.politisationpopulairefrancaise.core.reminders

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationCenterStore
import org.politisationpopulairefrancaise.core.notifications.NotificationEngine
import org.politisationpopulairefrancaise.core.notifications.NotificationSettings
import org.politisationpopulairefrancaise.core.notifications.SystemNotification
import org.politisationpopulairefrancaise.core.notifications.SystemNotifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The personal reminders of the device (S-98, first use DE-28): kept on the device, notified once their time has come. */
class ReminderStoreTest {
    private var now = 1_800_000_000L
    private val database = JvmDatabase.inMemory()
    private val store = ReminderStore(database) { now }

    private fun reminder(key: String, dueAt: Long, owner: String = OWNER, category: NotificationCategory = NotificationCategory.VOTES) =
        Reminder("$owner/$key", owner, category, "[EXEMPLE] Rappel $key", "Détail $key", "ppf:decider/election/exemple", dueAt, now)

    @Test
    fun aReminderReachesTheCentreOnceItsTimeHasComeAndOnlyOnce() = runBlocking {
        val posted = mutableListOf<SystemNotification>()
        val center = NotificationCenterStore(database, clock = { now })
        var settings = NotificationSettings(enabled = setOf(NotificationCategory.VOTES))
        val engine = NotificationEngine(
            settings = { settings },
            center = center,
            sources = ReminderStore.notificationSources(database) { now },
            notifier = object : SystemNotifier {
                override fun post(notification: SystemNotification) {
                    posted += notification
                }
            },
            clock = { now },
        )
        store.schedule(reminder("veille-1", now + 3_600))
        store.schedule(reminder("rassemblement", now + 7_200, owner = "agir:exemple", category = NotificationCategory.MOBILISATIONS))
        assertEquals(now + 3_600, store.nextDueAfter())

        assertEquals(0, engine.check().added, "not due yet")
        now += 3_601
        val report = engine.check()
        assertEquals(1, report.added)
        assertEquals(listOf(NotificationCategory.VOTES), report.posted.map { it.category })
        assertEquals(null, report.posted.single().title, "no detail by default: the system shows a generic text")
        val entry = center.entries().single()
        assertEquals("[EXEMPLE] Rappel veille-1", entry.title)
        assertEquals("ppf:decider/election/exemple", entry.target)
        assertEquals(0, engine.check().added, "never twice")

        // A category switched off is not read: the reminder of the mobilisations waits in the store.
        now += 3_600
        assertEquals(0, engine.check().added)
        assertEquals(1, posted.size)
        // Switched on, a reminder whose time came while it was off is not shown late.
        settings = settings.copy(enabled = settings.enabled + NotificationCategory.MOBILISATIONS)
        engine.skipToNow(NotificationCategory.MOBILISATIONS)
        assertEquals(0, engine.check().added)
    }

    @Test
    fun aNewPlanningMovesTheFutureRemindersAndNeverBringsBackOneTheUserRemoved() {
        store.setPlan(OWNER, setOf("veille", "procuration"))
        store.schedule(reminder("deja-passe", now - 10))
        store.replaceFuture(OWNER, listOf(reminder("procuration-1", now + 100), reminder("veille-1", now + 200)))
        assertEquals(listOf("$OWNER/procuration-1", "$OWNER/veille-1"), store.upcomingOf(OWNER).map { it.id })

        store.remove("$OWNER/procuration-1")
        assertEquals(setOf("$OWNER/procuration-1"), store.plan(OWNER)?.removed)
        assertEquals(setOf("procuration", "veille"), store.plan(OWNER)?.kinds, "removing one reminder keeps the kinds chosen")

        // A new decree moves the eve: the reminder moves, the one removed stays removed, the past one stays as it was.
        store.replaceFuture(OWNER, listOf(reminder("procuration-1", now + 100), reminder("veille-1", now + 300), reminder("ailleurs", now + 50, owner = "autre")))
        assertEquals(listOf("$OWNER/veille-1" to now + 300), store.upcomingOf(OWNER).map { it.id to it.dueAt })
        assertEquals(listOf("$OWNER/veille-1"), store.upcoming().map { it.id }, "a reminder of another owner is not written by this planning")
        assertEquals(now + 300, store.nextDueAfter())

        // Changing the kinds keeps the reminders removed one by one.
        store.setPlan(OWNER, setOf("veille"))
        assertEquals(setOf("$OWNER/procuration-1"), store.plan(OWNER)?.removed)

        // Thirty days later, the past reminders are dropped by the next planning.
        now += ReminderStore.KEEP_PAST_SECONDS + 400
        store.replaceFuture(OWNER, emptyList())
        assertNull(store.nextDueAfter(Long.MIN_VALUE))
    }

    @Test
    fun theFollowedElectionsAreFoundByTheirPrefixAndTheQuickEraseForgetsEverything() = runBlocking {
        store.setPlan("decider:election:a", setOf("veille"))
        store.setPlan("decider:election:b", emptySet())
        store.setPlan("DECIDER:election:c", setOf("veille"))
        store.setPlan("agir:x", setOf("rappel"))
        store.replaceFuture("decider:election:a", listOf(reminder("veille-1", now + 100, owner = "decider:election:a")))
        assertEquals(listOf("decider:election:a", "decider:election:b"), store.plans("decider:election:").map { it.owner }.sorted())
        assertEquals(emptySet(), store.plan("decider:election:b")?.kinds, "following without any reminder is still a plan")

        store.dropPlan("decider:election:a")
        assertNull(store.plan("decider:election:a"))
        assertTrue(store.upcomingOf("decider:election:a").isEmpty())

        store.schedule(reminder("veille-1", now + 100))
        assertEquals(ReminderStore.ERASABLE_ID, store.erasableId)
        store.eraseFromDevice()
        assertTrue(store.upcoming().isEmpty())
        assertTrue(store.plans("").isEmpty())
        assertNull(store.nextDueAfter(Long.MIN_VALUE))
        // Erasing again (a retry after another part failed) does nothing more and does not fail.
        store.eraseFromDevice()
    }

    private companion object {
        const val OWNER = "decider:election:exemple"
    }
}
