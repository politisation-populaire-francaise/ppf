package org.politisationpopulairefrancaise.server.groups

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Event kinds and tags of relay-based groups (NIP-29), the protocol of the collectives' rooms
 * chosen in D-027. Current version: public open groups, which any account may create (D-028) by
 * publishing a description that names a territory and accepts the charter of values.
 *
 * Extension points, in the order they are expected:
 * - membership: moderation events `put-user` (9000) and `remove-user` (9001), join and leave
 *   requests (9021, 9022), then `restricted` and `closed` groups; membership is derived from the
 *   latest 9000/9001 per key and is never published as a list (39002 stays refused);
 * - moderation: `edit-metadata` (9002), `delete-event` (9005), `delete-group` (9008), `create-invite`
 *   (9009), issued by the managing key or by admins (39001);
 * - timeline references (`previous` tag) once clients send them;
 * - `private` and `hidden` groups, which need reader authentication (NIP-42) before the relay can
 *   restrict who reads them; until then descriptions carrying these flags are refused;
 * - signing of 39000 by the relay's own key through the separate signing service, instead of the
 *   creator's key used for now;
 * - duplicate detection by meaning (self-hosted model) and a posteriori moderation of the groups.
 */
object GroupKinds {
    /** A chat message in a group; carries the group id in an `h` tag. */
    const val CHAT_MESSAGE: Int = 9

    /** Moderation events (put-user, remove-user, edit-metadata, delete-event...), not accepted yet. */
    val MODERATION: IntRange = 9000..9020
    const val JOIN_REQUEST: Int = 9021
    const val LEAVE_REQUEST: Int = 9022

    /** Group metadata, addressable by the group id in its `d` tag. */
    const val METADATA: Int = 39000
    const val ADMINS: Int = 39001

    /** Member list: never accepted, never stored, never published (D-027). */
    const val MEMBERS: Int = 39002
    const val ROLES: Int = 39003

    /** Every kind describing a group on behalf of the relay (39000 to 39005). */
    val RELAY_METADATA: IntRange = 39000..39005

    /** The tag naming the group of an event sent to it. */
    const val GROUP_TAG: String = "h"

    /** NIP-29 metadata tags. */
    const val NAME_TAG: String = "name"
    const val ABOUT_TAG: String = "about"
    const val PICTURE_TAG: String = "picture"

    /** PPF: the territory the group lives in (a town, a department, "France"); required (D-028). */
    const val TERRITORY_TAG: String = "ppf-territory"

    /** PPF: the version of the charter of values the creator accepted; required (D-028). */
    const val CHARTER_TAG: String = "ppf-charter"

    /** The only charter version accepted today. A new version will be accepted alongside, then alone. */
    const val CHARTER_VERSION: String = "1.0"

    /** Longest name and territory of a description, in characters (the app allows 80). */
    const val MAX_NAME_LENGTH: Int = 100
    const val MAX_TERRITORY_LENGTH: Int = 100

    /**
     * Largest description, once serialized (JSON of the signed event): enough for an `about` of a
     * few thousand characters, small enough that the directory stays light to list.
     */
    const val MAX_DESCRIPTION_BYTES: Int = 8 * 1024

    /** Metadata flags the relay cannot honour yet (they restrict reading to members). */
    val UNSUPPORTED_FLAGS: Set<String> = setOf("private", "hidden")

    /**
     * Group ids are short, lowercase, URL-safe strings (the spec leaves the format to the relay).
     * Lowercase only, so that two ids cannot differ by case alone and impersonate each other.
     */
    private val ID_PATTERN = Regex("[a-z0-9._~-]{1,64}")

    fun isValidId(id: String?): Boolean = id != null && ID_PATTERN.matches(id)

    fun isGroupMetadata(kind: Int): Boolean = kind in RELAY_METADATA

    fun isMembershipOrModeration(kind: Int): Boolean = kind in MODERATION || kind == JOIN_REQUEST || kind == LEAVE_REQUEST
}

/**
 * What the relay knows about a group: its metadata event (kind 39000) read into fields. The
 * flags follow NIP-29: a group is public unless its metadata carries a `private` tag (such
 * descriptions are refused for now, so every stored group is public); it is open to messages
 * from anyone unless it carries `closed` or `restricted` (both need a membership that this
 * version does not track, so such groups refuse messages for now).
 *
 * [territory] and [charter] are the PPF additions of D-028; they are null only for descriptions
 * stored before they became mandatory.
 */
@Serializable
data class Group(
    val id: String,
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    /** The territory as written by the creator (`ppf-territory`). */
    val territory: String? = null,
    /** The accepted charter version (`ppf-charter`), or null when the description does not carry the accepted one. */
    val charter: String? = null,
    @SerialName("public") val isPublic: Boolean,
    @SerialName("open") val isOpen: Boolean,
    /** The signed metadata, so that clients can check who published it. */
    val event: SignedEvent,
) {
    /** The name as compared for duplicates and searched (see [GroupNames]); empty without a usable name. */
    val normalizedName: String get() = GroupNames.normalize(name.orEmpty())

    /** The territory as compared for duplicates and searched; empty without a territory. */
    val normalizedTerritory: String get() = GroupNames.normalize(territory.orEmpty())

    /** The description as searched; empty without one. */
    val normalizedAbout: String get() = GroupNames.normalize(about.orEmpty())

    companion object {
        /** Reads a metadata event; null if it is not a well-formed group description (exactly one valid `d`). */
        fun from(event: SignedEvent): Group? {
            if (event.kind != GroupKinds.METADATA) return null
            val ids = event.tagValues("d")
            val id = ids.singleOrNull()?.takeIf(GroupKinds::isValidId) ?: return null
            val flags = event.tags.mapNotNull { it.firstOrNull() }.toSet()
            return Group(
                id = id,
                name = event.firstTagValue(GroupKinds.NAME_TAG),
                about = event.firstTagValue(GroupKinds.ABOUT_TAG),
                picture = event.firstTagValue(GroupKinds.PICTURE_TAG),
                territory = event.firstTagValue(GroupKinds.TERRITORY_TAG),
                charter = event.firstTagValue(GroupKinds.CHARTER_TAG)?.takeIf { it == GroupKinds.CHARTER_VERSION },
                isPublic = "private" !in flags,
                isOpen = "closed" !in flags && "restricted" !in flags,
                event = event,
            )
        }
    }
}
