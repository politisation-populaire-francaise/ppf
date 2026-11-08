package org.politisationpopulairefrancaise.server.export

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** The type of a column of an open dataset, as its JSON Schema and its CSV cell write it. */
enum class ColumnType(val jsonType: String, val format: String? = null) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),

    /** ISO 8601 calendar date, `2026-11-08`. */
    DATE("string", "date"),

    /** An http(s) address. */
    URL("string", "uri"),
}

/** One column: [name] is the CSV header and the JSON property, in English, stable within a major version. */
data class Column(val name: String, val type: ColumnType, val description: String, val nullable: Boolean = false) {
    init {
        require(NAME.matches(name)) { "A column name is lowercase snake case: $name" }
    }

    private companion object {
        val NAME = Regex("[a-z][a-z0-9_]{0,40}")
    }
}

/**
 * What the datasets are computed from: the latest version of each address of the editorial keys
 * ([events], sorted by address), and the same as editorial articles. [derived] keeps a value built once
 * per export (the catalog of « Voir », shared by its datasets).
 */
class ExportContents(val events: List<SignedEvent>) {
    val articles: List<Article> by lazy { events.mapNotNull(Article::from) }
    private val memo = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Any> derived(key: String, build: () -> T): T = memo.getOrPut(key, build) as T
}

/**
 * A table of the open export (M34, S-48; docs/integration/export-ouvert.md): one line per fact, every line
 * with its source and the version of the content it comes from. Each pole registers its own datasets in its
 * own file, as the queues of the back-office. A column added at the end keeps [version]; any other change
 * makes the next version.
 */
interface OpenDataset {
    /** Stable, lowercase with hyphens, prefixed by the space: `voir-mesures`. */
    val id: String
    val title: String
    val description: String
    /** The space of the application it belongs to (`voir`, `comprendre`...). */
    val space: String
    val version: Int
    val columns: List<Column>

    /** The lines, each with one value per column in their order (null for an empty nullable cell). */
    fun rows(contents: ExportContents): List<List<Any?>>
}

/** The datasets registered by the poles, in their order. */
class OpenDatasets {
    private val datasets = LinkedHashMap<String, OpenDataset>()

    @Synchronized
    fun register(dataset: OpenDataset) {
        require(ID.matches(dataset.id)) { "A dataset id is lowercase with hyphens: ${dataset.id}" }
        require(dataset.id != OpenExport.CONTENTS_ID) { "${OpenExport.CONTENTS_ID} is the file of the signed contents" }
        require(dataset.version >= 1 && dataset.columns.isNotEmpty())
        require(dataset.columns.map { it.name }.toSet().size == dataset.columns.size) { "${dataset.id}: two columns with the same name" }
        require(datasets.putIfAbsent(dataset.id, dataset) == null) { "The dataset ${dataset.id} is already registered" }
    }

    @Synchronized
    fun all(): List<OpenDataset> = datasets.values.toList()

    private companion object {
        val ID = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")
    }
}

/** The JSON Schema (2020-12) of the JSON file of [dataset]: an array of objects, one property per column. */
fun schemaOf(dataset: OpenDataset, id: String): JsonObject = buildJsonObject {
    put("\$schema", "https://json-schema.org/draft/2020-12/schema")
    put("\$id", id)
    put("title", dataset.title)
    put("description", dataset.description)
    put("type", "array")
    putJsonObject("items") {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { dataset.columns.forEach { add(JsonPrimitive(it.name)) } }
        putJsonObject("properties") {
            dataset.columns.forEach { column ->
                putJsonObject(column.name) {
                    if (column.nullable) {
                        put("type", JsonArray(listOf(JsonPrimitive(column.type.jsonType), JsonPrimitive("null"))))
                    } else {
                        put("type", column.type.jsonType)
                    }
                    column.type.format?.let { put("format", it) }
                    put("description", column.description)
                }
            }
        }
    }
}
