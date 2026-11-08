package org.politisationpopulairefrancaise.server.editorial

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.server.backoffice.instant
import org.politisationpopulairefrancaise.server.backoffice.nowSeconds
import org.politisationpopulairefrancaise.server.backoffice.setInstant
import org.politisationpopulairefrancaise.server.backoffice.setText
import org.politisationpopulairefrancaise.server.backoffice.setUuid
import org.politisationpopulairefrancaise.server.backoffice.transaction
import org.politisationpopulairefrancaise.server.backoffice.uuid
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** The states of a draft (docs/integration/circuit-editorial.md, section 4); only [DRAFT] is used before the queue. */
enum class DraftStatus(val db: String, val editable: Boolean) {
    DRAFT("draft", true),
    IN_REVIEW("in_review", false),
    CHANGES_REQUESTED("changes_requested", true),
    APPROVED("approved", false),
    SIGNING("signing", false),
    PUBLISHED("published", false),
    REJECTED("rejected", false),
    WITHDRAWN("withdrawn", false),
    SIGNING_REFUSED("signing_refused", false),
    ;

    companion object {
        fun of(db: String): DraftStatus = entries.first { it.db == db }
    }
}

/** The nature of the change of an update (section 3.4): a correction enters the public journal of corrections. */
enum class ChangeKind(val db: String) {
    CORRECTION("correction"),
    UPDATE("mise-a-jour"),
    ;

    companion object {
        fun of(db: String?): ChangeKind? = entries.firstOrNull { it.db == db }
    }
}

/** One draft, whatever its versions. [slug] and [title] are those of the latest version. */
data class EditorialDraft(
    val id: UUID,
    val space: EditorialSpace,
    val type: ArticleType,
    val slug: String?,
    val title: String,
    val baseEventId: String?,
    val status: DraftStatus,
    val author: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val latestVersion: Int,
)

/** What one save writes: the front matter entries in order, the body, the change of an update. */
data class DraftContent(
    val entries: List<Pair<String, String>>,
    val body: String,
    val changeKind: ChangeKind? = null,
    val changeNote: String? = null,
) {
    /** The answer to « produced with the help of an AI? », read from the entries (null: not answered). */
    val origin: ContentOrigin? get() = entries.lastOrNull { it.first == ContentSchema.ORIGIN }?.second?.trim()?.let(ContentOrigin::fromTag)

    fun draft(type: ArticleType): ContentDraft = ContentDraft(type, entries, body)

    /** Whether [other] holds the same text (the author and the date apart). */
    fun sameAs(other: DraftContent): Boolean = entries == other.entries && body == other.body && changeKind == other.changeKind && changeNote == other.changeNote
}

/** One version of a draft, never modified. */
data class DraftVersion(
    val draftId: UUID,
    val version: Int,
    val content: DraftContent,
    val writtenBy: UUID,
    val writtenAt: Instant,
    val problems: Int,
)

/** What a save of a new version led to. */
sealed interface SaveResult {
    data class Saved(val draft: EditorialDraft) : SaveResult

    /** Nothing changed since the latest version: no version added. */
    data class Unchanged(val draft: EditorialDraft) : SaveResult

    /** Another version was saved since [expected]: nothing written (optimistic concurrency). */
    data class Conflict(val latest: DraftVersion) : SaveResult

    /** Another open draft holds this slug (one open draft per address). */
    data object SlugTaken : SaveResult

    /** The draft is in review or finished: it cannot be changed. */
    data object NotEditable : SaveResult

    data object NotFound : SaveResult
}

/**
 * The drafts of the editorial circuit and their versions (tables of `V11__editorial.sql`, provisional
 * number). A save never changes a version: it adds the next one, and only when the version it started
 * from is still the latest (optimistic concurrency on `latest_version`). Plain JDBC, blocking.
 */
class EditorialDrafts(private val dataSource: DataSource, private val clock: Clock = Clock.systemUTC()) {
    /** A new draft of [type] in [space], its first version written by [writer]; [baseEventId] for an update. */
    fun create(space: EditorialSpace, type: ArticleType, content: DraftContent, problems: Int, writer: UUID, baseEventId: String? = null): SaveResult {
        require(content.body.length <= MAX_BODY) { "The body has at most $MAX_BODY characters" }
        val now = clock.nowSeconds()
        val id = UUID.randomUUID()
        return withSlugGuard {
            dataSource.transaction { connection ->
                connection.prepareStatement(
                    "INSERT INTO editorial_draft (id, space, type, slug, title, base_event_id, status, author_account, created_at, updated_at, latest_version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                ).use { insert ->
                    insert.setUuid(1, id)
                    insert.setString(2, space.id)
                    insert.setString(3, type.tagValue)
                    insert.setText(4, slugOf(content))
                    insert.setString(5, titleOf(content))
                    insert.setText(6, baseEventId)
                    insert.setString(7, DraftStatus.DRAFT.db)
                    insert.setUuid(8, writer)
                    insert.setInstant(9, now)
                    insert.setInstant(10, now)
                    insert.executeUpdate()
                }
                insertVersion(connection, id, 1, content, writer, now, problems)
                SaveResult.Saved(requireNotNull(find(connection, id)))
            }
        }
    }

    /**
     * The next version of draft [id], written by [writer], when [expectedVersion] is still its latest
     * version; otherwise [SaveResult.Conflict] with the latest version, and nothing is written.
     */
    fun save(id: UUID, expectedVersion: Int, content: DraftContent, problems: Int, writer: UUID): SaveResult {
        require(content.body.length <= MAX_BODY) { "The body has at most $MAX_BODY characters" }
        val now = clock.nowSeconds()
        return withSlugGuard {
            dataSource.transaction { connection ->
                val draft = find(connection, id, lock = true) ?: return@transaction SaveResult.NotFound
                if (!draft.status.editable) return@transaction SaveResult.NotEditable
                val latest = requireNotNull(version(connection, id, draft.latestVersion))
                if (draft.latestVersion != expectedVersion) return@transaction SaveResult.Conflict(latest)
                if (latest.content.sameAs(content)) return@transaction SaveResult.Unchanged(draft)
                val next = draft.latestVersion + 1
                connection.prepareStatement(
                    "UPDATE editorial_draft SET latest_version = ?, slug = ?, title = ?, updated_at = ? WHERE id = ? AND latest_version = ?",
                ).use { update ->
                    update.setInt(1, next)
                    update.setText(2, slugOf(content))
                    update.setString(3, titleOf(content))
                    update.setInstant(4, now)
                    update.setUuid(5, id)
                    update.setInt(6, expectedVersion)
                    check(update.executeUpdate() == 1) { "The draft row is locked" }
                }
                insertVersion(connection, id, next, content, writer, now, problems)
                SaveResult.Saved(requireNotNull(find(connection, id)))
            }
        }
    }

    fun find(id: UUID): EditorialDraft? = dataSource.connection.use { find(it, id) }

    fun version(id: UUID, version: Int): DraftVersion? = dataSource.connection.use { version(it, id, version) }

    fun latest(id: UUID): DraftVersion? = find(id)?.let { version(id, it.latestVersion) }

    /** Every version of draft [id], the newest first. */
    fun versions(id: UUID): List<DraftVersion> = dataSource.connection.use { connection ->
        connection.prepareStatement("$VERSION_COLUMNS WHERE draft_id = ? ORDER BY version DESC").use { query ->
            query.setUuid(1, id)
            query.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.toVersion() else null }.toList() }
        }
    }

    /** The drafts of [space], the most recently changed first, [limit] at most. */
    fun list(space: EditorialSpace, limit: Int = 200): List<EditorialDraft> = dataSource.connection.use { connection ->
        connection.prepareStatement("$DRAFT_COLUMNS WHERE space = ? ORDER BY updated_at DESC, id LIMIT ?").use { query ->
            query.setString(1, space.id)
            query.setInt(2, limit.coerceIn(1, 500))
            query.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.toDraft() else null }.toList() }
        }
    }

    /** The slugs of the open drafts of every space and their types (the « other drafts of the batch » of a check). */
    fun openSlugs(): Map<String, ArticleType> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT slug, type FROM editorial_draft WHERE slug IS NOT NULL AND status IN ('draft', 'in_review', 'changes_requested', 'approved', 'signing', 'signing_refused')",
        ).use { query ->
            query.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) ArticleType.entries.firstOrNull { it.tagValue == rows.getString("type") }?.let { put(rows.getString("slug"), it) }
                }
            }
        }
    }

    private fun find(connection: Connection, id: UUID, lock: Boolean = false): EditorialDraft? =
        connection.prepareStatement("$DRAFT_COLUMNS WHERE id = ?" + if (lock) " FOR UPDATE" else "").use { query ->
            query.setUuid(1, id)
            query.executeQuery().use { rows -> if (rows.next()) rows.toDraft() else null }
        }

    private fun version(connection: Connection, id: UUID, version: Int): DraftVersion? =
        connection.prepareStatement("$VERSION_COLUMNS WHERE draft_id = ? AND version = ?").use { query ->
            query.setUuid(1, id)
            query.setInt(2, version)
            query.executeQuery().use { rows -> if (rows.next()) rows.toVersion() else null }
        }

    private fun insertVersion(connection: Connection, id: UUID, version: Int, content: DraftContent, writer: UUID, now: Instant, problems: Int) {
        connection.prepareStatement(
            "INSERT INTO editorial_draft_version (draft_id, version, entries, body, origin, change_kind, change_note, written_by, written_at, problems) " +
                "VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setUuid(1, id)
            insert.setInt(2, version)
            insert.setString(3, encodeEntries(content.entries))
            insert.setString(4, content.body)
            insert.setText(5, content.origin?.tagValue)
            insert.setText(6, content.changeKind?.db)
            insert.setText(7, content.changeNote?.take(MAX_CHANGE_NOTE))
            insert.setUuid(8, writer)
            insert.setInstant(9, now)
            insert.setInt(10, problems)
            insert.executeUpdate()
        }
    }

    /** The unique index of open slugs answers [SaveResult.SlugTaken], the transaction being rolled back. */
    private fun withSlugGuard(block: () -> SaveResult): SaveResult = try {
        block()
    } catch (e: SQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) SaveResult.SlugTaken else throw e
    }

    private fun ResultSet.toDraft(): EditorialDraft = EditorialDraft(
        id = requireNotNull(uuid("id")),
        space = EditorialSpace.entries.first { it.id == getString("space") },
        type = ArticleType.entries.first { it.tagValue == getString("type") },
        slug = getString("slug"),
        title = getString("title"),
        baseEventId = getString("base_event_id"),
        status = DraftStatus.of(getString("status")),
        author = requireNotNull(uuid("author_account")),
        createdAt = requireNotNull(instant("created_at")),
        updatedAt = requireNotNull(instant("updated_at")),
        latestVersion = getInt("latest_version"),
    )

    private fun ResultSet.toVersion(): DraftVersion = DraftVersion(
        draftId = requireNotNull(uuid("draft_id")),
        version = getInt("version"),
        content = DraftContent(decodeEntries(getString("entries")), getString("body"), ChangeKind.of(getString("change_kind")), getString("change_note")),
        writtenBy = requireNotNull(uuid("written_by")),
        writtenAt = requireNotNull(instant("written_at")),
        problems = getInt("problems"),
    )

    companion object {
        const val MAX_BODY: Int = 60_000
        const val MAX_CHANGE_NOTE: Int = 1_000
        private const val UNIQUE_VIOLATION = "23505"
        private val SLUG = Regex("^[a-z0-9][a-z0-9-]{0,99}$")

        private const val DRAFT_COLUMNS =
            "SELECT id, space, type, slug, title, base_event_id, status, author_account, created_at, updated_at, latest_version FROM editorial_draft"
        private const val VERSION_COLUMNS =
            "SELECT draft_id, version, entries::text AS entries, body, change_kind, change_note, written_by, written_at, problems FROM editorial_draft_version"

        /** The slug of the content when it is valid, else null (the draft is saved anyway). */
        fun slugOf(content: DraftContent): String? = content.entries.lastOrNull { it.first == ContentSchema.SLUG }?.second?.trim()?.takeIf(SLUG::matches)

        fun titleOf(content: DraftContent): String = content.entries.lastOrNull { it.first == ContentSchema.TITLE }?.second?.trim().orEmpty().take(200)

        fun encodeEntries(entries: List<Pair<String, String>>): String =
            JsonArray(entries.map { (key, value) -> JsonArray(listOf(JsonPrimitive(key), JsonPrimitive(value))) }).toString()

        fun decodeEntries(text: String): List<Pair<String, String>> = Json.parseToJsonElement(text).jsonArray.map { pair ->
            val items = pair.jsonArray
            items[0].jsonPrimitive.content to items[1].jsonPrimitive.content
        }
    }
}
