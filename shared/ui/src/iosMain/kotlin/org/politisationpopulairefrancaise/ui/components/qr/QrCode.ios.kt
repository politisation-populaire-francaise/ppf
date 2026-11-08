package org.politisationpopulairefrancaise.ui.components.qr

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.qr_scan_phone_only

/** Not compiled yet (no Apple Developer account): the system's own QR encoder and camera will come here. */
actual fun encodeQr(text: String): QrMatrix? = null

actual val canScanQr: Boolean = false

@Composable
actual fun QrScanner(onRead: (String) -> Unit, modifier: Modifier) {
    Text(stringResource(Res.string.qr_scan_phone_only), style = MaterialTheme.typography.bodyMedium, modifier = modifier.testTag(QrTags.SCANNER))
}
