package org.politisationpopulairefrancaise.core.editorial

import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.BodySpec
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.Presence
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val author = IdentityKeys.generate().publicKey
private val context = CheckContext.of(author.hex, emptyMap())

/** A schema with no specific field: only the common ones, review date included. */
private val schema = ContentSchema(
    type = ArticleType.ARTICLE,
    space = EditorialSpace.COMPRENDRE,
    labelKey = "test",
    sourceDirectory = "test",
    bundle = "test.json",
    fields = emptyList(),
    body = BodySpec(),
)

private const val BODY = "Texte.\n\n## Sources\n\n- une source\n"

private fun draft(vararg more: Pair<String, String>) = ContentDraft(
    ArticleType.ARTICLE,
    listOf(
        "slug" to "un-article",
        "title" to "[EXEMPLE] Un article",
        "summary" to "Un résumé.",
        "type" to "article",
        "origin" to "human",
        "published_at" to "2026-11-05",
        "updated_at" to "2026-11-08",
    ) + more.toList(),
    BODY,
)

private fun signed(d: ContentDraft): SignedEvent {
    val unsigned = EditorialEngine.build(schema, d, author)
    return SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
}

/** The common review date (`ppf-review-by`, decision of the team lead of 9 November 2026). */
class ReviewDatesTest {
    private val today = CivilDate(2026, 11, 9)

    @Test
    fun aContentWithoutADateIsNeverDueAndItsEventDoesNotChange() {
        val event = signed(draft())
        assertNull(ReviewDates.of(event))
        assertEquals(ReviewStatus.None, ReviewDates.status(null, today))
        assertFalse(ReviewDates.isDue(null, today))
        // Absent, the event has no tag of it: the contents signed before the field existed keep their bytes.
        assertTrue(event.tags.none { it.firstOrNull() == ReviewDates.TAG })
    }

    @Test
    fun aPastDateIsOverdueAndADateWithinSixtyDaysIsDue() {
        assertEquals(ReviewStatus.Overdue(CivilDate(2026, 11, 1), 8), ReviewDates.status(CivilDate(2026, 11, 1), today))
        assertEquals(ReviewStatus.Soon(today, 0), ReviewDates.status(today, today))
        assertEquals(ReviewStatus.Soon(CivilDate(2027, 1, 8), 60), ReviewDates.status(CivilDate(2027, 1, 8), today))
        assertEquals(ReviewStatus.Later(CivilDate(2027, 1, 9)), ReviewDates.status(CivilDate(2027, 1, 9), today))
        assertTrue(ReviewDates.isDue(CivilDate(2026, 11, 1), today))
        assertTrue(ReviewDates.isDue(CivilDate(2027, 1, 8), today))
        assertFalse(ReviewDates.isDue(CivilDate(2028, 10, 1), today))
    }

    @Test
    fun theDateIsSignedAfterTheSpecificTagsAndReadsBack() {
        val d = draft("review_by" to "2028-10-01")
        assertEquals(emptyList(), EditorialEngine.check(schema, d, context))
        val event = signed(d)
        assertEquals(listOf(ReviewDates.TAG, "2028-10-01"), event.tags.last())
        assertEquals(CivilDate(2028, 10, 1), ReviewDates.of(event))
        assertEquals(CivilDate(2028, 10, 1), ReviewDates.of(assertNotNull(Article.from(event))))
        // An update starts from the published event: the date comes back in the draft, then in the same event.
        val back = EditorialEngine.draftOf(schema, event, context)
        assertEquals("2028-10-01", back.entries.last { it.first == ContentSchema.REVIEW_BY }.second)
        assertEquals(event.tags, signed(back).tags)
    }

    @Test
    fun theDateMustBeValidAndAfterTheUpdate() {
        fun codes(value: String) = EditorialEngine.check(schema, draft("review_by" to value), context).map { it.code }
        assertEquals(listOf(Codes.VALUE_DATE), codes("2028-02-30"))
        assertEquals(listOf(Codes.VALUE_DATE), codes("octobre 2028"))
        // A review date at or before the update would put the content back in « À revoir » at once.
        assertEquals(listOf(Codes.REVIEW_NOT_AFTER_UPDATE), codes("2026-11-08"))
        assertEquals(listOf(Codes.REVIEW_NOT_AFTER_UPDATE), codes("2025-01-01"))
        assertEquals(emptyList(), codes("2026-11-09"))
        assertEquals(listOf(Codes.FIELD_REPEATED), EditorialEngine.check(schema, draft("review_by" to "2027-01-01", "review_by" to "2028-01-01"), context).map { it.code })
        assertEquals(
            "Champ « review_by » : la date de revue (2026-11-08) doit tomber après la date de mise à jour (2026-11-08) : choisissez la prochaine date de revue",
            EditorialMessages.french(EditorialEngine.check(schema, draft("review_by" to "2026-11-08"), context).single()),
        )
    }

    @Test
    fun aSchemaWithoutTheDateRefusesIt() {
        val without = schema.copy(reviewBy = Presence.ABSENT)
        assertEquals(listOf(Codes.FIELD_UNKNOWN), EditorialEngine.check(without, draft("review_by" to "2028-10-01"), context).map { it.code })
    }

    @Test
    fun everySchemaOfComprendreAndVoirOffersTheDate() {
        assertTrue(EditorialSchemas.all.isNotEmpty())
        EditorialSchemas.all.forEach { assertEquals(Presence.OPTIONAL, it.reviewBy, it.type.tagValue) }
        assertEquals(Presence.OPTIONAL, ComprendreSchemas.FICHE_PRATIQUE.reviewBy)
    }
}
