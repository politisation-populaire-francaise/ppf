package org.politisationpopulairefrancaise.core.agir.testimonies

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.publication.HeldStatus

/** Where one of the witness's testimonies stands, on this device. */
enum class TestimonyProgress(val column: String) {
    /** Encrypted, waiting for the network (S-27). */
    UNSENT("unsent"),
    PENDING("pending"),
    PUBLISHED("approved"),
    REJECTED("rejected"),
    ;

    companion object {
        fun of(column: String): TestimonyProgress = entries.firstOrNull { it.column == column } ?: PENDING

        fun of(status: HeldStatus): TestimonyProgress = when (status) {
            HeldStatus.PENDING -> PENDING
            HeldStatus.APPROVED -> PUBLISHED
            HeldStatus.REJECTED -> REJECTED
        }
    }
}

/** A line of "Mes témoignages": no text, no photo, no place, only what tells it apart and where it stands. */
data class MyTestimony(
    val id: String,
    val createdAt: Long,
    val callAddress: String?,
    val hasPhoto: Boolean,
    val gaveOriginal: Boolean,
    val progress: TestimonyProgress,
    val reason: String?,
    val published: String?,
)

/**
 * The witness's testimonies on this device (table `testimony`, schema in Testimony.sq). What is
 * kept is minimal: once the server has a testimony, only its number, its date, whether it had a
 * photo and where it stands remain; before that, the already encrypted envelopes and files.
 */
class TestimonyStore(private val database: PpfDatabase) {
    private val queries get() = database.testimonyQueries

    /** Keeps [testimony] until it is sent; [now] is the local date shown in "Mes témoignages". */
    fun keep(testimony: TestimonyPackage, now: Long) {
        val outgoing = testimony.outgoing()
        database.transaction {
            queries.insertTestimony(
                id = testimony.id,
                created_at = now,
                call_address = testimony.callAddress,
                has_photo = if (testimony.hasPhoto) 1 else 0,
                gave_original = if (testimony.gaveOriginal) 1 else 0,
                status = TestimonyProgress.UNSENT.column,
                submission = TestimonyJson.encodeToString(TestimonySubmission.serializer(), outgoing.submission),
                updated_at = now,
            )
            outgoing.files.forEach { (id, bytes) -> queries.insertFile(testimony.id, id, bytes) }
        }
    }

    /** The request and files of a testimony not sent yet, or null once it is sent. */
    fun outgoing(id: String): OutgoingTestimony? {
        val json = queries.selectSubmission(id).executeAsOneOrNull()?.submission ?: return null
        val submission = runCatching { TestimonyJson.decodeFromString(TestimonySubmission.serializer(), json) }.getOrNull() ?: return null
        val files = queries.selectFiles(id).executeAsList().associate { it.file_id to it.bytes }
        return OutgoingTestimony(submission, files)
    }

    fun unsent(): List<String> = queries.selectUnsentIds().executeAsList()

    /** The server has it: the encrypted copies leave the device. */
    fun markSent(id: String, now: Long) {
        database.transaction {
            queries.markSent(TestimonyProgress.PENDING.column, now, id)
            queries.deleteFiles(id)
        }
    }

    fun update(state: TestimonyState, now: Long) {
        queries.updateStatus(TestimonyProgress.of(state.status).column, state.reason, state.published, now, state.id)
    }

    fun all(): List<MyTestimony> = queries.selectAll().executeAsList().map { row ->
        MyTestimony(
            id = row.id,
            createdAt = row.created_at,
            callAddress = row.call_address,
            hasPhoto = row.has_photo != 0L,
            gaveOriginal = row.gave_original != 0L,
            progress = if (row.unsent != 0L) TestimonyProgress.UNSENT else TestimonyProgress.of(row.status),
            reason = row.reason,
            published = row.published,
        )
    }

    /** Forgets one testimony on this device (the server copy is not affected). */
    fun forget(id: String) {
        database.transaction {
            queries.deleteFiles(id)
            queries.deleteTestimony(id)
        }
    }

    /** Forgets them all (the witness's choice, or the erasure of the keys). */
    fun clear() {
        database.transaction {
            queries.deleteAllFiles()
            queries.deleteAllTestimonies()
        }
    }

    fun count(): Long = queries.countTestimonies().executeAsOne()
}
