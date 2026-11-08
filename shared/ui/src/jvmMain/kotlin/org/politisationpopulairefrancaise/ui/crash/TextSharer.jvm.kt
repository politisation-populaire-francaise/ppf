package org.politisationpopulairefrancaise.ui.crash

import androidx.compose.runtime.Composable

/** No share sheet on the computer: the report is copied, then pasted where the person wants. */
@Composable
internal actual fun rememberTextSharer(title: String): ((String) -> Unit)? = null
