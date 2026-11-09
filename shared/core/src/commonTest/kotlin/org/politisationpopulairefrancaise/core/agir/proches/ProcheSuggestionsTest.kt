package org.politisationpopulairefrancaise.core.agir.proches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The contents suggested for a relative (AG-63, « contenu adapté »): matched by the subjects chosen,
 * through the topics of a content or the words of its title, in an order that follows the next step,
 * four at most and two of a kind before the others. All titles are [EXEMPLE] of the tests.
 */
class ProcheSuggestionsTest {

    private fun suggestion(
        kind: SuggestionKind,
        slug: String,
        title: String,
        topics: List<String> = emptyList(),
        at: Long? = null,
        summary: String? = null,
    ) = ProcheSuggestion(
        kind = kind,
        key = "30023:" + "a".repeat(64) + ":" + slug,
        title = title,
        example = true,
        url = "https://politisation-populaire-francaise.org/exemple/$slug",
        topics = topics,
        summary = summary,
        at = at,
    )

    private val strikeFiche = suggestion(SuggestionKind.FICHE, "faire-greve", "Faire grève : vos droits", listOf("droits", "greve", "travail"))
    private val strikeCourse = suggestion(SuggestionKind.COURSE, "droit-de-greve", "Le droit de grève", listOf("droits", "travail", "greve"))
    private val winterFiche = suggestion(SuggestionKind.FICHE, "treve", "La trêve hivernale", listOf("droits", "logement", "energie"))
    private val hospitalMarch = suggestion(SuggestionKind.MOBILIZATION, "marche-hopital", "Marche pour l'hôpital public", at = 2_000)
    private val maternity = suggestion(SuggestionKind.MOBILIZATION, "maternite", "Rassemblement pour garder la maternité", summary = "La santé des familles du territoire.", at = 1_000)
    private val rentsCall = suggestion(SuggestionKind.CALL, "loyers", "Assemblée de quartier sur la hausse des loyers", at = 1_500)
    private val transportsStrike = suggestion(SuggestionKind.MOBILIZATION, "greve-transports", "Grève dans les transports, deux jours", at = 3_000)
    private val all = listOf(strikeFiche, strikeCourse, winterFiche, hospitalMarch, maternity, rentsCall, transportsStrike)

    private fun entry(subjects: List<String>, step: ProcheStep = ProcheStep.TALK) =
        Proche("1", "[EXEMPLE] Proche", subjects, step.id)

    @Test
    fun aContentIsAboutAThemeByItsTopicsOrTheWordsOfItsTitle() {
        assertEquals(listOf("travail"), ProcheSuggestions.themesOf(strikeFiche))
        assertTrue("logement" in ProcheSuggestions.themesOf(winterFiche), "the topic « energie » is an alias of « pouvoir d'achat », « logement » a theme")
        // The agenda and the calls carry no topics: the words of the title say it.
        assertEquals(listOf("sante"), ProcheSuggestions.themesOf(hospitalMarch))
        assertEquals(listOf("logement"), ProcheSuggestions.themesOf(rentsCall))
        assertEquals(listOf("transports"), ProcheSuggestions.themesOf(transportsStrike))
        // The summary counts too; whole words only.
        assertTrue("sante" in ProcheSuggestions.themesOf(maternity))
        assertEquals(emptyList<String>(), ProcheSuggestions.themesOf(suggestion(SuggestionKind.CALL, "x", "Transportsxyz et santeux")))
    }

    @Test
    fun theSuggestionsFollowTheSubjectsOfTheRelative() {
        val forWork = ProcheSuggestions.forEntry(entry(listOf("travail")), all)
        assertEquals(setOf(strikeFiche, strikeCourse), forWork.toSet())
        val forHealth = ProcheSuggestions.forEntry(entry(listOf("sante"), ProcheStep.INVITE), all)
        assertEquals(listOf(maternity, hospitalMarch), forHealth, "the soonest mobilization first")
        assertTrue(ProcheSuggestions.forEntry(entry(listOf("culture")), all).isEmpty())
    }

    @Test
    fun withoutSubjectThereIsNoSuggestionAndUnknownSubjectsAreIgnored() {
        assertTrue(ProcheSuggestions.forEntry(entry(emptyList()), all).isEmpty())
        assertTrue(ProcheSuggestions.forEntry(entry(listOf("opinion-de-quelqu-un")), all).isEmpty())
    }

    @Test
    fun theNextStepDecidesWhatComesFirst() {
        val subjects = listOf("travail", "sante", "logement")
        // Talking or sharing: something to read first.
        val talk = ProcheSuggestions.forEntry(entry(subjects, ProcheStep.TALK), all)
        assertEquals(SuggestionKind.COURSE, talk.first().kind)
        // Inviting or going together: a mobilization first.
        val invite = ProcheSuggestions.forEntry(entry(subjects, ProcheStep.GO_TOGETHER), all)
        assertEquals(SuggestionKind.MOBILIZATION, invite.first().kind)
        assertEquals(maternity, invite.first())
    }

    @Test
    fun fourAtMostAndTwoOfAKindBeforeTheOthers() {
        val subjects = listOf("travail", "sante", "logement")
        val many = all + suggestion(SuggestionKind.MOBILIZATION, "autre-hopital", "Rassemblement devant l'hôpital", at = 500)
        val picked = ProcheSuggestions.forEntry(entry(subjects, ProcheStep.INVITE), many)
        assertEquals(ProcheSuggestions.LIMIT, picked.size)
        assertEquals(2, picked.count { it.kind == SuggestionKind.MOBILIZATION }, picked.toString())
        assertTrue(picked.any { it.kind == SuggestionKind.CALL })
        assertEquals(picked.size, picked.map { it.key }.distinct().size)
    }

    @Test
    fun theSharedTextIsTheTitleAndTheStableAddressOnly() {
        val text = strikeFiche.shareText()
        assertEquals("[EXEMPLE] Faire grève : vos droits\nhttps://politisation-populaire-francaise.org/exemple/faire-greve", text)
        assertFalse('?' in text || '#' in text, "no parameter, no fragment")
        assertEquals("Titre\nhttps://exemple.org/a", strikeFiche.copy(title = "Titre", example = false, url = "https://exemple.org/a").shareText())
    }
}
