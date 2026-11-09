package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.elus.CommuneOfficials
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.LevelNote
import org.politisationpopulairefrancaise.core.elus.LevelOfficials
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.elus.OfficialGroup
import org.politisationpopulairefrancaise.core.elus.OfficialGroupKind
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.elus_members
import org.politisationpopulairefrancaise.ui.resources.elus_others
import org.politisationpopulairefrancaise.ui.resources.elus_box_level_none
import org.politisationpopulairefrancaise.ui.resources.elus_group_canton
import org.politisationpopulairefrancaise.ui.resources.elus_group_collectivite
import org.politisationpopulairefrancaise.ui.resources.elus_group_department_presidency
import org.politisationpopulairefrancaise.ui.resources.elus_group_epci_caption
import org.politisationpopulairefrancaise.ui.resources.elus_group_european
import org.politisationpopulairefrancaise.ui.resources.elus_group_municipal
import org.politisationpopulairefrancaise.ui.resources.elus_group_region_presidency
import org.politisationpopulairefrancaise.ui.resources.elus_group_regional_council
import org.politisationpopulairefrancaise.ui.resources.elus_group_senate
import org.politisationpopulairefrancaise.ui.resources.elus_level_empty
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_collectivite
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_communautaire
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_departemental
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_depute
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_europeen
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_municipal
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_regional
import org.politisationpopulairefrancaise.ui.resources.elus_mandate_senateur
import org.politisationpopulairefrancaise.ui.resources.elus_note_all_cantons
import org.politisationpopulairefrancaise.ui.resources.elus_note_all_circonscriptions
import org.politisationpopulairefrancaise.ui.resources.elus_note_all_epci
import org.politisationpopulairefrancaise.ui.resources.elus_note_no_epci
import org.politisationpopulairefrancaise.ui.resources.elus_note_no_national
import org.politisationpopulairefrancaise.ui.resources.elus_show_less
import org.politisationpopulairefrancaise.ui.resources.elus_show_more
import org.politisationpopulairefrancaise.ui.resources.elus_since
import org.politisationpopulairefrancaise.ui.resources.elus_size_ko
import org.politisationpopulairefrancaise.ui.resources.elus_size_mo
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The test tags of the directory of elected officials. */
object ElusTags {
    const val HOME_ENTRY = "elus-home-entry"
    const val PAGE = "elus-page"
    const val PAGE_BACK = "elus-page-back"
    const val COMMUNE = "elus-commune"
    const val CHOOSE = "elus-choose"
    const val CHANGE = "elus-change"
    const val FORGET = "elus-forget"
    const val FORGET_DIALOG = "elus-forget-dialog"
    const val FORGET_CONFIRM = "elus-forget-confirm"
    const val FORGET_KEEP = "elus-forget-keep"
    const val EXAMPLE_BANNER = "elus-example-banner"
    const val SOURCE = "elus-source"
    const val SOURCE_OPEN = "elus-source-open"
    const val CHECK_UPDATE = "elus-check-update"
    const val UPDATE = "elus-update"
    const val UPDATE_STATE = "elus-update-state"
    const val PRIVACY = "elus-privacy"
    const val DIRECTORIES = "elus-directories"
    const val LEAVE_DIALOG = "elus-leave-dialog"
    const val LEAVE_STAY = "elus-leave-stay"
    const val LEAVE_OPEN = "elus-leave-open"

    const val CHOOSER = "elus-chooser"
    const val POSTAL_CODE = "elus-postal-code"
    const val CONTINUE = "elus-continue"
    const val CANCEL = "elus-cancel"
    const val DOWNLOAD = "elus-download"
    const val DATA_SAVER = "elus-data-saver"
    const val CHOOSER_ERROR = "elus-chooser-error"
    const val CHOOSER_BACK = "elus-chooser-back"

    const val BOX_CHOOSE = "elus-box-choose"
    const val BOX_ALL = "elus-box-all"
    const val BOX_DIRECTORIES = "elus-box-directories"

    fun level(level: DecisionLevel): String = "elus-level-${level.key}"
    fun boxLevel(level: DecisionLevel): String = "elus-box-level-${level.key}"
    fun directory(key: String): String = "elus-directory-$key"
    fun commune(code: String): String = "elus-commune-$code"
    fun showMore(level: DecisionLevel, index: Int): String = "elus-show-more-${level.key}-$index"
}

/** The neutral label of a mandate: no gender is deduced, none is needed. */
@Composable
fun mandateLabel(mandate: Mandate): String = stringResource(
    when (mandate) {
        Mandate.MUNICIPAL -> Res.string.elus_mandate_municipal
        Mandate.COMMUNAUTAIRE -> Res.string.elus_mandate_communautaire
        Mandate.DEPARTEMENTAL -> Res.string.elus_mandate_departemental
        Mandate.REGIONAL -> Res.string.elus_mandate_regional
        Mandate.COLLECTIVITE -> Res.string.elus_mandate_collectivite
        Mandate.DEPUTE -> Res.string.elus_mandate_depute
        Mandate.SENATEUR -> Res.string.elus_mandate_senateur
        Mandate.EUROPEEN -> Res.string.elus_mandate_europeen
    },
)

/** The title of a group: « Conseil municipal de Lyon », « 1re circonscription du Rhône »... */
@Composable
fun groupTitle(group: OfficialGroup, officials: CommuneOfficials): String {
    val territory = group.territory?.name.orEmpty()
    return when (group.kind) {
        OfficialGroupKind.MUNICIPAL_COUNCIL -> stringResource(Res.string.elus_group_municipal, officials.commune.name)
        OfficialGroupKind.INTERCOMMUNALITE -> territory
        OfficialGroupKind.CANTON -> stringResource(Res.string.elus_group_canton, territory)
        OfficialGroupKind.DEPARTMENT_PRESIDENCY -> stringResource(Res.string.elus_group_department_presidency)
        OfficialGroupKind.COLLECTIVITE -> stringResource(Res.string.elus_group_collectivite, territory)
        OfficialGroupKind.REGION_PRESIDENCY -> stringResource(Res.string.elus_group_region_presidency)
        OfficialGroupKind.REGIONAL_COUNCIL -> stringResource(Res.string.elus_group_regional_council, territory)
        OfficialGroupKind.CIRCONSCRIPTION -> territory
        OfficialGroupKind.SENATE -> stringResource(Res.string.elus_group_senate, officials.department.name)
        OfficialGroupKind.EUROPEAN_PARLIAMENT -> stringResource(Res.string.elus_group_european)
    }
}

@Composable
private fun groupCaption(group: OfficialGroup, officials: CommuneOfficials, notes: List<LevelNote>): String? = when {
    group.kind == OfficialGroupKind.INTERCOMMUNALITE && LevelNote.ALL_MEMBERS_OF_INTERCOMMUNALITE !in notes ->
        stringResource(Res.string.elus_group_epci_caption, officials.commune.name)
    else -> null
}

@Composable
fun noteText(note: LevelNote): String = stringResource(
    when (note) {
        LevelNote.NO_INTERCOMMUNALITE -> Res.string.elus_note_no_epci
        LevelNote.ALL_MEMBERS_OF_INTERCOMMUNALITE -> Res.string.elus_note_all_epci
        LevelNote.ALL_CANTONS_OF_DEPARTMENT -> Res.string.elus_note_all_cantons
        LevelNote.ALL_CIRCONSCRIPTIONS_OF_DEPARTMENT -> Res.string.elus_note_all_circonscriptions
        LevelNote.NATIONAL_PACKAGE_MISSING -> Res.string.elus_note_no_national
    },
)

/** A size in French: « 245 ko », « 1,2 Mo » (1 ko = 1 000 octets). */
@Composable
fun fileSize(bytes: Long): String = if (bytes < 1_000_000) {
    stringResource(Res.string.elus_size_ko, ((bytes + 999) / 1_000).coerceAtLeast(1).toString())
} else {
    stringResource(Res.string.elus_size_mo, FrenchNumbers.format(bytes / 1_000_000.0))
}

/** One official: the name, then the function given by the source or the neutral label of the mandate, and the start date. */
@Composable
fun OfficialLine(official: ElectedOfficial, modifier: Modifier = Modifier, withDate: Boolean = true) {
    val palette = PpfTheme.palette
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Lucide.User, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(official.displayName, style = MaterialTheme.typography.titleSmall)
            val label = official.function ?: mandateLabel(official.mandate)
            val since = (official.functionSince ?: official.since)?.takeIf { withDate }?.let { stringResource(Res.string.elus_since, it.toFrench()) }
            Text(listOfNotNull(label, since).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        if (official.isExample) {
            Spacer(Modifier.width(8.dp))
            ExampleBadge()
        }
    }
}

/**
 * The optional slot under each elected official of the page « Mes élus » (team lead, 9 November 2026, on a
 * proposal of « Voir »): nothing by default. « Voir » provides it once both spaces are in main, with the
 * sponsorships of the presidential election read on the device only (« mon maire, mon député a parrainé ») and a
 * link to its page; the same slot will carry the link « Leurs votes au Parlement » (VO-34). Its rules: never a
 * mention « n'a pas parrainé », never a button to call on the official. Not shown in the boxes of the sheets.
 */
val LocalElectedOfficialExtra = staticCompositionLocalOf<(@Composable (ElectedOfficial) -> Unit)?> { null }

/** A group on the page « Mes élus »: its title, then its officials (the first twelve, the others on request). */
@Composable
fun OfficialGroupBlock(group: OfficialGroup, officials: CommuneOfficials, notes: List<LevelNote>, showMoreTag: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    var expanded by rememberSaveable(showMoreTag) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(groupTitle(group, officials), style = MaterialTheme.typography.titleSmall, color = palette.brand, modifier = Modifier.semantics { heading() })
        groupCaption(group, officials, notes)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
        Spacer(Modifier.height(4.dp))
        val shown = if (expanded || group.officials.size <= PAGE_LIMIT + 2) group.officials else group.officials.take(PAGE_LIMIT)
        val extra = LocalElectedOfficialExtra.current
        shown.forEach { official ->
            OfficialLine(official)
            extra?.invoke(official)
        }
        if (group.officials.size > PAGE_LIMIT + 2) {
            PpfTextButton(
                onClick = { expanded = !expanded },
                text = if (expanded) stringResource(Res.string.elus_show_less) else stringResource(Res.string.elus_show_more, group.officials.size - PAGE_LIMIT),
                modifier = Modifier.testTag(showMoreTag),
            )
        }
    }
}

/** The groups and notes of one level on the page, or a sentence when the list has none. */
@Composable
fun LevelOfficialsBlock(level: LevelOfficials, officials: CommuneOfficials) {
    val palette = PpfTheme.palette
    level.notes.forEach { note ->
        Text(noteText(note), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(6.dp))
    }
    if (level.groups.isEmpty() && level.notes.isEmpty()) {
        Text(stringResource(Res.string.elus_level_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
    level.groups.forEachIndexed { index, group ->
        if (index > 0) Spacer(Modifier.height(12.dp))
        OfficialGroupBlock(group, officials, level.notes, ElusTags.showMore(level.level, index))
    }
}

/**
 * The officials of one level in the box of a « Qui décide de ça ? » sheet: a few names per group
 * (the head of the council first), then how many others; the page « Mes élus » has them all.
 */
@Composable
fun BoxLevelOfficials(level: LevelOfficials, officials: CommuneOfficials, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(ElusTags.boxLevel(level.level))) {
        if (level.groups.isEmpty()) {
            val text = level.notes.firstOrNull()?.let { noteText(it) } ?: stringResource(Res.string.elus_box_level_none)
            Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        level.groups.forEach { group ->
            val limit = when (group.kind) {
                OfficialGroupKind.REGIONAL_COUNCIL, OfficialGroupKind.EUROPEAN_PARLIAMENT, OfficialGroupKind.COLLECTIVITE -> 0
                else -> BOX_LIMIT
            }
            if (group.kind != OfficialGroupKind.MUNICIPAL_COUNCIL && group.kind != OfficialGroupKind.DEPARTMENT_PRESIDENCY && group.kind != OfficialGroupKind.REGION_PRESIDENCY) {
                Text(groupTitle(group, officials), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted, modifier = Modifier.padding(top = 4.dp))
            }
            group.officials.take(limit).forEach { OfficialLine(it, withDate = false) }
            val others = group.officials.size - limit.coerceAtMost(group.officials.size)
            if (others > 0) {
                Text(
                    if (limit == 0) pluralStringResource(Res.plurals.elus_members, others, others) else pluralStringResource(Res.plurals.elus_others, others, others),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
        }
    }
}

private const val PAGE_LIMIT = 12
private const val BOX_LIMIT = 3
