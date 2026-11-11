package org.politisationpopulairefrancaise.ui.agir.gather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.gather_association_missing
import org.politisationpopulairefrancaise.ui.resources.gather_association_text
import org.politisationpopulairefrancaise.ui.resources.gather_association_title
import org.politisationpopulairefrancaise.ui.resources.gather_back
import org.politisationpopulairefrancaise.ui.resources.gather_collectives_text
import org.politisationpopulairefrancaise.ui.resources.gather_collectives_title
import org.politisationpopulairefrancaise.ui.resources.gather_description
import org.politisationpopulairefrancaise.ui.resources.gather_organisations_text
import org.politisationpopulairefrancaise.ui.resources.gather_organisations_title
import org.politisationpopulairefrancaise.ui.resources.gather_rooms_text
import org.politisationpopulairefrancaise.ui.resources.gather_rooms_title
import org.politisationpopulairefrancaise.ui.resources.gather_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Se réunir ». */
object GatherTags {
    const val SCREEN = "gather-screen"
    const val BACK = "gather-back"
    const val COLLECTIVES = "gather-collectives"
    const val ORGANISATIONS = "gather-organisations"
    const val ASSOCIATION = "gather-association"
    const val ASSOCIATION_MISSING = "gather-association-missing"
    const val ROOMS = "gather-rooms"
}

/** The practical guide « Créer une association » (content/fiches/creer-une-association.md). */
const val GUIDE_CREATE_ASSOCIATION: String = "creer-une-association"

/** Where « Se réunir » leads inside « Agir ». */
class GatherLinks(
    val openCollectives: () -> Unit,
    val openOrganisations: () -> Unit,
    val openRooms: () -> Unit,
)

/**
 * « Se réunir » (D-198, step 4 of the tutorial and its button « m'allier »): one clear entry that gathers what
 * already exists: the collectifs (M24), the directory of the organisations (associations, unions), the guide
 * « Créer une association » read inside « Agir », and the public rooms of the messaging (frozen block, reused
 * as it is). Each destination leads back here.
 */
@Composable
fun GatherScreen(
    library: ContentLibrary,
    windowClass: WindowClass,
    links: GatherLinks,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val guide = library.guides.firstOrNull { it.slug == GUIDE_CREATE_ASSOCIATION }
    var reading by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(reading) { scroll.scrollTo(0) }
    SystemBack(enabled = reading) { reading = false }
    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp).testTag(GatherTags.SCREEN),
    ) {
        if (reading) {
            GuideReader(guide, onBack = { reading = false })
            return@Column
        }
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.gather_back), onBack, Modifier.testTag(GatherTags.BACK), tint = palette.ink)
            }
            PageHeader(stringResource(Res.string.gather_title), stringResource(Res.string.gather_description))
            Spacer(Modifier.height(4.dp))
            Destination(Lucide.Users, Res.string.gather_collectives_title, Res.string.gather_collectives_text, GatherTags.COLLECTIVES, links.openCollectives)
            Destination(Lucide.Building2, Res.string.gather_organisations_title, Res.string.gather_organisations_text, GatherTags.ORGANISATIONS, links.openOrganisations)
            Destination(
                Lucide.BookOpen,
                Res.string.gather_association_title,
                if (guide != null) Res.string.gather_association_text else Res.string.gather_association_missing,
                if (guide != null) GatherTags.ASSOCIATION else GatherTags.ASSOCIATION_MISSING,
                onClick = if (guide != null) ({ reading = true }) else null,
            )
            Destination(Lucide.MessageCircle, Res.string.gather_rooms_title, Res.string.gather_rooms_text, GatherTags.ROOMS, links.openRooms)
        }
    }
}

/** One destination: an icon, its name and one sentence; the whole card opens it (none: not on the device yet). */
@Composable
private fun Destination(icon: ImageVector, title: StringResource, text: StringResource, tag: String, onClick: (() -> Unit)?) {
    val palette = PpfTheme.palette
    PpfCard(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.semantics { role = Role.Button } else Modifier).testTag(tag),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = if (onClick != null) palette.brand else palette.inkMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            if (onClick != null) Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}
