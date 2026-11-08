package org.politisationpopulairefrancaise.core.editorial

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** « Vérifier un contenu PPF » (S-141): reading what a person gives, and the answers of the check. */
class ContentCheckTest {
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())

    private suspend fun article(signer: LocalEventSigner, title: String, updatedAt: Long, slug: String = "exemple") =
        signer.sign(Article.build(signer.publicKey, slug, title, null, emptyList(), ArticleType.FAIT, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "[EXEMPLE] Texte."))

    /** PPF's copy: [served] are the versions served now, by address. */
    private class Copy(private val trusted: Set<String>, private val served: List<SignedEvent>) : ContentLookup {
        override fun isTrusted(pubkeyHex: String) = pubkeyHex in trusted
        override fun byId(id: String) = served.firstOrNull { it.id == id }
        override fun byCode(prefix: String) = served.filter { it.id.startsWith(prefix) }
        override fun latestAt(address: ContentAddress) = served.firstOrNull { ContentAddress.of(it) == address }
    }

    @Test
    fun aPastedEventAnIdentifierOrAGroupedCodeIsRead() = runTest {
        val event = article(team, "[EXEMPLE] Un fait", 1_794_000_000)
        assertEquals(CheckQuery.Event(event), CheckQuery.parse("  ${event.toJson()}\n"))
        assertEquals(CheckQuery.Id(event.id), CheckQuery.parse(event.id.uppercase()))
        val code = ContentCheck.codeOf(event)
        assertEquals(16, code.length)
        assertEquals(CheckQuery.Code(code), CheckQuery.parse(code.chunked(4).joinToString(" ")))
        assertEquals(CheckQuery.Code(code), CheckQuery.parse(code.chunked(4).joinToString("-")))
        listOf("", "   ", "{ pas du JSON", "bonjour", "1234", "x".repeat(ContentCheck.MAX_INPUT_CHARS + 1)).forEach {
            assertEquals(CheckQuery.Unreadable, CheckQuery.parse(it), it.take(20))
        }
    }

    @Test
    fun aVerificationLinkIsFollowedOnlyOnADomainOfPpf() {
        val code = "dc90c95f09947507"
        assertEquals(CheckQuery.Code(code), CheckQuery.parse(OfficialSite.verificationUrl(code)))
        assertEquals(CheckQuery.Code(code), CheckQuery.parse("https://www.politisation-populaire-francaise.fr/v/${code.uppercase()}"))
        assertEquals(CheckQuery.Code(code), CheckQuery.parse("politisation-populaire-francaise.app/v/$code"))
        assertEquals(CheckQuery.Link("politisation-populaire-francaise.org", true), CheckQuery.parse("https://politisation-populaire-francaise.org/voir/faits/exemple"))
        // Look-alikes, user names and subdomains of other sites are never PPF, whatever the page says.
        listOf(
            "https://politisation-populaire-francaise.info/v/$code" to "politisation-populaire-francaise.info",
            "https://politisation-populaire-francaise.org.piege.example/v/$code" to "politisation-populaire-francaise.org.piege.example",
            "https://piege.example/politisation-populaire-francaise.org/v/$code" to "piege.example",
            "https://politisation-populaire-francaise.org@piege.example/v/$code" to "piege.example",
        ).forEach { (link, host) -> assertEquals(CheckQuery.Link(host, false), CheckQuery.parse(link), link) }
        assertEquals(CheckOutcome.UNOFFICIAL_LINK, ContentCheck.check(CheckQuery.parse("https://piege.example/v/$code"), Copy(emptySet(), emptyList())).outcome)
        assertEquals(CheckOutcome.OFFICIAL_LINK, ContentCheck.check(CheckQuery.parse("https://politisation-populaire-francaise.org/"), Copy(emptySet(), emptyList())).outcome)
    }

    @Test
    fun theCheckSaysWhetherAContentIsAuthenticCurrentOutdatedOrNotFromPpf() = runTest {
        val old = article(team, "[EXEMPLE] Première version", 1_794_000_000)
        val current = article(team, "[EXEMPLE] Version corrigée", 1_794_100_000)
        val newer = article(team, "[EXEMPLE] Version à venir", 1_794_200_000)
        val other = article(team, "[EXEMPLE] Retiré", 1_794_000_000, slug = "retire")
        val strangers = article(stranger, "[EXEMPLE] Faux contenu de PPF", 1_794_000_000)
        val copy = Copy(setOf(team.publicKey.hex), listOf(current))

        assertEquals(CheckResult(CheckOutcome.AUTHENTIC_CURRENT, current, current), ContentCheck.check(CheckQuery.Event(current), copy))
        assertEquals(CheckResult(CheckOutcome.AUTHENTIC_OUTDATED, old, current), ContentCheck.check(CheckQuery.Event(old), copy))
        assertEquals(CheckOutcome.AUTHENTIC_UNLISTED, ContentCheck.check(CheckQuery.Event(newer), copy).outcome, "more recent than this copy")
        assertEquals(CheckOutcome.AUTHENTIC_UNLISTED, ContentCheck.check(CheckQuery.Event(other), copy).outcome, "withdrawn or not received")
        assertEquals(CheckOutcome.NOT_PPF_KEY, ContentCheck.check(CheckQuery.Event(strangers), copy).outcome)
        // One changed letter, the signature kept: altered.
        val altered = current.copy(content = current.content.replace("Texte", "Textes"))
        assertEquals(CheckOutcome.ALTERED, ContentCheck.check(CheckQuery.Event(altered), copy).outcome)
        assertEquals(CheckOutcome.ALTERED, ContentCheck.check(CheckQuery.parse(altered.toJson()), copy).outcome)

        // By identifier or code: PPF's copy gives the event, then the same check.
        assertEquals(CheckOutcome.AUTHENTIC_CURRENT, ContentCheck.check(CheckQuery.Id(current.id), copy).outcome)
        val byCode = ContentCheck.check(CheckQuery.parse(ContentCheck.codeOf(current)), copy)
        assertEquals(CheckOutcome.AUTHENTIC_CURRENT, byCode.outcome)
        assertEquals(current, byCode.event)
        assertEquals(CheckOutcome.UNKNOWN, ContentCheck.check(CheckQuery.Id("0".repeat(64)), copy).outcome)
        assertEquals(CheckOutcome.UNKNOWN, ContentCheck.check(CheckQuery.Code("0".repeat(16)), copy).outcome)
        assertIs<CheckQuery.Unreadable>(CheckQuery.parse("rien"))
        assertEquals(CheckOutcome.UNREADABLE, ContentCheck.check(CheckQuery.Unreadable, copy).outcome)
    }
}
