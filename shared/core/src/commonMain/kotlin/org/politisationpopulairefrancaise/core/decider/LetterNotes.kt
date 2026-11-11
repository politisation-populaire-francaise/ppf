package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * What a person notes for herself or himself after sending a model letter (DE-09, option A of D-119): to whom, the day it
 * was sent, the day an answer came. On the device only: never sent, never published from here (the public counters of
 * option B come with DE-23, through anonymous tokens).
 */
data class LetterNote(
    val id: String,
    val template: ContentAddress,
    /** The elected official as the directory named them when the note was taken; null when the person wrote the name. */
    val official: String?,
    val mandate: String?,
    val territory: String?,
    val sentOn: CivilDate,
    val answeredOn: CivilDate?,
)

/** The private notes of the letters sent (table `letter_note`). Part of the quick erase (S-44) as [ERASABLE_ID]. */
class LetterNoteStore(private val database: PpfDatabase) : DeviceErasable {

    private val queries get() = database.letterNotesQueries

    override val erasableId: String = ERASABLE_ID

    /** « J'ai envoyé ce courrier » to [official] (null: a name the person wrote herself or himself) on [day]. */
    fun markSent(template: ContentAddress, official: ElectedOfficial?, day: CivilDate): LetterNote {
        val id = idOf(template, official)
        val before = notesOf(template).firstOrNull { it.id == id }
        queries.insertNote(id, template.toString(), official?.mandate?.key, official?.territory, official?.displayName, day.toEpochDays(), before?.answeredOn?.toEpochDays())
        return LetterNote(id, template, official?.displayName, official?.mandate?.key, official?.territory, day, before?.answeredOn)
    }

    /** « Réponse reçue » on [day], or null to take it back. */
    fun setAnswered(id: String, day: CivilDate?) = queries.setAnswered(day?.toEpochDays(), id)

    fun remove(id: String) = queries.deleteNote(id)

    /** The notes of one model letter, the latest first. */
    fun notesOf(template: ContentAddress): List<LetterNote> =
        queries.selectNotesOf(template.toString()).executeAsList().mapNotNull { row ->
            val address = runCatching { ContentAddress.parse(row.template) }.getOrNull() ?: return@mapNotNull null
            LetterNote(row.id, address, row.official, row.mandate, row.territory, CivilDate.fromEpochDays(row.sent_on), row.answered_on?.let(CivilDate::fromEpochDays))
        }

    fun clear() = queries.deleteAllNotes()

    override suspend fun eraseFromDevice() {
        clear()
        check(queries.countNotes().executeAsOne() == 0L) { "letter notes left on the device" }
    }

    companion object {
        /** The part of the quick erase (S-44) that empties the notes of the letters. */
        const val ERASABLE_ID: String = "decider-courriers"

        /** One note per model and per elected official: sending again only moves the day. */
        fun idOf(template: ContentAddress, official: ElectedOfficial?): String =
            template.toString() + "|" + (official?.let { "${it.mandate.key}:${it.territory}:${it.displayName}" } ?: "")
    }
}
