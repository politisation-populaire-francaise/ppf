package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoubleValidationTest {
    private val key = "e".repeat(64)
    private val person = "30023:${"b".repeat(64)}:personne-exemple"

    /** A brick of « Voir » with its frame and [tags]. */
    private fun brick(vararg tags: List<String>, kind: Int = EventKinds.LONG_FORM_ARTICLE): UnsignedEvent = UnsignedEvent(
        key, 1_793_000_000L, kind,
        listOf(listOf("d", "exemple"), listOf("title", "[EXEMPLE] Une brique"), listOf("ppf-origin", "human")) + tags.toList(),
        "[EXEMPLE]",
    )

    private fun double(event: UnsignedEvent, use: SigningUse = SigningUse.EDITORIAL_VOIR) = DoubleValidation.requiredFor(use, event)

    @Test
    fun eachOrdinaryTypeAloneNeedsOneApproval() {
        for (type in listOf("indicateur", "texte", "mouvement")) {
            assertFalse(double(brick(listOf("ppf-type", type))), type)
            // Other tags, an `a` tag that names a source or nothing at all, change nothing.
            assertFalse(double(brick(listOf("ppf-type", type), listOf("t", "logement"), listOf("ppf-source", "https://exemple.invalid"))), type)
        }
    }

    @Test
    fun everyOtherTypeNeedsTwoApprovals() {
        for (type in listOf("personne", "fait", "dossier", "parti", "reponse", "inconnu", "", "Texte", " texte")) {
            assertTrue(double(brick(listOf("ppf-type", type))), "type « $type »")
        }
        // A type tag without its value.
        assertTrue(double(brick(listOf("ppf-type"))))
    }

    @Test
    fun noTypeOrSeveralTypesNeedTwoApprovals() {
        assertTrue(double(brick()))
        assertTrue(double(brick(listOf("ppf-type", "texte"), listOf("ppf-type", "texte"))))
        assertTrue(double(brick(listOf("ppf-type", "texte"), listOf("ppf-type", "indicateur"))))
        assertTrue(double(brick(listOf("ppf-type", "texte"), listOf("ppf-type", "personne"))))
    }

    @Test
    fun eachSensitiveTagNeedsTwoApprovals() {
        val tags = listOf("ppf-procedure", "ppf-sentence", "ppf-sentence-served", "ppf-quote", "ppf-promise", "ppf-vote", "ppf-mandate", "ppf-fact-status")
        for (tag in tags) {
            assertTrue(double(brick(listOf("ppf-type", "texte"), listOf(tag, "exemple"))), tag)
            assertTrue(double(brick(listOf("ppf-type", "indicateur"), listOf(tag))), "$tag without value")
        }
    }

    @Test
    fun eachRoleThatPointsAtAPersonNeedsTwoApprovals() {
        for (role in listOf("personne", "responsable", "parti", "cible")) {
            assertTrue(double(brick(listOf("ppf-type", "mouvement"), listOf("a", person, "", role))), role)
            // Wherever the tag stands, and with more elements after the role.
            assertTrue(double(brick(listOf("a", person, "", role, "en plus"), listOf("ppf-type", "texte"))), "$role first")
        }
    }

    @Test
    fun anATagWithAnotherRoleOrNoneNeedsOneApproval() {
        for (a in listOf(listOf("a", person, "", "source"), listOf("a", person, "", ""), listOf("a", person, ""), listOf("a", person), listOf("a"))) {
            assertFalse(double(brick(listOf("ppf-type", "texte"), a)), a.toString())
        }
        // The role is the fourth element only, never the relay hint.
        assertFalse(double(brick(listOf("ppf-type", "texte"), listOf("a", person, "personne"))))
    }

    @Test
    fun aBrickOfAnotherKindNeedsTwoApprovals() {
        assertTrue(double(brick(listOf("ppf-type", "texte"), kind = EventKinds.TEXT_NOTE)))
    }

    @Test
    fun otherUsesNeverNeedTheDoubleValidation() {
        val implicating = brick(listOf("ppf-type", "personne"), listOf("a", person, "", "personne"), listOf("ppf-quote", "exemple"))
        for (use in SigningUse.entries.filter { it != SigningUse.EDITORIAL_VOIR }) {
            assertFalse(double(implicating, use), use.id)
            assertFalse(double(brick(), use), use.id)
        }
        assertTrue(double(implicating))
        // Every content of « Décider » needs one approval, petitions and final texts of a deliberation included.
        for (type in SigningUse.PACTE_TYPES) {
            assertFalse(double(brick(listOf("ppf-type", type)), SigningUse.DECIDER_PACTE), type)
        }
    }
}
