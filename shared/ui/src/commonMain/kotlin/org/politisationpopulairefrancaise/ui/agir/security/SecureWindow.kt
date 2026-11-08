package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable

/**
 * True where PPF can hide its content from the system (S-45): Android, with `FLAG_SECURE`, which
 * shows the application empty in the list of recent applications and blocks screenshots and screen
 * recordings. A computer has no such switch: the setting is not shown there.
 */
expect val canHideContentFromSystem: Boolean

/** Applies the setting to the window of the application while it is on screen; does nothing where it is not possible. */
@Composable
expect fun HideContentFromSystem(hide: Boolean)
