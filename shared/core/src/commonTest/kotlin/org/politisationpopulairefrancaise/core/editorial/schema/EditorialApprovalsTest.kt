package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.comprendre.ComprendreTags
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The number of approvals of an editorial content (D-095): one by default, two for the contents of M17 that
 * implicate a person (decision of the owner of 7 November 2026).
 */
class EditorialApprovalsTest {
    private val key = "e".repeat(64)
    private val person = "30023:${"b".repeat(64)}:personne-exemple"
    private val comprendre = SigningUse.EDITORIAL_COMPRENDRE
    private val voir = SigningUse.EDITORIAL_VOIR

    /** A long-form article of type [type] with its frame and [tags]. */
    private fun event(type: String, vararg tags: List<String>): UnsignedEvent = UnsignedEvent(
        key, 1_793_000_000L, EventKinds.LONG_FORM_ARTICLE,
        listOf(listOf("d", "exemple"), listOf("title", "[EXEMPLE] Un contenu"), listOf("ppf-type", type), listOf("ppf-origin", "human")) + tags.toList(),
        "[EXEMPLE]",
    )

    @Test
    fun everySchemaOfComprendreDeclaresOneApproval() {
        for (schema in ComprendreSchemas.all) assertEquals(EditorialApprovals.ORDINARY, schema.approvals, schema.type.tagValue)
    }

    @Test
    fun anOrdinaryContentNeedsOneApproval() {
        for (schema in ComprendreSchemas.all) {
            assertEquals(EditorialApprovals.ORDINARY, EditorialApprovals.required(schema, comprendre, event(schema.type.tagValue)), schema.type.tagValue)
        }
        // Links that point at no person (a glossary « see also », a source) change nothing.
        val linked = event("glossaire", listOf("a", "30023:$key:glossaire-loi", "", GlossaryEntry.ROLE_SEE_ALSO), listOf("ppf-source", "https://exemple.invalid"))
        assertEquals(EditorialApprovals.ORDINARY, EditorialApprovals.required(ComprendreSchemas.GLOSSAIRE, comprendre, linked))
    }

    @Test
    fun aSchemaThatDeclaresTwoAlwaysNeedsTwo() {
        val m17 = ComprendreSchemas.DECRYPTAGE.copy(approvals = EditorialApprovals.M17)
        assertEquals(EditorialApprovals.M17, EditorialApprovals.required(m17, comprendre, event("decryptage")))
    }

    @Test
    fun onlyOneOrTwoApprovalsCanBeDeclared() {
        assertFailsWith<IllegalArgumentException> { ComprendreSchemas.DECRYPTAGE.copy(approvals = 0) }
        assertFailsWith<IllegalArgumentException> { ComprendreSchemas.DECRYPTAGE.copy(approvals = 3) }
    }

    @Test
    fun anUnknownTypeNeedsTwo() {
        assertEquals(EditorialApprovals.M17, EditorialApprovals.required(null, comprendre, event("inconnu")))
    }

    @Test
    fun aContentThatLinksAPersonNeedsTwoWhateverTheSpace() {
        // « Qui décide de ça ? » with its `persons`: the content names who decides.
        val named = event("qui-decide", listOf("a", person, "", ComprendreTags.PERSON_LINK_ROLE))
        assertTrue(EditorialApprovals.linksAPerson(named))
        assertEquals(EditorialApprovals.M17, EditorialApprovals.required(ComprendreSchemas.QUI_DECIDE, comprendre, named))
        for (role in listOf("personne", "responsable", "parti", "cible")) {
            val linked = event("indicateur", listOf("a", person, "", role))
            assertEquals(EditorialApprovals.M17, EditorialApprovals.required(ComprendreSchemas.DECRYPTAGE, voir, linked), role)
        }
        // A link without a role, or with another role, names no person.
        assertFalse(EditorialApprovals.linksAPerson(event("qui-decide", listOf("a", person), listOf("a", person, ""), listOf("a", person, "", "fiche"))))
    }

    @Test
    fun theRuleOfTheSigningServiceIsAlwaysFollowed() {
        // For « Voir », the service requires two approvals for every type but the ordinary ones (DoubleValidation).
        assertEquals(EditorialApprovals.M17, EditorialApprovals.required(ComprendreSchemas.DECRYPTAGE, voir, event("fait")))
        assertEquals(EditorialApprovals.M17, EditorialApprovals.required(ComprendreSchemas.DECRYPTAGE, voir, event("indicateur", listOf("ppf-quote", "[EXEMPLE]"))))
        assertEquals(EditorialApprovals.ORDINARY, EditorialApprovals.required(ComprendreSchemas.DECRYPTAGE, voir, event("indicateur")))
    }
}
