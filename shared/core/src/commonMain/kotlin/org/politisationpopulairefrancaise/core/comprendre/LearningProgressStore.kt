package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** The answer given to the quiz of one step. */
data class QuizAnswer(val step: Int, val choice: Int, val correct: Boolean)

/** What the device remembers of one course. */
data class ParcoursProgress(
    val address: String,
    /** The furthest step opened, from 0. */
    val furthestStep: Int,
    /** The answers, by step. */
    val answers: Map<Int, QuizAnswer>,
    /** When the reader reached the end, in seconds since 1970; null while unfinished. */
    val completedAt: Long?,
    val updatedAt: Long,
) {
    val completed: Boolean get() = completedAt != null

    /** Right answers among the steps the course still has (a course can be shortened by an update). */
    fun correctCount(stepCount: Int): Int = answers.values.count { it.correct && it.step < stepCount }

    fun answeredCount(stepCount: Int): Int = answers.keys.count { it < stepCount }
}

/**
 * Progress in the courses of "Comprendre", kept on the device only (cahier des charges, 5.1:
 * "La progression dans les parcours éducatifs est stockée uniquement sur l'appareil"). Nothing here
 * is ever sent: what someone studies can reveal political interests. It is not tied to the
 * account (reading needs none) and [clear] forgets everything in one gesture.
 */
class LearningProgressStore(private val database: PpfDatabase) {

    private val queries get() = database.learningQueries

    fun progress(address: String): ParcoursProgress? {
        val row = queries.selectProgress(address).executeAsOneOrNull() ?: return null
        val answers = queries.selectAnswers(address).executeAsList().associate { it.step.toInt() to QuizAnswer(it.step.toInt(), it.choice.toInt(), it.correct) }
        return ParcoursProgress(row.address, row.furthest_step.toInt(), answers, row.completed_at, row.updated_at)
    }

    /** Every course started on this device, by address. */
    fun all(): Map<String, ParcoursProgress> {
        val answers = queries.selectAllAnswers().executeAsList().groupBy { it.address }
        return queries.selectAllProgress().executeAsList().associate { row ->
            val own = answers[row.address].orEmpty().associate { it.step.toInt() to QuizAnswer(it.step.toInt(), it.choice.toInt(), it.correct) }
            row.address to ParcoursProgress(row.address, row.furthest_step.toInt(), own, row.completed_at, row.updated_at)
        }
    }

    /** Records that the reader opened [step]; the furthest step never goes back. */
    fun reachStep(address: String, step: Int, now: Long) = database.transaction {
        require(step >= 0) { "steps are numbered from 0" }
        queries.insertProgress(address, now)
        queries.reachStep(step = step.toLong(), now = now, address = address)
    }

    /** Records the answer to the quiz of [step] (a new answer replaces the previous one). */
    fun answer(address: String, step: Int, choice: Int, correct: Boolean, now: Long) = database.transaction {
        require(step >= 0 && choice >= 0) { "steps and options are numbered from 0" }
        queries.insertProgress(address, now)
        queries.reachStep(step = step.toLong(), now = now, address = address)
        queries.upsertAnswer(address, step.toLong(), choice.toLong(), correct)
    }

    /** Forgets the answer to the quiz of [step], so that the reader can try again. */
    fun forgetAnswer(address: String, step: Int) {
        queries.deleteAnswer(address, step.toLong())
    }

    /** Records that the reader reached the end of the course. */
    fun complete(address: String, now: Long) = database.transaction {
        queries.insertProgress(address, now)
        queries.complete(now = now, address = address)
    }

    /** Forgets one course, to start it again from the beginning. */
    fun restart(address: String) = database.transaction {
        queries.deleteAnswers(address)
        queries.deleteProgress(address)
    }

    /** Forgets every course: nothing of the reader's learning stays on the device. */
    fun clear() = database.transaction {
        queries.deleteAllAnswers()
        queries.deleteAllProgress()
    }

    fun count(): Long = queries.countProgress().executeAsOne()
}
