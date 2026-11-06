package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable

/** The private and small-group conversations (NIP-17). Placeholder until the conversations exist. */
@Composable
fun PrivatePage(compact: Boolean) {
    PlannedView(MessagesView.PRIVATE, compact)
}
