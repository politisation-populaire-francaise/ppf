package org.politisationpopulairefrancaise.ui.voir

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.platform.DoubleValidation
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shape rule of M17 written in the signing service (core/platform/DoubleValidation.kt, D-129) must
 * stay in step with the vocabulary of « Voir » (VoirTags) and give the expected answer on every bundled
 * block: two approvals for anything that implicates a person, one for the ordinary blocks.
 */
class VoirDoubleValidationTest {
    private val events: List<SignedEvent> = Json { ignoreUnknownKeys = true }
        .decodeFromString(ListSerializer(SignedEvent.serializer()), File("src/commonMain/composeResources/files/contents/voir.json").readText())

    private fun SignedEvent.unsigned() = UnsignedEvent(pubkey, createdAt, kind, tags, content)

    private fun SignedEvent.type() = tags.first { it.firstOrNull() == "ppf-type" }[1]

    @Test
    fun theRuleUsesTheVocabularyOfVoir() {
        assertEquals(setOf(ArticleType.INDICATEUR, ArticleType.TEXTE, ArticleType.MOUVEMENT).map { it.tagValue }.toSet(), DoubleValidation.VOIR_ORDINARY_TYPES)
        assertEquals(setOf(VoirTags.ROLE_PERSON, VoirTags.ROLE_LEADER, VoirTags.ROLE_PARTY, VoirTags.ROLE_TARGET), DoubleValidation.VOIR_PERSON_ROLES)
        assertEquals(
            setOf(
                VoirTags.PROCEDURE, VoirTags.SENTENCE, VoirTags.SENTENCE_SERVED, VoirTags.QUOTE,
                VoirTags.PROMISE, VoirTags.VOTE, VoirTags.MANDATE, VoirTags.FACT_STATUS,
            ),
            DoubleValidation.VOIR_SENSITIVE_TAGS,
        )
    }

    @Test
    fun everyBlockThatCanImplicateAPersonNeedsTwoApprovals() {
        val sensitive = setOf(ArticleType.PERSONNE, ArticleType.FAIT, ArticleType.DOSSIER, ArticleType.PARTI, ArticleType.REPONSE).map { it.tagValue }
        val blocks = events.filter { it.type() in sensitive }
        assertTrue(blocks.isNotEmpty())
        blocks.forEach { event -> assertTrue(DoubleValidation.requiredFor(SigningUse.EDITORIAL_VOIR, event.unsigned()), "${event.type()} ${event.id}") }
    }

    @Test
    fun anOrdinaryBlockNeedsTwoApprovalsOnlyWhenItNamesSomeone() {
        val ordinary = events.filter { it.type() in DoubleValidation.VOIR_ORDINARY_TYPES }
        assertTrue(ordinary.isNotEmpty())
        ordinary.forEach { event ->
            val namesSomeone = event.tags.any { it.firstOrNull() == VoirTags.LINK && it.getOrNull(3) in DoubleValidation.VOIR_PERSON_ROLES } ||
                event.tags.any { it.firstOrNull() in DoubleValidation.VOIR_SENSITIVE_TAGS }
            assertEquals(namesSomeone, DoubleValidation.requiredFor(SigningUse.EDITORIAL_VOIR, event.unsigned()), "${event.type()} ${event.id}")
        }
        // At least one bundled indicator goes through with one approval: the rule is not "always two".
        assertTrue(ordinary.any { !DoubleValidation.requiredFor(SigningUse.EDITORIAL_VOIR, it.unsigned()) })
        assertFalse(events.isEmpty())
    }
}
