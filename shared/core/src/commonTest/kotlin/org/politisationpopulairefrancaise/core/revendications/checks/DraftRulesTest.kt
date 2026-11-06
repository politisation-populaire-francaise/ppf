package org.politisationpopulairefrancaise.core.revendications.checks

import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.draft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftRulesTest {

    private fun kinds(draft: RevendicationDraft) = DraftRules.check(draft).map { it.kind to it.severity }.toSet()

    @Test
    fun aWellFormedDraftHasNoIssue() {
        assertEquals(emptyList(), DraftRules.check(draft("Un médecin de garde joignable le soir dans chaque canton")))
    }

    @Test
    fun theDemandHasAMinimumAndAMaximumLength() {
        assertTrue(IssueKind.TOO_SHORT to IssueSeverity.BLOCKING in kinds(draft("Santé")))
        assertTrue(IssueKind.TOO_LONG to IssueSeverity.BLOCKING in kinds(draft("Des transports gratuits ".repeat(15))))
        val threeSentences = "Les bus sont trop rares. Il faut en ajouter. Le soir surtout."
        assertTrue(IssueKind.TOO_LONG to IssueSeverity.WARNING in kinds(draft(threeSentences)))
        assertFalse(IssueKind.TOO_LONG to IssueSeverity.WARNING in kinds(draft("Les bus sont trop rares. Il faut en ajouter le soir.")))
    }

    @Test
    fun severalActionsInOneDemandAreFlagged() {
        assertTrue(DraftRules.hasSeveralIdeas("Augmenter le SMIC et baisser la TVA sur l'énergie"))
        assertTrue(DraftRules.hasSeveralIdeas("Garantir un médecin de garde, ouvrir des centres de santé"))
        assertTrue(DraftRules.hasSeveralIdeas("Des écoles rénovées\nDes cantines à un euro"))
        assertFalse(DraftRules.hasSeveralIdeas("Rendre les transports gratuits et accessibles à tous"))
        assertFalse(DraftRules.hasSeveralIdeas("Un tarif de cantine à un euro pour les familles aux revenus modestes"))
        assertFalse(DraftRules.hasSeveralIdeas("Baisser le loyer et le prix de l'énergie des étudiants"))
        assertFalse(DraftRules.hasSeveralIdeas("Défendre le pouvoir d'achat et le premier emploi"))
        assertTrue(IssueKind.SEVERAL_IDEAS to IssueSeverity.WARNING in kinds(draft("Augmenter le SMIC et baisser la TVA sur l'énergie")))
    }

    @Test
    fun shoutingIsAWarning() {
        assertTrue(IssueKind.ALL_CAPS to IssueSeverity.WARNING in kinds(draft("DES MÉDECINS PARTOUT TOUT DE SUITE")))
        assertFalse(IssueKind.ALL_CAPS to IssueSeverity.WARNING in kinds(draft("Le RIC et la TVA sociale en débat public")))
    }

    @Test
    fun themesAndTerritoryAreRequired() {
        assertTrue(IssueKind.MISSING_THEME to IssueSeverity.BLOCKING in kinds(draft("Un médecin de garde joignable le soir", themes = emptyList())))
        assertTrue(IssueKind.MISSING_THEME to IssueSeverity.BLOCKING in kinds(draft("Un médecin de garde joignable le soir", themes = listOf("inconnu"))))
        assertTrue(
            IssueKind.TOO_MANY_THEMES to IssueSeverity.BLOCKING in
                kinds(draft("Un médecin de garde joignable le soir", themes = listOf("sante", "societe", "travail", "logement"))),
        )
        assertTrue(IssueKind.MISSING_TERRITORY to IssueSeverity.BLOCKING in kinds(draft("Un médecin de garde joignable le soir", territory = " - ")))
    }

    @Test
    fun personalDataBlocksWithoutBeingRepeated() {
        val withMail = draft("Un médecin de garde joignable le soir").copy(why = "Écrivez-moi à jean.exemple@courriel.example pour en parler.")
        val issue = DraftRules.check(withMail).single()
        assertEquals(IssueKind.PERSONAL_DATA, issue.kind)
        assertEquals(IssueSeverity.BLOCKING, issue.severity)
        assertEquals(DraftField.WHY, issue.field)
        assertEquals(null, issue.detail, "the address found is never copied into the report")

        assertTrue(DraftRules.containsPersonalData("Appelez le 06 12 34 56 78"))
        assertTrue(DraftRules.containsPersonalData("Appelez le +33 6 12 34 56 78"))
        assertTrue(DraftRules.containsPersonalData("IBAN FR76 3000 6000 0112 3456 7890 189"))
        assertTrue(DraftRules.containsPersonalData("Mon numéro de sécurité sociale 1 85 05 78 006 084 36"))
        assertFalse(DraftRules.containsPersonalData("Avant l'élection de 2027, 30 départements et 500 000 signatures"))
        assertFalse(DraftRules.containsPersonalData("Le SMIC à 1 800 euros nets en 2027"))
    }

    @Test
    fun aHugeTextIsScannedQuicklyAndReportedAsTooLong() {
        val huge = "a".repeat(60_000)
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val issues = DraftRules.check(draft(huge).copy(why = huge, asks = "x.".repeat(30_000)))
        assertTrue(started.elapsedNow().inWholeSeconds < 2, "the patterns stay linear on long texts")
        assertTrue(issues.any { it.kind == IssueKind.TOO_LONG && it.field == DraftField.TITLE && it.severity == IssueSeverity.BLOCKING })
        assertTrue(issues.any { it.kind == IssueKind.TOO_LONG && it.field == DraftField.WHY })
        assertTrue(DraftRules.containsPersonalData("x".repeat(5_000) + " ecrire a quelqu.un@exemple.example merci"))
    }
}
