package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.DocumentRequestStore
import org.politisationpopulairefrancaise.core.decider.LetterNoteStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.resources.decider_consultations_section
import org.politisationpopulairefrancaise.ui.resources.decider_letters_section
import org.politisationpopulairefrancaise.ui.resources.decider_local_section
import org.politisationpopulairefrancaise.ui.resources.decider_local_section_intro
import org.politisationpopulairefrancaise.ui.resources.decider_letters_section_intro
import org.politisationpopulairefrancaise.ui.resources.decider_documents_open
import org.politisationpopulairefrancaise.ui.resources.decider_documents_section
import org.politisationpopulairefrancaise.ui.resources.decider_documents_section_intro
import org.politisationpopulairefrancaise.ui.resources.decider_consultations_section_intro
import org.politisationpopulairefrancaise.ui.resources.decider_elections_section
import org.politisationpopulairefrancaise.ui.resources.decider_elections_section_intro
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.LinkRow
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirRequestSession
import org.politisationpopulairefrancaise.ui.voir.rememberVoirRequestSession
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/**
 * "Décider" (vision, pillar 4: reprendre le pouvoir). This first version gathers what the law
 * already offers and what the platform builds to make it count: the official petitions with
 * their real thresholds (M10, DE-07) and the democratic pact sent to every candidate (M13.5,
 * DE-08), then the ways to the demands wall and to « Qui décide de ça ? ». The citizens' votes
 * wait for the method « une personne, une voix » (M13.6), which is said plainly.
 *
 * One column on phones, two on tablets, three on wide windows; the table of the pact becomes a
 * real table on wide windows. The back button (or the system's) closes the open page.
 */
@Composable
fun DeciderScreen(
    library: ContentLibrary,
    state: DeciderState,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    /** Opens a content of "Voir" (a candidate's sheet, a bill, a fact) in its space. */
    onOpenVoir: (ContentAddress) -> Unit = {},
    now: () -> Long = ::currentEpochSeconds,
    /** For the past votes of the comparator (history packages of "Voir") and their reports; null in previews. */
    dependencies: AppDependencies? = null,
    /** The reminders of the elections (DE-28), kept on the device; null where there is none (previews). */
    reminders: DeciderRemindersState? = null,
) {
    val pastVotes = dependencies?.let { rememberPastVotesState(it) }
    val reports = dependencies?.let { rememberVoirRequestSession(it, messaging = null) }
    val photos = dependencies?.let { rememberComparatorPhotosState(it) }
    val catalog = library.decider
    val voir = library.voir
    val today = CivilDate.fromEpochSeconds(now())
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val actions = DeciderActions(
        openPerson = onOpenVoir,
        openFact = onOpenVoir,
        next = { target ->
            when (target) {
                is NextTarget.ToSpace -> onNavigate(target.space)
                is NextTarget.ToContent -> when {
                    catalog.petition(target.address) != null -> state.open(DeciderDestination.Petition(target.address))
                    catalog.pacte(target.address) != null -> state.open(DeciderDestination.Pacte(target.address))
                    voir.item(target.address) != null -> onOpenVoir(target.address)
                    else -> library.guides.firstOrNull { it.address == target.address }?.let { guide ->
                        library.open(guide)
                        onNavigate(Space.COMPRENDRE)
                    }
                }
            }
        },
    )
    SystemBack(enabled = state.stack.isNotEmpty()) { state.back() }

    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 960.dp -> 2
            else -> 3
        }
        val wide = maxWidth >= 900.dp
        Box(Modifier.fillMaxSize().testTag(DeciderTags.SCREEN)) {
            val current = state.current
            if (current == null) {
                DeciderHome(catalog, voir, library.status, state, today, compact, columns, onNavigate, territories = rememberDeviceTerritories(dependencies))
            } else {
                key(current) { DeciderDetail(current, catalog, voir, state, actions, today, compact, wide, columns, onOpenVoir, nowSeconds = now(), pastVotes = pastVotes, reports = reports, photos = photos, reminders = reminders, dependencies = dependencies) }
            }
        }
    }
}

@Composable
private fun DeciderHome(
    catalog: DeciderCatalog,
    voir: VoirCatalog,
    status: LibraryStatus,
    state: DeciderState,
    today: CivilDate,
    compact: Boolean,
    columns: Int,
    onNavigate: (Space) -> Unit,
    /** The commune chosen on the device and its department: the local elections, consultations and petitions. */
    territories: Set<String> = emptySet(),
) {
    val palette = PpfTheme.palette
    val padding = if (compact) 16.dp else 24.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
        PageHeader(stringResource(Res.string.space_decider), if (compact) null else stringResource(Res.string.desc_decider))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_intro), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
        FollowedStrip(catalog, state)
        if (catalog.isEmpty && status != LibraryStatus.Loading) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.decider_empty), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        val elections = catalog.upcomingElections(today, territories)
        if (elections.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("elections"))) {
                SectionHeader(stringResource(Res.string.decider_elections_section))
                Text(stringResource(Res.string.decider_elections_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                elections.forEach { election ->
                    Spacer(Modifier.height(12.dp))
                    ElectionCard(election, today, onOpen = { state.open(DeciderDestination.Election(election.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                }
            }
        }
        catalog.currentPacte()?.let { pacte ->
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("pacte"))) {
                SectionHeader(stringResource(Res.string.decider_pacte_section))
                Text(stringResource(Res.string.decider_pacte_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                Spacer(Modifier.height(12.dp))
                PacteCard(catalog.board(pacte, voir, today), onOpen = { state.open(DeciderDestination.Pacte(pacte.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                catalog.pactes.filter { it != pacte }.forEach { other ->
                    Spacer(Modifier.height(8.dp))
                    LinkRow(other.title.withoutExample(), Lucide.BadgeCheck, onClick = { state.open(DeciderDestination.Pacte(other.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                }
            }
        }
        catalog.currentComparator()?.let { sheet ->
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("comparateur"))) {
                SectionHeader(stringResource(Res.string.decider_comparator_section))
                Text(stringResource(Res.string.decider_comparator_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                Spacer(Modifier.height(12.dp))
                ComparatorCard(catalog.grid(sheet, voir), onOpen = { state.open(DeciderDestination.Comparator(sheet.address)) }, modifier = Modifier.widthIn(max = 760.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        val open = catalog.openPetitions(today)
        val closed = catalog.closedPetitions(today)
        Column(Modifier.testTag(DeciderTags.section("petitions"))) {
            SectionHeader(
                stringResource(Res.string.decider_petitions_section_count, open.size),
                action = stringResource(Res.string.decider_thresholds_link),
                actionTag = DeciderTags.THRESHOLDS_OPEN,
                onAction = { state.open(DeciderDestination.Thresholds()) },
            )
            Text(stringResource(Res.string.decider_petitions_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
            Spacer(Modifier.height(12.dp))
            if (open.isEmpty()) Text(stringResource(Res.string.decider_petitions_none_open), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            PetitionGrid(open, today, columns) { state.open(DeciderDestination.Petition(it.address)) }
            if (closed.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PpfTextButton(
                    onClick = { state.open(DeciderDestination.ClosedPetitions) },
                    text = stringResource(Res.string.decider_closed_link, closed.size),
                    modifier = Modifier.testTag(DeciderTags.CLOSED_OPEN),
                )
            }
        }
        // The official consultations (DE-27): open ones first, then those to come; the local ones later, by the commune.
        val consultations = catalog.currentConsultations(today, territories)
        if (consultations.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("consultations"))) {
                SectionHeader(stringResource(Res.string.decider_consultations_section))
                Text(stringResource(Res.string.decider_consultations_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                consultations.forEach { consultation ->
                    Spacer(Modifier.height(12.dp))
                    ConsultationCard(consultation, today, onOpen = { state.open(DeciderDestination.Consultation(consultation.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                }
            }
        }
        // The local petitions (DE-26), for the commune chosen on the device and its department only.
        val localPetitions = catalog.localPetitionsFor(territories)
        if (localPetitions.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("petitions-locales"))) {
                SectionHeader(stringResource(Res.string.decider_local_section))
                Text(stringResource(Res.string.decider_local_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                localPetitions.forEach { petition ->
                    Spacer(Modifier.height(12.dp))
                    LocalPetitionCard(petition, onOpen = { state.open(DeciderDestination.LocalPetition(petition.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                }
            }
        }
        // The model letters to one's elected officials (DE-09): filled in on the device, sent by the person.
        if (catalog.letters.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.testTag(DeciderTags.section("courriers"))) {
                SectionHeader(stringResource(Res.string.decider_letters_section))
                Text(stringResource(Res.string.decider_letters_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.widthIn(max = 760.dp))
                catalog.letters.forEach { letter ->
                    Spacer(Modifier.height(12.dp))
                    LetterCard(letter, onOpen = { state.open(DeciderDestination.Letter(letter.address)) }, modifier = Modifier.widthIn(max = 760.dp))
                }
            }
        }
        // Asking for an administrative document (DE-31): written on the device, sent by the person, deadlines counted here.
        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 760.dp).testTag(DeciderTags.section("documents"))) {
            SectionHeader(stringResource(Res.string.decider_documents_section))
            Text(stringResource(Res.string.decider_documents_section_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(8.dp))
            LinkRow(stringResource(Res.string.decider_documents_open), Lucide.ScrollText, onClick = { state.open(DeciderDestination.Documents) }, modifier = Modifier.testTag(DeciderTags.link("documents")))
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 760.dp).testTag(DeciderTags.section("more"))) {
            SectionHeader(stringResource(Res.string.decider_more_section))
            LinkRow(stringResource(Res.string.decider_more_demands), Lucide.Megaphone, onClick = { onNavigate(Space.REVENDICATIONS) }, modifier = Modifier.testTag(DeciderTags.link("revendications")))
            Spacer(Modifier.height(8.dp))
            LinkRow(stringResource(Res.string.decider_more_who_decides), Lucide.Landmark, onClick = { onNavigate(Space.COMPRENDRE) }, modifier = Modifier.testTag(DeciderTags.link("comprendre")))
            Spacer(Modifier.height(12.dp))
            InfoBox(Lucide.Vote, Modifier.testTag(DeciderTags.section("votations"))) {
                Text(stringResource(Res.string.decider_votes_soon_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_votes_soon_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The petitions and pacts the user follows, one touch away; the list never leaves the device. */
@Composable
private fun FollowedStrip(catalog: DeciderCatalog, state: DeciderState) {
    val items = state.followed.mapNotNull { address ->
        catalog.petition(address)?.let { Triple(address, it.title, DeciderDestination.Petition(address) as DeciderDestination) }
            ?: catalog.pacte(address)?.let { Triple(address, it.title, DeciderDestination.Pacte(address) as DeciderDestination) }
    }
    if (items.isEmpty()) return
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    Text(stringResource(Res.string.decider_followed), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(DeciderTags.section("followed")), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (_, title, destination) ->
            Surface(
                onClick = { state.open(destination) },
                modifier = Modifier.heightIn(min = 44.dp).widthIn(max = 260.dp),
                shape = MaterialTheme.shapes.small,
                color = palette.brandTint,
                contentColor = palette.ink,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (destination is DeciderDestination.Pacte) Lucide.BadgeCheck else Lucide.ScrollText, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title.withoutExample(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Text(stringResource(Res.string.decider_followed_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DeciderDetail(
    current: DeciderDestination,
    catalog: DeciderCatalog,
    voir: VoirCatalog,
    state: DeciderState,
    actions: DeciderActions,
    today: CivilDate,
    compact: Boolean,
    wide: Boolean,
    columns: Int,
    onOpenVoir: (ContentAddress) -> Unit,
    nowSeconds: Long,
    pastVotes: PastVotesState?,
    reports: VoirRequestSession?,
    photos: ComparatorPhotosState?,
    reminders: DeciderRemindersState?,
    dependencies: AppDependencies?,
) {
    val palette = PpfTheme.palette
    val padding = if (compact) 16.dp else 24.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(palette.surface1).padding(horizontal = padding - 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.content_back), onClick = { state.back() }, modifier = Modifier.testTag(DeciderTags.BACK), tint = palette.ink)
            Spacer(Modifier.width(4.dp))
            PpfTextButton(onClick = { state.home() }, text = stringResource(Res.string.space_decider), color = palette.inkMuted)
        }
        HorizontalDivider(color = palette.border)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(padding)) {
            Column(Modifier.widthIn(max = if (wide) 1100.dp else 760.dp)) {
                when (current) {
                    is DeciderDestination.Petition -> catalog.petition(current.address)?.let { petition ->
                        PetitionPage(
                            petition = petition,
                            bills = petition.texts.mapNotNull(voir::text),
                            today = today,
                            state = state,
                            actions = actions,
                            onOpenBill = { onOpenVoir(it.address) },
                            wide = wide,
                        )
                    } ?: Missing()
                    is DeciderDestination.Pacte -> catalog.pacte(current.address)?.let { pacte ->
                        PactePage(catalog.board(pacte, voir, today), state, actions, wide)
                    } ?: Missing()
                    is DeciderDestination.Comparator -> catalog.comparators.firstOrNull { it.address == current.address }?.let { sheet ->
                        ComparatorPage(
                            catalog.grid(sheet, voir),
                            wide,
                            nowSeconds = nowSeconds,
                            onOpenSheet = onOpenVoir,
                            onOpenVotes = pastVotes?.let { { candidate -> state.open(DeciderDestination.PastVotes(sheet.address, candidate)) } },
                            photos = photos,
                        )
                    } ?: Missing()
                    is DeciderDestination.Election -> catalog.elections.firstOrNull { it.address == current.address }?.let { election ->
                        ElectionPage(election, today, reminders)
                    } ?: Missing()
                    is DeciderDestination.Consultation -> catalog.consultation(current.address)?.let { consultation ->
                        ConsultationPage(consultation, today, actions, reminders)
                    } ?: Missing()
                    is DeciderDestination.LocalPetition -> catalog.localPetition(current.address)?.let { petition ->
                        LocalPetitionPage(petition, actions)
                    } ?: Missing()
                    is DeciderDestination.Letter -> catalog.letter(current.address)?.let { letter ->
                        LetterPage(letter, rememberDeviceElus(dependencies), today, actions, notes = dependencies?.database?.let { remember(it) { LetterNoteStore(it) } })
                    } ?: Missing()
                    DeciderDestination.Documents -> DocumentRequestPage(
                        store = dependencies?.database?.let { remember(it) { DocumentRequestStore(it) } },
                        reminders = reminders,
                        today = today,
                    )
                    is DeciderDestination.PastVotes -> {
                        val sheet = catalog.comparators.firstOrNull { it.address == current.sheet }
                        if (sheet == null || pastVotes == null) {
                            Missing()
                        } else {
                            val name = catalog.grid(sheet, voir).rows.firstOrNull { it.candidate == current.candidate }?.name ?: current.candidate.identifier
                            val memberId = voir.person(current.candidate)?.article?.event?.let(ParliamentLinks::memberId)
                            PastVotesPage(sheet, current.candidate, name, pastVotes, reports, memberId)
                        }
                    }
                    is DeciderDestination.Thresholds -> ThresholdsPage(current.platform, columns)
                    DeciderDestination.ClosedPetitions -> ClosedPetitionsPage(catalog.closedPetitions(today), today, columns) {
                        state.open(DeciderDestination.Petition(it.address))
                    }
                }
            }
        }
    }
}

@Composable
private fun Missing() {
    Text(stringResource(Res.string.decider_empty), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
}
