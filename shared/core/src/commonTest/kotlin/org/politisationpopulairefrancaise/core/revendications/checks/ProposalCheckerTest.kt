package org.politisationpopulairefrancaise.core.revendications.checks

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.ALL
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.CANTINE
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.ECOLES
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.draft
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProposalCheckerTest {
    private val now = 1_793_000_000L

    @Test
    fun onTheDeviceTheLegalAndAiChecksWaitForTheServer() {
        val report = ProposalChecker(clock = { now }).checkOnDevice(draft("Cantine à un euro pour les familles modestes"), ALL)
        assertEquals(CheckState.DONE, report.state(CheckKind.RULES))
        assertEquals(CheckState.DONE, report.state(CheckKind.SIMILARITY))
        assertEquals(CheckState.AFTER_SENDING, report.state(CheckKind.LEGAL))
        assertEquals(CheckState.AFTER_SENDING, report.state(CheckKind.AI))
        assertEquals(CANTINE.address, report.issues(IssueKind.DUPLICATE).single().related.first().address)
        assertTrue(report.canSubmit, "a duplicate is a warning: the author may still send")
        assertEquals(ALL.size, report.checks.first { it.kind == CheckKind.SIMILARITY }.compared)
        assertEquals(now, report.checkedAt)
    }

    @Test
    fun blockingIssuesComeFirstAndPreventSending() {
        val report = ProposalChecker(clock = { now }).checkOnDevice(draft("Augmenter le SMIC et baisser la TVA", themes = emptyList()), ALL)
        assertFalse(report.canSubmit)
        assertEquals(IssueSeverity.BLOCKING, report.issues.first().severity)
        assertTrue(report.issues(IssueKind.SEVERAL_IDEAS).isNotEmpty())
    }

    @Test
    fun aCloseDemandAlreadyRetainedIsPointedAt() {
        val report = ProposalChecker(clock = { now }).checkOnDevice(
            draft("Rénover les écoles du Nord contre les fortes chaleurs", territory = "Nord", themes = listOf("education")),
            ALL,
        )
        assertEquals(ECOLES.address, report.issues(IssueKind.ALREADY_ADVANCED).single().related.single().address)
    }

    @Test
    fun withoutCorpusNorModelTheServerSaysSo() = runTest {
        val report = ProposalChecker(clock = { now }).checkOnServer(draft("Un médecin de garde joignable le soir dans chaque canton"), ALL)
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.LEGAL))
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.AI))
    }

    @Test
    fun theModelSeesTheClosestDemandsAndItsFindingsAreMergedWithoutRepetition() = runTest {
        var seen: ReviewRequest? = null
        val model = ProposalReviewModel { request ->
            seen = request
            ReviewOutcome.Reviewed(
                listOf(
                    // the words already found this duplicate: not repeated
                    ProposalIssue(IssueKind.DUPLICATE, IssueSeverity.WARNING, IssueSource.AI, related = listOf(RelatedDemand(CANTINE.address, CANTINE.title, 1.0))),
                    ProposalIssue(IssueKind.EXISTING_LAW, IssueSeverity.INFO, IssueSource.AI, detail = "Une aide existe déjà dans certaines communes."),
                ),
                model = "modele-de-test",
            )
        }
        val corpus = LegalCorpus { _, _ -> LegalSearch.Found(listOf(LegalReference(title = "Texte [EXEMPLE]"))) }
        val report = ProposalChecker(corpus, model, clock = { now }).checkOnServer(draft("Cantine à un euro pour les familles modestes"), ALL)

        assertEquals(CANTINE.address, seen!!.candidates.first().address, "closest first")
        assertTrue(seen!!.legal.single().verified, "what the corpus found is verified")
        assertEquals(1, report.issues(IssueKind.DUPLICATE).size)
        assertEquals(IssueSource.SIMILARITY, report.issues(IssueKind.DUPLICATE).single().source)
        assertEquals(IssueSource.AI, report.issues(IssueKind.EXISTING_LAW).single().source)
        assertEquals("modele-de-test", report.checks.first { it.kind == CheckKind.AI }.model)
        assertEquals(CheckState.DONE, report.state(CheckKind.LEGAL))
    }

    @Test
    fun aFailingServiceIsReportedAndNeverThrown() = runTest {
        val model = ProposalReviewModel { error("unreachable") }
        val corpus = LegalCorpus { _, _ -> LegalSearch.Unavailable() }
        val report = ProposalChecker(corpus, model, clock = { now }).checkOnServer(draft("Un médecin de garde joignable le soir dans chaque canton"), ALL)
        assertEquals(CheckState.FAILED, report.state(CheckKind.AI))
        assertEquals(CheckState.FAILED, report.state(CheckKind.LEGAL))
        assertTrue(report.canSubmit)
    }

    @Test
    fun reportsSurviveTheirJsonForm() {
        val report = ProposalChecker(clock = { now }).checkOnDevice(draft("Rénover les écoles du Nord contre les fortes chaleurs", territory = "Nord", themes = listOf("education")), ALL)
        val json = Json { encodeDefaults = false }
        val text = json.encodeToString(ProposalReport.serializer(), report)
        assertTrue("\"already_advanced\"" in text && "\"checked_at\"" in text && "\"retenue\"" in text)
        assertEquals(report, Json.decodeFromString(ProposalReport.serializer(), text))
        assertEquals(RevendicationStatus.RETENUE, report.issues(IssueKind.ALREADY_ADVANCED).single().related.single().status)
    }

    @Test
    fun aDraftBecomesANewDemandEventOfItsAuthor() {
        val keys = IdentityKeys.generate()
        val draft = RevendicationDraft(
            title = "  Un médecin de garde joignable le soir dans chaque canton ",
            summary = "",
            why = "Le soir, il faut aller aux urgences.",
            asks = "Une permanence par canton.",
            themes = listOf("sante", "services-publics"),
            territory = " ",
        )
        val unsigned = draft.toUnsignedEvent(keys.publicKey, now, slug = "medecin-garde-canton-0001")
        assertEquals(Article.KIND, unsigned.kind)
        assertEquals("medecin-garde-canton-0001", unsigned.tags.first { it[0] == "d" }[1])
        assertEquals(listOf("sante", "services-publics"), unsigned.tags.filter { it[0] == "t" }.map { it[1] })
        assertEquals(ArticleType.REVENDICATION.tagValue, unsigned.tags.first { it[0] == Article.TAG_TYPE }[1])
        assertEquals(RevendicationStatus.NOUVELLE.tagValue, unsigned.tags.first { it[0] == Article.TAG_STATUS }[1])
        assertEquals("France", unsigned.tags.first { it[0] == Article.TAG_TERRITORY }[1])
        assertTrue(unsigned.tags.none { it[0] == Article.TAG_SUMMARY })
        assertEquals("## Pourquoi\n\nLe soir, il faut aller aux urgences.\n\n## Ce que la revendication demande\n\nUne permanence par canton.", unsigned.content)

        val slug = RevendicationDraft.slugFor("Un médecin de garde, le soir !", Random(1))
        assertTrue(Regex("medecin-garde-soir-[0-9a-f]{4}").matches(slug), slug)
        assertTrue(Regex("revendication-[0-9a-f]{4}").matches(RevendicationDraft.slugFor("!!!", Random(2))))
    }
}
