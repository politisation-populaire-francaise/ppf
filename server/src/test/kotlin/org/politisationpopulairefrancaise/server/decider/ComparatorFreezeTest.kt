package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.DeciderTags
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DE-16, Q4 option A: nothing of a comparator is published from 0 h the day before the vote until the stations close. */
class ComparatorFreezeTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    /** Round 1: overseas on Saturday 10 April 2027, mainland on Sunday 11 April; frozen from 1_807_221_600 to 1_807_466_400. */
    private val frozenTime = 1_807_300_000L
    private val before = 1_807_221_599L
    private val after = 1_807_466_400L

    private fun signed(kind: Int, tags: List<List<String>>, content: String = "", createdAt: Long = 1_794_000_000): SignedEvent =
        SignedEvent("%064x".format(tags.hashCode().toLong() and 0xffffffffL), author, createdAt, kind, tags, content, "0".repeat(128))

    private fun article(slug: String, type: ArticleType, title: String, vararg tags: List<String>, updatedAt: Long = 1_794_000_000): SignedEvent {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "Corps.", extraTags = tags.toList())
        return SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
    }

    private fun election(id: String = "presidentielle-2027") = listOf(DeciderTags.ELECTION, id, "[EXEMPLE] Élection", "")
    private val candidate = listOf(VoirTags.LINK, "30023:$author:alex", "", DeciderTags.ROLE_CANDIDATE)

    private fun comparator(electionId: String = "presidentielle-2027", round: List<String> = listOf(DeciderTags.ROUND, "1", "2027-04-11", "2027-04-10", "20:00"), updatedAt: Long = 1_794_000_000) =
        article("comparateur-$electionId", ArticleType.COMPARATEUR, "[EXEMPLE] Comparateur", election(electionId), listOf(DeciderTags.THEME, "sante"), candidate, round, updatedAt = updatedAt)

    private fun position(electionId: String = "presidentielle-2027", updatedAt: Long = 1_794_000_000) = article(
        "position-alex-sante-$electionId", ArticleType.POSITION, "[EXEMPLE] Une position.",
        listOf(DeciderTags.ELECTION, electionId), candidate, listOf(DeciderTags.THEME, "sante"),
        listOf(DeciderTags.QUOTE, "[EXEMPLE] Des mots.", "https://exemple.invalid/programme", "2027-01-15"),
        updatedAt = updatedAt,
    )

    private fun freeze(at: Long, vararg stored: SignedEvent): ComparatorFreeze {
        val catalog = DeciderCatalog.from(stored.mapNotNull(Article::from))
        return ComparatorFreeze({ catalog }, Clock.fixed(Instant.ofEpochSecond(at), ZoneOffset.UTC))
    }

    @Test
    fun aPositionOrANewVersionOfTheComparatorIsRefusedOnlyWhileTheElectionIsFrozen() = runBlocking {
        val stored = arrayOf(comparator(), position())
        val refused = assertNotNull(freeze(frozenTime, *stored).refusal(position(updatedAt = 1_807_300_000)))
        assertEquals(409, refused.status)
        assertEquals("comparator_frozen", refused.error)
        // A new version that moves the round away cannot lift the freeze of the stored one.
        val moved = comparator(round = listOf(DeciderTags.ROUND, "1", "2027-05-30"), updatedAt = 1_807_300_000)
        assertNotNull(freeze(frozenTime, *stored).refusal(moved))
        assertNull(freeze(before, *stored).refusal(position(updatedAt = 1_807_221_000)), "the day before the freeze")
        assertNull(freeze(after, *stored).refusal(position(updatedAt = 1_807_466_400)), "once the last stations closed")
        // Another election is not frozen, and the other contents of "Décider" never are.
        assertNull(freeze(frozenTime, *stored).refusal(position(electionId = "legislatives-2027")))
        val pacte = article("pacte", ArticleType.PACTE, "[EXEMPLE] Pacte", election())
        assertNull(freeze(frozenTime, *stored).refusal(pacte))
    }

    @Test
    fun aFirstComparatorWhoseOwnRoundIsFrozenNowIsRefused() = runBlocking {
        assertNotNull(freeze(frozenTime).refusal(comparator()))
        assertNull(freeze(before).refusal(comparator()))
    }

    @Test
    fun aDeletionOfAFrozenComparatorOrOfOneOfItsPositionsIsRefused() = runBlocking {
        val sheet = comparator()
        val held = position()
        val byAddress = signed(EventKinds.DELETION_REQUEST, listOf(listOf("a", "30023:$author:position-alex-sante-presidentielle-2027")))
        val byId = signed(EventKinds.DELETION_REQUEST, listOf(listOf("e", sheet.id)))
        val unrelated = signed(EventKinds.DELETION_REQUEST, listOf(listOf("a", "30023:$author:une-autre-fiche")))
        assertNotNull(freeze(frozenTime, sheet, held).refusal(byAddress))
        assertNotNull(freeze(frozenTime, sheet, held).refusal(byId))
        assertNull(freeze(frozenTime, sheet, held).refusal(unrelated))
        assertNull(freeze(after, sheet, held).refusal(byAddress))
    }
}
