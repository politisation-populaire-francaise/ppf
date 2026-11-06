package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.content.ContentLibrary

/** The flows of followed media and elected officials, read as one-way conversations. Placeholder until they exist. */
@Composable
fun FlowsPage(dependencies: AppDependencies, session: AccountSession, library: ContentLibrary, compact: Boolean) {
    PlannedView(MessagesView.FLOWS, compact)
}
