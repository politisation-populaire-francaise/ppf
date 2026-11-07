package org.politisationpopulairefrancaise.core.agir.organisations

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.sign
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
    fun nothingIsVerifiedByDefault() {
        assertTrue(VerifiedOrganisations.EMPTY.keys.isEmpty())
        assertFalse(VerifiedOrganisations.EMPTY.isVerified(unionKey))
    }
}
