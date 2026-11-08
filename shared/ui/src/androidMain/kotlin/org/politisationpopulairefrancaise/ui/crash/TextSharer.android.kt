package org.politisationpopulairefrancaise.ui.crash

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The system's share sheet, with the text only (no file, no permission): the person picks where it goes. */
@Composable
internal actual fun rememberTextSharer(title: String): ((String) -> Unit)? {
    val context = LocalContext.current
    return remember(context, title) {
        { text: String ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, title).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
            Unit
        }
    }
}
