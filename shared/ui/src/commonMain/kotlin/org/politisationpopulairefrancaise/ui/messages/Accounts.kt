package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_name
import org.politisationpopulairefrancaise.ui.resources.feed_badge_you
import org.politisationpopulairefrancaise.ui.resources.messages_group_participants
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * How the messages screens name and draw an account, like the feed does: the application's name
 * and its badge for a trusted editorial account, "Vous" for the user, the short npub otherwise
 * (profiles do not exist yet).
 */

@Composable
internal fun accountName(pubkeyHex: String, trust: EditorialTrust, me: String?): String = when {
    trust.isTrusted(pubkeyHex) -> stringResource(Res.string.app_name)
    pubkeyHex == me -> stringResource(Res.string.feed_badge_you)
    else -> shortNpub(pubkeyHex)
}

@Composable
internal fun AccountAvatar(pubkeyHex: String, trust: EditorialTrust, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (trust.isTrusted(pubkeyHex)) {
        Avatar(seed = pubkeyHex, letters = "PPF", modifier = modifier, size = size, rounded = true, color = PpfTheme.palette.brand)
    } else {
        Avatar(seed = pubkeyHex, letters = avatarLetters(pubkeyHex), modifier = modifier, size = size)
    }
}

/** What the list and the header call a conversation: the other person, or the subject or size of a group. */
@Composable
internal fun conversationTitle(others: List<String>, subject: String?, trust: EditorialTrust): String = when {
    others.size == 1 -> accountName(others.single(), trust, me = null)
    subject != null -> subject
    else -> stringResource(Res.string.messages_group_participants, others.size + 1)
}

/** The avatar of a conversation: the other person's, or a squared one with the number of participants for a group. */
@Composable
internal fun ConversationAvatar(others: List<String>, conversationId: String, trust: EditorialTrust, size: Dp = 40.dp) {
    if (others.size == 1) {
        AccountAvatar(others.single(), trust, size = size)
    } else {
        Avatar(seed = conversationId, letters = (others.size + 1).toString(), size = size, rounded = true)
    }
}
