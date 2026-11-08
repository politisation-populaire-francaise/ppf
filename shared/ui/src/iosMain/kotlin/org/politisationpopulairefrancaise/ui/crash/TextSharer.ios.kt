package org.politisationpopulairefrancaise.ui.crash

import androidx.compose.runtime.Composable

// iOS is not compiled yet: the share sheet (UIActivityViewController) comes with the iOS host.
@Composable
internal actual fun rememberTextSharer(title: String): ((String) -> Unit)? = null
