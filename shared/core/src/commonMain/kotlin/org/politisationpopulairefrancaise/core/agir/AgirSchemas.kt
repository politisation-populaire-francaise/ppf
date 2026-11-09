package org.politisationpopulairefrancaise.core.agir

import org.politisationpopulairefrancaise.core.agir.followups.FollowUpSchema
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema

/**
 * The schemas of the editorial contents of « Agir » for the editorial engine (D-092), gathered in
 * EditorialSchemas.all like those of « Comprendre » and « Voir ». Step 1 of AG-62: the follow-ups of a
 * mobilization ([FollowUpSchema.SUITE]). The other contents of « Agir » (calls, directory, solidarity) keep
 * their own builders for now.
 */
object AgirSchemas {
    val all: List<ContentSchema> = FollowUpSchema.all
}
