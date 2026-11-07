package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.callTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.nowSeconds
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withTag
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withoutTag
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The shape of the contents of the Agir space (contract, sections 2 to 4), through the publication policy. */
class AgirRulesTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(editor.publicKey.hex))

    private fun allowed(event: SignedEvent) = assertEquals(PublicationPolicy.Decision.Allowed, policy.check(event))

    /** The error code and message the policy answers for [event]. */
    private fun refused(event: SignedEvent): Pair<String, String> {
        val refusal = assertIs<PublicationPolicy.Decision.Refused>(policy.check(event))
        assertEquals(400, refusal.status, refusal.error)
        return refusal.error to refusal.message
    }

    private fun code(event: SignedEvent): String = refused(event).first

    @Test
    fun recognisesTheContentsOfTheSpace() = runBlocking {
        assertEquals(AgirCategory.MOBILIZATION, AgirRules.categoryOf(citizen.mobilization()))
        assertEquals(AgirCategory.CALL, AgirRules.categoryOf(citizen.call("debattre")))
        assertEquals(AgirCategory.ORGANISATION, AgirRules.categoryOf(editor.organisation()))
        assertEquals("debattre", AgirRules.callTypeOf(citizen.call("debattre")))
        val fiche = editor.sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "f"), listOf("title", "[EXEMPLE] Fiche"), listOf("ppf-type", "fiche-pratique")))
        assertNull(AgirRules.categoryOf(fiche))
        assertNull(AgirRules.check(fiche))
        assertNull(AgirRules.categoryOf(citizen.sign(kind = 1, content = "x")))
    }

    @Test
    fun aCompleteMobilizationPassesWhoeverSignsIt() = runBlocking {
        allowed(citizen.mobilization())
        allowed(editor.mobilization())
        val start = nowSeconds() + AgirFixtures.DAY
        allowed(
            citizen.mobilization(
                mobilizationTags(start = start, end = start + 1, type = "greve", status = "annule", organizers = listOf(editor.publicKey.hex))
                    + listOf(listOf("g", "u05kq"), listOf("r", "https://exemple.invalid/manif"), listOf("summary", "[EXEMPLE] court"))
                    + listOf(listOf(AgirFormats.TAG_DECLARATION, "en-cours"), listOf("a", "30023:${editor.publicKey.hex}:appel-manifester")),
            ),
        )
        // Overseas departments keep their own time zone; start stays absolute.
        allowed(citizen.mobilization(mobilizationTags().withTag(AgirFormats.TAG_START_TZID, "Indian/Reunion")))
        // Every type of the contract.
        for (type in AgirFormats.EVENT_TYPES) allowed(citizen.mobilization(mobilizationTags(type = type)))
    }

    @Test
    fun everyShapeErrorOfAMobilizationHasItsCode() = runBlocking {
        val tags = mobilizationTags(start = 2_000_000_000)
        assertEquals("missing_title", code(citizen.mobilization(tags.withoutTag("title"))))
        assertEquals("missing_title", code(citizen.mobilization(tags.withTag("title", "   "))))
        assertEquals("missing_start", code(citizen.mobilization(tags.withoutTag("start"))))
        assertEquals("missing_start", code(citizen.mobilization(tags.withTag("start", "demain"))))
        assertEquals("bad_end", code(citizen.mobilization(tags.withTag("end", "2000000000"))))
        assertEquals("bad_end", code(citizen.mobilization(tags.withTag("end", "1999999999"))))
        assertEquals("bad_end", code(citizen.mobilization(tags.withTag("end", "soir"))))
        assertEquals("missing_location", code(citizen.mobilization(tags.withoutTag("location"))))
        assertEquals("bad_event_type", code(citizen.mobilization(tags.withoutTag(AgirFormats.TAG_EVENT_TYPE))))
        assertEquals("bad_event_type", code(citizen.mobilization(tags.withTag(AgirFormats.TAG_EVENT_TYPE, "emeute"))))
        assertEquals("missing_territory", code(citizen.mobilization(tags.withoutTag(AgirFormats.TAG_TERRITORY))))
        assertEquals("missing_territory", code(citizen.mobilization(tags.withTag(AgirFormats.TAG_TERRITORY, " - "))))
        assertEquals("missing_charter", code(citizen.mobilization(tags.withoutTag(AgirFormats.TAG_CHARTER))))
        assertEquals("missing_charter", code(citizen.mobilization(tags.withTag(AgirFormats.TAG_CHARTER, "0.9"))))
        assertEquals("too_long" to "title", refused(citizen.mobilization(tags.withTag("title", "x".repeat(AgirFormats.MAX_TITLE + 1)))))
        assertEquals("too_long" to "content", refused(citizen.mobilization(tags, content = "x".repeat(AgirFormats.MAX_TEXT + 1))))
        assertEquals("too_long" to AgirFormats.TAG_TERRITORY, refused(citizen.mobilization(tags.withTag(AgirFormats.TAG_TERRITORY, "L".repeat(101)))))
        assertEquals("too_long" to "location", refused(citizen.mobilization(tags.withTag("location", "x".repeat(AgirRules.MAX_TAG_VALUE + 1)))))
        // The longest allowed values pass.
        allowed(citizen.mobilization(tags.withTag("title", "x".repeat(AgirFormats.MAX_TITLE)), content = "x".repeat(AgirFormats.MAX_TEXT)))
        // missing_field names the tag, absent or unreadable.
        assertEquals("missing_field" to "d", refused(citizen.mobilization(tags.withoutTag("d"))))
        assertEquals("missing_field" to "d", refused(citizen.mobilization(tags.withTag("d", "Majuscules"))))
        assertEquals("missing_field" to "d", refused(citizen.mobilization(tags.withTag("d", "x".repeat(65)))))
        assertEquals("missing_field" to "d", refused(citizen.mobilization(tags + listOf(listOf("d", "deuxieme")))))
        assertEquals("missing_field" to AgirFormats.TAG_EVENT_STATUS, refused(citizen.mobilization(tags.withTag(AgirFormats.TAG_EVENT_STATUS, "peut-etre"))))
        assertEquals("missing_field" to AgirFormats.TAG_DECLARATION, refused(citizen.mobilization(tags.withTag(AgirFormats.TAG_DECLARATION, "oui"))))
        assertEquals("missing_field" to AgirFormats.TAG_START_TZID, refused(citizen.mobilization(tags.withTag(AgirFormats.TAG_START_TZID, "Paris"))))
        assertEquals("missing_field" to AgirFormats.TAG_START_TZID, refused(citizen.mobilization(tags.withTag(AgirFormats.TAG_START_TZID, "Mars/Olympus"))))
        assertEquals("missing_field" to "g", refused(citizen.mobilization(tags.withTag("g", "u05kqaaaaa"))))
        assertEquals("missing_field" to "g", refused(citizen.mobilization(tags.withTag("g", "ail"))))
        assertEquals("missing_field" to "p", refused(citizen.mobilization(tags + listOf(listOf("p", "ABC", "", "organizer")))))
        assertEquals("missing_field" to "a", refused(citizen.mobilization(tags.withTag("a", "31923:${editor.publicKey.hex}:x"))))
        assertEquals("missing_field" to "r", refused(citizen.mobilization(tags.withTag("r", "javascript:alert(1)"))))
        assertEquals("missing_field" to "r", refused(citizen.mobilization(tags.withTag("r", "https://exemple.invalid/a b"))))
        // A control character anywhere.
        assertEquals("malformed_event", code(citizen.mobilization(tags.withTag("title", "[EXEMPLE] \u0000"))))
        assertEquals("malformed_event", code(citizen.mobilization(tags, content = "[EXEMPLE] \u0007")))
        // A line break is fine in the text, not in a tag.
        allowed(citizen.mobilization(tags, content = "[EXEMPLE] ligne 1\nligne 2"))
        assertEquals("malformed_event", code(citizen.mobilization(tags.withTag("location", "rue\nville"))))
    }

    @Test
    fun aCallOfEveryTypeNeedsTheFieldsOfItsType() = runBlocking {
        for (type in AgirFormats.CALL_TYPES) {
            allowed(citizen.call(type))
            for (required in AgirFormats.CALL_REQUIRED_TAGS.getValue(type)) {
                val alternatives = required.split('|')
                // Without any of the alternatives, the first missing entry is named back as written.
                val without = alternatives.fold(callTags(type)) { tags, tag -> tags.withoutTag(tag) }
                assertEquals("missing_field" to required, refused(citizen.call(type, without)), "$type without $required")
                // One alternative is enough.
                for (alternative in alternatives) {
                    val only = (alternatives - alternative).fold(callTags(type)) { tags, tag -> tags.withoutTag(tag) }
                        .let { if (it.none { tag -> tag[0] == alternative }) it + listOf(listOf(alternative, AgirFixtures.valueFor(alternative, nowSeconds() + AgirFixtures.DAY))) else it }
                    allowed(citizen.call(type, only))
                }
            }
        }
        // "location|r" really accepts either one (meetings may be online).
        val online = callTags("se-reunir").withoutTag("location") + listOf(listOf("r", "https://exemple.invalid/visio"))
        allowed(citizen.call("se-reunir", online))
    }

    @Test
    fun everyShapeErrorOfACallHasItsCode() = runBlocking {
        val tags = callTags("petition")
        assertEquals("bad_call_type", code(citizen.call("petition", tags.withTag(AgirFormats.TAG_CALL_TYPE, "emeute"))))
        assertEquals("bad_call_type", code(citizen.call("petition", tags.withoutTag(AgirFormats.TAG_CALL_TYPE))))
        assertEquals("missing_title", code(citizen.call("petition", tags.withoutTag("title"))))
        assertEquals("missing_territory", code(citizen.call("petition", tags.withoutTag(AgirFormats.TAG_TERRITORY))))
        assertEquals("missing_charter", code(citizen.call("petition", tags.withoutTag(AgirFormats.TAG_CHARTER))))
        assertEquals("missing_field" to AgirFormats.TAG_PUBLISHED_AT, refused(citizen.call("petition", tags.withoutTag(AgirFormats.TAG_PUBLISHED_AT))))
        assertEquals("missing_field" to "d", refused(citizen.call("petition", tags.withoutTag("d"))))
        assertEquals("missing_field" to AgirFormats.TAG_GOAL, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_GOAL, "0"))))
        assertEquals("missing_field" to AgirFormats.TAG_GOAL, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_GOAL, "beaucoup"))))
        assertEquals("missing_field" to AgirFormats.TAG_DEADLINE, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_DEADLINE, "bientot"))))
        assertEquals("missing_field" to AgirRules.TAG_PLATFORM, refused(citizen.call("petition", tags.withTag(AgirRules.TAG_PLATFORM, "change"))))
        assertEquals("missing_field" to AgirFormats.TAG_LINK, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_LINK, "ftp://exemple.invalid"))))
        assertEquals("missing_field" to AgirFormats.TAG_CALL_STATUS, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_CALL_STATUS, "ferme"))))
        allowed(citizen.call("petition", tags.withTag(AgirFormats.TAG_CALL_STATUS, "clos").withTag(AgirFormats.TAG_REPORT, "x".repeat(AgirRules.MAX_REPORT))))
        assertEquals("too_long" to AgirFormats.TAG_REPORT, refused(citizen.call("petition", tags.withTag(AgirFormats.TAG_REPORT, "x".repeat(AgirRules.MAX_REPORT + 1)))))
        assertEquals("too_long" to "content", refused(citizen.call("petition", tags, content = "x".repeat(AgirFormats.MAX_TEXT + 1))))
        assertEquals("too_long" to "title", refused(citizen.call("petition", tags.withTag("title", "x".repeat(AgirFormats.MAX_TITLE + 1)))))
        // Dates of a call: a start that is not a number, an end before the start.
        val witnesses = callTags("temoins", start = 2_000_000_000)
        assertEquals("missing_field" to AgirFormats.TAG_START, refused(citizen.call("temoins", witnesses.withTag(AgirFormats.TAG_START, "hier"))))
        assertEquals("bad_end", code(citizen.call("temoins", witnesses.withTag(AgirFormats.TAG_END, "1999999999"))))
        assertEquals("bad_end", code(citizen.call("information", callTags("information").withTag(AgirFormats.TAG_END, "soir"))))
        // A d longer than a mobilization's is fine for a call (the bound of the demands).
        allowed(citizen.call("information", callTags("information", d = "a".repeat(AgirRules.MAX_ARTICLE_ID))))
        assertEquals("missing_field" to "d", refused(citizen.call("information", callTags("information", d = "a".repeat(AgirRules.MAX_ARTICLE_ID + 1)))))
    }

    @Test
    fun directoryEntriesAreForEditorsAndHaveTheirShape() = runBlocking {
        val member = LocalEventSigner(IdentityKeys.generate()).publicKey.hex
        allowed(editor.organisation(organisationTags(member = member)))
        allowed(editor.organisation(organisationTags(member = null)))
        // Another key: refused before its shape is read.
        val fromCitizen = assertIs<PublicationPolicy.Decision.Refused>(policy.check(citizen.organisation(organisationTags().withoutTag("title"))))
        assertEquals(403 to "not_allowed", fromCitizen.status to fromCitizen.error)
        val tags = organisationTags(member = member)
        assertEquals("missing_title", code(editor.organisation(tags.withoutTag("title"))))
        assertEquals("missing_territory", code(editor.organisation(tags.withoutTag(AgirFormats.TAG_TERRITORY))))
        assertEquals("missing_field" to AgirFormats.TAG_SUMMARY, refused(editor.organisation(tags.withoutTag(AgirFormats.TAG_SUMMARY))))
        assertEquals("missing_field" to AgirFormats.TAG_ORG_TYPE, refused(editor.organisation(tags.withTag(AgirFormats.TAG_ORG_TYPE, "parti"))))
        assertEquals("missing_field" to AgirFormats.TAG_VERIFIED, refused(editor.organisation(tags.withTag(AgirFormats.TAG_VERIFIED, "2026-13-01"))))
        assertEquals("missing_field" to AgirFormats.TAG_VERIFIED, refused(editor.organisation(tags.withoutTag(AgirFormats.TAG_VERIFIED))))
        assertEquals("missing_field" to AgirFormats.TAG_PUBLISHED_AT, refused(editor.organisation(tags.withoutTag(AgirFormats.TAG_PUBLISHED_AT))))
        assertEquals("missing_field" to AgirFormats.TAG_PUBKEY, refused(editor.organisation(tags.withTag(AgirFormats.TAG_PUBKEY, "npub1x"))))
        assertEquals("too_long" to "content", refused(editor.organisation(tags, content = "x".repeat(AgirFormats.MAX_TEXT + 1))))
    }

    @Test
    fun theRulesOfTheOtherEditorialContentsDoNotChange() = runBlocking {
        val demandTags = listOf(listOf("d", "demande"), listOf("title", "[EXEMPLE] Revendication"), listOf("ppf-type", "revendication"), listOf("ppf-origin", "human"))
        val citizenDemand = citizen.sign(kind = Article.KIND, content = "x", tags = demandTags)
        assertEquals(true, policy.isCitizenDemand(citizenDemand))
        // The policy alone still refuses a citizen demand: only the review queue may accept it.
        assertEquals("not_allowed", assertIs<PublicationPolicy.Decision.Refused>(policy.check(citizenDemand)).error)
        allowed(editor.sign(kind = Article.KIND, content = "x", tags = demandTags))
        val ficheTags = listOf(listOf("d", "fiche"), listOf("title", "[EXEMPLE] Fiche"), listOf("ppf-type", "fiche-pratique"), listOf("ppf-origin", "human"))
        assertEquals("not_allowed", assertIs<PublicationPolicy.Decision.Refused>(policy.check(citizen.sign(kind = Article.KIND, content = "x", tags = ficheTags))).error)
        allowed(editor.sign(kind = Article.KIND, content = "x", tags = ficheTags))
    }

    @Test
    fun aCallCarriesNoPersonalContactDetailWhoeverSignsIt() = runBlocking {
        // A verified organisation publishes without review: the server checks again what the form checks.
        val phone = "[EXEMPLE] Appelez le 06 12 34 56 78 pour vous inscrire."
        assertEquals("personal_data" to "content", refused(editor.call("soutenir", content = phone)))
        assertEquals("personal_data" to "ppf-action", refused(citizen.call("soutenir", tags = callTags("soutenir").withTag("ppf-action", "[EXEMPLE] Écrire à jean.exemple@example.org"))))
        // Links, keys and dates are not free text; an ordinary call passes.
        allowed(citizen.call("soutenir"))
        allowed(citizen.call("petition"))
    }

    @Test
    fun theLimitsOfTheServerAreThoseOfTheForm() {
        assertEquals(AgirFormats.MAX_TAG_VALUE, AgirRules.MAX_TAG_VALUE)
        assertEquals(AgirFormats.MAX_REPORT, AgirRules.MAX_REPORT)
        assertEquals(AgirRules.MAX_TAG_VALUE, org.politisationpopulairefrancaise.core.agir.calls.CallRules.LONG_FIELD_MAX)
        assertEquals(AgirRules.MAX_TAG_VALUE, org.politisationpopulairefrancaise.core.agir.calls.CallRules.LINK_MAX)
        assertEquals(AgirRules.MAX_TAG_VALUE, org.politisationpopulairefrancaise.core.agir.agenda.Mobilization.MAX_LINK)
    }
}
