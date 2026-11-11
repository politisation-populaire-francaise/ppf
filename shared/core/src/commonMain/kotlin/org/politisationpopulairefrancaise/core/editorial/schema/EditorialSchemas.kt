package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.agir.AgirSchemas
import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.voir.VoirSchemas

/**
 * Every schema of the editorial circuit. Each space declares its schemas in its own file
 * (core/comprendre/ComprendreSchemas.kt, core/voir/VoirSchemas.kt, core/agir/AgirSchemas.kt) and they are
 * gathered here: a new type is a new schema, the engine does not change.
 */
object EditorialSchemas {
    val all: List<ContentSchema> = ComprendreSchemas.all + VoirSchemas.all + AgirSchemas.all

    fun of(type: ArticleType): ContentSchema? = all.firstOrNull { it.type == type }

    /** The schemas of one space: the types its key may sign (purpose `editorial.<space>.publish`). */
    fun of(space: EditorialSpace): List<ContentSchema> = all.filter { it.space == space }
}
