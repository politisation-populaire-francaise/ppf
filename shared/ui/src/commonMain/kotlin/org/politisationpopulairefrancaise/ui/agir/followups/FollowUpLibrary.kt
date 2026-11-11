package org.politisationpopulairefrancaise.ui.agir.followups

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.followups.FollowUp
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpFormat
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpLink
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpSchema
import org.politisationpopulairefrancaise.core.agir.followups.FollowUps
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.RightOfReply
import org.politisationpopulairefrancaise.ui.resources.Res

/** A link of a follow-up whose content is on the device, with its title as published. */
data class KnownLink(val link: FollowUpLink, val title: String)

/** What the section « Et après ? » shows of one follow-up: the follow-up, its links known on the device, its rights of reply. */
data class FollowUpEntry(val followUp: FollowUp, val links: List<KnownLink>, val replies: List<RightOfReply>)

/**
 * The follow-ups of the mobilizations (AG-62 « Et après ? », docs/integration/agir-et-apres.md), read-only.
 *
 * Step 1: the follow-ups bundled with the application (`suites.json`, written by tools/content-signer from
 * content/suites) are imported once into the local event store, the first time a past mobilization is opened,
 * and shown only when signed by a trusted editorial key ([FollowUp.from]). The file may be absent (not signed
 * yet): nothing breaks, the section says that no follow-up is known. Without a database, the bundled follow-ups
 * are kept in memory.
 *
 * Nothing is sent: the follow-ups of a mobilization are looked up on the device, by the `a` tag of its address;
 * the titles of the demands, calls, chronology items and persons they link to come from the copy of the contents
 * already on the device, and a link whose content is not there is left out. A right of reply of « Voir »
 * (`ppf-type` = `reponse`) whose target is a follow-up is shown under it.
 */
class FollowUpLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    /** The signed follow-ups bundled with the application; injectable for the tests. */
    private val bundled: suspend () -> ByteArray? = { runCatching { Res.readBytes(BUNDLED_FILE) }.getOrNull() },
) {
    private val imports = Mutex()
    private var imported = false

    /** Without a database: the trusted follow-ups of the bundle. */
    private var inMemory: List<FollowUp> = emptyList()

    /** The follow-ups of [mobilization], what happened first coming first, with what the device knows of their links. */
    suspend fun entriesOf(mobilization: ContentAddress): List<FollowUpEntry> {
        ensureImported()
        return withContext(Dispatchers.Default) { read(mobilization) }
    }

    /** Imports the bundle once; an absent or unreadable file imports nothing. */
    private suspend fun ensureImported() {
        imports.withLock {
            if (imported) return@withLock
            imported = true
            val bytes = bundled() ?: return@withLock
            val events = decode(bytes) ?: return@withLock
            withContext(Dispatchers.Default) { importTrusted(events) }
        }
    }

    private fun decode(bytes: ByteArray): List<SignedEvent>? =
        runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull()

    /** Only authentic follow-ups of a trusted editorial key enter the device. */
    private fun importTrusted(events: List<SignedEvent>) {
        val trusted = events.filter { FollowUp.from(it, trust, checkSignature = true) != null }
        val store = store
        if (store == null) {
            inMemory = trusted.mapNotNull { FollowUp.from(it, trust, checkSignature = false) }
        } else {
            for (event in trusted) store.store(event)
        }
    }

    private fun read(mobilization: ContentAddress): List<FollowUpEntry> {
        val store = store
        val followUps = if (store == null) {
            inMemory
        } else {
            // Everything in the local database was checked on arrival; the author is checked again here.
            store.byTag(listOf(FollowUp.KIND), FollowUpFormat.TAG_LINK, mobilization.toString(), limit = MAX_FOLLOW_UPS)
                .mapNotNull { FollowUp.from(it, trust, checkSignature = false) }
        }
        return FollowUps.of(followUps, mobilization).map { followUp ->
            FollowUpEntry(
                followUp = followUp,
                links = followUp.links.mapNotNull { link -> titleOf(store, link.address)?.let { KnownLink(link, it) } },
                replies = repliesTo(store, followUp.address),
            )
        }
    }

    /** The title of the newest version of [address] on the device, or null when the device does not hold it. */
    private fun titleOf(store: LocalEventStore?, address: ContentAddress): String? {
        if (store == null) return null
        return store.byTag(listOf(address.kind), TAG_IDENTIFIER, address.identifier, limit = MAX_VERSIONS)
            .filter { it.pubkey == address.pubkey }
            .minWithOrNull(compareByDescending<SignedEvent> { it.createdAt }.thenBy { it.id })
            ?.let { Article.from(it) }
            ?.title
    }

    /** The rights of reply of a trusted editorial key that answer [followUp], oldest first. */
    private fun repliesTo(store: LocalEventStore?, followUp: ContentAddress): List<RightOfReply> {
        if (store == null) return emptyList()
        return store.byTag(listOf(Article.KIND), FollowUpFormat.TAG_LINK, followUp.toString(), limit = MAX_VERSIONS)
            .filter { trust.isTrusted(it.pubkey) }
            .mapNotNull { event -> Article.from(event)?.let { RightOfReply.from(it) } }
            .filter { it.target == followUp }
            .sortedBy { it.article.publishedAt ?: it.article.updatedAt }
    }

    companion object {
        /** Written by tools/content-signer (content/suites); absent until it is first signed. */
        const val BUNDLED_FILE: String = "files/contents/" + FollowUpSchema.BUNDLE

        /** The follow-ups read for one mobilization, and the versions read for one linked content. */
        const val MAX_FOLLOW_UPS: Int = 200
        const val MAX_VERSIONS: Int = 20

        private const val TAG_IDENTIFIER: String = "d"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
