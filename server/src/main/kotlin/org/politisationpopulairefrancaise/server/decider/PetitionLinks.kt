package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.runBlocking
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.politisationpopulairefrancaise.core.decider.DeciderTags
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.core.decider.PetitionStatus
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.urne.UrneResults
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.signing.DraftSubmission
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.SigningStatus
import org.politisationpopulairefrancaise.server.signing.SigningTicket
import org.politisationpopulairefrancaise.server.urne.RevendicationUrnes
import org.politisationpopulairefrancaise.server.urne.UrneService
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/** A petition link waiting for the approval of the team on the page of the signing service (table petition_draft). */
data class PetitionDraft(val eventId: String, val demand: String, val unsigned: UnsignedEvent, val approvalUrl: String, val submittedDay: LocalDate)

/**
 * The official petition attached by the team to a demand (D-115, owner's decision of 7 November 2026: « un
 * membre de l'équipe PPF ira déposer une pétition officielle sur le site de l'assemblée et on mettra le lien
 * dans la card de la revendication »): a relayed petition (kind 30023, `ppf-type` = `petition`, link of role
 * `revendication` to the demand), signed by the platform for the use `decider.pacte` after the approval of a
 * member of the team on the page of the signing service (one approval, two on demand). The server never signs
 * it itself and never carries an approval.
 *
 * Steps: the team files the petition on the official site, outside PPF; pastes its address in the queue
 * `decider.petitions` ([PetitionLinksQueue]); the draft goes to the signing service ([submit]); a member
 * approves it there; then [runDue] asks for the signature and publishes the petition, unchanged. The cards then
 * show the link and pin the demand while the petition is open.
 */
class PetitionLinks(
    private val dataSource: DataSource,
    private val events: EventRepository,
    private val signer: PlatformSigner,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(PetitionLinks::class.java)
    private val tickets = ConcurrentHashMap<String, SigningTicket>()

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)

    sealed interface Submit {
        data class Done(val approvalUrl: String) : Submit

        data class Refused(val message: String) : Submit
    }

    /** The published demand at [address], or null. */
    fun demand(address: ContentAddress): Article? =
        events.query(EventFilter(kinds = listOf(address.kind), authors = listOf(address.pubkey), identifiers = listOf(address.identifier), limit = 1)).firstOrNull()?.let(Article::from)

    /** Builds the petition of [demand] at [officialUrl] and submits it for approval. */
    fun submit(demand: ContentAddress, officialUrl: String, reference: String?): Submit = runBlocking {
        val url = officialUrl.trim()
        val platform = PetitionPlatform.entries.firstOrNull { it.isOfficial(url) }
            ?: return@runBlocking Submit.Refused("Collez l'adresse de la pétition sur le site officiel (Assemblée nationale, Sénat, CESE, référendum d'initiative partagée ou initiative citoyenne européenne), en https.")
        val article = demand(demand) ?: return@runBlocking Submit.Refused("Cette revendication n'est pas publiée.")
        if (pending(demand.toString()) != null) return@runBlocking Submit.Refused("Une pétition de cette revendication attend déjà son approbation.")
        val key = signer.currentKey(SigningUse.DECIDER_PACTE) ?: return@runBlocking Submit.Refused("Le service de signature n'est pas configuré ou n'a pas de clé pour le Pacte et les pétitions.")
        val now = clock.instant().epochSecond
        val unsigned = UnsignedEvent(
            pubkey = key,
            createdAt = now,
            kind = EventKinds.LONG_FORM_ARTICLE,
            tags = buildList {
                add(listOf("d", identifierFor(demand)))
                add(listOf("title", article.title.take(MAX_TITLE)))
                add(listOf("summary", "Pétition officielle déposée par l'équipe de PPF pour la revendication « ${article.title.take(MAX_TITLE)} », très soutenue par les citoyens."))
                add(listOf("published_at", now.toString()))
                add(listOf(Article.TAG_TYPE, "petition"))
                add(listOf("ppf-origin", "human"))
                add(listOf(DeciderTags.PLATFORM, platform.tagValue))
                add(listOf(DeciderTags.OFFICIAL_URL, url))
                reference?.trim()?.takeIf { it.isNotEmpty() }?.let { add(listOf(DeciderTags.REFERENCE, it.take(MAX_REFERENCE))) }
                add(listOf(DeciderTags.OPENED, today().toString()))
                add(listOf(DeciderTags.PETITION_STATUS, PetitionStatus.OPEN.tagValue))
                add(listOf("a", demand.toString(), "", DeciderTags.ROLE_DEMAND))
            },
            content = "",
        )
        when (val submission = signer.submitDraft(unsigned, SigningUse.DECIDER_PACTE)) {
            is DraftSubmission.Submitted -> {
                record(demand.toString(), unsigned, submission.approvalUrl)
                Submit.Done(submission.approvalUrl)
            }
            is DraftSubmission.Refused -> Submit.Refused("Le service de signature a refusé la pétition (${submission.code}).")
        }
    }

    /** One pass: asks for the signature of the approved drafts, publishes the signed petitions, forgets the expired drafts. */
    fun runDue(): Int = runBlocking {
        var published = 0
        for (draft in drafts()) {
            val ticket = tickets[draft.eventId]
            if (ticket != null) {
                when (val status = signer.status(ticket)) {
                    SigningStatus.Pending -> Unit
                    is SigningStatus.Signed -> {
                        tickets.remove(draft.eventId)
                        if (status.event.id == draft.eventId && events.store(status.event) != StoreOutcome.SUPERSEDED) published++
                        delete(draft.eventId)
                    }
                    is SigningStatus.Refused -> {
                        tickets.remove(draft.eventId)
                        log.warn("Petitions: the signature of a petition link was refused (${status.code}).")
                        if (status.code !in RETRIED) delete(draft.eventId)
                    }
                }
                continue
            }
            val state = signer.draftStatus(SigningUse.DECIDER_PACTE, draft.eventId)
            when {
                // Unknown to the service (restarted, or the 7 days passed): submitted again while young, forgotten after.
                state == null -> if (draft.submittedDay.isBefore(today().minusDays(DRAFT_DAYS))) delete(draft.eventId) else signer.submitDraft(draft.unsigned, SigningUse.DECIDER_PACTE)
                !state.contentKnown -> signer.submitDraft(draft.unsigned, SigningUse.DECIDER_PACTE)
                state.ready -> tickets[draft.eventId] = signer.request(draft.unsigned, SigningUse.DECIDER_PACTE)
            }
        }
        published
    }

    /** How many approvals a pending draft has, as the service says; null when it does not answer. */
    fun approvals(draft: PetitionDraft): Pair<Int, Int>? = runBlocking {
        signer.draftStatus(SigningUse.DECIDER_PACTE, draft.eventId)?.let { it.approvals.size to it.required }
    }

    fun pending(demand: String): PetitionDraft? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM petition_draft WHERE demand = ?").use { s ->
            s.setString(1, demand)
            s.executeQuery().use { r -> if (r.next()) r.draft() else null }
        }
    }

    fun drafts(): List<PetitionDraft> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM petition_draft ORDER BY submitted_day, event_id").use { s ->
            s.executeQuery().use { r -> buildList { while (r.next()) add(r.draft()) } }
        }
    }

    /** The petitions already published for [demand] (by any trusted key), newest first. */
    fun published(demand: String): List<SignedEvent> =
        events.query(EventFilter(kinds = listOf(EventKinds.LONG_FORM_ARTICLE), tags = mapOf(Article.TAG_TYPE to listOf("petition"), "a" to listOf(demand)), limit = 20))

    private fun record(demand: String, unsigned: UnsignedEvent, approvalUrl: String) = dataSource.connection.use { c ->
        c.prepareStatement("INSERT INTO petition_draft (event_id, demand, unsigned, approval_url, submitted_day) VALUES (?, ?, ?::jsonb, ?, ?)").use { s ->
            s.setString(1, unsigned.idHex())
            s.setString(2, demand)
            s.setString(3, encode(unsigned))
            s.setString(4, approvalUrl)
            s.setObject(5, today())
            s.executeUpdate()
        }
    }

    private fun delete(eventId: String) = dataSource.connection.use { c ->
        c.prepareStatement("DELETE FROM petition_draft WHERE event_id = ?").use { s ->
            s.setString(1, eventId)
            s.executeUpdate()
        }
    }

    private fun ResultSet.draft() = PetitionDraft(
        eventId = getString("event_id"),
        demand = getString("demand"),
        unsigned = decode(getString("unsigned")),
        approvalUrl = getString("approval_url"),
        submittedDay = getObject("submitted_day", LocalDate::class.java),
    )

    companion object {
        const val MAX_TITLE: Int = 200
        const val MAX_REFERENCE: Int = 100
        const val DRAFT_DAYS: Long = 7
        private val RETRIED = setOf("rate_limited", "signer_unavailable", "unknown_ticket")

        /** The `d` of the petition of a demand: stable, one per demand and key. */
        fun identifierFor(demand: ContentAddress): String = "petition-" + demand.identifier.take(100)

        fun encode(event: UnsignedEvent): String = buildJsonObject {
            put("pubkey", event.pubkey)
            put("created_at", event.createdAt)
            put("kind", event.kind)
            put("tags", JsonArray(event.tags.map { tag -> JsonArray(tag.map { JsonPrimitive(it) }) }))
            put("content", event.content)
        }.toString()

        fun decode(json: String): UnsignedEvent {
            val o = Json.parseToJsonElement(json).jsonObject
            return UnsignedEvent(
                pubkey = o.getValue("pubkey").jsonPrimitive.content,
                createdAt = o.getValue("created_at").jsonPrimitive.long,
                kind = o.getValue("kind").jsonPrimitive.int,
                tags = o.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
                content = o.getValue("content").jsonPrimitive.content,
            )
        }
    }
}

/**
 * The queue `decider.petitions` of the back-office: the demands in debate with their citizens' supports, the
 * most supported first; for one of them the team pastes the address of the official petition it filed. The
 * second threshold of supports (D-115) is shown on every card; the team checks it before filing.
 */
class PetitionLinksQueue(
    private val links: PetitionLinks,
    private val urnes: UrneService,
) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = "Pétitions officielles à relier aux revendications"
    override val viewPermission: String = Permissions.queueDecide(ID)

    private data class Row(val demand: String, val supports: Long, val draft: PetitionDraft?)

    private fun rows(): List<Row> {
        val drafts = links.drafts().associateBy { it.demand }
        return urnes.openWithPrefix(RevendicationUrnes.PREFIX).map { urne ->
            val results: UrneResults? = runCatching { urnes.journal(urne.number).results }.getOrNull()
            Row(urne.subject, results?.citizens?.counts?.get("pour") ?: 0L, drafts[urne.subject])
        }.sortedWith(compareByDescending<Row> { it.draft != null }.thenByDescending { it.supports }.thenBy { it.demand })
    }

    override fun pendingCount(): Int = links.drafts().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val all = rows()
        val items = all.drop(offset).take(limit).map { row ->
            val title = runCatching { ContentAddress.parse(row.demand) }.getOrNull()?.let(links::demand)?.title ?: row.demand
            QueueItem(
                id = row.demand,
                title = title.take(200),
                summary = (if (row.draft != null) "Pétition en attente d'approbation · " else "") + "${row.supports} soutien(s) des citoyens",
            )
        }
        return QueuePage(items, (offset + limit).takeIf { it < all.size }?.toString())
    }

    override fun render(itemId: String, html: FlowContent) {
        val address = runCatching { ContentAddress.parse(itemId) }.getOrNull()
        val article = address?.let(links::demand)
        if (address == null || article == null) {
            html.h1 { +"Cette revendication n'est pas publiée." }
            return
        }
        html.h1 { +article.title }
        html.dl(classes = "bo-facts") {
            dt { +"Revendication" }
            dd { code { +itemId } }
            article.territory?.let { territory -> dt { +"Territoire" }; dd { +territory } }
        }
        val published = links.published(itemId)
        if (published.isNotEmpty()) {
            html.h2 { +"Pétitions déjà publiées" }
            published.forEach { event -> html.p { +(event.firstTagValue(DeciderTags.OFFICIAL_URL) ?: event.id) } }
        }
        val draft = links.pending(itemId)
        html.h2 { +"Pétition en attente" }
        if (draft == null) {
            html.p { +"Aucune. Déposez la pétition sur le site officiel, puis collez ici son adresse (bouton « Relier la pétition officielle »)." }
        } else {
            val approvals = links.approvals(draft)
            html.p {
                +(approvals?.let { "Approbations : ${it.first} sur ${it.second}. " } ?: "Le service de signature ne répond pas. ")
                a(href = draft.approvalUrl) {
                    attributes["rel"] = "noopener noreferrer"
                    +"Lire et approuver sur la page du service de signature"
                }
            }
        }
        html.p { +"Vérifiez d'abord sur la carte que la revendication a passé le second seuil de soutiens (D-115) et plus de « pour » que de « contre »." }
    }

    override fun actions(itemId: String): List<QueueAction> {
        if (links.pending(itemId) != null) return emptyList()
        return listOf(QueueAction(ATTACH, "Relier la pétition officielle (collez son adresse)", viewPermission, reasonRequired = true, sensitive = true))
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
        if (action != ATTACH) return ActionOutcome.Refused("Action inconnue.")
        val address = runCatching { ContentAddress.parse(itemId) }.getOrNull() ?: return ActionOutcome.NotFound
        val text = reason?.trim().orEmpty()
        if (text.isEmpty()) return ActionOutcome.Refused("Collez l'adresse de la pétition officielle.")
        // « adresse » alone, or « adresse numéro » (the number given by the platform).
        val url = text.substringBefore(' ')
        val reference = text.substringAfter(' ', "").ifBlank { null }
        return when (val outcome = links.submit(address, url, reference)) {
            is PetitionLinks.Submit.Done -> ActionOutcome.Done
            is PetitionLinks.Submit.Refused -> ActionOutcome.Refused(outcome.message)
        }
    }

    companion object {
        const val ID: String = "decider.petitions"
        const val ATTACH: String = "attach"
    }
}
