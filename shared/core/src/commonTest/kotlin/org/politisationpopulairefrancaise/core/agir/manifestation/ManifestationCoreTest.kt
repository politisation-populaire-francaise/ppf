package org.politisationpopulairefrancaise.core.agir.manifestation

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.organisations.OrganisationFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManifestationCoreTest {

    @Test
    fun theFifteenAndTheOneOneTwoComeFirstThenSeventeenEighteenAndTheTextToOneOneFour() {
        assertEquals(listOf("15", "112", "17", "18", "114"), EmergencyNumbers.emergency.map { it.number })
        assertTrue(EmergencyNumbers.emergency.take(2).all { it.vital })
        assertTrue(EmergencyNumbers.emergency.drop(2).none { it.vital })
        assertEquals("tel:15", EmergencyNumbers.SAMU.uri)
        assertEquals("tel:112", EmergencyNumbers.EUROPE.uri)
        assertEquals("sms:114", EmergencyNumbers.DEAF.uri)
        assertEquals("tel:116000", EmergencyNumbers.MISSING_CHILD.uri)
        assertEquals("tel:3114", EmergencyNumbers.SUICIDE.uri)
        assertEquals(EmergencyNumbers.all.size, EmergencyNumbers.all.map { it.id }.toSet().size)
    }

    @Test
    fun aRallyPointIsTrimmedCutAndReadBack() {
        assertNull(RallyPoint.of("   "))
        val point = RallyPoint.of("  Devant la fontaine\u0007 ", " À 18 h\nsinon on s'appelle ")!!
        assertEquals("Devant la fontaine", point.place)
        assertEquals("À 18 h\nsinon on s'appelle", point.details)
        assertEquals(point, RallyPoint.decode(point.encode()))
        assertEquals(RallyPoint.MAX_PLACE, RallyPoint.of("x".repeat(500))!!.place.length)
        assertNull(RallyPoint.decode("not json"))
        assertNull(RallyPoint.decode(null))
        assertNull(RallyPoint.decode("{\"place\":\"  \"}"))
    }

    @Test
    fun theStorageInMemoryKeepsWhatWasSavedUntilCleared() {
        val storage = InMemoryRallyPointStorage()
        assertNull(storage.load())
        storage.save("a")
        assertEquals("a", storage.load())
        storage.clear()
        assertNull(storage.load())
    }

    @Test
    fun legalContactsAreTheLegalTeamsAndLawyersWithAPhoneNearFirst() = runTest {
        val fixtures = OrganisationFixtures()
        val directory = listOf(
            fixtures.entry("legal-nord", type = "legal-team", territory = "Nord (59)", phone = "06 39 98 52 10"),
            fixtures.entry("legal-lyon", type = "legal-team", territory = "Lyon (69)", phone = "06 39 98 52 11"),
            fixtures.entry("avocats", type = "avocats-benevoles", territory = "France", phone = "06 39 98 52 12"),
            fixtures.entry("sans-telephone", type = "legal-team", territory = "Nord (59)"),
            fixtures.entry("medics", type = "street-medics", territory = "Nord (59)", phone = "06 39 98 52 13"),
            fixtures.entry("retiree", type = "legal-team", territory = "Nord (59)", phone = "06 39 98 52 14", status = "revoquee"),
        ).map(fixtures::read)
        assertEquals(listOf("legal-nord", "avocats"), LegalContacts.of(directory, "59000").map { it.slug })
        assertEquals(listOf("avocats", "legal-lyon", "legal-nord"), LegalContacts.of(directory, null).map { it.slug })
    }
}
