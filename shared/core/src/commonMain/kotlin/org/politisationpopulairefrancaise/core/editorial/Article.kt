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
    /**
     * A short course of the "Comprendre en 5 minutes" series (cahier fonctionnel, M13.1): steps,
     * each closing with a quiz, read by `core.comprendre.Parcours`.
     */
    PARCOURS("parcours"),
    /**
     * A story of the history of social movements and revolutions ("Comprendre", owner's decision of
     * 7 November 2026), read by `core.comprendre.HistoryStory`.
     */
    HISTOIRE("histoire"),
    /** A « Qui décide de ça ? » sheet (cahier fonctionnel, M13.1), read by `core.comprendre.QuiDecide`. */
    QUI_DECIDE("qui-decide"),
    /**
     * An entry of the glossary (D-048): a term, its short definition (`summary`), the forms spotted
     * in the texts (`ppf-variant`), read by `core.comprendre.GlossaryEntry`.
     */
    GLOSSAIRE("glossaire"),
    /** A demand of the demands wall: one idea, in one or two sentences, with its argument as body. */
    REVENDICATION("revendication"),

    // The building blocks of "Voir" (D-034, core/voir): structured tags, Markdown body.
    /** An indicator of the observatory (M2): definition, unit, method as body, measures as tags. */
    INDICATEUR("indicateur"),
    /** A sourced fact: an event of the chronology, a vote, a promise, a case, a contradicted statement... */
    FAIT("fait"),
    /** A public person (M4, VO-10): mandates and functions; the facts point to it. */
    PERSONNE("personne"),
    /** A bill or a law under discussion (législomètre, VO-20), with its stages and its effects. */
    TEXTE("texte"),
    /** A social movement elsewhere in the world (M9). */
    MOUVEMENT("mouvement"),
    /** A right of reply (VO-21) published under the content it answers. */
    REPONSE("reponse"),
    ORGANISATION("organisation"),
    /** A call of the Agir space (AP-01 to AP-10), read by core.agir.calls.Call; never shown among the guides. */
    APPEL("appel"),

    // The building blocks of "Décider" (core/decider).
    /** An official petition relayed by PPF (M10, DE-07): platform, official page, counter readings. */
    PETITION("petition"),
    /** A democratic pact (M13.5, DE-08): the election, the measures, the candidates, the deadline. */
    PACTE("pacte"),
    /** The answer of one candidate to a pact: signed or refused, measure by measure, with its archive. */
    ENGAGEMENT("engagement"),

    /** A political party of "Voir" (axis of power), read by core.voir.PoliticalParty; persons and facts link to it. */
    PARTI("parti"),

    /** A testimony published by the team after review (M5, AG-07; core.agir.testimonies.PublishedTestimony), shown by VO-08 in "Le mouvement". */
    TEMOIGNAGE("temoignage"),
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
 * `ppf-territory`, courses add `ppf-duration`. See docs/FORMATS.md.
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
    /** Courses only: the announced reading time, in minutes (`ppf-duration`). */
    val durationMinutes: Int? = null,
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
        const val TAG_DURATION: String = "ppf-duration"

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
                durationMinutes = event.firstTagValue(TAG_DURATION)?.toIntOrNull()?.takeIf { it in 1..240 },
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
            durationMinutes: Int? = null,
            /** Further tags of a "Voir" building block (core/voir), added after the common ones in their order. */
            extraTags: List<List<String>> = emptyList(),
        ): UnsignedEvent {
            require(slug.isNotBlank() && slug.none { it.isWhitespace() }) { "slug must be a single word" }
            require(title.isNotBlank()) { "title is required" }
            require(durationMinutes == null || durationMinutes in 1..240) { "duration must be between 1 and 240 minutes" }
            val tags = buildList {
                add(listOf("d", slug))
                add(listOf(TAG_TITLE, title.trim()))
                summary?.takeIf { it.isNotBlank() }?.let { add(listOf(TAG_SUMMARY, it.trim())) }
                add(listOf(TAG_PUBLISHED_AT, publishedAt.toString()))
                add(listOf(TAG_TYPE, type.tagValue))
                add(listOf(TAG_ORIGIN, origin.tagValue))
                status?.let { add(listOf(TAG_STATUS, it.tagValue)) }
                territory?.takeIf { it.isNotBlank() }?.let { add(listOf(TAG_TERRITORY, it.trim())) }
                durationMinutes?.let { add(listOf(TAG_DURATION, it.toString())) }
                topics.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { add(listOf(TAG_TOPIC, it)) }
                extraTags.forEach { tag ->
                    require(tag.isNotEmpty() && tag[0].isNotBlank()) { "a tag needs a name" }
                    add(tag)
                }
            }
            return UnsignedEvent.create(author, KIND, updatedAt, tags, body)
        }
    }
}
