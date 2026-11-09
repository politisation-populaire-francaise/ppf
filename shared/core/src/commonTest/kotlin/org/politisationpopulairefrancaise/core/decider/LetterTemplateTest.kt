package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DE-09: the model letters, filled in on the device, sent by the person; PPF sends nothing. */
class LetterTemplateTest {
    private val keys = IdentityKeys.generate()

    private fun letter(body: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, "courrier", "[EXEMPLE] Courrier", null, emptyList(), ArticleType.MODELE_COURRIER, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, body, extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val text = "{commune}, le {date}\n\nÀ l’attention de {elu}\n\nMadame, Monsieur,"
    private val depute = listOf(DeciderTags.RECIPIENT, "depute")
    private val subject = listOf(DeciderTags.SUBJECT, "Votre vote")
    private val contact = listOf(DeciderTags.CONTACT, "depute", "Contacter son député", "https://exemple.invalid/deputes")

    private fun official(mandate: Mandate, name: String, function: String? = null) = ElectedOfficial(mandate, "6901", null, name, function, null, null)

    @Test
    fun theLetterIsFilledWithTheNameGivenByTheDirectoryNeverAGuessedCivility() {
        val template = assertNotNull(LetterTemplate.from(letter(text, depute, subject, contact)))
        assertEquals(listOf(LetterRecipient.DEPUTE), template.recipients)
        assertEquals("Votre vote", template.subject)
        val filled = template.fill(official(Mandate.DEPUTE, "[EXEMPLE] Camille Martin"), "Lyon", CivilDate(2026, 11, 9))
        assertEquals("Lyon, le 9 novembre 2026\n\nÀ l’attention de Camille Martin\n\nMadame, Monsieur,", filled)
        // Without a commune chosen on the device: visible marks the person replaces.
        val blank = template.fill(null, null, CivilDate(2026, 11, 9))
        assertTrue(LetterTemplate.UNKNOWN_ELU in blank && LetterTemplate.UNKNOWN_COMMUNE in blank)
        assertEquals(emptyList(), template.warnings())
    }

    @Test
    fun eachRecipientMatchesItsMandateAndTheMayorIsTheMemberOfTheCouncilWhoseFunctionSaysSo() {
        assertTrue(LetterRecipient.MAIRE.matches(official(Mandate.MUNICIPAL, "A", "Maire")))
        assertFalse(LetterRecipient.MAIRE.matches(official(Mandate.MUNICIPAL, "B", "1er adjoint au maire")))
        assertFalse(LetterRecipient.MAIRE.matches(official(Mandate.MUNICIPAL, "C")))
        assertTrue(LetterRecipient.SENATEUR.matches(official(Mandate.SENATEUR, "D")))
        assertTrue(LetterRecipient.REGIONAL.matches(official(Mandate.COLLECTIVITE, "E")), "the assembly of a collectivity with a special status")
        assertFalse(LetterRecipient.DEPUTE.matches(official(Mandate.SENATEUR, "F")))
    }

    @Test
    fun aLetterWithoutRecipientSubjectOrTextIsNotShownAndTheToolIsToldWhatLooksOdd() {
        assertNull(LetterTemplate.from(letter(text, subject, contact)), "no recipient")
        assertNull(LetterTemplate.from(letter(text, depute, contact)), "no subject")
        assertNull(LetterTemplate.from(letter("  ", depute, subject, contact)), "no text")
        val odd = assertNotNull(LetterTemplate.from(letter("Bonjour {prenom}", depute, listOf(DeciderTags.RECIPIENT, "senateur"), subject, contact, listOf(DeciderTags.CONTACT, "senateur", "Ailleurs", "http://exemple.invalid"))))
        assertEquals(1, odd.contacts.size, "a contact outside https is dropped")
        assertEquals(2, odd.warnings().size, odd.warnings().toString())
    }
}
