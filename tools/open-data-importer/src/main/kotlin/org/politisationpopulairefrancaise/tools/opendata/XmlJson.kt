package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Reading the JSON files of the Assemblée nationale, converted from XML (voir-donnees-sources.md):
 * every number is a string, a single element is an object while several are a list, an empty list
 * is null, and some identifiers are objects (`"uid": {"#text": "PA…"}`). These helpers accept
 * every form everywhere.
 */

/** The child [key] of an object, null for anything else. */
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }

/** Follows [keys] from this element. */
internal fun JsonElement?.at(vararg keys: String): JsonElement? = keys.fold(this) { element, key -> element[key] }

/** One element or a list of them, as a list; null and empty as an empty list. */
internal fun JsonElement?.items(): List<JsonElement> = when (this) {
    null, JsonNull -> emptyList()
    is JsonArray -> filter { it !is JsonNull }
    else -> listOf(this)
}

/** A text value: a string or a number, or an object carrying `#text`; null when empty. */
internal fun JsonElement?.text(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content.trim().ifEmpty { null }
    is JsonObject -> this["#text"].text()
    is JsonArray -> null
}

/** A whole number written as a string or a number. */
internal fun JsonElement?.int(): Int? = text()?.toIntOrNull()

/** `"true"` or `true`. */
internal fun JsonElement?.flag(): Boolean = text()?.lowercase() == "true"

/** The day part of a date or date-time, AAAA-MM-JJ, or null when it is not one. */
internal fun JsonElement?.day(): String? = text()?.take(10)?.takeIf { DAY.matches(it) }

private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** One instance per distinct identifier: the 577 deputies appear in thousands of ballots. */
internal class Interner {
    private val values = HashMap<String, String>()
    fun of(value: String): String = values.getOrPut(value) { value }
}
