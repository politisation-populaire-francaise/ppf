package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacteTest {

    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = "30023:$author:$slug"

    private fun block(slug: String, type: ArticleType, title: String, vararg tags: List<String>, updatedAt: Long = 1_794_000_000): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "Corps.", extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private fun link(slug: String, role: String) = listOf(VoirTags.LINK, address(slug), "", role)

    private val measures = listOf(
        listOf(DeciderTags.MEASURE, "ric", "Référendum d'initiative citoyenne", "Déposer dans les six mois un projet de révision", "Constitution, article 11"),
        listOf(DeciderTags.MEASURE, "blanc", "Vote blanc", "Compter les bulletins blancs", "Code électoral, article L. 65"),
        listOf(DeciderTags.MEASURE, "petitions", "Pétitions", "Vote obligatoire au-delà d'un seuil", ""),
    )

    private fun pacte(
        status: String = "envoye",
        candidates: List<String> = listOf("zoe", "alex", "camille"),
        sent: String? = "2027-01-10",
        deadline: String? = "2027-02-10",
        extra: List<List<String>> = measures,
    ) = block(
        "pacte", ArticleType.PACTE, "[EXEMPLE] Pacte",
        listOf(DeciderTags.ELECTION, "presidentielle-2027", "Élection présidentielle de 2027", ""),
        listOf(DeciderTags.PACTE_STATUS, status),
        *(listOfNotNull(sent?.let { listOf(DeciderTags.SENT, it) }, deadline?.let { listOf(DeciderTags.DEADLINE, it) }) + extra + candidates.map { link(it, DeciderTags.ROLE_CANDIDATE) }).toTypedArray(),
    )

    private fun engagement(slug: String, candidate: String, fingerprint: String, received: String, vararg answers: String, updatedAt: Long = 1_794_000_000) = block(
        slug, ArticleType.ENGAGEMENT, "Réponse de $candidate",
        link("pacte", DeciderTags.ROLE_PACTE),
        link(candidate, DeciderTags.ROLE_CANDIDATE),
        listOf(DeciderTags.PACTE_TEXT, fingerprint),
        listOf(DeciderTags.RECEIVED, received),
        *answers.map { listOf(DeciderTags.ANSWER) + it.split('|').map(String::trim) }.toTypedArray(),
        updatedAt = updatedAt,
    )

    private fun person(slug: String, name: String) = block(slug, ArticleType.PERSONNE, name)

    private val voir = VoirCatalog.from(listOf(person("zoe", "Zoé Durand"), person("alex", "[EXEMPLE] Alex Modèle"), person("camille", "Camille Été")))

    @Test
    fun readsAPactWithItsMeasuresInOrder() {
        val pacte = assertNotNull(Pacte.from(pacte()))
        assertEquals(PacteStatus.SENT, pacte.status)
        assertEquals(listOf("ric", "blanc", "petitions"), pacte.measures.map { it.id })
        assertNull(pacte.measure("petitions")!!.textToChange, "an empty field means unknown")
        assertEquals(CivilDate(2027, 2, 10), pacte.deadline)
        assertEquals(3, pacte.candidates.size)
        assertEquals(64, pacte.fingerprint.length)
    }

    @Test
    fun aSentPactAlwaysShowsTheSameDateAndDeadlineForAll() {
        assertNull(Pacte.from(pacte(deadline = null)), "no deadline")
        assertNull(Pacte.from(pacte(sent = null)), "no sending day")
        assertNull(Pacte.from(pacte(sent = "2027-02-11", deadline = "2027-02-10")), "deadline before sending")
        assertNotNull(Pacte.from(pacte(status = "proposee", sent = null, deadline = null)), "a starting list is not sent yet")
    }

    @Test
    fun aPactWithAMeasureTwiceOrTooManyMeasuresIsUnreadable() {
        assertNull(Pacte.from(pacte(extra = measures + listOf(measures[0]))))
        val eleven = (1..11).map { listOf(DeciderTags.MEASURE, "m$it", "Mesure $it", "Engagement $it", "") }
        assertNull(Pacte.from(pacte(extra = eleven)))
        assertNull(Pacte.from(pacte(extra = emptyList())))
    }

    @Test
    fun theFingerprintChangesWithAnyWordOfTheText() {
        val a = Pacte.from(pacte())!!
        val changed = measures.toMutableList().also { it[1] = listOf(DeciderTags.MEASURE, "blanc", "Vote blanc", "Compter les bulletins blancs et nuls", "Code électoral, article L. 65") }
        val b = Pacte.from(pacte(extra = changed))!!
        assertNotEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.fingerprint, Pacte.from(pacte(candidates = listOf("alex")))!!.fingerprint, "the list of candidates is not part of the text sent")
    }

    @Test
    fun everyCandidateHasALineInAlphabeticalOrderWhateverTheAnswers() {
        val pacte = Pacte.from(pacte())!!
        val catalog = DeciderCatalog.from(
            listOf(
                pacte.article,
                engagement("r-zoe", "zoe", pacte.fingerprint, "2027-01-20", "ric | signe", "blanc | signe", "petitions | signe"),
                engagement("r-camille", "camille", pacte.fingerprint, "2027-01-25", "ric | refuse | Contre le principe", "blanc | signe"),
            ),
        )
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 2, 1))
        assertEquals(listOf("[EXEMPLE] Alex Modèle", "Camille Été", "Zoé Durand"), board.rows.map { it.name }, "alphabetical, the example marker and accents aside")
        val alex = board.rows[0]
        assertNull(alex.engagement)
        assertEquals(listOf(CellStatus.AWAITING, CellStatus.AWAITING, CellStatus.AWAITING), alex.cells.map { it.status }, "before the deadline")
        val camille = board.rows[1]
        assertEquals(listOf(CellStatus.REFUSED, CellStatus.SIGNED, CellStatus.AWAITING), camille.cells.map { it.status })
        assertEquals("Contre le principe", camille.cells[0].comment)
        assertEquals(3, board.rows[2].signed)
        assertEquals(listOf(1, 2, 1), board.summaries.map { it.signed })
        assertEquals(2, board.answeredCandidates)
        assertFalse(board.deadlinePassed)
    }

    @Test
    fun afterTheDeadlineSilenceIsNoAnswerAndALateAnswerIsNotCounted() {
        val pacte = Pacte.from(pacte())!!
        val catalog = DeciderCatalog.from(listOf(pacte.article, engagement("r-zoe", "zoe", pacte.fingerprint, "2027-02-12", "ric | signe")))
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 2, 13))
        assertTrue(board.deadlinePassed)
        assertEquals(CellStatus.NO_ANSWER, board.row(ContentAddress.parse(address("alex")))!!.cells[0].status)
        val zoe = board.row(ContentAddress.parse(address("zoe")))!!
        assertNull(zoe.engagement, "an answer received after the deadline is not counted (D-062)")
        assertEquals(listOf(CellStatus.NO_ANSWER, CellStatus.NO_ANSWER, CellStatus.NO_ANSWER), zoe.cells.map { it.status })
        assertEquals(0, board.answeredCandidates)
        assertEquals(listOf(SetAsideReason.LATE), board.setAside.map { it.reason }, "but it stays visible below the table")
        assertEquals("r-zoe", board.setAside.single().engagement.article.slug)
    }

    @Test
    fun anAnswerReceivedOnTheDeadlineCountsAndALaterChangeDoesNot() {
        val pacte = Pacte.from(pacte())!!
        assertEquals(CivilDate(2027, 2, 10), pacte.deadline)
        val catalog = DeciderCatalog.from(
            listOf(
                pacte.article,
                engagement("r-zoe-1", "zoe", pacte.fingerprint, "2027-02-10", "ric | refuse"),
                engagement("r-zoe-2", "zoe", pacte.fingerprint, "2027-02-11", "ric | signe"),
            ),
        )
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 2, 12))
        val zoe = board.row(ContentAddress.parse(address("zoe")))!!
        assertEquals("r-zoe-1", zoe.engagement!!.article.slug)
        assertEquals(CellStatus.REFUSED, zoe.cells[0].status)
        assertEquals(listOf("r-zoe-2"), board.setAside.filter { it.reason == SetAsideReason.LATE }.map { it.engagement.article.slug })
    }

    @Test
    fun anAnswerToAnotherTextOrFromSomeoneElseIsNotCounted() {
        val pacte = Pacte.from(pacte())!!
        val other = "a".repeat(64)
        val catalog = DeciderCatalog.from(
            listOf(
                pacte.article,
                engagement("r-zoe", "zoe", other, "2027-01-20", "ric | signe"),
                engagement("r-intrus", "intrus", pacte.fingerprint, "2027-01-20", "ric | signe"),
            ),
        )
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 1, 21))
        assertEquals(3, board.rows.size, "no line for someone who is not a candidate")
        assertNull(board.row(ContentAddress.parse(address("zoe")))!!.engagement)
        assertEquals(setOf(SetAsideReason.OTHER_TEXT, SetAsideReason.NOT_A_CANDIDATE), board.setAside.map { it.reason }.toSet())
    }

    @Test
    fun theLatestAnswerOfACandidateCounts() {
        val pacte = Pacte.from(pacte())!!
        val catalog = DeciderCatalog.from(
            listOf(
                pacte.article,
                engagement("r-zoe-1", "zoe", pacte.fingerprint, "2027-01-20", "ric | refuse"),
                engagement("r-zoe-2", "zoe", pacte.fingerprint, "2027-01-28", "ric | signe"),
            ),
        )
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 1, 30))
        assertEquals(CellStatus.SIGNED, board.row(ContentAddress.parse(address("zoe")))!!.cells[0].status)
    }

    @Test
    fun anAnswerNeedsItsPactItsCandidateItsTextAndOneAnswerPerMeasure() {
        val fingerprint = "b".repeat(64)
        assertNotNull(Engagement.from(engagement("ok", "zoe", fingerprint, "2027-01-20", "ric | signe")))
        assertNull(Engagement.from(engagement("bad-hash", "zoe", "xyz", "2027-01-20", "ric | signe")))
        assertNull(Engagement.from(engagement("no-date", "zoe", fingerprint, "", "ric | signe")))
        assertNull(Engagement.from(engagement("twice", "zoe", fingerprint, "2027-01-20", "ric | signe", "ric | refuse")))
        val unknownStatus = Engagement.from(engagement("maybe", "zoe", fingerprint, "2027-01-20", "ric | peut-etre", "blanc | signe"))!!
        assertEquals(listOf("blanc"), unknownStatus.answers.map { it.measureId }, "an unknown answer is no answer")
    }

    @Test
    fun aStartingListHasNoTableYet() {
        val catalog = DeciderCatalog.from(listOf(pacte(status = "proposee", sent = null, deadline = null)))
        val board = catalog.board(catalog.pactes.single(), voir, today = CivilDate(2027, 1, 1))
        assertFalse(board.isOpenForAnswers)
        assertTrue(board.rows.isEmpty())
    }

    @Test
    fun factsOfVoirFollowAMeasureAfterTheElection() {
        val pacte = Pacte.from(pacte(status = "clos"))!!
        val vote = block(
            "vote-zoe", ArticleType.FAIT, "Vote",
            listOf(VoirTags.FACT, "vote"), listOf(VoirTags.DATE, "2027-09-01"), listOf(VoirTags.FACT_STATUS, "etabli"), listOf(VoirTags.VOTE, "pour"),
            link("zoe", VoirTags.ROLE_PERSON), link("pacte", DeciderTags.ROLE_PACTE), listOf(DeciderTags.PACTE_MEASURE, "blanc"),
        )
        val voirWithFact = VoirCatalog.from(listOf(person("zoe", "Zoé Durand"), person("alex", "Alex"), person("camille", "Camille"), vote))
        val catalog = DeciderCatalog.from(listOf(pacte.article))
        val board = catalog.board(catalog.pactes.single(), voirWithFact, today = CivilDate(2027, 9, 2))
        val zoe = board.row(ContentAddress.parse(address("zoe")))!!
        assertEquals(1, zoe.cells[1].followUps.size)
        assertTrue(zoe.cells[0].followUps.isEmpty())
        assertEquals(CellStatus.NO_ANSWER, zoe.cells[0].status, "the election is over")
    }
}
