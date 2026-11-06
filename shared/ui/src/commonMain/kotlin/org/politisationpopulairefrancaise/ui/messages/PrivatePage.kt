package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession

/** The private and small-group conversations (NIP-17). Placeholder until the conversations exist. */
@Composable
fun PrivatePage(dependencies: AppDependencies, session: AccountSession, compact: Boolean) {
    PlannedView(MessagesView.PRIVATE, compact)
}
