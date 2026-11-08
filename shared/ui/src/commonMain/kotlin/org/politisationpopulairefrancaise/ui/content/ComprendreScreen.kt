package org.politisationpopulairefrancaise.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.ParcoursProgress
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.comprendre.ComprendreLibrary
import org.politisationpopulairefrancaise.ui.comprendre.DecryptageCard
import org.politisationpopulairefrancaise.ui.comprendre.DecryptageDetail
import org.politisationpopulairefrancaise.ui.comprendre.HistoryCard
import org.politisationpopulairefrancaise.ui.comprendre.HistoryDetail
import org.politisationpopulairefrancaise.ui.comprendre.QuiDecideCard
import org.politisationpopulairefrancaise.ui.comprendre.QuiDecideDetail
import org.politisationpopulairefrancaise.ui.comprendre.CourseScreen
import org.politisationpopulairefrancaise.ui.comprendre.courseMetaLine
import org.politisationpopulairefrancaise.ui.comprendre.elus.CommuneChooserDialog
import org.politisationpopulairefrancaise.ui.comprendre.elus.MesElusCard
import org.politisationpopulairefrancaise.ui.comprendre.elus.MesElusPage
import org.politisationpopulairefrancaise.ui.comprendre.displayTitle
import org.politisationpopulairefrancaise.ui.comprendre.progressLine
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_cancel
import org.politisationpopulairefrancaise.ui.resources.comprendre_clear_confirm
import org.politisationpopulairefrancaise.ui.resources.comprendre_clear_progress
import org.politisationpopulairefrancaise.ui.resources.comprendre_clear_text
import org.politisationpopulairefrancaise.ui.resources.comprendre_clear_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_course_kind
import org.politisationpopulairefrancaise.ui.resources.comprendre_courses_intro
import org.politisationpopulairefrancaise.ui.resources.comprendre_courses_none
import org.politisationpopulairefrancaise.ui.resources.comprendre_courses_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_discover_day
import org.politisationpopulairefrancaise.ui.resources.comprendre_discover_resume
import org.politisationpopulairefrancaise.ui.resources.comprendre_discover_start
import org.politisationpopulairefrancaise.ui.resources.comprendre_discover_text
import org.politisationpopulairefrancaise.ui.resources.comprendre_discover_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_checks_intro
import org.politisationpopulairefrancaise.ui.resources.comprendre_decryptages_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_history_intro
import org.politisationpopulairefrancaise.ui.resources.comprendre_history_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_history
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_teaching
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_who
import org.politisationpopulairefrancaise.ui.resources.comprendre_who_intro
import org.politisationpopulairefrancaise.ui.resources.comprendre_who_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_dossiers_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_private_note
import org.politisationpopulairefrancaise.ui.resources.comprendre_rights_intro
import org.politisationpopulairefrancaise.ui.resources.comprendre_rights_title
import org.politisationpopulairefrancaise.ui.resources.appels_title
import org.politisationpopulairefrancaise.ui.resources.content_back
import org.politisationpopulairefrancaise.ui.resources.content_none
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.content_refresh
import org.politisationpopulairefrancaise.ui.resources.content_status_loading
import org.politisationpopulairefrancaise.ui.resources.content_status_offline
import org.politisationpopulairefrancaise.ui.resources.content_status_synced
import org.politisationpopulairefrancaise.ui.resources.content_status_unavailable
import org.politisationpopulairefrancaise.ui.resources.content_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.content_topics
import org.politisationpopulairefrancaise.ui.resources.content_type_article
import org.politisationpopulairefrancaise.ui.resources.content_type_decryptage
import org.politisationpopulairefrancaise.ui.resources.content_type_dossier
import org.politisationpopulairefrancaise.ui.resources.content_type_fiche
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.resources.desc_comprendre
import org.politisationpopulairefrancaise.ui.resources.glossaire_home_intro
import org.politisationpopulairefrancaise.ui.resources.glossaire_home_open
import org.politisationpopulairefrancaise.ui.resources.glossaire_kind
import org.politisationpopulairefrancaise.ui.glossaire.GlossaryScreen
import org.politisationpopulairefrancaise.ui.glossaire.LocalGlossary
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import org.politisationpopulairefrancaise.ui.resources.organisations_content_type
import org.politisationpopulairefrancaise.ui.resources.space_comprendre
import org.politisationpopulairefrancaise.ui.resources.space_revendications
import org.politisationpopulairefrancaise.ui.resources.space_voir
import org.politisationpopulairefrancaise.ui.resources.temoignages_content_type
import org.politisationpopulairefrancaise.ui.resources.space_decider
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ContentTags {
    const val GUIDE_CARD = "article-" // followed by the slug
    const val GUIDE_LIST = "guide-list"
    const val GUIDE_DETAIL = "guide-detail"
    const val BACK = "guide-back"
    const val REFRESH = "content-refresh"
    const val STATUS = "content-status"
}

object ComprendreTags {
    const val HOME = "comprendre-home"
    const val COURSE_LIST = "course-list"
    const val COURSE_CARD = "course-" // followed by the slug
    const val CLEAR_PROGRESS = "comprendre-clear-progress"
    const val CLEAR_CONFIRM = "comprendre-clear-confirm"
    const val CLEAR_CANCEL = "comprendre-clear-cancel"
    const val WHO_LIST = "who-decides-list"
    const val CHECK_LIST = "decryptage-list"
    const val HISTORY_LIST = "history-list"
    const val GLOSSARY = "comprendre-glossary"
    const val GLOSSARY_OPEN = "comprendre-glossary-open"
    const val DISCOVER = "comprendre-discover"

    /** A card of the home: followed by the slug of the content. */
    const val CARD = "comprendre-card-"
}

/**
 * "Comprendre" (vision, pillar 2; cahier fonctionnel, M6 and M13.1): the courses "Comprendre en
 * 5 minutes" with their quizzes, the practical guides readable offline, then the dossiers and the
 * fact-checks when there are some. One pane on phones and tablets (the home, then the content with
 * a back button), two panes side by side on wide windows (the home stays on the left).
 */
@Composable
fun ComprendreScreen(
    library: ContentLibrary,
    comprendre: ComprendreLibrary,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
) {
    // The bundled contents are imported by the library: read the courses once it is done, and after each synchronization.
    LaunchedEffect(library.status) { comprendre.reload() }

    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val course = comprendre.openCourse
    val article = comprendre.openArticle
    val guide = library.selected
    val elus = comprendre.elus
    val elusOpen = elus?.pageOpen == true
    val glossary = LocalGlossary.current
    val actions = HomeActions(
        openGuide = { comprendre.close(); comprendre.closeArticle(); elus?.closePage(); library.open(it) },
        openCourse = { library.close(); elus?.closePage(); comprendre.open(it) },
        openArticle = { library.close(); elus?.closePage(); comprendre.openArticle(it) },
        openElus = { library.close(); comprendre.close(); comprendre.closeArticle(); elus?.openPage() },
        openGlossary = { comprendre.close(); comprendre.closeArticle(); library.close(); elus?.closePage(); glossary?.browse() },
    )
    // A content linked to an entry of the glossary (« Pour aller plus loin »): opened once it is read.
    val pending = glossary?.pendingContent
    LaunchedEffect(pending, comprendre.courses, comprendre.decryptages, comprendre.stories, comprendre.sheets, comprendre.dossiers, library.guides) {
        if (pending == null) return@LaunchedEffect
        val course = comprendre.courses.firstOrNull { it.address == pending }
        val linked = (comprendre.decryptages.map { it.article } + comprendre.stories.map { it.article } + comprendre.sheets.map { it.article } + comprendre.dossiers)
            .firstOrNull { it.address.toString() == pending }
        val fiche = library.guides.firstOrNull { it.address.toString() == pending }
        when {
            course != null -> actions.openCourse(course)
            linked != null -> actions.openArticle(linked)
            fiche != null -> actions.openGuide(fiche)
            else -> return@LaunchedEffect
        }
        glossary.pendingDone()
    }
    // A content that could not be found is forgotten when the reader leaves the space.
    DisposableEffect(glossary) { onDispose { glossary?.pendingDone() } }
    val hasDetail = course != null || article != null || guide != null || elusOpen
    // The system's back closes the open course, article, page « Mes élus » or guide first.
    SystemBack(enabled = hasDetail) {
        when {
            course != null -> comprendre.close()
            article != null -> comprendre.closeArticle()
            elus != null && elusOpen -> elus.closePage()
            else -> library.close()
        }
    }

    @Composable
    fun Detail(onBack: Boolean, detailModifier: Modifier) {
        when {
            course != null -> CourseScreen(comprendre, course, onBack = if (onBack) comprendre::close else null, onNavigate = onNavigate, compact = compact, modifier = detailModifier)
            article != null -> ArticleDetail(comprendre, article, onBack = if (onBack) comprendre::closeArticle else null, onNavigate = onNavigate, compact = compact, modifier = detailModifier, onOpenElus = actions.openElus)
            elus != null && elusOpen -> MesElusPage(elus, onBack = if (onBack) elus::closePage else null, compact = compact, modifier = detailModifier)
            guide != null -> GuideDetail(guide, onBack = if (onBack) library::close else null, compact = compact, modifier = detailModifier)
        }
    }

    // The panes carry their own test tags, so the host's modifier stays on this container.
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        when {
            // The full glossary takes the whole space, with its own list and detail (D-048).
            glossary != null && glossary.browsing -> GlossaryScreen(glossary, windowClass, onBack = glossary::stopBrowsing, modifier = Modifier.fillMaxSize())
            twoPanes -> Row(Modifier.fillMaxSize()) {
                ComprendreHome(library, comprendre, actions, Modifier.width(400.dp).fillMaxSize(), compact = false, withHeader = false)
                VerticalDivider(color = PpfTheme.palette.border)
                if (hasDetail) {
                    Detail(onBack = false, detailModifier = Modifier.fillMaxSize())
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        PageHeader(stringResource(Res.string.space_comprendre), stringResource(Res.string.desc_comprendre))
                    }
                }
            }
            hasDetail -> Detail(onBack = true, detailModifier = Modifier.fillMaxSize())
            else -> ComprendreHome(library, comprendre, actions, Modifier.fillMaxSize(), compact = compact, withHeader = true)
        }
    }
    // The choice of the commune opens over the sheet or the page it was asked from.
    elus?.let { CommuneChooserDialog(it) }
}

private class HomeActions(
    val openGuide: (Article) -> Unit,
    val openCourse: (Parcours) -> Unit,
    val openArticle: (Article) -> Unit,
    val openElus: () -> Unit,
    val openGlossary: () -> Unit,
)

@Composable
private fun ComprendreHome(
    library: ContentLibrary,
    comprendre: ComprendreLibrary,
    actions: HomeActions,
    modifier: Modifier,
    compact: Boolean,
    withHeader: Boolean,
) {
    val palette = PpfTheme.palette
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ComprendreTags.HOME)) {
        Column(Modifier.widthIn(max = 760.dp)) {
            if (withHeader) {
                PageHeader(stringResource(Res.string.space_comprendre), stringResource(Res.string.desc_comprendre))
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { StatusLine(library.status) }
                if (library.status != LibraryStatus.Offline && library.status != LibraryStatus.Unavailable) {
                    PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.content_refresh), library::refresh, Modifier.testTag(ContentTags.REFRESH), tint = palette.brand)
                }
            }
            Spacer(Modifier.height(16.dp))

            // « Je découvre » (CO-19): the lesson of the day of a daily course, until the course is done.
            comprendre.courses.firstOrNull { it.daily && comprendre.progressOf(it)?.completed != true }?.let { course ->
                DiscoverCard(course, comprendre.progressOf(course)) { lesson ->
                    actions.openCourse(course)
                    comprendre.goToLesson(lesson)
                }
            }

            Column(Modifier.fillMaxWidth().testTag(ComprendreTags.COURSE_LIST)) {
                SectionHeading(stringResource(Res.string.comprendre_courses_title), stringResource(Res.string.comprendre_courses_intro))
                if (comprendre.courses.isEmpty() && library.status != LibraryStatus.Loading) {
                    Text(stringResource(Res.string.comprendre_courses_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                comprendre.courses.forEach { course ->
                    CourseCard(course, comprendre, onOpen = { actions.openCourse(course) }, selected = comprendre.openCourse?.address == course.address)
                    Spacer(Modifier.height(12.dp))
                }
            }

            GlossarySection(actions.openGlossary)

            HomeSection(stringResource(Res.string.comprendre_who_title), stringResource(Res.string.comprendre_who_intro), comprendre.sheets, ComprendreTags.WHO_LIST) { sheet ->
                QuiDecideCard(sheet, onOpen = { actions.openArticle(sheet.article) }, selected = comprendre.openArticle?.address == sheet.article.address, modifier = Modifier.testTag(ComprendreTags.CARD + sheet.article.slug))
            }
            comprendre.elus?.let { elus ->
                Spacer(Modifier.height(if (comprendre.sheets.isEmpty()) 20.dp else 0.dp))
                MesElusCard(elus, onOpen = actions.openElus, selected = elus.pageOpen)
            }
            HomeSection(stringResource(Res.string.comprendre_decryptages_title), stringResource(Res.string.comprendre_checks_intro), comprendre.decryptages, ComprendreTags.CHECK_LIST) { check ->
                DecryptageCard(check, onOpen = { actions.openArticle(check.article) }, selected = comprendre.openArticle?.address == check.article.address, modifier = Modifier.testTag(ComprendreTags.CARD + check.article.slug))
            }

            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().testTag(ContentTags.GUIDE_LIST)) {
                SectionHeading(stringResource(Res.string.comprendre_rights_title), stringResource(Res.string.comprendre_rights_intro))
                if (library.guides.isEmpty() && library.status != LibraryStatus.Loading) {
                    Text(stringResource(Res.string.content_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                library.guides.forEach { guide ->
                    GuideCard(guide, onOpen = { actions.openGuide(guide) }, selected = library.selected?.address == guide.address)
                    Spacer(Modifier.height(12.dp))
                }
            }

            HomeSection(stringResource(Res.string.comprendre_history_title), stringResource(Res.string.comprendre_history_intro), comprendre.stories, ComprendreTags.HISTORY_LIST) { story ->
                HistoryCard(story, onOpen = { actions.openArticle(story.article) }, selected = comprendre.openArticle?.address == story.article.address, modifier = Modifier.testTag(ComprendreTags.CARD + story.article.slug))
            }
            ArticleSection(stringResource(Res.string.comprendre_dossiers_title), comprendre.dossiers, comprendre.openArticle, actions.openArticle)

            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.comprendre_private_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            if (comprendre.progress.isNotEmpty()) {
                PpfTextButton(
                    onClick = { confirmClear = true },
                    text = stringResource(Res.string.comprendre_clear_progress),
                    color = palette.accentStrong,
                    modifier = Modifier.testTag(ComprendreTags.CLEAR_PROGRESS),
                )
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
            title = { Text(stringResource(Res.string.comprendre_clear_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(Res.string.comprendre_clear_text), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; comprendre.clearProgress() }, modifier = Modifier.testTag(ComprendreTags.CLEAR_CONFIRM)) {
                    Text(stringResource(Res.string.comprendre_clear_confirm), color = palette.accentStrong)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.testTag(ComprendreTags.CLEAR_CANCEL)) {
                    Text(stringResource(Res.string.comprendre_cancel), color = palette.inkMuted)
                }
            },
        )
    }
}

/**
 * « Je découvre » (CO-19, cahier fonctionnel M23): for newcomers, the lesson to read today of a course of one
 * lesson a day. A suggestion only: nothing counts days, nothing is locked, the progress stays on the device.
 */
@Composable
private fun DiscoverCard(course: Parcours, progress: ParcoursProgress?, onOpen: (Int) -> Unit) {
    val lesson = course.nextLesson(progress) ?: return
    val palette = PpfTheme.palette
    PpfCard(onClick = { onOpen(lesson) }, modifier = Modifier.fillMaxWidth().testTag(ComprendreTags.DISCOVER)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(Res.string.comprendre_discover_title),
                style = MaterialTheme.typography.titleMedium,
                color = palette.brand,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (course.article.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(displayTitle(course), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.comprendre_discover_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.comprendre_discover_day, lesson + 1, course.steps.size), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress == null) stringResource(Res.string.comprendre_discover_start) else stringResource(Res.string.comprendre_discover_resume, lesson + 1),
                style = MaterialTheme.typography.titleSmall,
                color = palette.brand,
                modifier = Modifier.weight(1f),
            )
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
}

/** The entry « Glossaire » of the home (D-048), shown once the device holds entries. */
@Composable
private fun GlossarySection(onOpen: () -> Unit) {
    val glossary = LocalGlossary.current ?: return
    val count = glossary.index.size
    if (count == 0) return
    val palette = PpfTheme.palette
    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth().testTag(ComprendreTags.GLOSSARY)) {
        SectionHeading(stringResource(Res.string.glossaire_kind), stringResource(Res.string.glossaire_home_intro))
        PpfCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag(ComprendreTags.GLOSSARY_OPEN)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.BookOpen, contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(pluralStringResource(Res.plurals.glossaire_home_open, count, count), style = MaterialTheme.typography.titleSmall, color = palette.brand, modifier = Modifier.weight(1f))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A section of the home, shown only when it has contents. */
@Composable
private fun <T> HomeSection(title: String, intro: String, items: List<T>, tag: String, card: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        SectionHeading(title, intro)
        items.forEach { item ->
            card(item)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A dossier, a fact-check, a story or a « Qui décide de ça ? » sheet, each with its own layout. */
@Composable
private fun ArticleDetail(comprendre: ComprendreLibrary, article: Article, onBack: (() -> Unit)?, onNavigate: (Space) -> Unit, compact: Boolean, modifier: Modifier, onOpenElus: () -> Unit) {
    val check = comprendre.decryptages.firstOrNull { it.article.address == article.address }
    val story = comprendre.stories.firstOrNull { it.article.address == article.address }
    val sheet = comprendre.sheets.firstOrNull { it.article.address == article.address }
    val method = comprendre.methodPage
    when {
        check != null -> DecryptageDetail(check, onOpenMethod = method?.let { page -> { comprendre.openArticle(page) } }, onBack = onBack, onNavigate = onNavigate, compact = compact, modifier = modifier)
        story != null -> HistoryDetail(story, onBack, onNavigate, compact, modifier)
        sheet != null -> QuiDecideDetail(sheet, comprendre.persons, onBack, onNavigate, compact, modifier, elus = comprendre.elus, onOpenElus = onOpenElus)
        else -> GuideDetail(article, onBack = onBack, compact = compact, modifier = modifier)
    }
}

@Composable
private fun SectionHeading(title: String, intro: String?) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    intro?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ArticleSection(title: String, articles: List<Article>, open: Article?, onOpen: (Article) -> Unit) {
    if (articles.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    SectionHeading(title, null)
    articles.forEach { article ->
        GuideCard(article, onOpen = { onOpen(article) }, selected = open?.address == article.address)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CourseCard(course: Parcours, comprendre: ComprendreLibrary, onOpen: () -> Unit, selected: Boolean) {
    val palette = PpfTheme.palette
    val progress = comprendre.progressOf(course)
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.fillMaxWidth().testTag(ComprendreTags.COURSE_CARD + course.article.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(displayTitle(course), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (course.article.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        course.article.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(courseMetaLine(course), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        val done = when {
            progress == null -> 0f
            progress.completed -> 1f
            else -> (progress.furthestStep + 1).toFloat() / course.steps.size
        }
        LinearProgressIndicator(
            progress = { done },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = if (progress?.completed == true) palette.success else palette.brand,
            trackColor = palette.border,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(progressLine(course, progress), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

@Composable
private fun StatusLine(status: LibraryStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(ContentTags.STATUS)) {
        if (status == LibraryStatus.Loading) {
            CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), color = PpfTheme.palette.brand)
            Spacer(Modifier.width(8.dp))
        }
        val text = when (status) {
            LibraryStatus.Loading -> stringResource(Res.string.content_status_loading)
            LibraryStatus.Unavailable -> stringResource(Res.string.content_status_unavailable)
            LibraryStatus.Offline -> stringResource(Res.string.content_status_offline)
            is LibraryStatus.Synced -> stringResource(Res.string.content_status_synced, status.pulled)
            LibraryStatus.Unreachable -> stringResource(Res.string.content_status_unreachable)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
private fun GuideCard(guide: Article, onOpen: () -> Unit, selected: Boolean) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.fillMaxWidth().testTag(ContentTags.GUIDE_CARD + guide.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(guide.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (guide.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        guide.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(metaLine(guide), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun GuideDetail(guide: Article, onBack: (() -> Unit)?, compact: Boolean, modifier: Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ContentTags.GUIDE_DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.content_back), onBack, Modifier.testTag(ContentTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfBadge(typeLabel(guide.type), BadgeStyle.BRAND)
                if (guide.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = guide.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(metaLine(guide), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            if (guide.topics.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.content_topics, guide.topics.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSubtle,
                )
            }
            Spacer(Modifier.height(20.dp))
            SelectionContainer {
                MarkdownText(guide.body)
            }
        }
    }
}

@Composable
private fun typeLabel(type: ArticleType): String = stringResource(
    when (type) {
        ArticleType.FICHE_PRATIQUE -> Res.string.content_type_fiche
        ArticleType.DOSSIER -> Res.string.content_type_dossier
        ArticleType.DECRYPTAGE -> Res.string.content_type_decryptage
        ArticleType.ARTICLE -> Res.string.content_type_article
        ArticleType.PARCOURS -> Res.string.comprendre_course_kind
        ArticleType.HISTOIRE -> Res.string.comprendre_kind_history
        ArticleType.QUI_DECIDE -> Res.string.comprendre_kind_who
        ArticleType.GLOSSAIRE -> Res.string.glossaire_kind
        ArticleType.REVENDICATION -> Res.string.space_revendications
        ArticleType.INDICATEUR, ArticleType.FAIT, ArticleType.PERSONNE, ArticleType.TEXTE, ArticleType.MOUVEMENT, ArticleType.REPONSE, ArticleType.PARTI -> Res.string.space_voir
        ArticleType.ORGANISATION -> Res.string.organisations_content_type
        ArticleType.APPEL -> Res.string.appels_title
        ArticleType.PETITION, ArticleType.PACTE, ArticleType.ENGAGEMENT, ArticleType.COMPARATEUR, ArticleType.POSITION -> Res.string.space_decider
        ArticleType.TEMOIGNAGE -> Res.string.temoignages_content_type
        ArticleType.FICHE_PEDAGOGIQUE -> Res.string.comprendre_kind_teaching
    },
)

@Composable
private fun metaLine(guide: Article): String {
    val type = typeLabel(guide.type)
    val origin = stringResource(
        when (guide.origin) {
            ContentOrigin.HUMAN -> Res.string.content_origin_human
            ContentOrigin.AI_ASSISTED -> Res.string.content_origin_ai
        },
    )
    val updated = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(guide.updatedAt).toFrench())
    return "$type · $updated · $origin"
}
