package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.MediaKit
import org.politisationpopulairefrancaise.core.voir.PressRelease
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The page of the dashboard on the site (PP-07, web/voir/DashboardPage.kt). */
private const val DASHBOARD_PATH: String = "/tableau-de-bord"

/** Test tags of « Espace presse ». */
object PressTags {
    const val OPEN = "press-open"
    const val PAGE = "press-page"
    const val RELEASES = "press-releases"
    const val KIT = "press-kit"
    const val FIGURES = "press-figures"
    const val CONTACT = "press-contact"
    const val DASHBOARD = "press-dashboard"
    fun release(slug: String) = "press-release-$slug"
}

/**
 * « Espace presse » (M34, PP-04): the press releases newest first, the media kit, the reusable figures and the
 * press contact through the contact of PPF (M30, category « Journaliste »).
 */
@Composable
internal fun PressPage(catalog: VoirCatalog, onTransparency: () -> Unit, onVictories: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    Column(modifier.fillMaxWidth().widthIn(max = 860.dp).testTag(PressTags.PAGE), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.press_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.press_intro), style = MaterialTheme.typography.bodyMedium)

        Column(Modifier.fillMaxWidth().testTag(PressTags.RELEASES), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(Res.string.press_releases))
            if (catalog.pressReleases.isEmpty()) {
                Text(stringResource(Res.string.press_no_release), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            catalog.pressReleases.forEach { ReleaseCard(it) }
        }

        Column(Modifier.fillMaxWidth().testTag(PressTags.KIT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(Res.string.press_kit))
            KitLine(Res.string.press_kit_name, "${MediaKit.NAME} (${MediaKit.ACRONYM})")
            KitLine(Res.string.press_kit_site, MediaKit.SITE)
            KitLine(Res.string.press_kit_colors, "${MediaKit.BRAND_COLOR}, ${MediaKit.ACCENT_COLOR}, ${MediaKit.INK_COLOR}")
            KitLine(Res.string.press_kit_typeface, MediaKit.TYPEFACE)
            KitLine(Res.string.press_kit_icons, MediaKit.ICONS)
            Text(stringResource(Res.string.press_kit_license), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            Text(stringResource(Res.string.press_kit_state), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }

        Column(Modifier.fillMaxWidth().testTag(PressTags.FIGURES), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(Res.string.press_figures))
            Text(stringResource(Res.string.press_figures_text), style = MaterialTheme.typography.bodyMedium)
            LinkRow(stringResource(Res.string.transp_title), Lucide.Landmark, onClick = onTransparency)
            LinkRow(stringResource(Res.string.victories_title), Lucide.Flag, onClick = onVictories)
            // The figures of the movement (PP-07), computed by the server: the page of the site, after confirmation.
            LinkRow(stringResource(Res.string.press_dashboard), Lucide.ExternalLink, onClick = { open(VoirLinks.SITE + DASHBOARD_PATH) }, modifier = Modifier.testTag(PressTags.DASHBOARD))
        }

        InfoBox(Lucide.Mail, Modifier.testTag(PressTags.CONTACT)) {
            Text(stringResource(Res.string.press_contact_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.press_contact_text, MediaKit.PRESS_REPLY_GOAL_DAYS), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KitLine(label: org.jetbrains.compose.resources.StringResource, value: String) {
    Text(stringResource(Res.string.press_kit_line, stringResource(label), value), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ReleaseCard(release: PressRelease) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(PressTags.release(release.article.slug))) {
        Text(release.date.toFrench(), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        VoirTitle(release.title, style = MaterialTheme.typography.titleMedium)
        release.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (release.article.body.isNotBlank()) MarkdownText(release.article.body, glossary = false)
        ArchivesSection(release.archives)
        SourcesSection(release.sources)
        CorrectionsSection(release.corrections)
    }
}
