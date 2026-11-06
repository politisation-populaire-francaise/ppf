package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_name
import org.politisationpopulairefrancaise.ui.resources.feed_author_close
import org.politisationpopulairefrancaise.ui.resources.feed_author_key_hint
import org.politisationpopulairefrancaise.ui.resources.feed_author_key_label
import org.politisationpopulairefrancaise.ui.resources.feed_follow
import org.politisationpopulairefrancaise.ui.resources.feed_follow_account
import org.politisationpopulairefrancaise.ui.resources.feed_following
import org.politisationpopulairefrancaise.ui.resources.feed_unfollow
import org.politisationpopulairefrancaise.ui.resources.feed_unfollow_account
import org.politisationpopulairefrancaise.ui.resources.feed_verified
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * Following an account (S-120): the small "Suivre" / "Suivi" button of a publication, and the
 * little box that opens on an author's name. The list of followed accounts stays on the device.
 */

/** Test tags of the author box. */
object AuthorDialogTags {
    const val KEY = "author-dialog-key"
    const val FOLLOW = "author-dialog-follow"
    const val CLOSE = "author-dialog-close"
}

/**
 * "Suivre" when the account is not followed, "Suivi" with a check mark when it is; one touch
 * toggles. Screen readers hear the action ("Suivre ce compte" / "Ne plus suivre ce compte").
 */
@Composable
fun FollowButton(followed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, small: Boolean = true) {
    val description = stringResource(if (followed) Res.string.feed_unfollow_account else Res.string.feed_follow_account)
    PpfSecondaryButton(
        onClick = onToggle,
        text = stringResource(if (followed) Res.string.feed_following else Res.string.feed_follow),
        icon = if (followed) Lucide.Check else null,
        small = small,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * The small box opened by touching an author's name or avatar: pseudonym, full selectable `npub`
 * key and the follow action. [followed] is null when the action is not offered (the user's own
 * account). Styled like the dialogs of "Mon espace".
 */
@Composable
fun AuthorDialog(
    pubkeyHex: String,
    followed: Boolean?,
    onToggleFollow: () -> Unit,
    onDismiss: () -> Unit,
    editorial: Boolean = false,
) {
    val palette = PpfTheme.palette
    val npub = remember(pubkeyHex) { runCatching { PublicKey.fromHex(pubkeyHex).npub }.getOrDefault(pubkeyHex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = {
            if (editorial) {
                Avatar(seed = pubkeyHex, letters = "PPF", size = 56.dp, rounded = true, color = palette.brand)
            } else {
                Avatar(seed = pubkeyHex, letters = avatarLetters(pubkeyHex), size = 56.dp)
            }
        },
        title = { Text(shortNpub(pubkeyHex), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (editorial) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.BadgeCheck, contentDescription = stringResource(Res.string.feed_verified), tint = palette.brand, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall, color = palette.ink)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(Res.string.feed_author_key_label), style = MaterialTheme.typography.labelLarge, color = palette.ink)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = npub,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = palette.ink,
                        modifier = Modifier.testTag(AuthorDialogTags.KEY),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.feed_author_key_hint), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (followed != null) {
                TextButton(onClick = onToggleFollow, modifier = Modifier.testTag(AuthorDialogTags.FOLLOW)) {
                    Text(stringResource(if (followed) Res.string.feed_unfollow else Res.string.feed_follow), color = palette.brand)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(AuthorDialogTags.CLOSE)) {
                Text(stringResource(Res.string.feed_author_close), color = palette.inkMuted)
            }
        },
    )
}
