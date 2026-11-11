package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * The takeover of a public room left without administrator, signed by the platform (use
 * [SigningUse.GROUPS_TAKEOVER], decision D-144, design docs/integration/salons-reprise.md): a NIP-29
 * put-user (kind 9000) that names one administrator, with the public reason as content.
 *
 * The form is exactly the one of [RoomModeration.putUser], the reason aside: one `h` tag (the room id,
 * [Group.isValidId]), one `p` tag `["p", <key>, "admin"]` (the key in 64 lowercase hexadecimal
 * characters), no other tag; the content is the reason shown in the journal of the room, not blank,
 * at most [MAX_REASON_LENGTH] characters. The applications check it ([SigningUse.matches]) and so does
 * the signing service, with the refusal codes of the contract ([breach]).
 */
object GroupTakeover {
    /** The only role a takeover gives. */
    val ROLE: String = RoomRole.ADMIN.id

    /** Longest reason, in characters (shorter than the reason of an exclusion: it is read by everyone). */
    const val MAX_REASON_LENGTH: Int = 500

    /** A rule of the form that an event breaks: a refusal code of [SigningRefusals] and the tag concerned. */
    data class Breach(val code: String, val tag: String? = null)

    /**
     * The first rule of the form that [kind], [tags] and [content] break, null when they have the form of a
     * takeover. The order is fixed: the kind, an unknown tag, the room, the named key and its role, the reason.
     */
    fun breach(kind: Int, tags: List<List<String>>, content: String): Breach? {
        if (kind != RoomModeration.PUT_USER) return Breach(SigningRefusals.KIND_NOT_ALLOWED)
        val allowed = setOf(RoomModeration.TAG_GROUP, RoomModeration.TAG_KEY)
        tags.firstOrNull { it.isEmpty() || it[0] !in allowed }?.let { return Breach(SigningRefusals.TAG_NOT_ALLOWED, it.firstOrNull()) }
        val groups = tags.filter { it[0] == RoomModeration.TAG_GROUP }
        if (groups.isEmpty()) return Breach(SigningRefusals.MISSING_TAG, RoomModeration.TAG_GROUP)
        if (groups.size != 1 || groups[0].size != 2 || !Group.isValidId(groups[0][1])) return Breach(SigningRefusals.BAD_TAG, RoomModeration.TAG_GROUP)
        val keys = tags.filter { it[0] == RoomModeration.TAG_KEY }
        if (keys.isEmpty()) return Breach(SigningRefusals.MISSING_TAG, RoomModeration.TAG_KEY)
        if (keys.size != 1 || keys[0].size != 3 || !isHexKey(keys[0][1]) || keys[0][2] != ROLE) return Breach(SigningRefusals.BAD_TAG, RoomModeration.TAG_KEY)
        if (content.isBlank() || content.length > MAX_REASON_LENGTH) return Breach(SigningRefusals.BAD_CONTENT)
        return null
    }

    /** The key a takeover names administrator, null when [tags] do not name exactly one. */
    fun target(tags: List<List<String>>): String? =
        tags.filter { it.isNotEmpty() && it[0] == RoomModeration.TAG_KEY }.singleOrNull()?.getOrNull(1)

    /**
     * The unsigned takeover of the room [groupId] by [target], with the public [reason] (trimmed), to be
     * signed by the working key [platformKey] of [SigningUse.GROUPS_TAKEOVER] (`PlatformSigner.currentKey`).
     */
    fun unsigned(platformKey: String, groupId: String, target: String, reason: String, createdAt: Long): UnsignedEvent {
        require(isHexKey(platformKey) && isHexKey(target)) { "A key is 64 lowercase hex characters" }
        require(target != platformKey) { "The key of the platform never receives a role" }
        val event = UnsignedEvent(
            platformKey, createdAt, RoomModeration.PUT_USER,
            listOf(listOf(RoomModeration.TAG_GROUP, groupId), listOf(RoomModeration.TAG_KEY, target, ROLE)),
            reason.trim(),
        )
        breach(event.kind, event.tags, event.content)?.let { throw IllegalArgumentException("Not the form of a takeover: ${it.code} ${it.tag.orEmpty()}") }
        return event
    }

    private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
