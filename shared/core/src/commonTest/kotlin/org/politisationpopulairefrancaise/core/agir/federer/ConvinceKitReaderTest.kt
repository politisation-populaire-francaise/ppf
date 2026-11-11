package org.politisationpopulairefrancaise.core.agir.federer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Fédérer » (D-195): the kit « Convaincre autour de soi » of « Comprendre » cut into cards, nothing invented. */
class ConvinceKitReaderTest {
    private val kit = """
        ::: en-bref
        - **Écouter d'abord**, partir de ce qui touche la personne.
        :::

        ## Cinq arguments courts

        1. **[EXEMPLE] Vous n'êtes pas seul.** Une phrase sourcée (source A).
        2. **[EXEMPLE] Le compte n'y est pas.** Une autre phrase (source B).
        3. Une ligne sans titre en gras.

        ::: objections
        ? « [EXEMPLE] Manifester, ça ne sert à rien. »
        > Une réponse sourcée,
        > sur deux lignes.
        ? « [EXEMPLE] Attendons la présidentielle. »
        > Une seconde réponse.
        :::

        ## Le monde bouge, en une carte

        - **[EXEMPLE] Pays A, 2024** : une loi retirée.
        - **[EXEMPLE] Pays B, 2025** : un budget retiré.

        Pour aller plus loin, lire « Le monde bouge » dans Comprendre.

        ## Sources

        - Une source, consultée le 2026-11-11 : https://example.org/a
    """.trimIndent()

    @Test
    fun theArgumentsTheAnswersAndTheWorldBecomeCards() {
        val read = ConvinceKitReader.read(kit)
        assertEquals(
            listOf(
                KitCard.Argument("argument-1", "[EXEMPLE] Vous n'êtes pas seul.", "Une phrase sourcée (source A)."),
                KitCard.Argument("argument-2", "[EXEMPLE] Le compte n'y est pas.", "Une autre phrase (source B)."),
                KitCard.Argument("argument-3", "Une ligne sans titre en gras.", ""),
            ),
            read.arguments,
        )
        assertEquals(
            listOf(
                KitCard.Answer("objection-1", "[EXEMPLE] Manifester, ça ne sert à rien.", "Une réponse sourcée, sur deux lignes."),
                KitCard.Answer("objection-2", "[EXEMPLE] Attendons la présidentielle.", "Une seconde réponse."),
            ),
            read.answers,
        )
        assertEquals(listOf("[EXEMPLE] Pays A, 2024 : une loi retirée.", "[EXEMPLE] Pays B, 2025 : un budget retiré."), read.world?.lines)
        // The paragraph after the list and the sources are not cards.
        assertEquals(6, read.cards.size)
    }

    @Test
    fun aKitWithoutThesePartsGivesNoCard() {
        val read = ConvinceKitReader.read("Un texte sans arguments ni objections.\n\n## Sources\n\n- une source")
        assertTrue(read.isEmpty)
        assertNull(read.world)
    }

    @Test
    fun aSharedCardSaysWhereItComesFromAndLinksTheWholeGuide() {
        val answer = ConvinceKitReader.read(kit).answers.first()
        val text = ConvinceKitReader.shareText(answer, base = "https://exemple.test/")
        assertEquals(
            "« [EXEMPLE] Manifester, ça ne sert à rien. »\nUne réponse sourcée, sur deux lignes.\n\n" +
                ConvinceKitReader.SHARE_FOOTER + "\nhttps://exemple.test/comprendre/fiches/convaincre-autour-de-soi",
            text,
        )
        val world = ConvinceKitReader.shareText(ConvinceKitReader.read(kit).world!!, base = "https://exemple.test")
        assertTrue(world.startsWith("Le monde bouge\n- [EXEMPLE] Pays A, 2024 : une loi retirée.\n- [EXEMPLE] Pays B"), world)
        assertTrue("rédigé par l'IA" in world)
    }
}
