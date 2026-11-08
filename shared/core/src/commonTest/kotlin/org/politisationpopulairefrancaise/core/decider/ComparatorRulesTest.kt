package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rounds and the freeze (Q4 option A), the requests of the candidates and the official list (Q1 option B). */
class ComparatorRulesTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun address(slug: String) = ContentAddress.parse("30023:$author:$slug")

    private fun sheet(vararg tags: List<String>, candidates: List<String> = listOf("alex", "camille")): ComparatorSheet? {
        val all = listOf(
            listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection présidentielle", ""),
            listOf(DeciderTags.THEME, "sante"),
        ) + candidates.map { listOf(VoirTags.LINK, address(it).toString(), "", DeciderTags.ROLE_CANDIDATE) } + tags.toList()
        val unsigned = Article.build(keys.publicKey, "comparateur", "[EXEMPLE] Comparateur", null, emptyList(), ArticleType.COMPARATEUR, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = all)
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return ComparatorSheet.from(Article.from(event)!!)
    }

    private fun round(vararg fields: String) = listOf(DeciderTags.ROUND, *fields)
    private fun request(vararg fields: String) = listOf(DeciderTags.REQUEST, *fields)
    private val delay = listOf(DeciderTags.ADDITION_DELAY, "7")

    @Test
    fun theFreezeRunsFromMidnightTheDayBeforeTheFirstVotingDayUntilThePollingStationsClose() {
        // Summer time: overseas votes on Saturday 10 April, mainland France on Sunday 11 April 2027.
        val round = assertNotNull(ElectionRound.parse(listOf("1", "2027-04-11", "2027-04-10", "20:00")))
        assertEquals(1_807_221_600, round.freezeStart, "Friday 9 April, 0 h in Paris = 22:00 UTC the day before")
        assertEquals(1_807_466_400, round.freezeEnd, "Sunday 11 April, 20 h in Paris = 18:00 UTC")
        assertTrue(!round.isFrozen(1_807_221_599))
        assertTrue(round.isFrozen(1_807_221_600))
        assertTrue(round.isFrozen(1_807_466_399))
        assertTrue(!round.isFrozen(1_807_466_400), "readable and publishable again once the last stations closed")
        // Winter time, the same day everywhere, 20 h by default.
        val winter = assertNotNull(ElectionRound.parse(listOf("2", "2027-03-14", null, null)))
        assertEquals(CivilDate(2027, 3, 14), winter.firstDay)
        assertEquals(1_804_892_400, winter.freezeStart)
        assertEquals(1_805_050_800, winter.freezeEnd)
    }

    @Test
    fun aWrongRoundIsNeverIgnoredTheWholeSheetIsNotRead() {
        assertNotNull(sheet(round("1", "2027-04-11"), delay, request("alex", "2026-10-01", "2026-10-02"), request("camille", "2026-10-03", "2026-10-03")))
        assertNull(sheet(round("1", "11/04/2027")), "a date that cannot be read")
        assertNull(sheet(round("1", "2027-04-11", "2027-04-12")), "overseas cannot vote after mainland France")
        assertNull(sheet(round("1", "2027-04-11", "2027-04-01")), "more than two days earlier")
        assertNull(sheet(round("1", "2027-04-11", "", "20h")), "a closing hour that cannot be read")
        assertNull(sheet(round("1", "2027-04-11"), round("1", "2027-04-25")), "two rounds with the same number")
        assertNull(sheet(request("alex", "2026-10-05", "2026-10-01")), "added before being asked")
        assertNull(sheet(listOf(DeciderTags.ADDITION_DELAY, "90")), "a delay longer than a month")
        assertNull(sheet(listOf(DeciderTags.OFFICIAL_LIST, "ftp://exemple.invalid", "2027-03-01")), "the official list is a web page")
    }

    @Test
    fun theFrozenRoundIsTheOneWhoseWindowHolds() {
        val sheet = assertNotNull(sheet(round("2", "2027-04-25", "2027-04-24"), round("1", "2027-04-11", "2027-04-10")))
        assertEquals(listOf(1, 2), sheet.rounds.map { it.number })
        assertEquals(1, sheet.frozenRound(1_807_300_000)?.number)
        assertNull(sheet.frozenRound(1_794_139_200), "8 November 2026: nothing is frozen")
    }

    @Test
    fun beforeTheOfficialListEveryCandidateHasARequestHonouredWithinTheSameDelay() {
        val ok = assertNotNull(
            sheet(round("1", "2027-04-11"), delay, request("alex", "2026-10-01", "2026-10-03"), request("camille", "2026-10-02", "2026-10-09")),
        )
        assertEquals(emptyList(), ok.ruleProblems())
        assertEquals(CivilDate(2026, 10, 1), ok.requestOf(address("alex"))?.requestedOn)
        assertEquals(7L, ok.requestOf(address("camille"))?.delayDays)

        val missing = assertNotNull(sheet(round("1", "2027-04-11"), delay, request("alex", "2026-10-01", "2026-10-03")))
        assertEquals(listOf("no request for camille before the official list"), missing.ruleProblems())

        val late = assertNotNull(sheet(round("1", "2027-04-11"), delay, request("alex", "2026-10-01", "2026-10-03"), request("camille", "2026-10-01", "2026-10-20")))
        assertEquals(listOf("camille added after 19 days, more than 7"), late.ruleProblems())

        val stranger = assertNotNull(
            sheet(round("1", "2027-04-11"), delay, request("alex", "2026-10-01", "2026-10-01"), request("camille", "2026-10-01", "2026-10-01"), request("dominique", "2026-10-01", "2026-10-01")),
        )
        assertEquals(listOf("request of dominique, who is not a candidate of the sheet"), stranger.ruleProblems())

        val noRound = assertNotNull(sheet(delay, request("alex", "2026-10-01", "2026-10-01"), request("camille", "2026-10-01", "2026-10-01")))
        assertEquals(listOf("no round (ppf-round): the comparator could not be frozen"), noRound.ruleProblems())
    }

    @Test
    fun withTheOfficialListTheComparatorFollowsItAndTheEarlierRequestsStay() {
        val sheet = assertNotNull(
            sheet(
                round("1", "2027-04-11"),
                delay,
                listOf(DeciderTags.OFFICIAL_LIST, "https://exemple.invalid/liste-officielle", "2027-03-15"),
                request("alex", "2026-10-01", "2026-10-03"),
            ),
        )
        assertEquals(emptyList(), sheet.ruleProblems(), "camille comes from the official list, without a request")
        assertEquals(CivilDate(2027, 3, 15), sheet.officialList?.date)
        assertNotNull(sheet.requestOf(address("alex")))
        assertNull(sheet.requestOf(address("camille")))
    }

    @Test
    fun thePhotosAreShownOnlyWhenEveryCandidateHasOneWithItsRights() {
        fun photo(candidate: String, sha: Char) = listOf(DeciderTags.PHOTO, candidate, sha.toString().repeat(64), "Licence ouverte 2.0", "[EXEMPLE] Service photo", "https://exemple.invalid/photo")
        val all = assertNotNull(sheet(photo("alex", 'a'), photo("camille", 'b')))
        assertTrue(all.photosShown)
        assertEquals("a".repeat(64), all.photoOf(address("alex"))?.sha256)
        val one = assertNotNull(sheet(photo("alex", 'a')))
        assertTrue(!one.photosShown, "one missing: none for anybody")
        assertNull(sheet(listOf(DeciderTags.PHOTO, "alex", "a".repeat(64), "", "[EXEMPLE] Auteur", "https://exemple.invalid/photo")), "a photo without its licence")
        assertNull(sheet(listOf(DeciderTags.PHOTO, "alex", "A".repeat(64), "Licence", "Auteur", "https://exemple.invalid/photo")), "a fingerprint is lowercase hexadecimal")
        assertNull(sheet(listOf(DeciderTags.PHOTO, "alex", "a".repeat(64), "Licence", "Auteur", "http://exemple.invalid/photo")), "the source is a page in https")
        assertNull(sheet(photo("alex", 'a'), photo("alex", 'b')), "two photos for one candidate")
        val stranger = assertNotNull(sheet(photo("alex", 'a'), photo("camille", 'b'), photo("dominique", 'c')))
        assertTrue("photo of dominique, who is not a candidate of the sheet" in stranger.ruleProblems())
    }
}
