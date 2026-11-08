package org.politisationpopulairefrancaise.core.editorial

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * « Citer ce contenu » (S-144, M34; docs/integration/export-ouvert.md, section 5): a reference ready to
 * copy into an article or a study, the same on the site and in the applications: publisher, title,
 * dates, version, stable address and fingerprint. [text] is the French reference; [cslJson] the same
 * reference in CSL-JSON, the format of bibliography software (Zotero, for example).
 *
 * The version is the identifier of the signed event (its SHA-256), given short in the text and in full
 * as the fingerprint: anyone can find this exact version and check its signature (S-141).
 */
data class Citation(val text: String, val cslJson: String) {
    companion object {
        const val PUBLISHER: String = "Politisation Populaire Française"

        /** The characters of the event id shown as the version in the text. */
        const val SHORT_VERSION: Int = 12

        private val JSON = Json { prettyPrint = false }

        /** The reference of [article], published at [url], consulted on [consultedOn] (the day of the reader). */
        fun of(article: Article, url: String, consultedOn: CivilDate): Citation {
            val updated = CivilDate.fromEpochSeconds(article.updatedAt)
            val published = article.publishedAt?.let(CivilDate::fromEpochSeconds)
            val version = article.event.id.take(SHORT_VERSION)
            val text = buildString {
                append(PUBLISHER).append(", « ").append(article.title.trim()).append(" »")
                if (article.origin == ContentOrigin.AI_ASSISTED) append(" (rédigé avec l’aide d’une IA)")
                if (published != null) append(", publié le ").append(published.toFrench())
                if (published == null || published != updated) append(", mis à jour le ").append(updated.toFrench())
                append(", version ").append(version)
                append(", ").append(url)
                append(", empreinte SHA-256 ").append(article.event.id)
                append(", consulté le ").append(consultedOn.toFrench()).append('.')
            }
            val csl = buildJsonObject {
                put("id", article.address.toString())
                put("type", "webpage")
                put("title", article.title.trim())
                putJsonArray("author") { add(buildJsonObject { put("literal", PUBLISHER) }) }
                put("publisher", PUBLISHER)
                put("issued", dateParts(published ?: updated))
                put("accessed", dateParts(consultedOn))
                put("URL", url)
                put("version", version)
                put("note", "Empreinte SHA-256 : ${article.event.id}" + if (article.origin == ContentOrigin.AI_ASSISTED) " ; rédigé avec l’aide d’une IA" else "")
            }
            return Citation(text, JSON.encodeToString(JsonObject.serializer(), csl))
        }

        private fun dateParts(date: CivilDate): JsonObject = buildJsonObject {
            put("date-parts", buildJsonArray { add(JsonArray(listOf(JsonPrimitive(date.year), JsonPrimitive(date.month), JsonPrimitive(date.day)))) })
        }
    }
}
