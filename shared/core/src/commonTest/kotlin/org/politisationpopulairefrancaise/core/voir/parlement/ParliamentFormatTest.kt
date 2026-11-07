package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParliamentFormatTest {

    private val importKeys = IdentityKeys.generate()
    private val signer = LocalEventSigner(importKeys)
    private val trust = OpenDataTrust.of(importKeys.publicKey.hex)

    private fun index(importedOn: String = "2026-11-07", packages: List<PackageRef> = listOf(PackageRef("membres", "a".repeat(64), 10))) = ParliamentIndex(
        example = true,
        importedOn = importedOn,
        sources = listOf(DataSource("an", "Assemblée nationale, données ouvertes", "https://data.assemblee-nationale.fr/", "Licence Ouverte", "2026-11-07")),
        packages = packages,
    )

    private suspend fun signed(index: ParliamentIndex, createdAt: Long = 1_794_000_000): SignedEvent =
        signer.sign(ParliamentFormat.indexEvent(importKeys.publicKey, index, createdAt))

    @Test
    fun aSignedIndexFromTheImportKeyIsAccepted() = runTest {
        val reading = ParliamentIndexReader.read(signed(index()), trust)
        val accepted = assertIs<IndexReading.Accepted>(reading)
        assertEquals("2026-11-07", accepted.index.importedOn)
        assertEquals("membres", accepted.index.packages.single().name)
    }

    @Test
    fun anIndexFromAnotherKeyOrTamperedIsRefused() = runTest {
        val event = signed(index())
        assertEquals(IndexProblem.UNTRUSTED_KEY, (ParliamentIndexReader.read(event, OpenDataTrust.of(IdentityKeys.generate().publicKey.hex)) as IndexReading.Refused).problem)
        val tampered = event.copy(content = event.content.replace("membres", "membrez"))
        assertEquals(IndexProblem.NOT_AUTHENTIC, (ParliamentIndexReader.read(tampered, trust) as IndexReading.Refused).problem)
    }

    @Test
    fun anOlderIndexNeverReplacesTheOneOnTheDevice() = runTest {
        val current = signed(index(importedOn = "2026-11-07"), createdAt = 1_794_000_000)
        val olderEvent = signed(index(importedOn = "2026-11-08"), createdAt = 1_793_999_000)
        val olderImport = signed(index(importedOn = "2026-11-06"), createdAt = 1_794_100_000)
        val newer = signed(index(importedOn = "2026-11-08"), createdAt = 1_794_100_000)
        assertEquals(IndexProblem.OLDER_THAN_CURRENT, (ParliamentIndexReader.read(olderEvent, trust, current) as IndexReading.Refused).problem)
        assertEquals(IndexProblem.OLDER_THAN_CURRENT, (ParliamentIndexReader.read(olderImport, trust, current) as IndexReading.Refused).problem)
        assertIs<IndexReading.Accepted>(ParliamentIndexReader.read(newer, trust, current))
    }

    @Test
    fun anIndexWithAnUnsafePackageNameIsUnreadable() = runTest {
        val bad = signed(index(packages = listOf(PackageRef("../secret", "a".repeat(64), 1))))
        assertEquals(IndexProblem.UNREADABLE, (ParliamentIndexReader.read(bad, trust) as IndexReading.Refused).problem)
        assertTrue(ParliamentFormat.isPackageName("scrutins-an-2026-10"))
        assertFalse(ParliamentFormat.isPackageName("Scrutins"))
    }

    @Test
    fun aPackageMustMatchItsFingerprintAndSize() {
        val bytes = "{\"format\":1}".encodeToByteArray()
        val ref = PackageRef("membres", ParliamentFormat.sha256Hex(bytes), bytes.size.toLong())
        assertTrue(ParliamentIndexReader.matches(ref, bytes))
        assertFalse(ParliamentIndexReader.matches(ref, "{\"format\":2}".encodeToByteArray()))
    }

    @Test
    fun dataOlderThanTwoWeeksIsStale() {
        assertFalse(ParliamentIndexReader.isStale(index("2026-11-01"), CivilDate(2026, 11, 15)))
        assertTrue(ParliamentIndexReader.isStale(index("2026-11-01"), CivilDate(2026, 11, 16)))
    }

    @Test
    fun anImportThatLosesMoreThanAFifthIsRefused() {
        assertTrue(ImportGuard.accepts(null, 10))
        assertTrue(ImportGuard.accepts(577, 462))
        assertFalse(ImportGuard.accepts(577, 461))
    }

    @Test
    fun packagesKeepTheFrenchKeysOfTheContract() {
        val ballot = Ballot(
            id = "an:17:1", number = 1, date = "2026-10-21", title = "[EXEMPLE] Scrutin", adopted = true,
            counts = Counts(voters = 3, expressed = 3, inFavour = 2, against = 1, abstention = 0),
            positions = Positions(inFavour = listOf("an:PA1", "an:PA2"), against = listOf("an:PA3"), corrections = listOf(VoteCorrection("an:PA3", Position.FOR, date = "2026-10-22")), notVotingCauses = listOf(NotVotingCause("an:PA4", "MG"))),
        )
        val text = ParliamentFormat.json.encodeToString(Ballot.serializer(), ballot)
        assertTrue("\"pour\":[\"an:PA1\",\"an:PA2\"]" in text, text)
        assertTrue("\"intended\":\"pour\"" in text, text)
        val back = ParliamentFormat.json.decodeFromString(Ballot.serializer(), text)
        assertEquals(Position.AGAINST, back.positions.of("an:PA3"))
        assertEquals(ballot, back)
        assertEquals("scrutins-an-2026-03", ParliamentFormat.ballotsPackage(Chamber.AN, 2026, 3))
    }
}
