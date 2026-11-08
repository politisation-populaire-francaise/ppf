package org.politisationpopulairefrancaise.server.editorial

import io.ktor.http.Parameters
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.BodySpec
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.Occurs
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The form generated from a schema: repeated fields without JavaScript, labels, problems next to their controls. */
class EditorialFormTest {
    /** A schema with repeated positional fields, like the sources of Voir (no schema of Comprendre repeats a key). */
    private val schema = ContentSchema(
        type = ArticleType.FAIT,
        space = EditorialSpace.VOIR,
        labelKey = "test.type",
        sourceDirectory = "test",
        bundle = "test.json",
        summary = org.politisationpopulairefrancaise.core.editorial.schema.Presence.OPTIONAL,
        fields = listOf(
            FieldSpec(
                "source", "test.field.source", occurs = Occurs.MANY(min = 1),
                value = ValueSpec.Positional(
                    listOf(
                        Position("kind", "test.source.kind", FieldType.Choice(listOf("justice", "presse", "parlement"), "test.kind.")),
                        Position("title", "test.source.title", FieldType.Text(300)),
                        Position("url", "test.source.url", FieldType.Url),
                        Position("date", "test.source.date", FieldType.Date(), required = false),
                    ),
                ),
            ),
            FieldSpec("next", "test.field.next", occurs = Occurs.MANY(), value = ValueSpec.NextStep),
            FieldSpec("place", "test.field.place", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(120))),
        ),
        body = BodySpec(requiredSections = emptyList()),
    )

    private fun form(vararg fields: Pair<String, String>): Parameters = Parameters.build { fields.forEach { (key, value) -> append(key, value) } }

    private fun render(state: FormState, problems: List<Problem> = emptyList()): String = createHTML().html {
        body { EditorialForm.render(this, EditorialForm.View(schema, state, problems, "/x", 1, isUpdate = false, known = emptyMap(), csrf = {})) }
    }

    @Test
    fun aRepeatedFieldShowsItsRowsPlusTwoBlankOnesAndAnAddButton() {
        val html = render(EditorialForm.empty(schema, "2026-11-08"))
        assertTrue(html.contains("name=\"rows.source\" value=\"2\""), "no row written: two blank rows")
        assertTrue(html.contains("name=\"f.source.1.title\""))
        assertFalse(html.contains("name=\"f.source.2.title\""))
        assertTrue(html.contains("value=\"more:source\""), "the add button, a plain submit")
        assertTrue(html.contains("value=\"move:source:0:down\""))
        assertFalse(html.contains("value=\"move:source:0:up\""), "the first row cannot go up")
        // A single field: one control, no row buttons.
        assertTrue(html.contains("name=\"rows.place\" value=\"1\""))
        assertFalse(html.contains("more:place"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun theRowsAreReadInOrderAsLinesOfContentAndBlankRowsAreIgnored() {
        val posted = EditorialForm.read(
            schema,
            form(
                "action" to "save", "rows.source" to "3",
                "f.source.0.kind" to "justice", "f.source.0.title" to "[EXEMPLE] Jugement", "f.source.0.url" to "https://a.example", "f.source.0.date" to "2025-12-10",
                "f.source.1.kind" to "", "f.source.1.title" to " ", "f.source.1.url" to "",
                "f.source.2.kind" to "presse", "f.source.2.title" to "[EXEMPLE] Article", "f.source.2.url" to "https://b.example", "f.source.2.date" to "",
                "rows.next" to "1", "f.next.0.label" to "[EXEMPLE] Agir", "f.next.0.target" to "espace:agir",
                "rows.place" to "1", "f.place.0" to "",
                "origin" to "ai-assisted", "c.title" to "[EXEMPLE] Titre",
            ),
            isUpdate = false,
        )
        assertEquals(FormAction.Save, posted.action)
        assertEquals(
            listOf(
                "title" to "[EXEMPLE] Titre", "type" to "fait", "origin" to "ai-assisted",
                "source" to "justice | [EXEMPLE] Jugement | https://a.example | 2025-12-10",
                "source" to "presse | [EXEMPLE] Article | https://b.example",
                "next" to "[EXEMPLE] Agir | espace:agir",
            ),
            posted.state.content.entries,
        )
        // Shown again: the two rows written, then two blank rows.
        val html = render(posted.state)
        assertTrue(html.contains("name=\"rows.source\" value=\"4\""))
        assertTrue(html.contains("name=\"f.source.1.title\" value=\"[EXEMPLE] Article\"") || html.contains("value=\"[EXEMPLE] Article\""))
    }

    @Test
    fun addingRowsKeepsEverythingAndMovingARowSwapsIt() {
        val base = arrayOf(
            "rows.source" to "2", "blank.source" to "2",
            "f.source.0.kind" to "justice", "f.source.0.title" to "[EXEMPLE] A", "f.source.0.url" to "https://a.example",
            "f.source.1.kind" to "presse", "f.source.1.title" to "[EXEMPLE] B", "f.source.1.url" to "https://b.example",
        )
        val more = EditorialForm.read(schema, form(*base, "action" to "more:source"), isUpdate = false)
        assertEquals(FormAction.More("source"), more.action)
        assertEquals(2, more.state.content.entries.count { it.first == "source" }, "nothing lost")
        assertEquals(4, more.state.blankRows["source"])
        assertTrue(render(more.state).contains("name=\"rows.source\" value=\"6\""))

        val moved = EditorialForm.read(schema, form(*base, "action" to "move:source:1:up"), isUpdate = false)
        assertEquals(listOf("presse | [EXEMPLE] B | https://b.example", "justice | [EXEMPLE] A | https://a.example"), moved.state.content.entries.filter { it.first == "source" }.map { it.second })
        // A move outside the rows changes nothing.
        val outside = EditorialForm.read(schema, form(*base, "action" to "move:source:0:up"), isUpdate = false)
        assertEquals(listOf("justice | [EXEMPLE] A | https://a.example", "presse | [EXEMPLE] B | https://b.example"), outside.state.content.entries.filter { it.first == "source" }.map { it.second })
    }

    @Test
    fun aProblemOfAPositionIsShownOnItsControl() {
        val state = FormState(DraftContent(listOf("source" to "justice | [EXEMPLE] A | pas-une-adresse"), ""))
        val problems = listOf(
            Problem(Codes.VALUE_URL, "source", 0, "url", listOf("pas-une-adresse")),
            Problem(Codes.FIELD_MISSING, "title"),
        )
        assertEquals("f-source-0-url", EditorialForm.anchorOf(schema, problems[0]))
        assertEquals("f-source-0-kind-v0", EditorialForm.anchorOf(schema, Problem(Codes.VALUE_CHOICE, "source", 0, "kind")))
        assertEquals("f-next-0-label", EditorialForm.anchorOf(schema, Problem(Codes.VALUE_NEXT, "next", 0)))
        assertEquals(null, EditorialForm.anchorOf(schema, Problem(Codes.TYPE_MISMATCH, "type")))
        val html = render(state, problems)
        val control = Regex("<input[^>]*id=\"f-source-0-url\"[^>]*>").find(html)!!.value
        assertTrue(control.contains("aria-invalid=\"true\""))
        assertTrue(control.contains("aria-describedby=\"err-0\"") || control.contains(" err-0"))
        assertTrue(html.contains("id=\"err-0\""))
        assertTrue(html.contains("href=\"#f-source-0-url\""))
        assertTrue(html.contains("href=\"#c-title\""))
        assertTrue(html.contains("ligne 1"), "located for a person: the row")
    }
}
