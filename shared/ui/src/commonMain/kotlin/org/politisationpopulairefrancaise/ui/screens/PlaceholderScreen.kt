package org.politisationpopulairefrancaise.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowHeightClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.layout_compact
import org.politisationpopulairefrancaise.ui.resources.layout_expanded
import org.politisationpopulairefrancaise.ui.resources.layout_landscape_hint
import org.politisationpopulairefrancaise.ui.resources.layout_medium
import org.politisationpopulairefrancaise.ui.resources.layout_size
import org.politisationpopulairefrancaise.ui.resources.platform_label
import org.politisationpopulairefrancaise.ui.resources.skeleton_notice
import org.politisationpopulairefrancaise.ui.resources.version_label
import org.politisationpopulairefrancaise.ui.shell.SectionSpec
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Phase 0 placeholder for every space: its title, its description and a card
 * that tells which layout is active, so that the owner can check the adaptive
 * behaviour on her phone, a tablet and her computer.
 */
@Composable
fun PlaceholderScreen(
    section: SectionSpec,
    windowClass: WindowClass,
    widthDp: Int,
    heightDp: Int,
    modifier: Modifier = Modifier,
) {
    val platform = currentPlatform()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (windowClass.width == WindowWidthClass.COMPACT) 16.dp else 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(Modifier.widthIn(max = 720.dp)) {
            PageHeader(stringResource(section.title), stringResource(section.description))
            Spacer(Modifier.height(24.dp))
            PpfCard(Modifier.fillMaxWidth()) {
                Column {
                    val layoutLabel = when (windowClass.width) {
                        WindowWidthClass.COMPACT -> Res.string.layout_compact
                        WindowWidthClass.MEDIUM -> Res.string.layout_medium
                        WindowWidthClass.EXPANDED -> Res.string.layout_expanded
                    }
                    Text(stringResource(layoutLabel), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.layout_size, widthDp, heightDp), style = MaterialTheme.typography.bodyMedium)
                    if (windowClass.height == WindowHeightClass.COMPACT) {
                        Text(stringResource(Res.string.layout_landscape_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.platform_label, platform.name, platform.version), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.version_label, AppInfo.VERSION), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.skeleton_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = PpfTheme.palette.inkSubtle,
            )
        }
    }
}
