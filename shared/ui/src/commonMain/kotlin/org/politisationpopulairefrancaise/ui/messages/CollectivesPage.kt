package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession

/** The rooms of collectives, unions and associations (NIP-29). Placeholder until the rooms exist. */
@Composable
fun CollectivesPage(dependencies: AppDependencies, session: AccountSession, compact: Boolean) {
    PlannedView(MessagesView.COLLECTIVES, compact)
}
