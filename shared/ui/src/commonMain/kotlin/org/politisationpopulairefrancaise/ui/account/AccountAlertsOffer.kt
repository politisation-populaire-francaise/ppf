package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_alerts_offer
import org.politisationpopulairefrancaise.ui.resources.account_alerts_offer_accept
import org.politisationpopulairefrancaise.ui.resources.account_alerts_offer_decline
import org.politisationpopulairefrancaise.ui.resources.account_alerts_offer_done
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Offered once the phrase was typed back (decision of the team lead of 8 November 2026, S-146):
 * switch on the category « Mon compte », so that the next alerts reach the system too. Only a
 * proposal: « Non merci » leaves everything off, and the alerts stay in Mon espace and in the
 * notification centre anyway. Nothing is shown when the category was already on.
 */
@Composable
internal fun AccountAlertsOffer(notifications: NotificationsSettingsState, modifier: Modifier = Modifier) {
    val enabled = notifications.settings.isEnabled(NotificationCategory.ACCOUNT)
    val wasOn = remember { enabled }
    var declined by rememberSaveable { mutableStateOf(false) }
    if (wasOn || declined) return
    InfoBox(Lucide.Bell, modifier.testTag(AccountAlertsOfferTags.OFFER)) {
        if (enabled) {
            Text(
                stringResource(Res.string.account_alerts_offer_done),
                style = MaterialTheme.typography.bodyMedium,
                color = PpfTheme.palette.ink,
                modifier = Modifier.testTag(AccountAlertsOfferTags.DONE).semantics { liveRegion = LiveRegionMode.Polite },
            )
            return@InfoBox
        }
        Text(stringResource(Res.string.account_alerts_offer), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PpfSecondaryButton(
                onClick = { notifications.toggleCategory(NotificationCategory.ACCOUNT) },
                text = stringResource(Res.string.account_alerts_offer_accept),
                icon = Lucide.Bell,
                small = true,
                modifier = Modifier.testTag(AccountAlertsOfferTags.ACCEPT),
            )
            PpfTextButton(
                onClick = { declined = true },
                text = stringResource(Res.string.account_alerts_offer_decline),
                color = PpfTheme.palette.inkMuted,
                modifier = Modifier.testTag(AccountAlertsOfferTags.DECLINE),
            )
        }
    }
}

object AccountAlertsOfferTags {
    const val OFFER = "account-alerts-offer"
    const val ACCEPT = "account-alerts-offer-accept"
    const val DECLINE = "account-alerts-offer-decline"
    const val DONE = "account-alerts-offer-done"
}
