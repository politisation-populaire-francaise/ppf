package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.ParcoursProgress
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_course_meta
import org.politisationpopulairefrancaise.ui.resources.comprendre_daily_meta
import org.politisationpopulairefrancaise.ui.resources.comprendre_progress_done
import org.politisationpopulairefrancaise.ui.resources.comprendre_progress_new
import org.politisationpopulairefrancaise.ui.resources.comprendre_progress_step
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.content_updated

/** Reading time, steps, questions, origin (human or AI-assisted, always shown) and last update. */
@Composable
internal fun courseMetaLine(parcours: Parcours): String {
    val article = parcours.article
    // « Je découvre » (CO-19): « Une leçon par jour · 7 jours » rather than minutes and steps.
    val counts = if (parcours.daily) {
        stringResource(Res.string.comprendre_daily_meta, parcours.steps.size)
    } else {
        stringResource(Res.string.comprendre_course_meta, article.durationMinutes ?: DEFAULT_MINUTES, parcours.steps.size, parcours.quizCount)
    }
    val origin = stringResource(
        when (article.origin) {
            ContentOrigin.HUMAN -> Res.string.content_origin_human
            ContentOrigin.AI_ASSISTED -> Res.string.content_origin_ai
        },
    )
    val updated = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(article.updatedAt).toFrench())
    return "$counts · $origin · $updated"
}

/** Where the reader stands: not started, at a step, or finished with the score. */
@Composable
internal fun progressLine(parcours: Parcours, progress: ParcoursProgress?): String = when {
    progress == null -> stringResource(Res.string.comprendre_progress_new)
    progress.completed -> stringResource(Res.string.comprendre_progress_done, progress.correctCount(parcours.steps.size), parcours.quizCount)
    else -> stringResource(Res.string.comprendre_progress_step, (progress.furthestStep + 1).coerceAtMost(parcours.steps.size), parcours.steps.size)
}

/** The series is named after five minutes: a course without a duration is announced as such. */
private const val DEFAULT_MINUTES = 5
