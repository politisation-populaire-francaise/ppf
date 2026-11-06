package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.unsignedForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallSimilarityTest {
    private val bus = CallFixtures.call(
        CallFixtures.validDraft(CallType.PETITION).copy(
            title = "[EXEMPLE] Pétition pour sauver la ligne de bus 12 du quartier nord",
            summary = "La ligne de bus 12 doit rester ouverte le soir.",
            territory = "Lille",
        ),
        slug = "bus-12",
    )
    private val garden = CallFixtures.call(
        CallFixtures.validDraft(CallType.BENEVOLES).copy(title = "[EXEMPLE] Bénévoles pour le jardin partagé", territory = "Lille"),
        slug = "jardin",
    )
    private val calls = listOf(bus, garden)

    @Test
    fun aDraftThatRepeatsAnOpenCallFindsIt() {
        val draft = CallDraft(type = CallType.PETITION, title = "Sauver la ligne de bus 12 dans le quartier nord", territory = "Lille")
        val found = CallSimilarity.find(draft, calls)
        assertEquals(listOf("bus-12"), found.map { it.call.slug })
        assertTrue(found.single().likelyDuplicate)
    }

    @Test
    fun anotherTypeOnTheSameSubjectIsCloseButNotADuplicate() {
        val draft = CallDraft(type = CallType.MANIFESTER, title = "Sauver la ligne de bus 12 dans le quartier nord", territory = "Lille")
        val found = CallSimilarity.find(draft, calls).single()
        assertEquals("bus-12", found.call.slug)
        assertFalse(found.likelyDuplicate)
    }

    @Test
    fun closedCallsTheCallItselfAndUnrelatedTitlesAreLeftOut() {
        val draft = CallDraft(type = CallType.PETITION, title = "Sauver la ligne de bus 12 dans le quartier nord", territory = "Lille")
        val closed = assertNotNull(Call.from(bus.closedVersion("", NOW + 1).unsignedForTest()))
        assertEquals(emptyList(), CallSimilarity.find(draft, listOf(closed, garden)))
        assertEquals(emptyList(), CallSimilarity.find(draft, calls, ownAddress = bus.address.toString()))
        assertEquals(emptyList(), CallSimilarity.find(CallDraft(title = "Concert de soutien aux grévistes", territory = "Lille"), calls))
        assertEquals(emptyList(), CallSimilarity.find(CallDraft(title = "de la", territory = "Lille"), calls))
    }
}
