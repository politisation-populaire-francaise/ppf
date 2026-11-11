package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_wordmark
import org.politisationpopulairefrancaise.ui.resources.badge_example
import org.politisationpopulairefrancaise.ui.resources.feed_live_label
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * The building blocks of the visual identity (mock-ups of 2026-11-06): cards, buttons, badges,
 * avatars, tabs, action buttons, banners. Every screen composes these rather than styling Material
 * components one by one, so that the whole application changes together.
 */

/** A card: white (or dark) surface, 1 dp border, 16 dp corners, a subtle shadow. */
@Composable
fun PpfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(PpfTheme.dimens.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = PpfTheme.palette
    val color = if (selected) palette.brandTint else palette.surface2
    val border = BorderStroke(1.dp, if (selected) palette.brand else palette.border)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = color,
            contentColor = palette.ink,
            border = border,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = color,
            contentColor = palette.ink,
            border = border,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A quiet panel inside a card (dates, figures, tips): page-coloured background, 10 dp corners. */
@Composable
fun Inset(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = PpfTheme.palette.surface1, contentColor = PpfTheme.palette.ink) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** A highlighted note in the brand tint, with an icon: explanations, invitations. */
@Composable
fun InfoBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = PpfTheme.palette
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = palette.brandTint, contentColor = palette.ink) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), content = content)
        }
    }
}

enum class BadgeStyle { EXAMPLE, SUCCESS, BRAND, NEUTRAL, WARNING, ACCENT }

/** A small bold label with a tinted background: [EXEMPLE], established facts, states. */
@Composable
fun PpfBadge(text: String, style: BadgeStyle = BadgeStyle.NEUTRAL, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val (container, content) = when (style) {
        BadgeStyle.EXAMPLE -> palette.warningTint to palette.warning
        BadgeStyle.SUCCESS -> palette.successTint to palette.success
        BadgeStyle.BRAND -> palette.brandTint to palette.brand
        BadgeStyle.NEUTRAL -> palette.surface1 to palette.inkMuted
        BadgeStyle.WARNING -> palette.warningTint to palette.warning
        BadgeStyle.ACCENT -> palette.accentTint to palette.accentInk
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The badge every example content carries (cahier des charges: no real data before launch). */
@Composable
fun ExampleBadge(modifier: Modifier = Modifier) {
    PpfBadge(stringResource(Res.string.badge_example), BadgeStyle.EXAMPLE, modifier)
}

/**
 * A generated avatar: initials on one of six colours chosen from [seed] (a public key), round for
 * people and with 10 dp corners for organisations and collectives ([rounded] = true).
 */
@Composable
fun Avatar(
    seed: String,
    letters: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    rounded: Boolean = false,
    color: Color? = null,
) {
    val palette = PpfTheme.palette
    val background = color ?: palette.avatars[(seed.hashCode() and 0x7fffffff) % palette.avatars.size]
    val shape = if (rounded) RoundedCornerShape(size * 0.25f) else CircleShape
    Box(
        modifier = modifier.size(size).clip(shape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letters,
            color = palette.onAvatar,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.5f).sp),
            maxLines = 1,
        )
    }
}

/** Two letters derived from a public key, until profiles exist. */
fun avatarLetters(pubkeyHex: String): String =
    runCatching { PublicKey.fromHex(pubkeyHex).npub }.getOrNull()?.removePrefix("npub1")?.take(2)?.uppercase() ?: pubkeyHex.take(2).uppercase()

/** The readable short form of a public key: `npub1abc…`. */
fun shortNpub(pubkeyHex: String): String {
    val npub = runCatching { PublicKey.fromHex(pubkeyHex).npub }.getOrNull() ?: return pubkeyHex.take(12)
    return npub.take(12) + "…"
}

/** The brand name on two lines, in the brand colour: the wordmark of every bar. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text = stringResource(Res.string.app_wordmark),
        color = PpfTheme.palette.brand,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = if (large) 16.sp else 14.sp,
            lineHeight = if (large) 18.sp else 16.sp,
        ),
        maxLines = 2,
        modifier = modifier,
    )
}

/** Filled brand button: the main action of a screen. */
@Composable
fun PpfPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
) {
    val palette = PpfTheme.palette
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = if (small) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = palette.brand, contentColor = palette.onBrand),
        contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp, vertical = 0.dp),
        modifier = modifier.heightIn(min = if (small) 36.dp else 44.dp),
    ) {
        ButtonContent(icon, text)
    }
}

/** Outlined button on a card: the secondary action. */
@Composable
fun PpfSecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
) {
    val palette = PpfTheme.palette
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = if (small) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = palette.surface2, contentColor = palette.ink),
        border = BorderStroke(1.dp, if (enabled) palette.borderStrong else palette.border),
        contentPadding = PaddingValues(horizontal = if (small) 12.dp else 16.dp, vertical = 0.dp),
        modifier = modifier.heightIn(min = if (small) 36.dp else 44.dp),
    ) {
        ButtonContent(icon, text)
    }
}

/** A bare text button: cancel, secondary links, destructive actions in the accent colour. */
@Composable
fun PpfTextButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PpfTheme.palette.brand,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RowScope.ButtonContent(icon: ImageVector?, text: String) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** An icon button of the touch target of the tokens (48 dp) with 10 dp corners; [badge] adds the small accent dot (unread). */
@Composable
fun PpfIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = PpfTheme.palette.inkMuted,
    badge: Boolean = false,
    /** The current place (the messages of the top bar while they are open): a tinted background. */
    selected: Boolean = false,
) {
    val palette = PpfTheme.palette
    val dimens = PpfTheme.dimens
    // The touch target of the tokens (48 dp, never less: WCAG 2.2, 2.5.8).
    Box(modifier.size(dimens.iconButton)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) palette.brandTint else Color.Transparent,
                contentColor = tint,
                disabledContentColor = tint.copy(alpha = 0.38f),
            ),
            modifier = Modifier.size(dimens.iconButton),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(dimens.barIcon))
        }
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(10.dp)
                    .background(palette.accent, CircleShape)
                    .border(2.dp, palette.surface0, CircleShape),
            )
        }
    }
}

/** An action under a publication: icon and label, accent-coloured when [active] (liked, saved). */
@Composable
fun ActionButton(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = label,
) {
    val palette = PpfTheme.palette
    val color = if (active) palette.accent else palette.inkMuted
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = active, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}

/** Underlined tabs: bold labels, a 3 dp brand bar under the active one. */
@Composable
fun PpfTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagFor: ((Int) -> String)? = null,
) {
    val palette = PpfTheme.palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                // As wide as its label (not the remaining width), so the underline matches the text.
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .then(if (tagFor != null) Modifier.testTag(tagFor(index)) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) palette.brand else palette.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) palette.brand else Color.Transparent))
            }
        }
    }
}

/** A small filled circle: live indicator, unread marker. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** The "EN DIRECT" caption with its pulsing-red dot. */
@Composable
fun LiveLabel(modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(palette.accent)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.feed_live_label), style = MaterialTheme.typography.labelMedium, color = palette.accentInk)
    }
}

/** The one-line live banner at the top of the feed on phones. */
@Composable
fun LiveBanner(message: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = palette.accentTint, contentColor = palette.accentInk) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveLabel()
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** The live card of the side column on wide windows. */
@Composable
fun LiveCard(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = palette.accentTint, contentColor = palette.accentInk) {
        Column(Modifier.padding(16.dp)) {
            LiveLabel()
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A setting line: label on the left, its value or control on the right, 56 dp high. */
@Composable
fun SettingRow(label: String, divider: Boolean = true, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        trailing()
    }
    if (divider) HorizontalDivider(color = PpfTheme.palette.border)
}

/** The title of a page and, optionally, its one-line description. */
@Composable
fun PageHeader(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        val dimens = PpfTheme.dimens
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = dimens.pageTitleSize, lineHeight = dimens.pageTitleLineHeight),
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
        }
    }
}

/** A plain text field of the identity: page-coloured box, 6 dp corners, grows with its content. */
@Composable
fun PpfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val palette = PpfTheme.palette
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = placeholder },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
        cursorBrush = SolidColor(palette.brand),
        minLines = minLines,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .background(palette.surface1, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = palette.inkSubtle)
                }
                inner()
            }
        },
    )
}

/** A clickable row of a navigation list (side bar, drawer): icon, bold label, brand tint when selected. */
@Composable
fun NavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    labelColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) palette.brandTint else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint ?: if (selected) palette.brand else palette.inkMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = labelColor ?: if (selected) palette.brand else palette.inkMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (trailing != null) trailing()
    }
}

/** A secondary entry under a navigation row, with a 3 dp bar when it is the current page. */
@Composable
fun NavSubRow(label: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(24.dp).background(if (current) palette.brand else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (current) palette.ink else palette.inkMuted,
        )
    }
}
