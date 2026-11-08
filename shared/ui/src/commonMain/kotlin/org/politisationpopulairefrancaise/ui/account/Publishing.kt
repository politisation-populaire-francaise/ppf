package org.politisationpopulairefrancaise.ui.account

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_publish_blocked
import androidx.compose.material3.MaterialTheme

/**
 * Whether this device's account may publish (S-146, D-134), the one common point for every space:
 * false once the account is declared compromised. The server refuses such a publication anyway
 * (403 account_compromised); this only greys the buttons out and says why.
 *
 * In a screen of any space:
 * ```
 * val canPublish = LocalCanPublish.current
 * PpfPrimaryButton(text, onClick, enabled = canPublish && formIsValid)
 * PublishBlockedNotice() // shows nothing while publishing is possible
 * ```
 */
val LocalCanPublish = compositionLocalOf { true }

/** Provides [LocalCanPublish] from the account session (done once, in the shell). */
@Composable
fun ProvidePublishing(session: AccountSession, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCanPublish provides (session.compromise == null), content = content)
}

/** The reason publishing is blocked, in plain French; nothing while publishing is possible. */
@Composable
fun PublishBlockedNotice(modifier: Modifier = Modifier) {
    if (LocalCanPublish.current) return
    InfoBox(Lucide.Lock, modifier.testTag(PUBLISH_BLOCKED_TAG)) {
        Text(stringResource(Res.string.account_publish_blocked), style = MaterialTheme.typography.bodyMedium)
    }
}

const val PUBLISH_BLOCKED_TAG: String = "account-publish-blocked"
