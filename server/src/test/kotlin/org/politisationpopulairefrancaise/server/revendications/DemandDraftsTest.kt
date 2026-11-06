package org.politisationpopulairefrancaise.server.revendications

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.alteredDemand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DemandDraftsTest {
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val now = DemandFixtures.nowSeconds()

    private fun shape(event: SignedEvent) = DemandDrafts.shapeOf(event, now)

    private fun error(event: SignedEvent): String = assertIs<DemandDrafts.Shape.Invalid>(shape(event)).error

    @Test
    fun theDraftIsReadBackFromTheEventTheFormSigns() = runBlocking {
        val written = draft(
            summary = "[EXEMPLE] Une consultation à moins de 30 minutes.",
            existing = "[EXEMPLE] Des maisons médicales de garde existent dans certaines villes.",
            themes = listOf("sante", "services-publics"),
            territory = "Nord",
        )
        val article = assertIs<DemandDrafts.Shape.Valid>(shape(citizen.demand(written))).article
        assertEquals(written, DemandDrafts.draftOf(article))
    }

    @Test
    fun textUnderAnUnknownHeadingCountsAsWhy() = runBlocking {
        val event = citizen.alteredDemand { unsigned ->
            unsigned.copy(content = "Avant tout titre.\n\n## Pourquoi\n\nParce que.\n\n## Autre chose\n\nCaché ?\n\n## Ce que la revendication demande\n\nCeci.")
        }
        val article = assertIs<DemandDrafts.Shape.Valid>(shape(event)).article
        val draft = DemandDrafts.draftOf(article)
        for (part in listOf("Avant tout titre.", "Parce que.", "## Autre chose", "Caché ?")) {
            assertTrue(part in draft.why, part)
        }
        assertEquals("Ceci.", draft.asks)
        assertEquals("", draft.existing)
    }

    @Test
    fun onlyTheShapeOfTheFormIsAccepted() = runBlocking {
        fun replaceTag(name: String, value: String): (UnsignedEvent) -> UnsignedEvent =
            { unsigned -> unsigned.copy(tags = unsigned.tags.map { if (it[0] == name) listOf(name, value) else it }) }

        assertEquals("status_not_allowed", error(citizen.alteredDemand(change = replaceTag(Article.TAG_STATUS, "campagne"))))
        assertEquals("status_not_allowed", error(citizen.alteredDemand { it.copy(tags = it.tags.filter { tag -> tag[0] != Article.TAG_STATUS }) }))
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("p", "a".repeat(64)))) }), "no tag outside the form")
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("h", "collectif"))) }), "not sent to a group")
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("d", "autre"))) }), "one address only")
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf(Article.TAG_STATUS, "nouvelle"))) }), "one status only")
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf(Article.TAG_TITLE, "Un autre titre"))) }))
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("t"))) }), "a tag without value")
        assertEquals("malformed_demand", error(citizen.demand(draft(themes = listOf("pas-un-theme")))))
        assertEquals("malformed_demand", error(citizen.demand(draft(themes = listOf("sante", "travail", "logement", "transports")))))
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("t", "sante"))) }), "the same theme twice")
        assertEquals("malformed_demand", error(citizen.demand(slug = "x".repeat(129))))
        assertEquals("malformed_demand", error(citizen.demand(createdAt = now + 7_200)), "dated in the future")
        assertIs<DemandDrafts.Shape.Valid>(shape(citizen.demand(createdAt = now + 600)), "a clock a little ahead is fine")
        assertEquals("malformed_demand", error(citizen.alteredDemand { it.copy(tags = it.tags.filter { tag -> tag[0] != Article.TAG_ORIGIN }) }), "origin required")
    }

    @Test
    fun aDemandFromOutsideTheTeamIsACitizenDemand() = runBlocking {
        val editor = LocalEventSigner(IdentityKeys.generate())
        val policy = PublicationPolicy(setOf(editor.publicKey.hex))
        assertTrue(policy.isCitizenDemand(citizen.demand()))
        assertFalse(policy.isCitizenDemand(editor.demand()), "the team publishes directly")
        val guide = citizen.sign(
            Article.build(citizen.publicKey, "fiche", "[EXEMPLE] Fiche", null, emptyList(), ArticleType.FICHE_PRATIQUE, ContentOrigin.HUMAN, now, now, "x"),
        )
        assertFalse(policy.isCitizenDemand(guide), "other editorial contents stay refused")
        assertEquals(403, assertIs<PublicationPolicy.Decision.Refused>(policy.check(citizen.demand())).status, "the policy alone never lets a citizen demand through")
    }
}
