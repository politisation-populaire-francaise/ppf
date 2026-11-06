package org.politisationpopulairefrancaise.server.agir

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

/** Contents of the Agir space for the tests, all fictitious and marked [EXEMPLE]. */
object AgirFixtures {
    const val HOUR: Long = 3_600
    const val DAY: Long = 86_400

    fun nowSeconds(): Long = Instant.now().epochSecond

    /** Settings of the Agir space for a test: no periodic task, never read from the environment. */
    fun testAgir(config: AgirConfig = AgirConfig(cleanupInterval = Duration.ZERO), clock: Clock = Clock.systemUTC()): AgirSetup = AgirSetup(config, clock)

    /** The tags of a complete mobilization (contract, section 2). */
    fun mobilizationTags(
        d: String = "manif-exemple",
        title: String = "[EXEMPLE] Manifestation pour les services publics",
        start: Long = nowSeconds() + DAY,
        end: Long? = null,
        type: String = "manifestation",
        territory: String = "Lyon",
        location: String = "[EXEMPLE] Place de la République",
        status: String? = null,
        organizers: List<String> = emptyList(),
    ): List<List<String>> = buildList {
        add(listOf("d", d))
        add(listOf(AgirFormats.TAG_TITLE, title))
        add(listOf(AgirFormats.TAG_START, start.toString()))
        end?.let { add(listOf(AgirFormats.TAG_END, it.toString())) }
        add(listOf(AgirFormats.TAG_START_TZID, AgirFormats.TIME_ZONE))
        add(listOf(AgirFormats.TAG_LOCATION, location))
        add(listOf(AgirFormats.TAG_EVENT_TYPE, type))
        status?.let { add(listOf(AgirFormats.TAG_EVENT_STATUS, it)) }
        add(listOf(AgirFormats.TAG_TERRITORY, territory))
        add(listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION))
        organizers.forEach { add(listOf(AgirFormats.TAG_PUBKEY, it, "", AgirFormats.ROLE_ORGANIZER)) }
    }

    suspend fun LocalEventSigner.mobilization(
        tags: List<List<String>> = mobilizationTags(),
        content: String = "[EXEMPLE] Départ à 14 heures, parcours déclaré.",
        createdAt: Long = nowSeconds() - 60,
    ): SignedEvent = sign(kind = AgirFormats.KIND_MOBILIZATION, content = content, tags = tags, createdAt = createdAt)

    /** A plausible value for a tag that a call of some type requires (AgirFormats.CALL_REQUIRED_TAGS). */
    fun valueFor(tag: String, start: Long): String = when (tag) {
        AgirFormats.TAG_START -> start.toString()
        AgirFormats.TAG_END -> (start + HOUR).toString()
        AgirFormats.TAG_DEADLINE -> (start + 7 * DAY).toString()
        AgirFormats.TAG_GOAL -> "500"
        AgirFormats.TAG_LINK -> "https://exemple.invalid/appel"
        AgirRules.TAG_PLATFORM -> AgirFormats.PETITION_PLATFORMS.last()
        AgirFormats.TAG_DECLARATION -> AgirFormats.DECLARATION_STATES.first()
        else -> "[EXEMPLE] $tag"
    }

    /**
     * The tags of a complete call of [type]: the common tags, then every tag the type requires (the
     * first alternative of "a|b"), read from the contract, never written here.
     */
    fun callTags(type: String, d: String = "appel-$type", start: Long = nowSeconds() + DAY, title: String = "[EXEMPLE] Appel : $type"): List<List<String>> = buildList {
        add(listOf("d", d))
        add(listOf(AgirFormats.TAG_TITLE, title))
        add(listOf(AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL))
        add(listOf(AgirFormats.TAG_CALL_TYPE, type))
        add(listOf(AgirFormats.TAG_TERRITORY, "France"))
        add(listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION))
        add(listOf(AgirFormats.TAG_PUBLISHED_AT, nowSeconds().toString()))
        for (required in AgirFormats.CALL_REQUIRED_TAGS.getValue(type)) {
            val tag = required.substringBefore('|')
            add(listOf(tag, valueFor(tag, start)))
        }
    }

    suspend fun LocalEventSigner.call(
        type: String,
        tags: List<List<String>> = callTags(type),
        content: String = "[EXEMPLE] Texte de l'appel.",
        createdAt: Long = nowSeconds() - 60,
    ): SignedEvent = sign(kind = Article.KIND, content = content, tags = tags, createdAt = createdAt)

    /** The tags of a directory entry (contract, section 4); [member] is the key of the organisation, if it has one. */
    fun organisationTags(d: String = "organisation-exemple", member: String? = null, title: String = "[EXEMPLE] Collectif fictif"): List<List<String>> = buildList {
        add(listOf("d", d))
        add(listOf(AgirFormats.TAG_TITLE, title))
        add(listOf(AgirFormats.TAG_SUMMARY, "[EXEMPLE] Organisation inventée pour les tests."))
        add(listOf(AgirFormats.TAG_TYPE, AgirFormats.TYPE_ORGANISATION))
        add(listOf(AgirFormats.TAG_ORG_TYPE, "collectif"))
        add(listOf(AgirFormats.TAG_TERRITORY, "Lyon"))
        add(listOf(AgirFormats.TAG_VERIFIED, "2026-11-01"))
        add(listOf(AgirFormats.TAG_PUBLISHED_AT, nowSeconds().toString()))
        member?.let { add(listOf(AgirFormats.TAG_PUBKEY, it)) }
    }

    suspend fun LocalEventSigner.organisation(
        tags: List<List<String>> = organisationTags(),
        content: String = "[EXEMPLE] Présentation de l'organisation.",
        createdAt: Long = nowSeconds() - 60,
    ): SignedEvent = sign(kind = Article.KIND, content = content, tags = tags, createdAt = createdAt)

    /** [tags] without the tags named [name]. */
    fun List<List<String>>.withoutTag(name: String): List<List<String>> = filterNot { it.firstOrNull() == name }

    /** [tags] with the first value of [name] replaced by [value] (added when absent). */
    fun List<List<String>>.withTag(name: String, value: String): List<List<String>> =
        if (any { it.firstOrNull() == name }) map { if (it.firstOrNull() == name) listOf(name, value) + it.drop(2) else it } else this + listOf(listOf(name, value))

    suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    suspend fun HttpResponse.error(): ApiError = Json.decodeFromString<ApiError>(bodyAsText())

    suspend fun HttpResponse.published(): PublishResponse = Json.decodeFromString<PublishResponse>(bodyAsText())
}
