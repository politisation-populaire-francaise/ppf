package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyDistribution
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-117: the past votes of the party of each candidate, sorted by a published rule of words, never by hand. */
class ComparatorVotesTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = ContentAddress.parse("30023:$author:$slug")

    private fun sheet(vararg tags: List<String>): ComparatorSheet? {
        val all = listOf(
            listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection présidentielle", ""),
            listOf(DeciderTags.THEME, "sante"),
            listOf(DeciderTags.THEME, "logement"),
            listOf(VoirTags.LINK, address("alex").toString(), "", DeciderTags.ROLE_CANDIDATE),
            listOf(VoirTags.LINK, address("camille").toString(), "", DeciderTags.ROLE_CANDIDATE),
        ) + tags.toList()
        val unsigned = Article.build(keys.publicKey, "comparateur", "[EXEMPLE] Comparateur", null, emptyList(), ArticleType.COMPARATEUR, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = all)
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return ComparatorSheet.from(Article.from(event)!!)
    }

    private val words = arrayOf(
        listOf(DeciderTags.THEME_WORDS, "sante", "santé", "hôpital", "médicament"),
        listOf(DeciderTags.THEME_WORDS, "logement", "logement", "loyer", "aide personnalisée"),
        listOf(DeciderTags.WORDS_VERSION, "1", "2026-11-20", "Première version"),
        listOf(DeciderTags.PARTY, "alex", "an:PO1", "2012-06-01", "2017-06-20"),
        listOf(DeciderTags.PARTY, "alex", "an:PO2", "2017-06-21", ""),
        listOf(DeciderTags.PARTY, "alex", "pe-org:7", "2019-07-01", ""),
        listOf(DeciderTags.PARTY, "camille", "senat-grp:X", "2006-10-01", ""),
    )

    private fun split(party: String, pour: Int, contre: Int = 0) = PartyDistribution(party, PartyBasis.PARTY, pour, contre, 0, 0, pour + contre)

    private fun ballot(id: String, date: String, title: String, vararg parties: PartyDistribution) =
        HistoryBallot(id = id, date = date, title = title, counts = Counts(inFavour = 1, against = 1, abstention = 0), parties = parties.toList())

    private val an14 = HistoryPackage(
        example = true, chamber = Chamber.AN, period = 14,
        parties = listOf(PartyRef("an:PO1", "[EXEMPLE] Parti des Collines", "PCO", PartyBasis.PARTY), PartyRef("an:PO2", "[EXEMPLE] Parti des Vallées", "PVA", PartyBasis.PARTY)),
        ballots = listOf(
            ballot("an:14:1", "2014-03-12", "[EXEMPLE] l'ensemble du projet de loi relatif à la santé", split("an:PO1", 10, 2), split("an:PO2", 1, 9)),
            ballot("an:14:2", "2015-01-20", "[EXEMPLE] l'amendement n° 4 sur les loyers des HLM", split("an:PO1", 3)),
            ballot("an:14:3", "2016-05-02", "[EXEMPLE] la motion de censure", split("an:PO2", 12)),
        ),
    )
    private val an15 = HistoryPackage(
        example = true, chamber = Chamber.AN, period = 15,
        parties = listOf(PartyRef("an:PO2", "[EXEMPLE] Parti des Vallées", "PVA", PartyBasis.PARTY)),
        ballots = listOf(ballot("an:15:7", "2018-02-01", "[EXEMPLE] l'article 2 du projet de loi sur l'aide personnalisée au logement", split("an:PO2", 30, 1))),
    )

    @Test
    fun theWordsAreReadAndTheirHistoryIsKept() {
        val sheet = assertNotNull(sheet(*words, listOf(DeciderTags.WORDS_VERSION, "2", "2027-01-10", "Ajout de « loyer » après un signalement")))
        assertEquals(listOf("sante", "logement"), sheet.themeWords.map { it.theme })
        assertEquals(2, sheet.currentWords?.version)
        assertEquals(emptyList(), sheet.ruleProblems().filter { "word" in it || "party" in it })
        assertNull(sheet(listOf(DeciderTags.THEME_WORDS, "sante")), "a theme without words")
        assertNull(sheet(listOf(DeciderTags.WORDS_VERSION, "1", "2026-11-20", "")), "a version without its reason")
        assertNull(sheet(listOf(DeciderTags.PARTY, "alex", "PO1", "2012-06-01", "")), "a party without the prefix of its assembly")
        assertNull(sheet(listOf(DeciderTags.PARTY, "alex", "an:PO1", "2017-06-01", "2012-06-01")), "a period that ends before it starts")
    }

    @Test
    fun theWordsAreFixedBeforeTheOfficialListAndNeverWithoutTheirHistory() {
        val late = assertNotNull(sheet(listOf(DeciderTags.OFFICIAL_LIST, "https://exemple.invalid/liste", "2026-11-01"), *words))
        assertTrue("the words were first published after the official list" in late.ruleProblems())
        val noHistory = assertNotNull(sheet(listOf(DeciderTags.THEME_WORDS, "sante", "santé")))
        assertTrue("words without their history (ppf-words-version)" in noHistory.ruleProblems())
        val stranger = assertNotNull(sheet(listOf(DeciderTags.THEME_WORDS, "chasse", "chasse"), listOf(DeciderTags.WORDS_VERSION, "1", "2026-11-20", "Première version")))
        assertTrue("words for chasse, which is not a theme of the sheet" in stranger.ruleProblems())
    }

    @Test
    fun theVotesFollowThePartyOfTheCandidateAtTheDateOfEachBallotNewestFirst() {
        val sheet = assertNotNull(sheet(*words))
        val alex = CandidateVotes.of(sheet, address("alex"), listOf(an14, an15))
        // 2014 and 2015: party PO1; from June 2017: PO2. The motion of 2016 was voted by PO2 only: not Alex's party then.
        assertEquals(listOf("an:15:7", "an:14:2", "an:14:1"), alex.all.map { it.ballot.id })
        val first = alex.all.last()
        assertEquals(10, first.distribution.inFavour)
        assertEquals(2, first.distribution.against)
        assertEquals("[EXEMPLE] Parti des Collines", first.party?.label)
        // Camille's group sat in none of these ballots: nothing, never a borrowed figure.
        assertEquals(emptyList(), CandidateVotes.of(sheet, address("camille"), listOf(an14, an15)).all)
    }

    @Test
    fun theThemesHelpToReadButNeverHideABallot() {
        val sheet = assertNotNull(sheet(*words))
        val alex = CandidateVotes.of(sheet, address("alex"), listOf(an14, an15))
        val health = assertNotNull(sheet.wordsOf("sante"))
        val housing = assertNotNull(sheet.wordsOf("logement"))
        assertEquals(listOf("an:14:1"), alex.inTheme(health).map { it.ballot.id }, "« santé » without its accent")
        // « loyer » starts « loyers »; « aide personnalisée » is found as a phrase.
        assertEquals(listOf("an:15:7", "an:14:2"), alex.inTheme(housing).map { it.ballot.id })
        assertEquals(emptyList(), alex.outsideEveryTheme(sheet.themeWords).map { it.ballot.id })
        val withCensure = CandidateVotes.of(sheet, address("alex"), listOf(an14, an15, an14.copy(period = 99, ballots = listOf(ballot("an:14:9", "2014-04-01", "[EXEMPLE] la motion de censure", split("an:PO1", 5))))))
        assertEquals(listOf("an:14:9"), withCensure.outsideEveryTheme(sheet.themeWords).map { it.ballot.id }, "listed apart, never dropped")
        assertEquals(listOf("an:14:9"), withCensure.search("censure").map { it.ballot.id })
        assertEquals(4, withCensure.all.size)
        assertTrue(!health.matches("[EXEMPLE] la sentence du tribunal"), "a word inside another word does not count")
    }
}
