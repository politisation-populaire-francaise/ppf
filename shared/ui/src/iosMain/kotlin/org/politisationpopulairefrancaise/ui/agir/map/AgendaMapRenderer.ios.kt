package org.politisationpopulairefrancaise.ui.agir.map

/**
 * Not yet: the iOS application is not compiled (no Apple developer account). MapLibre Compose
 * supports iOS; the Android renderer only uses its common API and can be shared with iOS then
 * (docs/integration/agir-carte.md).
 */
actual fun platformAgendaMapRenderer(): AgendaMapRenderer? = null

/** To be read from the system (Low Data Mode) when iOS is built; nothing is asked meanwhile. */
@androidx.compose.runtime.Composable
internal actual fun rememberServerTilesAllowed(): () -> Boolean = { false }
