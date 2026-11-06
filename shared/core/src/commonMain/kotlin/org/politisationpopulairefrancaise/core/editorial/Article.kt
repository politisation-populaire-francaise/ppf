package org.politisationpopulairefrancaise.core.editorial

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** The editorial families of the platform (cahier fonctionnel, M6), plus the demands of the wall (M11). */
enum class ArticleType(val tagValue: String) {
    FICHE_PRATIQUE("fiche-pratique"),
    DOSSIER("dossier"),
    DECRYPTAGE("decryptage"),
    ARTICLE("article"),
    /** A demand of the demands wall: one idea, in one or two sentences, with its argument as body. */
    REVENDICATION("revendication"),
    ;

    companion object {
        fun fromTag(value: String): ArticleType? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Every content says whether a human wrote it or an AI helped (decision of the owner, 2026-11-04). */
enum class ContentOrigin(val tagValue: String) {
    HUMAN("human"),
    AI_ASSISTED("ai-assisted"),
    ;

    companion object {
        fun fromTag(value: String): ContentOrigin? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Where a demand stands (cahier fonctionnel, DE-01); the team updates it, never the author alone. */
enum class RevendicationStatus(val tagValue: String) {
    NOUVELLE("nouvelle"),
    EN_DEBAT("en-debat"),
    TRES_SOUTENUE("tres-soutenue"),
    RETENUE("retenue"),
    CAMPAGNE("campagne"),
    ;

    companion object {
        fun fromTag(value: String): RevendicationStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * An editorial content (practical guide, dossier, fact-check, article, demand) read from a NIP-23
 * event: kind 30023, Markdown body, `d` identifier, `title`, `summary`, `published_at`, `t`
 * topics, plus the PPF tags `ppf-type` and `ppf-origin`; demands add `ppf-status` and
 * `ppf-territory`. See docs/FORMATS.md.
 */
data class Article(
    val event: SignedEvent,
    val address: ContentAddress,
    val title: String,
    val summary: String?,
    val topics: List<String>,
    val type: ArticleType,
    val origin: ContentOrigin,
    /** First publication, seconds since 1970; null when unknown. */
    val publishedAt: Long?,
    /** Demands only: where the demand stands. */
    val status: RevendicationStatus? = null,
    /** Demands only: the territory concerned (a town, a department, "France"). */
    val territory: String? = null,
) {
    val slug: String get() = address.identifier
    val author: String get() = event.pubkey
    val updatedAt: Long get() = event.createdAt
    val body: String get() = event.content

    companion object {
        const val KIND: Int = EventKinds.LONG_FORM_ARTICLE
        const val TAG_TITLE: String = "title"
        const val TAG_SUMMARY: String = "summary"
        const val TAG_PUBLISHED_AT: String = "published_at"
        const val TAG_TOPIC: String = "t"
        const val TAG_TYPE: String = "ppf-type"
        const val TAG_ORIGIN: String = "ppf-origin"
        const val TAG_STATUS: String = "ppf-status"
        const val TAG_TERRITORY: String = "ppf-territory"

        /** Reads an article; null if the event is not a well-formed editorial content. */
        fun from(event: SignedEvent): Article? {
            if (event.kind != KIND) return null
            val address = ContentAddress.of(event) ?: return null
            val title = event.firstTagValue(TAG_TITLE)?.takeIf { it.isNotBlank() } ?: return null
            val type = event.firstTagValue(TAG_TYPE)?.let(ArticleType::fromTag) ?: ArticleType.ARTICLE
            val origin = event.firstTagValue(TAG_ORIGIN)?.let(ContentOrigin::fromTag) ?: return null
            return Article(
                event = event,
                address = address,
                title = title,
                summary = event.firstTagValue(TAG_SUMMARY)?.takeIf { it.isNotBlank() },
                topics = event.tagValues(TAG_TOPIC),
                type = type,
                origin = origin,
                publishedAt = event.firstTagValue(TAG_PUBLISHED_AT)?.toLongOrNull(),
                status = event.firstTagValue(TAG_STATUS)?.let(RevendicationStatus::fromTag),
                territory = event.firstTagValue(TAG_TERRITORY)?.takeIf { it.isNotBlank() },
            )
        }

        /** Prepares the event of an article; the author's signer then signs it. */
        fun build(
            author: PublicKey,
            slug: String,
            title: String,
            summary: String?,
            topics: List<String>,
            type: ArticleType,
            origin: ContentOrigin,
            publishedAt: Long,
            updatedAt: Long,
            body: String,
            status: RevendicationStatus? = null,
            territory: String? = null,
        ): UnsignedEvent {
            require(slug.isNotBlank() && slug.none { it.isWhitespace() }) { "slug must be a single word" }
            require(title.isNotBlank()) { "title is required" }
            val tags = buildList {
                add(listOf("d", slug))
                add(listOf(TAG_TITLE, title.trim()))
                summary?.takeIf { it.isNotBlank() }?.let { add(listOf(TAG_SUMMARY, it.trim())) }
                add(listOf(TAG_PUBLISHED_AT, publishedAt.toString()))
                add(listOf(TAG_TYPE, type.tagValue))
                add(listOf(TAG_ORIGIN, origin.tagValue))
                status?.let { add(listOf(TAG_STATUS, it.tagValue)) }
                territory?.takeIf { it.isNotBlank() }?.let { add(listOf(TAG_TERRITORY, it.trim())) }
                topics.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { add(listOf(TAG_TOPIC, it)) }
            }
            return UnsignedEvent.create(author, KIND, updatedAt, tags, body)
        }
    }
}
