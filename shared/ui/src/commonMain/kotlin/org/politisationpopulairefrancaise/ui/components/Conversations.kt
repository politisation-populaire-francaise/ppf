package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * The building blocks of the conversation screens (mock-up of the owner, 2026-11-06): the rows of
 * a list of threads, the 64 dp header of a thread, the bubbles, the day separators, the pill
 * text field and the round send button. Private messages and the flows of followed accounts
 * share them; the rooms of collectives will too.
 */

/** The avatar colour chosen from [seed], the same as the one [Avatar] paints: for an author's name in a bubble. */
@Composable
fun avatarColor(seed: String): Color {
    val palette = PpfTheme.palette
    return palette.avatars[(seed.hashCode() and 0x7fffffff) % palette.avatars.size]
}

/** A small accent pill with a number: unread messages. Screen readers hear [contentDescription]. */
@Composable
fun CountBadge(count: Long, contentDescription: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Box(
        modifier = modifier
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .background(palette.accent, CircleShape)
            .padding(horizontal = 6.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = palette.onAccent,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            maxLines = 1,
        )
    }
}

/**
 * One thread of a list: [avatar] on the left, [title] in bold, [preview] on one line, [time] and,
 * when [unread] is positive, the count badge at the right. [pending] adds a small clock before the
 * time (the last message still waits for the server). The whole row is one button.
 */
@Composable
fun ThreadRow(
    title: String,
    preview: String?,
    time: String?,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    unread: Long = 0,
    unreadDescription: String? = null,
    unreadTag: String? = null,
    pending: Boolean = false,
    pendingDescription: String? = null,
    selected: Boolean = false,
    avatar: @Composable () -> Unit,
) {
    val palette = PpfTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) palette.brandTint else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics { }) { avatar() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread > 0) palette.ink else palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pending) {
                    Icon(Lucide.Clock, contentDescription = pendingDescription, tint = palette.warning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (time != null) {
                    Text(time, style = MaterialTheme.typography.labelMedium, color = if (unread > 0) palette.accent else palette.inkSubtle, maxLines = 1)
                }
            }
            if (unread > 0) {
                CountBadge(unread, unreadDescription ?: unread.toString(), if (unreadTag != null) Modifier.testTag(unreadTag) else Modifier)
            }
        }
    }
}

/**
 * The 64 dp header of a thread: an optional back button, the avatar, the title and a one-line
 * [subtitle] (the encryption notice, the public-flow notice), then optional [actions].
 */
@Composable
fun ThreadHeader(
    title: String,
    onBack: (() -> Unit)?,
    backDescription: String,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
    subtitle: @Composable RowScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    Surface(color = palette.surface0, contentColor = palette.ink, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (onBack != null) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    PpfIconButton(Lucide.ArrowLeft, contentDescription = backDescription, onClick = onBack, tint = palette.ink)
                    Spacer(Modifier.width(4.dp))
                }
                Box(Modifier.clearAndSetSemantics { }) { avatar() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, content = subtitle)
                }
                if (actions != null) actions()
            }
            HorizontalDivider(color = palette.border)
        }
    }
}

/** The small green line of every encrypted thread header: a lock and "Chiffré de bout en bout". */
@Composable
fun EncryptedNotice(text: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Lock, contentDescription = null, tint = palette.success, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = palette.success, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A centred day label between the bubbles of two days. */
@Composable
fun DaySeparator(label: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.extraSmall, color = palette.surface2, contentColor = palette.inkMuted, border = BorderStroke(1.dp, palette.border)) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/**
 * One message. Others' bubbles sit on the left (card surface, 1 dp border, 16/16/16/6 dp corners,
 * the author's name in their avatar colour); the user's own sit on the right (brand background,
 * 16/16/6/16 dp corners). The hour stands at the bottom right in 12/16; [pending] adds a clock to
 * it, described for screen readers by [pendingDescription]. A bubble takes at most 82 % of the width
 * and is read as one item by screen readers. [label] sits above the text (the [EXEMPLE] badge of a
 * public flow).
 */
@Composable
fun MessageBubble(
    text: String,
    mine: Boolean,
    time: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    authorSeed: String? = null,
    pending: Boolean = false,
    pendingDescription: String? = null,
    /** Applied to the clock icon, so that a test can find it. */
    pendingModifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val shape = if (mine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 6.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 6.dp)
    }
    val timeColor = if (mine) palette.onBrand.copy(alpha = 0.8f) else palette.inkSubtle
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = shape,
            color = if (mine) palette.brand else palette.surface2,
            contentColor = if (mine) palette.onBrand else palette.ink,
            border = if (mine) null else BorderStroke(1.dp, palette.border),
            modifier = Modifier.widthIn(max = maxWidth * 0.82f).semantics(mergeDescendants = true) { },
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!mine && author != null) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (authorSeed != null) avatarColor(authorSeed) else palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (label != null) {
                    label()
                    Spacer(Modifier.height(4.dp))
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (pending) {
                        Icon(Lucide.Clock, contentDescription = pendingDescription, tint = timeColor, modifier = pendingModifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(time, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp), color = timeColor, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The pill-shaped text field of a composer: page-coloured, fully rounded, grows up to five lines.
 * With [onSend], the keyboard's action key sends, and so does Enter on a physical keyboard
 * (Shift+Enter starts a new line), as in chat applications on a computer.
 */
@Composable
fun PpfPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSend: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (onSend == null || !enter || event.isShiftPressed) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) onSend()
                true
            }
            .semantics { contentDescription = placeholder },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
        cursorBrush = SolidColor(palette.brand),
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default),
        keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .background(palette.surface1, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = palette.inkSubtle, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** A 44 dp round filled brand button with one icon: send. */
@Composable
fun PpfRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = PpfTheme.palette
    IconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = palette.brand,
            contentColor = palette.onBrand,
            disabledContainerColor = palette.border,
            disabledContentColor = palette.inkSubtle,
        ),
        modifier = modifier.size(44.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}
