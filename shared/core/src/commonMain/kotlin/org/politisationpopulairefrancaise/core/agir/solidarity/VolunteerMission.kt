package org.politisationpopulairefrancaise.core.agir.solidarity

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * A volunteer mission published by the team (M35, AG-12, "Je veux aider"): a kind 30023 event whose
 * `ppf-type` is `mission`, signed by an editorial key (docs/integration/agir-solidarite.md,
 * section 2.2). Missions are sorted by skills; the skills a user picks to filter them stay on the
 * device.
 *
 * Required tags: `d`, `title`, `summary`, `published_at`, at least one `ppf-skill`,
 * `ppf-territory`, and either `ppf-remote` = `oui` or a `location`. Optional: `ppf-commitment`,
 * `ppf-people`, `ppf-deadline`, `ppf-mission-status`, `ppf-origin`. The content is the description,
 * in simple Markdown.
 */
data class VolunteerMission(
    val event: SignedEvent,
    val address: ContentAddress,
    /** The title, as published (with the [EXEMPLE] marker for examples). */
    val title: String,
    val summary: String,
    /** Among [SolidarityFormats.SKILLS], in their order; an unknown skill is read as `autre`. */
    val skills: List<String>,
    /** A commune, a department or `France`. */
    val territory: String,
    /** Done remotely. */
    val remote: Boolean,
    /** Where it happens, in words; null for a remote mission without a place. */
    val location: String?,
    /** The time asked, in words. */
    val commitment: String?,
    /** How many people are wanted. */
    val people: Int?,
    /** Deadline, seconds since 1970. */
    val deadline: Long?,
    /** First publication, seconds since 1970. */
    val publishedAt: Long,
    /** One of [SolidarityFormats.MISSION_STATUSES]; an unknown value is read as closed. */
    val status: String,
    /** Who wrote the mission; null when the tag is absent. */
    val origin: ContentOrigin?,
) {
    /** The identifier of the mission (its `d` tag), stable across its versions. */
    val slug: String get() = address.identifier

    /** The editorial key that published the mission. */
    val editor: String get() = event.pubkey

    val updatedAt: Long get() = event.createdAt

    /** The description, in simple Markdown. */
    val description: String get() = event.content

    val example: Boolean get() = title.trimStart().startsWith(Organisation.EXAMPLE_MARKER)

    /** The title without the example marker. */
    val displayTitle: String get() = title.trimStart().removePrefix(Organisation.EXAMPLE_MARKER).trim()

    /** Territory `France`. */
    val isNational: Boolean get() = FrenchText.fold(territory) == Organisation.NATIONAL

    val isOpen: Boolean get() = status == SolidarityFormats.MISSION_OPEN

    /** Enough volunteers came forward. */
    val isFilled: Boolean get() = status == SolidarityFormats.MISSION_FILLED

    val isClosed: Boolean get() = status == SolidarityFormats.MISSION_CLOSED

    /** The day of the deadline, in Paris. */
    val deadlineDate: CivilDate? get() = deadline?.let { ParisTime.moment(it).date }

    companion object {
        const val KIND: Int = EventKinds.LONG_FORM_ARTICLE
        const val TAG_ORIGIN: String = "ppf-origin"

        private val SLUG_PATTERN = Regex("[a-z0-9._~-]{1,64}")

        /**
         * Reads a mission that the application may show: null unless the event is signed by a key
         * [trust] holds, carries a valid signature ([checkSignature]) and is well-formed ([parse]).
         */
        fun from(event: SignedEvent, trust: EditorialTrust, checkSignature: Boolean = true): VolunteerMission? {
            if (!trust.isTrusted(event.pubkey)) return null
            if (checkSignature && !event.verify()) return null
            return parse(event)
        }

        /** Reads the form of a mission, without any check of its author or signature: use [from]. */
        fun parse(event: SignedEvent): VolunteerMission? {
            if (event.kind != KIND) return null
            if (event.firstTagValue(AgirFormats.TAG_TYPE) != SolidarityFormats.TYPE_MISSION) return null
            val address = ContentAddress.of(event)?.takeIf { SLUG_PATTERN.matches(it.identifier) } ?: return null
            val title = text(event, AgirFormats.TAG_TITLE)?.takeIf { it.length <= AgirFormats.MAX_TITLE } ?: return null
            val summary = text(event, AgirFormats.TAG_SUMMARY) ?: return null
            val skills = readSkills(event.tagValues(SolidarityFormats.TAG_SKILL)).takeIf { it.isNotEmpty() } ?: return null
            val territory = text(event, AgirFormats.TAG_TERRITORY) ?: return null
            val publishedAt = event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val remote = event.firstTagValue(SolidarityFormats.TAG_REMOTE)?.trim() == SolidarityFormats.REMOTE_YES
            val location = text(event, AgirFormats.TAG_LOCATION)
            if (!remote && location == null) return null
            return VolunteerMission(
                event = event,
                address = address,
                title = title,
                summary = summary,
                skills = skills,
                territory = territory,
                remote = remote,
                location = location,
                commitment = text(event, SolidarityFormats.TAG_COMMITMENT),
                people = event.firstTagValue(SolidarityFormats.TAG_PEOPLE)?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                deadline = event.firstTagValue(AgirFormats.TAG_DEADLINE)?.trim()?.toLongOrNull()?.takeIf { it > 0 },
                publishedAt = publishedAt,
                status = readStatus(event.firstTagValue(SolidarityFormats.TAG_MISSION_STATUS)),
                origin = event.firstTagValue(TAG_ORIGIN)?.let(ContentOrigin::fromTag),
            )
        }

        /** Prepares the event of a mission; an editorial key then signs it. A prepared mission always reads back whole. */
        fun build(
            editor: PublicKey,
            slug: String,
            title: String,
            summary: String,
            skills: List<String>,
            territory: String,
            publishedAt: Long,
            updatedAt: Long,
            description: String,
            remote: Boolean = false,
            location: String? = null,
            commitment: String? = null,
            people: Int? = null,
            deadline: Long? = null,
            status: String = SolidarityFormats.MISSION_OPEN,
            origin: ContentOrigin? = null,
        ): UnsignedEvent {
            require(SLUG_PATTERN.matches(slug)) { "slug: 1 to 64 characters among a-z, 0-9, '.', '_', '~', '-'" }
            require(title.isNotBlank() && title.trim().length <= AgirFormats.MAX_TITLE) { "title: 1 to ${AgirFormats.MAX_TITLE} characters" }
            require(summary.isNotBlank()) { "summary is required" }
            require(skills.isNotEmpty() && skills.all { it in SolidarityFormats.SKILLS }) { "skills: at least one of ${SolidarityFormats.SKILLS}" }
            require(territory.isNotBlank()) { "territory is required" }
            require(description.length <= AgirFormats.MAX_TEXT) { "description: at most ${AgirFormats.MAX_TEXT} characters" }
            require(remote || !location.isNullOrBlank()) { "a mission is remote or has a location" }
            require(commitment == null || commitment.trim().length <= SolidarityFormats.MAX_SHORT_TEXT) { "commitment: at most ${SolidarityFormats.MAX_SHORT_TEXT} characters" }
            require(people == null || people > 0) { "people must be positive" }
            require(deadline == null || deadline > 0) { "deadline must be positive" }
            require(status in SolidarityFormats.MISSION_STATUSES) { "status must be one of ${SolidarityFormats.MISSION_STATUSES}" }
            val tags = buildList {
                add(listOf("d", slug))
                add(listOf(AgirFormats.TAG_TITLE, title.trim()))
                add(listOf(AgirFormats.TAG_SUMMARY, summary.trim()))
                add(listOf(AgirFormats.TAG_PUBLISHED_AT, publishedAt.toString()))
                add(listOf(AgirFormats.TAG_TYPE, SolidarityFormats.TYPE_MISSION))
                SolidarityFormats.SKILLS.filter { it in skills }.forEach { add(listOf(SolidarityFormats.TAG_SKILL, it)) }
                add(listOf(AgirFormats.TAG_TERRITORY, territory.trim()))
                if (remote) add(listOf(SolidarityFormats.TAG_REMOTE, SolidarityFormats.REMOTE_YES))
                location?.takeIf { it.isNotBlank() }?.let { add(listOf(AgirFormats.TAG_LOCATION, it.trim())) }
                commitment?.takeIf { it.isNotBlank() }?.let { add(listOf(SolidarityFormats.TAG_COMMITMENT, it.trim())) }
                people?.let { add(listOf(SolidarityFormats.TAG_PEOPLE, it.toString())) }
                deadline?.let { add(listOf(AgirFormats.TAG_DEADLINE, it.toString())) }
                if (status != SolidarityFormats.MISSION_OPEN) add(listOf(SolidarityFormats.TAG_MISSION_STATUS, status))
                origin?.let { add(listOf(TAG_ORIGIN, it.tagValue)) }
            }
            return UnsignedEvent.create(editor, KIND, updatedAt, tags, description)
        }

        /** The status a `ppf-mission-status` value is read as: absent means open, anything unknown closed. */
        fun readStatus(value: String?): String = when (val trimmed = value?.trim()) {
            null, SolidarityFormats.MISSION_OPEN -> SolidarityFormats.MISSION_OPEN
            else -> if (trimmed in SolidarityFormats.MISSION_STATUSES) trimmed else SolidarityFormats.MISSION_CLOSED
        }

        /** The skills of the tags, in the order of [SolidarityFormats.SKILLS], each once; an unknown one counts as `autre`. */
        fun readSkills(values: List<String>): List<String> {
            val read = values.map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it in SolidarityFormats.SKILLS) it else SolidarityFormats.SKILL_OTHER }
                .toSet()
            return SolidarityFormats.SKILLS.filter { it in read }
        }

        private fun text(event: SignedEvent, tag: String): String? = event.firstTagValue(tag)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
