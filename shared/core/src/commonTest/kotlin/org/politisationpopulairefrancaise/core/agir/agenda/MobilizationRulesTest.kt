package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The codes of the server, one broken rule at a time, and their order. */
class MobilizationRulesTest {
    private val fixtures = AgendaFixtures()

    private suspend fun issues(tags: List<List<String>>, content: String = "") =
        MobilizationRules.check(fixtures.raw(tags, content)).map { it.error.code to it.field }

    @Test
    fun aValidEventHasNoIssue() = runTest {
        assertEquals(emptyList(), issues(validTags()))
        // The end is optional.
        assertEquals(emptyList(), issues(validTags().without("end")))
    }

    @Test
    fun eachRequiredTagHasItsCode() = runTest {
        assertEquals(listOf("missing_field" to "d"), issues(validTags().without("d")))
        assertEquals(listOf("missing_field" to "d"), issues(validTags().with("d", "Majuscules")))
        assertEquals(listOf("missing_title" to "title"), issues(validTags().without("title")))
        assertEquals(listOf("missing_title" to "title"), issues(validTags().with("title", "   ")))
        assertEquals(listOf("missing_start" to "start"), issues(validTags().without("start").without("end")))
        assertEquals(listOf("missing_start" to "start"), issues(validTags().with("start", "-5").without("end")))
        assertEquals(listOf("bad_end" to "end"), issues(validTags().with("end", AgendaTimes.SATURDAY_14H.toString())))
        assertEquals(listOf("bad_end" to "end"), issues(validTags().with("end", "bientôt")))
        assertEquals(listOf("missing_location" to "location"), issues(validTags().without("location")))
        assertEquals(listOf("bad_event_type" to "ppf-event-type"), issues(validTags().without("ppf-event-type")))
        assertEquals(listOf("bad_event_type" to "ppf-event-type"), issues(validTags().with("ppf-event-type", "fete")))
        assertEquals(listOf("missing_territory" to "ppf-territory"), issues(validTags().without("ppf-territory")))
        assertEquals(listOf("missing_charter" to "ppf-charter"), issues(validTags().without("ppf-charter")))
        assertEquals(listOf("missing_charter" to "ppf-charter"), issues(validTags().with("ppf-charter", "0.9")))
    }

    @Test
    fun lengthsAreBounded() = runTest {
        assertEquals(emptyList(), issues(validTags().with("title", "a".repeat(140))))
        assertEquals(listOf("too_long" to "title"), issues(validTags().with("title", "a".repeat(141))))
        assertEquals(listOf("too_long" to "location"), issues(validTags().with("location", "a".repeat(201))))
        assertEquals(listOf("too_long" to "ppf-territory"), issues(validTags().with("ppf-territory", "a".repeat(101))))
        assertEquals(listOf("too_long" to "summary"), issues(validTags().with("summary", "a".repeat(281))))
        assertEquals(emptyList(), issues(validTags(), "a".repeat(8000)))
        assertEquals(listOf("too_long" to "content"), issues(validTags(), "a".repeat(8001)))
    }

    @Test
    fun invalidOptionalValuesNameTheirTag() = runTest {
        assertEquals(emptyList(), issues(validTags().with("ppf-event-status", "annule").with("ppf-declaration", "en-cours").with("g", "u09tvw0f6")))
        assertEquals(listOf("missing_field" to "ppf-event-status"), issues(validTags().with("ppf-event-status", "complet")))
        assertEquals(listOf("missing_field" to "ppf-declaration"), issues(validTags().with("ppf-declaration", "oui")))
        assertEquals(listOf("missing_field" to "g"), issues(validTags().with("g", "u09tvw0f6abc")))
        assertEquals(listOf("missing_field" to "p"), issues(validTags() + listOf(listOf("p", "abc", "", "organizer"))))
        assertEquals(listOf("missing_field" to "r"), issues(validTags() + listOf(listOf("r", "javascript:alert(1)"))))
    }

    @Test
    fun everyIssueIsListedInTheOrderOfTheServer() = runTest {
        val broken = listOf(listOf("start", "x"), listOf("ppf-event-type", "?"))
        assertEquals(
            listOf("missing_field", "missing_title", "missing_start", "missing_location", "bad_event_type", "missing_territory", "missing_charter"),
            issues(broken).map { it.first },
        )
        assertEquals(MobilizationError.MISSING_FIELD, MobilizationRules.firstIssue(fixtures.raw(broken))?.error)
        assertEquals(MobilizationError.BAD_END, MobilizationError.fromCode("bad_end"))
    }
}
