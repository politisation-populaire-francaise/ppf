package org.politisationpopulairefrancaise.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.agir.kits.CitizenKits
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rubric pages of « Agir » on the site: readable without script, the rolls first, kits filtered by links. */
class AgirRubricPagesTest {

    @Test
    fun registeringComesFirstWithTheOfficialServicesAndTheNextDeadline() {
        val html = createHTML().html { engagerDocument(CivilDate(2026, 11, 11), catalog = null) }
        assertTrue(html.indexOf("Étape 1 : s’inscrire") in 0 until html.indexOf("Étape 2"), "registering first")
        assertTrue(html.contains("href=\"https://www.service-public.gouv.fr/particuliers/vosdroits/R51788\" rel=\"noopener noreferrer nofollow external\""))
        assertTrue(html.contains("Prochaine échéance : dernier jour pour s’inscrire"))
        assertTrue(html.contains("href=\"/comprendre/fiches/constituer-une-liste-citoyenne\""))
        assertTrue(html.contains("L52-8"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun theKitsAreFilteredByPlainLinks() {
        val all = createHTML().html { kitsDocument(null, false, catalog = null) }
        assertTrue(all.contains("${CitizenKits.all.size} kits"))
        val strike = createHTML().html { kitsDocument(KitGoal.GREVE, false, catalog = null) }
        assertTrue(strike.contains("${CitizenKits.filter(KitGoal.GREVE, false).size} kits"))
        assertTrue(strike.contains("href=\"/agir/kits?but=greve\" aria-current=\"true\""), strike)
        assertTrue(strike.contains("href=\"/agir/kits?but=greve&amp;sans-reseau=1\""))
        assertTrue(strike.contains("href=\"/agir/solidarite\""), "a tool on the site opens its page")
    }

    @Test
    fun emptyDirectoriesSaySoAndSendToTheApplication() {
        assertTrue(createHTML().html { solidarityDocument(null, null) }.contains("Aucune caisse publiée pour l’instant"))
        val collectives = createHTML().html { collectivesDocument("Lyon & co", emptyList()) }
        assertTrue(collectives.contains("Aucun collectif"))
        assertTrue(collectives.contains("value=\"Lyon &amp; co\""))
        assertTrue(collectives.contains("utilisez l’application PPF"))
    }
}
