package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.comprendre.LearningProgressStore
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.ParcoursProgress
import org.politisationpopulairefrancaise.core.comprendre.QuizAnswer
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the reader stands inside an open course. */
sealed interface CoursePage {
    /** The first page: what the course is about, its « En bref » box, start or resume. */
    data object Introduction : CoursePage

    data class Step(val index: Int) : CoursePage

    /** The last page: the result, « Et maintenant ? » and the sources. */
    data object End : CoursePage
}

/**
 * The state of the "Comprendre" space beyond the practical guides (which [ContentLibrary]
 * holds): the courses "Comprendre en 5 minutes", the dossiers and the fact-checks found on the
 * device, the open course and the reader's progress.
 *
 * The progress is kept on the device only ([LearningProgressStore]; cahier des charges, 5.1) and
 * is not tied to the account. Without a store (tests, previews) it lives in memory for the life
 * of the process. Every change is shown at once and written in the background, in order.
 */
class ComprendreLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val progressStore: LearningProgressStore?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val writes = Mutex()

    var courses: List<Parcours> by mutableStateOf(emptyList())
        private set

    var dossiers: List<Article> by mutableStateOf(emptyList())
        private set

    var decryptages: List<Article> by mutableStateOf(emptyList())
        private set

    /** The reader's progress, by course address. */
    var progress: Map<String, ParcoursProgress> by mutableStateOf(emptyMap())
        private set

    var openCourse: Parcours? by mutableStateOf(null)
        private set

    var page: CoursePage by mutableStateOf(CoursePage.Introduction)
        private set

    /** A dossier or a fact-check opened from the home of the space. */
    var openArticle: Article? by mutableStateOf(null)
        private set

    private var progressLoaded = false

    /** Reads the contents again from the device (after the bundled contents were imported or a synchronization). */
    fun reload() {
        val store = store
        scope.launch {
            if (!progressLoaded) {
                progressLoaded = true
                val saved = progressStore?.let { withContext(Dispatchers.Default) { it.all() } }.orEmpty()
                // Changes made before the first read (none in practice) win over the saved state.
                progress = saved + progress
            }
            if (store == null) return@launch
            val (freshCourses, freshDossiers, freshChecks) = withContext(Dispatchers.Default) {
                fun articles(type: ArticleType) = store.byTag(listOf(Article.KIND), Article.TAG_TYPE, type.tagValue, limit = 200)
                    .filter { trust.isTrusted(it.pubkey) }
                    .mapNotNull(Article::from)
                val courses = articles(ArticleType.PARCOURS).mapNotNull(Parcours::from)
                    .sortedWith(compareBy<Parcours>({ it.article.publishedAt ?: Long.MAX_VALUE }, { it.article.title.lowercase() }))
                val dossiers = articles(ArticleType.DOSSIER).sortedByDescending { it.updatedAt }
                val checks = articles(ArticleType.DECRYPTAGE).sortedByDescending { it.updatedAt }
                Triple(courses, dossiers, checks)
            }
            courses = freshCourses
            dossiers = freshDossiers
            decryptages = freshChecks
            // A newer version of the open course replaces it; a course that disappeared closes.
            openCourse = openCourse?.let { current -> freshCourses.firstOrNull { it.address == current.address } }
            val open = openCourse
            if (open == null) page = CoursePage.Introduction else page = page.clampedTo(open)
            openArticle = openArticle?.let { current -> (freshDossiers + freshChecks).firstOrNull { it.address == current.address } }
        }
    }

    fun progressOf(parcours: Parcours): ParcoursProgress? = progress[parcours.address]

    /** Opens a course on its introduction. */
    fun open(parcours: Parcours) {
        openArticle = null
        openCourse = parcours
        page = CoursePage.Introduction
    }

    fun close() {
        openCourse = null
        page = CoursePage.Introduction
    }

    fun openArticle(article: Article) {
        close()
        openArticle = article
    }

    fun closeArticle() {
        openArticle = null
    }

    /** Starts the open course at its first step, or where the reader stopped. */
    fun start(resume: Boolean = true) {
        val parcours = openCourse ?: return
        val known = progressOf(parcours)
        val step = if (resume && known != null && !known.completed) known.furthestStep.coerceIn(0, parcours.steps.lastIndex) else 0
        goToStep(parcours, step)
    }

    /** Forgets the open course's progress and starts it again. */
    fun restart() {
        val parcours = openCourse ?: return
        val address = parcours.address
        progress = progress - address
        persist { it.restart(address) }
        goToStep(parcours, 0)
    }

    fun next() {
        val parcours = openCourse ?: return
        when (val current = page) {
            CoursePage.Introduction -> start()
            is CoursePage.Step -> if (current.index < parcours.steps.lastIndex) goToStep(parcours, current.index + 1) else finish(parcours)
            CoursePage.End -> Unit
        }
    }

    fun previous() {
        val parcours = openCourse ?: return
        page = when (val current = page) {
            CoursePage.Introduction -> CoursePage.Introduction
            is CoursePage.Step -> if (current.index == 0) CoursePage.Introduction else CoursePage.Step(current.index - 1)
            CoursePage.End -> CoursePage.Step(parcours.steps.lastIndex)
        }
    }

    /** Goes back to the first step to read the course again, keeping the answers. */
    fun review() {
        if (openCourse == null) return
        page = CoursePage.Step(0)
    }

    /** Answers the quiz of [step] of the open course. */
    fun answer(step: Int, choice: Int) {
        val parcours = openCourse ?: return
        val quiz = parcours.steps.getOrNull(step)?.quiz ?: return
        if (choice !in quiz.options.indices) return
        val correct = quiz.isCorrect(choice)
        val now = clock()
        record(parcours) { current ->
            current.copy(
                furthestStep = maxOf(current.furthestStep, step),
                answers = current.answers + (step to QuizAnswer(step, choice, correct)),
                updatedAt = now,
            )
        }
        val address = parcours.address
        persist { it.answer(address, step, choice, correct, now) }
    }

    /** Forgets the answer of [step], to try again. */
    fun retry(step: Int) {
        val parcours = openCourse ?: return
        record(parcours) { it.copy(answers = it.answers - step) }
        val address = parcours.address
        persist { it.forgetAnswer(address, step) }
    }

    /** Forgets the progress of every course, on the device too. */
    fun clearProgress() {
        progress = emptyMap()
        persist { it.clear() }
    }

    private fun goToStep(parcours: Parcours, step: Int) {
        page = CoursePage.Step(step)
        val now = clock()
        record(parcours) { current -> current.copy(furthestStep = maxOf(current.furthestStep, step), updatedAt = now) }
        val address = parcours.address
        persist { it.reachStep(address, step, now) }
    }

    private fun finish(parcours: Parcours) {
        page = CoursePage.End
        val now = clock()
        record(parcours) { current -> current.copy(furthestStep = parcours.steps.lastIndex, completedAt = current.completedAt ?: now, updatedAt = now) }
        val address = parcours.address
        persist {
            it.reachStep(address, parcours.steps.lastIndex, now)
            it.complete(address, now)
        }
    }

    /** Updates the progress shown, starting from what is known of the course (or nothing). */
    private fun record(parcours: Parcours, change: (ParcoursProgress) -> ParcoursProgress) {
        val address = parcours.address
        val current = progress[address] ?: ParcoursProgress(address, furthestStep = 0, answers = emptyMap(), completedAt = null, updatedAt = clock())
        progress = progress + (address to change(current))
    }

    private fun persist(write: (LearningProgressStore) -> Unit) {
        val target = progressStore ?: return
        scope.launch { writes.withLock { withContext(Dispatchers.Default) { write(target) } } }
    }

    private fun CoursePage.clampedTo(parcours: Parcours): CoursePage = when (this) {
        is CoursePage.Step -> if (index > parcours.steps.lastIndex) CoursePage.Step(parcours.steps.lastIndex) else this
        else -> this
    }
}

@Composable
fun rememberComprendreLibrary(dependencies: AppDependencies): ComprendreLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        ComprendreLibrary(
            store = dependencies.contentStore,
            trust = dependencies.editorialTrust,
            progressStore = dependencies.database?.let(::LearningProgressStore),
            scope = scope,
        )
    }
}
