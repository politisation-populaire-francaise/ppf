package org.politisationpopulairefrancaise.ui.agir.federer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.federer.ConvinceKitReader
import org.politisationpopulairefrancaise.core.agir.federer.KitCard
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.crash.rememberTextSharer
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.federer_back
import org.politisationpopulairefrancaise.ui.resources.federer_card_answer
import org.politisationpopulairefrancaise.ui.resources.federer_card_argument
import org.politisationpopulairefrancaise.ui.resources.federer_card_world
import org.politisationpopulairefrancaise.ui.resources.federer_cards_help
import org.politisationpopulairefrancaise.ui.resources.federer_cards_title
import org.politisationpopulairefrancaise.ui.resources.federer_copied
import org.politisationpopulairefrancaise.ui.resources.federer_copy
import org.politisationpopulairefrancaise.ui.resources.federer_description
import org.politisationpopulairefrancaise.ui.resources.federer_kit_missing
import org.politisationpopulairefrancaise.ui.resources.federer_kit_open
import org.politisationpopulairefrancaise.ui.resources.federer_kit_text
import org.politisationpopulairefrancaise.ui.resources.federer_kit_title
import org.politisationpopulairefrancaise.ui.resources.federer_proches_open
import org.politisationpopulairefrancaise.ui.resources.federer_proches_text
import org.politisationpopulairefrancaise.ui.resources.federer_proches_title
import org.politisationpopulairefrancaise.ui.resources.federer_share
import org.politisationpopulairefrancaise.ui.resources.federer_share_title
import org.politisationpopulairefrancaise.ui.resources.federer_tips_text
import org.politisationpopulairefrancaise.ui.resources.federer_tips_title
import org.politisationpopulairefrancaise.ui.resources.federer_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Fédérer ». */
object FedererTags {
    const val SCREEN = "federer-screen"
    const val BACK = "federer-back"
    const val PROCHES = "federer-proches"
    const val KIT_OPEN = "federer-kit-open"
    const val KIT_MISSING = "federer-kit-missing"
    const val CARD = "federer-card-" // followed by the card id
    const val SHARE = "federer-share-" // followed by the card id
    const val COPY = "federer-copy-" // followed by the card id
    const val COPIED = "federer-copied"
}

/** Lets tests record what « Copier le texte » copies; null uses the system's clipboard. */
val LocalFedererClipboard = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The widest a column of text grows, for readable lines. */
private val READABLE = 720.dp

/**
 * « Fédérer » (D-195, demand of the owner: convincing one's circle, opening their eyes, is acting): the private
 * list of relatives (AG-63), the kit « Convaincre autour de soi » written by « Comprendre », and its cards cut out
 * to be shown on the phone or shared. Nothing is sent: sharing and copying are the person's own gesture, and the
 * list of relatives never leaves the device.
 *
 * Without the kit on the device (older contents), the screen says so honestly and keeps the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FedererScreen(
    library: ContentLibrary,
    windowClass: WindowClass,
    onOpenProches: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(library) { library.start() }
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val guide: Article? = library.guides.firstOrNull { it.slug == ConvinceKitReader.SLUG }
    val kit = remember(guide?.event?.id) { guide?.let { ConvinceKitReader.read(it.body) } }
    var reading by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(reading) { scroll.scrollTo(0) }
    // The system's back first closes the guide, then leaves the rubric (the screen of "Agir").
    SystemBack(enabled = reading) { reading = false }

    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val recorder = LocalFedererClipboard.current
    val copy: (String) -> Unit = recorder ?: { text -> clipboard.setText(AnnotatedString(text)) }
    val sharer = rememberTextSharer(stringResource(Res.string.federer_share_title))
    var copied by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp).testTag(FedererTags.SCREEN),
    ) {
        if (reading) {
            GuideReader(guide, onBack = { reading = false })
            return@Column
        }
        Column(Modifier.widthIn(max = READABLE).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.federer_back), onBack, Modifier.testTag(FedererTags.BACK), tint = palette.ink)
            }
            PageHeader(stringResource(Res.string.federer_title), stringResource(Res.string.federer_description))

            // 1. The private list of relatives (AG-63), the first step.
            PpfCard(Modifier.fillMaxWidth().testTag(FedererTags.PROCHES), onClick = onOpenProches) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.UsersRound, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.federer_proches_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.federer_proches_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PpfPrimaryButton(onClick = onOpenProches, text = stringResource(Res.string.federer_proches_open), modifier = Modifier.heightIn(min = 48.dp))
            }

            // 2. The kit of « Comprendre », read whole with its sources.
            PpfCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.federer_kit_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.federer_kit_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (guide == null) {
                    InfoBox(Lucide.Info, Modifier.testTag(FedererTags.KIT_MISSING)) {
                        Text(stringResource(Res.string.federer_kit_missing), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Origin(guide.origin)
                    Spacer(Modifier.height(8.dp))
                    PpfSecondaryButton(
                        onClick = { reading = true },
                        text = stringResource(Res.string.federer_kit_open),
                        icon = Lucide.BookOpen,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(FedererTags.KIT_OPEN),
                    )
                }
            }

            // 3. The cards, to show during a conversation or to share.
            if (kit != null && !kit.isEmpty) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.federer_cards_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(stringResource(Res.string.federer_cards_help), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                copied?.let {
                    Text(
                        stringResource(Res.string.federer_copied),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.success,
                        modifier = Modifier.testTag(FedererTags.COPIED).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                kit.cards.forEach { card ->
                    KitCardView(
                        card = card,
                        origin = guide?.origin ?: ContentOrigin.AI_ASSISTED,
                        onShare = sharer?.let { share -> { share(ConvinceKitReader.shareText(card)) } },
                        onCopy = {
                            copy(ConvinceKitReader.shareText(card))
                            copied = card.id
                        },
                    )
                }
            }

            // 4. A few tips, for every conversation.
            InfoBox(Lucide.Lightbulb) {
                Text(stringResource(Res.string.federer_tips_title), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.federer_tips_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitCardView(card: KitCard, origin: ContentOrigin, onShare: (() -> Unit)?, onCopy: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(FedererTags.CARD + card.id)) {
        val kind = when (card) {
            is KitCard.Argument -> Res.string.federer_card_argument
            is KitCard.Answer -> Res.string.federer_card_answer
            is KitCard.World -> Res.string.federer_card_world
        }
        PpfBadge(stringResource(kind), BadgeStyle.NEUTRAL)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Column {
                Text(
                    if (card is KitCard.Answer) "« ${card.title} »" else card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (card.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(card.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Origin(origin)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onShare != null) {
                PpfTextButton(onClick = onShare, text = stringResource(Res.string.federer_share), modifier = Modifier.heightIn(min = 48.dp).testTag(FedererTags.SHARE + card.id))
            }
            PpfTextButton(onClick = onCopy, text = stringResource(Res.string.federer_copy), modifier = Modifier.heightIn(min = 48.dp).testTag(FedererTags.COPY + card.id), color = palette.brand)
        }
    }
}

/** The origin of the text, always visible (D-212). */
@Composable
private fun Origin(origin: ContentOrigin) {
    Text(
        stringResource(if (origin == ContentOrigin.AI_ASSISTED) Res.string.content_origin_ai else Res.string.content_origin_human),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkSubtle,
    )
}
