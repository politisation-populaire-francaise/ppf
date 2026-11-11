package org.politisationpopulairefrancaise.server.agir.collectives

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.identity.AccountType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.accounts.AccountTypes
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.agir.AgirFixtures
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.callTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.error
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.publish
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.published
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.decider.CompromiseFixtures
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import org.politisationpopulairefrancaise.server.urne.UrneFixtures
import org.politisationpopulairefrancaise.server.urne.UrneStore
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The collectifs on the server (M24, step 1, D-087, docs/integration/agir-collectifs.md): founded only
 * by an account validated in person, precise refusals of malformed tags, what never changes, and who
 * publishes a mobilization or a call in the name of a collectif. Every content is marked [EXEMPLE].
 */
class CollectivesApiTest {

    private val founder = LocalEventSigner(IdentityKeys.generate())
    private val elected = LocalEventSigner(IdentityKeys.generate())
    private val moderator = LocalEventSigner(IdentityKeys.generate())
    private val outsider = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(editor.publicKey.hex))
    private val json = Json { ignoreUnknownKeys = true }
    private var clock = AgirFixtures.nowSeconds() - 3_600

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun next(): Long = ++clock

    private fun io.ktor.server.testing.ApplicationTestBuilder.server() {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), urne = UrneFixtures.CONFIG) }
    }

    /** Registers [signer] as a voter of [level] (D-112), as the in-person validation or the certification would. */
    private fun validate(signer: LocalEventSigner, level: VoteLevel = VoteLevel.VERIFIE) {
        val store = UrneStore(TestDatabase.dataSource)
        store.register(signer.publicKey.hex, WEEK)
        if (level != VoteLevel.NON_VERIFIE) store.setLevel(signer.publicKey.hex, level, WEEK, WEEK)
    }

    /** The description of a collectif, built by the rules shared with the application; [edit] changes its tags. */
    private suspend fun collective(
        signer: LocalEventSigner,
        id: String = ID,
        name: String = "[EXEMPLE] Collectif des cyclistes",
        territory: String = "Lyon",
        scope: CollectiveScope = CollectiveScope.LOCAL,
        themes: List<String> = listOf("vélo"),
        about: String = "[EXEMPLE] Des pistes cyclables sûres.",
        createdAt: Long = next(),
        edit: (List<List<String>>) -> List<List<String>> = { it },
    ): SignedEvent {
        val unsigned = CollectiveFormat.build(
            author = signer.publicKey, id = id, name = name, about = about, territory = territory, createdAt = createdAt,
            scope = scope, type = CollectiveType.COLLECTIF, themes = themes, territoryCode = "com:69123",
        )
        return signer.sign(unsigned.copy(tags = edit(unsigned.tags)))
    }

    private suspend fun room(signer: LocalEventSigner, id: String, name: String, territory: String = "Lyon", extra: List<List<String>> = emptyList()) =
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(
                listOf("d", id),
                listOf("name", name),
                listOf(GroupKinds.TERRITORY_TAG, territory),
                listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
            ) + extra,
            createdAt = next(),
        )

    private suspend fun putUser(by: LocalEventSigner, target: LocalEventSigner, role: String?, group: String = ID) =
        by.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("h", group), listOfNotNull("p", target.publicKey.hex, role)), createdAt = next())

    private suspend fun HttpClient.groups(): List<Group> = json.decodeFromString(get("/api/v1/groups").bodyAsText())

    private fun List<List<String>>.replacing(name: String, value: String) = filterNot { it.firstOrNull() == name } + listOf(listOf(name, value))

    @Test
    fun onlyAnAccountValidatedInPersonFoundsACollective() = testApplication {
        server()
        val refused = client.publish(collective(founder))
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("collective_needs_verified", refused.error().error)
        // A room stays open to every account (D-028).
        assertEquals(HttpStatusCode.Created, client.publish(room(founder, "jardins-lyon", "[EXEMPLE] Salon des jardiniers")).status)

        // Registered to vote but not validated in person: still refused.
        validate(founder, VoteLevel.NON_VERIFIE)
        assertEquals("collective_needs_verified", client.publish(collective(founder)).error().error)

        validate(founder)
        assertEquals(HttpStatusCode.Created, client.publish(collective(founder)).status)
        // A certified elected official was validated in person too.
        validate(elected, VoteLevel.ELU)
        val riverains = collective(elected, id = "riverains-paris", name = "[EXEMPLE] Comité des riverains", territory = "Paris", themes = listOf("logement"))
        assertEquals(HttpStatusCode.Created, client.publish(riverains).status)

        // The directory of the rooms serves it, signed by its founder, with its tags.
        val listed = client.groups().first { it.id == ID }
        assertTrue(CollectiveFormat.isCollective(listed.event.tags))
        assertEquals(founder.publicKey.hex, listed.event.pubkey)
        assertEquals(setOf(ID, "riverains-paris", "jardins-lyon"), client.groups().map { it.id }.toSet())
    }

    @Test
    fun malformedTagsAreRefusedWithTheirOwnCode() = testApplication {
        server()
        validate(founder)
        val cases: List<Pair<String, (List<List<String>>) -> List<List<String>>>> = listOf(
            "collective_bad_scope" to { tags: List<List<String>> -> tags.filterNot { it.firstOrNull() == CollectiveFormat.TAG_SCOPE } },
            "collective_bad_type" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_TYPE, "parti") },
            "collective_too_many_themes" to { tags: List<List<String>> -> tags + (1..5).map { listOf("t", "theme$it") } },
            "collective_charter_too_long" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_INTERNAL_CHARTER, "x".repeat(3_001)) },
            "collective_visibility_unsupported" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_VISIBILITY, "secret") },
            "collective_join_unsupported" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_JOIN, "request") },
            "collective_bad_territory_code" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_TERRITORY_CODE, "lyon") },
            "collective_bad_marker" to { tags: List<List<String>> -> tags.replacing(CollectiveFormat.TAG_COLLECTIVE, "2") },
        )
        for ((code, edit) in cases) {
            val response = client.publish(collective(founder, edit = edit))
            assertEquals(HttpStatusCode.BadRequest, response.status, code)
            assertEquals(code, response.error().error)
        }
        assertTrue(client.groups().isEmpty(), "nothing malformed was stored")
    }

    @Test
    fun whatMakesACollectiveNeverChanges() = testApplication {
        server()
        validate(founder)
        validate(outsider)
        assertEquals(HttpStatusCode.Created, client.publish(collective(founder)).status)

        // The founder may change the name, the presentation and the themes.
        val renamed = collective(founder, name = "[EXEMPLE] Collectif des cyclistes lyonnais", themes = listOf("vélo", "sécurité routière"), about = "[EXEMPLE] Pistes et ateliers.")
        assertEquals(HttpStatusCode.Created, client.publish(renamed).status)
        // Not the scope, the territory code, the visibility nor the marker.
        for (changed in listOf(
            collective(founder, scope = CollectiveScope.DEPARTEMENTAL),
            collective(founder) { it.replacing(CollectiveFormat.TAG_TERRITORY_CODE, "dep:69") },
            collective(founder) { tags -> tags.filterNot { it.firstOrNull()?.startsWith("ppf-collective") == true } },
        )) {
            val response = client.publish(changed)
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals("collective_immutable", response.error().error)
        }
        // Another key never redescribes it.
        assertEquals("group_taken", client.publish(collective(outsider)).error().error)

        // A plain room does not become a collectif by a new version, even from a validated account.
        assertEquals(HttpStatusCode.Created, client.publish(room(outsider, "ateliers-paris", "[EXEMPLE] Ateliers de réparation", territory = "Paris")).status)
        val takeover = collective(outsider, id = "ateliers-paris", name = "[EXEMPLE] Ateliers de réparation", territory = "Paris")
        assertEquals("collective_immutable", client.publish(takeover).error().error)

        val stored = client.groups().first { it.id == ID }
        assertEquals("[EXEMPLE] Collectif des cyclistes lyonnais", stored.name)
        assertEquals(listOf("vélo", "sécurité routière"), stored.event.tagValues(CollectiveFormat.TAG_THEME))
    }

    @Test
    fun onlyTheFounderAdministratorsAndModeratorsPublishInTheNameOfTheCollective() = testApplication {
        server()
        validate(founder)
        assertEquals(HttpStatusCode.Created, client.publish(collective(founder)).status)
        assertEquals(HttpStatusCode.Created, client.publish(room(outsider, "jardins-paris", "[EXEMPLE] Salon des jardiniers", territory = "Paris")).status)
        val reference = listOf(CollectiveFormat.TAG_COLLECTIVE, ID)

        // A key without role is refused; the same event without the tag waits for review as usual.
        val stranger = client.publish(outsider.mobilization(mobilizationTags(d = "velorution-1") + listOf(reference)))
        assertEquals(HttpStatusCode.Forbidden, stranger.status)
        assertEquals("collective_role_required", stranger.error().error)
        assertEquals(HttpStatusCode.Accepted, client.publish(outsider.mobilization(mobilizationTags(d = "velorution-1"))).status)

        // The founder: then the usual rules (a citizen's mobilization waits for review).
        val byFounder = client.publish(founder.mobilization(mobilizationTags(d = "velorution-2") + listOf(reference)))
        assertEquals(HttpStatusCode.Accepted, byFounder.status)
        assertEquals("pending_review", byFounder.published().status)

        // A moderator named by the founder publishes a call in the name of the collectif.
        assertEquals(HttpStatusCode.Created, client.publish(putUser(founder, moderator, GroupKinds.ROLE_MODERATOR)).status)
        val byModerator = client.publish(moderator.call("information", tags = callTags("information", d = "appel-velo") + listOf(reference)))
        assertEquals(HttpStatusCode.Accepted, byModerator.status)

        // A tag naming a plain room, an unknown room, or two rooms.
        assertEquals("collective_not_found", client.publish(founder.mobilization(mobilizationTags(d = "velorution-3") + listOf(listOf(CollectiveFormat.TAG_COLLECTIVE, "jardins-paris")))).error().error)
        assertEquals("collective_not_found", client.publish(founder.mobilization(mobilizationTags(d = "velorution-4") + listOf(listOf(CollectiveFormat.TAG_COLLECTIVE, "inconnu")))).error().error)
        val twice = client.publish(founder.mobilization(mobilizationTags(d = "velorution-5") + listOf(reference, listOf(CollectiveFormat.TAG_COLLECTIVE, "jardins-paris"))))
        assertEquals(HttpStatusCode.BadRequest, twice.status)
        val fault = twice.error()
        assertEquals("missing_field", fault.error)
        assertEquals(CollectiveFormat.TAG_COLLECTIVE, fault.message)

        // The editorial team too needs a role in the collectif; with one, it publishes directly.
        val start = AgirFixtures.nowSeconds() + AgirFixtures.DAY
        val tags = mobilizationTags(d = "fete-du-velo", start = start) + listOf(reference)
        assertEquals("collective_role_required", client.publish(editor.mobilization(tags)).error().error)
        assertEquals(HttpStatusCode.Created, client.publish(putUser(founder, editor, GroupKinds.ROLE_MODERATOR)).status)
        assertEquals(HttpStatusCode.Created, client.publish(editor.mobilization(tags, createdAt = next())).status)
        // Once its role is taken back, it still cancels what it published (decision of the owner, 2026-11-07).
        assertEquals(HttpStatusCode.Created, client.publish(putUser(founder, editor, null)).status)
        val cancelled = mobilizationTags(d = "fete-du-velo", start = start, status = "annule") + listOf(reference)
        assertEquals(HttpStatusCode.Created, client.publish(editor.mobilization(cancelled, createdAt = next())).status)
        // But nothing new.
        assertEquals("collective_role_required", client.publish(editor.mobilization(mobilizationTags(d = "fete-du-velo-2") + listOf(reference))).error().error)
    }

    @Test
    fun aCollectiveAccountOrACompromisedKeyFoundsNoCollective() = runBlocking<Unit> {
        val repository = EventRepository(TestDatabase.dataSource)
        val accounts = CompromisedAccounts(repository)
        val types = AccountTypes(TestDatabase.dataSource)
        val rules = CollectiveRules(UrneStore(TestDatabase.dataSource), types, accounts)
        validate(founder)
        validate(elected)
        val now = AgirFixtures.nowSeconds()
        assertNull(rules.descriptionRefusal(null, collective(founder, createdAt = now - 7_200)))

        // Declared compromised since an hour ago: what it signed after is refused, what it signed before is not.
        CompromiseFixtures.declare(accounts, founder, since = now - 3_600, at = now)
        assertEquals("account_compromised", rules.descriptionRefusal(null, collective(founder, createdAt = now - 60))?.error)
        assertNull(rules.descriptionRefusal(null, collective(founder, createdAt = now - 7_200)))

        // A collective account (D-133) is not a person, even with a level.
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO account_type(pubkey, type, declared_at) VALUES (?, ?, ?)").use { statement ->
                statement.setString(1, elected.publicKey.hex)
                statement.setString(2, AccountType.COLLECTIVE.tagValue)
                statement.setLong(3, now)
                statement.executeUpdate()
            }
        }
        assertEquals("collective_needs_verified", rules.descriptionRefusal(null, collective(elected, id = "riverains-paris"))?.error)

        // Without the store of the ballot boxes, no collectif is ever created; plain rooms are not concerned.
        val unplugged = CollectiveRules()
        assertEquals("collective_needs_verified", unplugged.descriptionRefusal(null, collective(founder, createdAt = now - 7_200))?.error)
        assertNull(unplugged.descriptionRefusal(null, room(founder, "jardins-lyon", "[EXEMPLE] Salon des jardiniers")))
    }

    companion object {
        const val ID: String = "cyclistes-lyon"

        /** A Monday: levels count by the week (D-110). */
        val WEEK: LocalDate = LocalDate.of(2026, 11, 2)
    }
}
