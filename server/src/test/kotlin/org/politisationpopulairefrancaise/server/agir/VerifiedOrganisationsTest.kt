package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withTag
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Verified organisations: named in a directory entry signed by an editorial key, read from the events table and cached. */
class VerifiedOrganisationsTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())
    private val member = LocalEventSigner(IdentityKeys.generate()).publicKey.hex
    private val other = LocalEventSigner(IdentityKeys.generate()).publicKey.hex
    private val events = EventRepository(TestDatabase.dataSource)
    private val time = AtomicLong(0)
    private val verified = VerifiedOrganisations(TestDatabase.dataSource, { it == editor.publicKey.hex }, maxAge = 5.minutes, nanoTime = time::get)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    @Test
    fun onlyEntriesSignedByTheEditorialTeamVerify() = runBlocking {
        assertFalse(verified.isVerified(member))
        // Stored directly (no API in between): a directory entry signed by another key verifies nobody.
        events.store(stranger.organisation(organisationTags(member = other)))
        events.store(editor.organisation(organisationTags(d = "collectif-membre", member = member)))
        verified.invalidate()
        assertTrue(verified.isVerified(member))
        assertFalse(verified.isVerified(other))
        assertFalse(verified.isVerified(editor.publicKey.hex))
        // A practical guide naming a key in a p tag is not an entry of the directory.
        val notAnEntry = editor.organisation(organisationTags(d = "fiche", member = other).withTag(AgirFormats.TAG_TYPE, "fiche-pratique"))
        events.store(notAnEntry)
        verified.invalidate()
        assertFalse(verified.isVerified(other))
        assertEquals(setOf(member), verified.keys())
    }

    @Test
    fun theCacheIsForgottenWhenAnEntryIsStoredOrReplaced() = runBlocking {
        val entry = editor.organisation(organisationTags(member = member), createdAt = 1_000)
        assertFalse(verified.isVerified(member))
        events.store(entry)
        // Still the cached answer: nobody told the cache.
        assertFalse(verified.isVerified(member))
        verified.stored(entry)
        assertTrue(verified.isVerified(member))
        // A new version without the key withdraws the verification.
        val withdrawn = editor.organisation(organisationTags(member = null), createdAt = 2_000)
        events.store(withdrawn)
        assertTrue(verified.isVerified(member))
        verified.stored(withdrawn)
        assertFalse(verified.isVerified(member))
        // Other events do not empty the cache.
        val again = editor.organisation(organisationTags(member = member), createdAt = 3_000)
        events.store(again)
        verified.stored(stranger.mobilization())
        assertFalse(verified.isVerified(member))
        // Read again after the maximum age anyway (entries stored by another process).
        time.addAndGet(5.minutes.inWholeNanoseconds)
        assertTrue(verified.isVerified(member))
    }

    @Test
    fun aKeyRemovedFromTheEditorialTeamVerifiesNobodyAnyMore() = runBlocking {
        events.store(editor.organisation(organisationTags(member = member)))
        val withoutEditors = VerifiedOrganisations(TestDatabase.dataSource, { false })
        assertFalse(withoutEditors.isVerified(member))
        assertTrue(VerifiedOrganisations(TestDatabase.dataSource, { it == editor.publicKey.hex }).isVerified(member))
    }
}
