package org.politisationpopulairefrancaise.core.voir.parrainages

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFixtures
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The package of presentations on the device (VO-37): kept only when it matches the signed index, the network on a gesture only. */
class SponsorshipsLoaderTest {
    private val keys = ParliamentFixtures.Keys()

    private fun bytes(count: Int = 2): ByteArray = SponsorshipsFormat.write(
        SponsorshipsPackage(
            example = true,
            election = SponsorshipsFormat.ELECTION_2027,
            publishedOn = "2027-02-16",
            sponsorships = (1..count).map { Sponsorship(lastName = "[EXEMPLE] NOM$it", firstName = "Prénom", mandate = SponsorMandate.MAIRE, territory = "Commune $it", department = "Rhône", departmentCode = "69", candidate = "[EXEMPLE] CANDIDATE Alice", publishedOn = "2027-02-16") },
        ),
    ).encodeToByteArray()

    @Test
    fun thePackageIsKeptOnlyWhenItMatchesTheIndex() = runTest {
        val packages = ParliamentFixtures.packages() + (SponsorshipsFormat.PACKAGE_2027 to bytes())
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(packages)), packages)
        val storage = InMemoryParliamentStorage()
        val repository = ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentFixtures.NOW })
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        val loader = SponsorshipsLoader(server, storage, clock = { ParliamentFixtures.NOW })
        assertEquals(bytes().size.toLong(), loader.ref(snapshot)?.bytes)
        assertNull(loader.held(snapshot), "nothing before the gesture")
        assertEquals(listOf<String?>(null), server.requests)

        assertEquals(PackageOutcome.Done, loader.download(snapshot))
        val held = assertNotNull(loader.held(snapshot))
        assertFalse(held.outdated)
        assertEquals(2, held.catalog.total)
        assertEquals("2027-02-16", storage.storedPackage(SponsorshipsFormat.PACKAGE_2027)?.sourceUpdatedOn)

        // Altered bytes on the server are refused; the device keeps its copy.
        server.packages = packages + (SponsorshipsFormat.PACKAGE_2027 to bytes(3))
        assertEquals(PackageOutcome.Refused(SponsorshipsFormat.PACKAGE_2027), loader.download(snapshot))
        assertEquals(2, loader.held(snapshot)?.catalog?.total)
    }

    @Test
    fun beforeThePublicationsTheIndexHasNoPackage() = runTest {
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(ParliamentFixtures.packages())), ParliamentFixtures.packages())
        val storage = InMemoryParliamentStorage()
        val snapshot = assertIs<ParliamentOpening.Ready>(ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentFixtures.NOW }).open()).snapshot
        val loader = SponsorshipsLoader(server, storage)
        assertNull(loader.ref(snapshot))
        assertEquals(PackageOutcome.Unknown(SponsorshipsFormat.PACKAGE_2027), loader.download(snapshot))
        assertTrue(server.requests.none { it == SponsorshipsFormat.PACKAGE_2027 })
    }
}
