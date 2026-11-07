package org.politisationpopulairefrancaise.ui.agir.map

/**
 * No map on the computer yet: MapLibre Compose draws on Windows, macOS and Linux only with Java 25
 * or later (the foreign memory interface), and the application is built with Java 21 (D-009). The
 * agenda keeps its list there, with an honest sentence (docs/integration/agir-carte.md).
 */
actual fun platformAgendaMapRenderer(): AgendaMapRenderer? = null

/** No renderer on the computer: the question does not arise; the network decides alone. */
@androidx.compose.runtime.Composable
internal actual fun rememberServerTilesAllowed(): () -> Boolean = { true }
