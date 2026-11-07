package org.politisationpopulairefrancaise.ui.comprendre

import org.politisationpopulairefrancaise.core.Space
import kotlin.test.Test
import kotlin.test.assertEquals

class NextActionTest {

    @Test
    fun aLinkToASpaceBecomesAButtonAndTheRestStaysText() {
        val actions = NextAction.parse(
            """
            - [Voir les revendications des citoyens](ppf:revendications)
            - Lire la Constitution sur Légifrance (https://www.legifrance.gouv.fr).
            * [Agir](ppf:agir).

            - [Un espace inconnu](ppf:ailleurs)
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NextAction.Open("Voir les revendications des citoyens", Space.REVENDICATIONS),
                NextAction.Text("Lire la Constitution sur Légifrance (https://www.legifrance.gouv.fr)."),
                NextAction.Open("Agir", Space.AGIR),
                NextAction.Text("[Un espace inconnu](ppf:ailleurs)"),
            ),
            actions,
        )
    }
}
