package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.schema.Problem

/**
 * The rules of an update of a published content, checked by the server around the engine (step 5,
 * section 3.4 of the design): the nature of the change and its summary are required, since the summary
 * enters the public journal of corrections (tag `ppf-correction`). A version with these problems is saved
 * all the same, but never submitted.
 */
object UpdateChecks {
    const val CHANGE_KIND_REQUIRED: String = "change_kind_required"
    const val CHANGE_NOTE_REQUIRED: String = "change_note_required"

    /** The form names of the two fields, also the `field` of their problems. */
    const val KIND_FIELD: String = "change_kind"
    const val NOTE_FIELD: String = "change_note"

    fun problems(content: DraftContent, isUpdate: Boolean): List<Problem> {
        if (!isUpdate) return emptyList()
        return buildList {
            if (content.changeKind == null) add(Problem(CHANGE_KIND_REQUIRED, KIND_FIELD))
            if (content.changeNote.isNullOrBlank()) add(Problem(CHANGE_NOTE_REQUIRED, NOTE_FIELD))
        }
    }
}
