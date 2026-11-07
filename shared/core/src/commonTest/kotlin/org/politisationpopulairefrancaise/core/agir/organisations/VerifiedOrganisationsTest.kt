package org.politisationpopulairefrancaise.core.agir.organisations

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifiedOrganisationsTest {

    private val fixtures = OrganisationFixtures()
    private val unionKey = IdentityKeys.generate().publicKey.hex
    private val legalTeamKey = IdentityKeys.generate().publicKey.hex
    private val impostorKey = IdentityKeys.generate().publicKey.hex

    @Test
    fun theVerifiedKeysAreThoseNamedByTrustedEntries() = runTest {
        val events = listOf(
            fixtures.entry("syndicat", type = "syndicat", accountKey = unionKey),
            fixtures.entry("legal-team", type = "legal-team", accountKey = legalTeamKey),
            fixtures.entry("sans-compte"),
            // The same form, but published by a key the application does not trust.
            fixtures.entry("imposteur", accountKey = impostorKey, signer = fixtures.stranger),
            // Not an entry of the directory at all.
            fixtures.editor.sign(kind = 1, content = "note", tags = listOf(listOf("p", impostorKey))),
        )
        val verified = VerifiedOrganisations.fromEvents(events, fixtures.trust)
        assertEquals(setOf(unionKey, legalTeamKey), verified.keys)
        assertTrue(verified.isVerified(unionKey))
        assertTrue(verified.isVerified(legalTeamKey))
        assertFalse(verified.isVerified(impostorKey))
        assertFalse(verified.isVerified(fixtures.editor.publicKey.hex), "an editorial key is not an organisation")
        assertEquals("syndicat", verified.organisationFor(unionKey)?.slug)
        assertNull(verified.organisationFor(impostorKey))
        assertEquals(2, verified.size)
    }

    @Test
    fun aForgedEntryVerifiesNothing() = runTest {
        val genuine = fixtures.entry("syndicat", accountKey = unionKey)
        val forged = genuine.copy(tags = genuine.tags.map { if (it[0] == "p") listOf("p", impostorKey) else it })
        assertTrue(VerifiedOrganisations.fromEvents(listOf(forged), fixtures.trust).keys.isEmpty())
    }

    @Test
    fun theMostRecentEntryDescribesAKeyNamedTwice() = runTest {
        val older = fixtures.entry("ancienne-fiche", accountKey = unionKey, updatedAt = 1_794_000_000)
        val newer = fixtures.entry("nouvelle-fiche", accountKey = unionKey, updatedAt = 1_794_100_000)
        val verified = VerifiedOrganisations.fromEvents(listOf(newer, older), fixtures.trust)
        assertEquals("nouvelle-fiche", verified.organisationFor(unionKey)?.slug)
        assertEquals(verified, VerifiedOrganisations.fromEvents(listOf(older, newer), fixtures.trust), "the order of the events does not matter")
    }

    @Test
    fun aRevokedEntryWithdrawsTheVerificationButStaysReadable() = runTest {
        val verifiedEntry = fixtures.entry("syndicat", accountKey = unionKey, updatedAt = 1_794_000_000)
        // 2026-11-07 at 09:00 in Paris (08:00 UTC).
        val revokedEntry = fixtures.entry("syndicat", accountKey = unionKey, updatedAt = 1_794_038_400, status = "revoquee")
        val revoked = fixtures.read(revokedEntry)
        assertTrue(revoked.revoked)
        assertEquals(unionKey, revoked.accountKey, "the key stays on the entry, for the page reached by a link")
        assertEquals(CivilDate(2026, 11, 7), revoked.revokedOn)
        assertFalse(fixtures.read(verifiedEntry).revoked)
        assertNull(fixtures.read(verifiedEntry).revokedOn)

        val verified = VerifiedOrganisations.fromEvents(listOf(verifiedEntry, revokedEntry), fixtures.trust)
        assertFalse(verified.isVerified(unionKey))
        assertNull(verified.organisationFor(unionKey))
        assertEquals(revokedEntry.id, verified.revocationFor(unionKey)?.event?.id)
        assertEquals(setOf(unionKey), verified.revokedKeys)
        assertTrue(verified.keys.isEmpty())
    }

    @Test
    fun aNewerVerifiedEntryRestoresARevokedKey() = runTest {
        val revoked = fixtures.entry("ancienne-fiche", accountKey = unionKey, updatedAt = 1_794_000_000, status = "revoquee")
        val again = fixtures.entry("nouvelle-fiche", accountKey = unionKey, updatedAt = 1_794_100_000)
        val verified = VerifiedOrganisations.fromEvents(listOf(revoked, again), fixtures.trust)
        assertTrue(verified.isVerified(unionKey))
        assertNull(verified.revocationFor(unionKey))
        // And the other way round: the most recent entry, revoked, wins over an older verified one.
        val older = fixtures.entry("autre-fiche", accountKey = legalTeamKey, updatedAt = 1_794_000_000)
        val newer = fixtures.entry("fiche-retiree", accountKey = legalTeamKey, updatedAt = 1_794_100_000, status = "revoquee")
        assertFalse(VerifiedOrganisations.fromEvents(listOf(older, newer), fixtures.trust).isVerified(legalTeamKey))
    }

    @Test
    fun anUnknownStatusNeverExtendsTheTrust() = runTest {
        val entry = fixtures.entry("syndicat", accountKey = unionKey)
        val unsigned = UnsignedEvent.create(fixtures.editor.publicKey, entry.kind, entry.createdAt, entry.tags + listOf(listOf("ppf-org-status", "suspendue")), entry.content)
        val future = fixtures.editor.sign(unsigned)
        assertTrue(fixtures.read(future).revoked)
        assertFalse(VerifiedOrganisations.fromEvents(listOf(future), fixtures.trust).isVerified(unionKey))
        assertEquals("verifiee", Organisation.readStatus(null))
        assertEquals("verifiee", Organisation.readStatus(" verifiee "))
        assertEquals("revoquee", Organisation.readStatus("revoquee"))
    }

    @Test
    fun nothingIsVerifiedByDefault() {
        assertTrue(VerifiedOrganisations.EMPTY.keys.isEmpty())
        assertFalse(VerifiedOrganisations.EMPTY.isVerified(unionKey))
    }
}
