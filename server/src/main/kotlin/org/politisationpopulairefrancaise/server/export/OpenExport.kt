package org.politisationpopulairefrancaise.server.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The licence of what PPF publishes (cahier des charges 3.8), chosen by the owner. */
@Serializable
data class ExportLicense(val status: String, val text: String, val url: String? = null) {
    companion object {
        /** Until the owner chooses (question of 8 November 2026): no reuse without asking. */
        val PENDING = ExportLicense("a-decider", "Licence en cours de choix par Politisation Populaire Française : pour réutiliser ces données, demandez-le nous en attendant.")
    }
}

@Serializable
data class ExportFile(val format: String, val url: String, val sha256: String, val bytes: Int, val rows: Int)

@Serializable
data class ExportDataset(
    val id: String,
    val title: String,
    val description: String,
    val space: String,
    val version: Int,
    val schema: String?,
    val files: List<ExportFile>,
)

/** The catalog `GET /api/v1/export` (format 1, docs/FORMATS.md section 5 bis). */
@Serializable
data class ExportCatalog(
    val format: Int = 1,
    /** The `created_at` of the most recent exported content (seconds), null when nothing is published. */
    @SerialName("updated_at") val updatedAt: Long?,
    val publisher: String = PUBLISHER,
    val license: ExportLicense,
    /** The keys of the editorial team whose contents are exported, to check the signatures. */
    @SerialName("editorial_keys") val editorialKeys: List<String>,
    val datasets: List<ExportDataset>,
) {
    companion object {
        const val PUBLISHER: String = "Politisation Populaire Française"
    }
}

/** A file of the export as it is served: its exact bytes, their SHA-256 (the ETag) and its media type. */
class ServedFile(val bytes: ByteArray, val contentType: String, val downloadName: String? = null) {
    val sha256: String = sha256Hex(bytes)
}

/** One computed export: the catalog and every file, by its path under `/api/v1/export/`. */
class ExportBuild(val catalog: ExportCatalog, val catalogFile: ServedFile, val files: Map<String, ServedFile>)

/**
 * The open export of PPF (M34, S-48; docs/integration/export-ouvert.md): the latest version of every
 * content of the editorial keys ([PublicationPolicy.isEditor], checked again at reading time) as signed
 * events, and the tables of the registered [datasets] in JSON and CSV with their JSON Schema. Computed in
 * memory at most once per [ttl]; nothing about the requests is kept.
 *
 * Only the contents signed by the team are exported: those of the citizens' accounts stay readable through
 * `GET /api/v1/events`, never gathered in one file (a file in bulk would link pseudonymous keys to political
 * opinions, RGPD article 9).
 */
class OpenExport(
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    private val datasets: OpenDatasets,
    private val license: ExportLicense = ExportLicense.PENDING,
    private val ttl: Duration = 1.minutes,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()
    private var cached: ExportBuild? = null
    private var builtAt: Long = 0

    suspend fun current(): ExportBuild = lock.withLock {
        val now = clock.millis()
        cached?.takeIf { now - builtAt < ttl.inWholeMilliseconds }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) { build(readContents()) }
        cached = fresh
        builtAt = now
        fresh
    }

    /** The latest version of each address of the editorial keys, among [KINDS], sorted by address. */
    internal fun readContents(): List<SignedEvent> {
        val editors = policy.editorKeys().sorted()
        if (editors.isEmpty()) return emptyList()
        val latest = HashMap<Triple<Int, String, String>, SignedEvent>()
        var until: Long? = null
        var seen = 0
        while (seen < MAX_EVENTS) {
            val page = events.query(EventFilter(kinds = KINDS, authors = editors, until = until, limit = EventFilter.MAX_LIMIT))
            var added = 0
            for (event in page) {
                if (!policy.isEditor(event.pubkey)) continue
                val d = event.firstTagValue("d") ?: continue
                val key = Triple(event.kind, event.pubkey, d)
                val known = latest[key]
                if (known == null || known.createdAt < event.createdAt || (known.createdAt == event.createdAt && event.id < known.id)) {
                    if (known == null) added++
                    latest[key] = event
                }
            }
            seen += page.size
            if (page.size < EventFilter.MAX_LIMIT) break
            val last = page.last().createdAt
            // Same-second events repeat on the next page; stop when nothing older can come.
            if (until == last && added == 0) break
            until = last
        }
        return latest.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third })).map { it.value }
    }

    internal fun build(events: List<SignedEvent>): ExportBuild {
        val contents = ExportContents(events)
        val files = LinkedHashMap<String, ServedFile>()
        val described = ArrayList<ExportDataset>()

        val contentsFile = ServedFile(("[" + events.joinToString(",") { it.toJson() } + "]").encodeToByteArray(), JSON)
        files["$CONTENTS_ID.json"] = contentsFile
        described += ExportDataset(
            CONTENTS_ID, "Contenus publiés par PPF",
            "Tous les contenus publiés par l’équipe de PPF, dans leur dernière version : les événements signés eux-mêmes (docs/FORMATS.md, section 2), à vérifier avec les clés editorial_keys.",
            space = "tous", version = 1, schema = null,
            files = listOf(ExportFile("json", "$PATH/$CONTENTS_ID.json", contentsFile.sha256, contentsFile.bytes.size, events.size)),
        )

        datasets.all().forEach { dataset ->
            val rows = dataset.rows(contents)
            val schemaPath = "schemas/${dataset.id}-${dataset.version}.schema.json"
            val schema = ServedFile(JSON_WRITER.encodeToString(JsonElement.serializer(), schemaOf(dataset, "$PATH/$schemaPath")).encodeToByteArray(), SCHEMA)
            val json = ServedFile(JSON_WRITER.encodeToString(JsonElement.serializer(), jsonRows(dataset.columns, rows)).encodeToByteArray(), JSON)
            val csv = ServedFile(Csv.of(dataset.columns, rows).encodeToByteArray(), CSV, downloadName = "ppf-${dataset.id}.csv")
            files[schemaPath] = schema
            files["${dataset.id}.json"] = json
            files["${dataset.id}.csv"] = csv
            described += ExportDataset(
                dataset.id, dataset.title, dataset.description, dataset.space, dataset.version, "$PATH/$schemaPath",
                listOf(
                    ExportFile("json", "$PATH/${dataset.id}.json", json.sha256, json.bytes.size, rows.size),
                    ExportFile("csv", "$PATH/${dataset.id}.csv", csv.sha256, csv.bytes.size, rows.size),
                ),
            )
        }

        val catalog = ExportCatalog(
            updatedAt = events.maxOfOrNull { it.createdAt },
            license = license,
            editorialKeys = policy.editorKeys().sorted(),
            datasets = described,
        )
        return ExportBuild(catalog, ServedFile(JSON_WRITER.encodeToString(ExportCatalog.serializer(), catalog).encodeToByteArray(), JSON), files)
    }

    companion object {
        const val PATH: String = "/api/v1/export"
        const val CONTENTS_ID: String = "contenus"

        /** Long-form contents (30023: Comprendre, Voir, Décider, Agir) and the agenda (31923) of the team. */
        val KINDS: List<Int> = listOf(30023, 31923)
        const val MAX_EVENTS: Int = 20_000

        const val JSON: String = "application/json; charset=utf-8"
        const val SCHEMA: String = "application/schema+json; charset=utf-8"
        const val CSV: String = "text/csv; charset=utf-8; header=present"

        private val JSON_WRITER = Json { encodeDefaults = true; explicitNulls = true }

        /** The rows as JSON objects, one property per column in their order. */
        internal fun jsonRows(columns: List<Column>, rows: List<List<Any?>>): JsonArray = JsonArray(
            rows.map { row -> JsonObject(columns.zip(row).associate { (column, value) -> column.name to jsonValue(column.type, value) }) },
        )

        @OptIn(ExperimentalSerializationApi::class)
        private fun jsonValue(type: ColumnType, value: Any?): JsonElement = when {
            value == null -> JsonNull
            type == ColumnType.INTEGER || type == ColumnType.NUMBER -> when (value) {
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                // A whole value is written without « .0 », as in the CSV file.
                is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 9.0e15) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
                // An amount keeps its scale (« 4210345.60 »): written as is, never through a double.
                is BigDecimal -> JsonUnquotedLiteral(value.toPlainString())
                else -> error("Not a number: ${value::class.simpleName}")
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
