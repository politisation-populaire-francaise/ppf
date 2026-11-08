package org.politisationpopulairefrancaise.server.api

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryCommitment
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.GroupOrphansQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupOrphans
import org.politisationpopulairefrancaise.server.module
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rooms left without administrator by a compromised account (S-146, docs/integration/salons-reprise.md). */
class GroupOrphansQueueTest {

    private val admin = LocalEventSigner(IdentityKeys.generate())
    private val coAdmin = LocalEventSigner(IdentityKeys.generate())
    private val moderator = LocalEventSigner(IdentityKeys.generate())
    private val successor = IdentityKeys.generate().publicKey
    private val actor = TeamActor.of(UUID.randomUUID(), "Modo", setOf(TeamRole.MODERATOR), 2)
    private var clock = java.time.Instant.now().epochSecond - 3_600

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private fun next(): Long = ++clock

    private suspend fun room(id: String, name: String) = admin.sign(
        kind = GroupKinds.METADATA,
        tags = listOf(
            listOf("d", id),
            listOf("name", name),
            listOf(GroupKinds.TERRITORY_TAG, "Lyon"),
            listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
        ),
        createdAt = next(),
    )

    private suspend fun putUser(room: String, target: PublicKey, role: String) =
        admin.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("h", room), listOf("p", target.hex, role)), createdAt = next())

    private suspend fun message(room: String, signer: LocalEventSigner) =
        signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Bonjour", tags = listOf(listOf("h", room)), createdAt = next())

    @Test
    fun aRoomWhoseOnlyAdministratorIsCompromisedWaitsForTheTeam() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val recovery = LocalEventSigner(IdentityKeys.generate())
        val commitment = AccountRecovery.signCommitment(admin, recovery.publicKey, createdAt = next())
        assertEquals(HttpStatusCode.Created, client.publish(commitment).status)
        // Two rooms of the same administrator: one where they are alone, one with a second administrator.
        assertEquals(HttpStatusCode.Created, client.publish(room("seul", "[EXEMPLE] Salon tenu seul")).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser("seul", moderator.publicKey, GroupKinds.ROLE_MODERATOR)).status)
        assertEquals(HttpStatusCode.Created, client.publish(message("seul", moderator)).status)
        assertEquals(HttpStatusCode.Created, client.publish(room("a-deux", "[EXEMPLE] Salon tenu à deux")).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser("a-deux", coAdmin.publicKey, "admin")).status)

        val committed = AccountRecovery.parseCommitment(commitment)!!
        val named = RecoveryCommitment(committed.id, committed.account, recovery.publicKey.hex, committed.createdAt)
        val notice = recovery.sign(AccountRecovery.notice(recovery.publicKey, named, next(), successor = successor))
        assertEquals(HttpStatusCode.Created, client.publish(notice).status)

        val repository = EventRepository(TestDatabase.dataSource)
        val orphans = GroupOrphans(TestDatabase.dataSource)
        val queue = GroupOrphansQueue(orphans, GroupDirectory(repository), CompromisedAccounts(repository))
        // Only the room left without any administrator; the moderators may see the queue.
        assertEquals(1, queue.pendingCount())
        val item = queue.page(null, 10).items.single()
        assertEquals("seul", item.id)
        assertEquals("[EXEMPLE] Salon tenu seul", item.title)
        assertTrue("compromis" in item.summary.orEmpty())
        assertTrue(Permissions.queueDecide(GroupOrphansQueue.ID) in TeamRole.MODERATOR.permissions)

        // What the team sees: the cause, the moderator in place, the successor as a claim, the activity.
        val html = createHTML().div { queue.render("seul", this) }
        assertTrue(moderator.publicKey.npub in html)
        assertTrue(successor.npub in html)
        assertTrue(GroupOrphansQueue.SUCCESSOR_CLAIM in html)
        assertTrue(notice.id in html)
        assertFalse(admin.publicKey.hex in html, "keys are shown as npub")

        // Leaving it without administrator: two people and a reason (the base enforces them).
        val leave = queue.actions("seul").single()
        assertEquals(GroupOrphansQueue.LEAVE, leave.id)
        assertTrue(leave.twoPerson && leave.reasonRequired && leave.sensitive)
        assertEquals(ActionOutcome.Done, queue.perform("seul", GroupOrphansQueue.LEAVE, "[EXEMPLE] Salon actif, tenu par sa modératrice", actor))
        assertEquals(0, queue.pendingCount())
        assertTrue(queue.actions("seul").isEmpty())
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform("seul", GroupOrphansQueue.LEAVE, "[EXEMPLE] Encore", actor))
        assertEquals(ActionOutcome.NotFound, queue.perform("a-deux", GroupOrphansQueue.LEAVE, "[EXEMPLE] Motif", actor))
        assertTrue(GroupOrphansQueue.ALREADY_LEFT in createHTML().div { queue.render("seul", this) })
    }
}
