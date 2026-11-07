package org.politisationpopulairefrancaise.core.signing

/**
 * Event kinds (NIP-01). The number says how an event behaves: regular events are kept as they
 * are; replaceable ones are superseded by a newer event of the same author and kind; addressable
 * ones are superseded per `d` tag, which makes `kind:pubkey:d` a stable content identifier.
 *
 * PPF content types will get their own kinds in the addressable range, documented in
 * docs/FORMATS.md, so that every published content has a stable identifier.
 */
object EventKinds {
    const val METADATA: Int = 0
    const val TEXT_NOTE: Int = 1
    const val DELETION_REQUEST: Int = 5
    const val LONG_FORM_ARTICLE: Int = 30023

    // Encrypted messaging (NIP-17 and NIP-59, decision D-027).
    /** Seal: a rumor encrypted for its recipient and signed by its real author; no tag. */
    const val SEAL: Int = 13
    /** Chat message rumor: never signed, only ever seen inside a seal. */
    const val CHAT_MESSAGE: Int = 14
    /** Gift wrap: a seal encrypted again by a throwaway key, the only event the server sees. */
    const val GIFT_WRAP: Int = 1059

    // Rooms of collectives (NIP-29, decisions D-027 and D-028) and user settings (NIP-78).
    /** A message in a room: plain text, exactly one `h` tag naming the room. */
    const val GROUP_CHAT: Int = 9
    /** The description of a room, addressable by its `d` tag (the room id). */
    const val GROUP_METADATA: Int = 39000
    /** Application-specific data of a user, addressable by `d` (for example `ppf:mailbox`, the retention of private messages). */
    const val APP_DATA: Int = 30078

    // Platform signature (contract docs/integration/service-signature.md, decision D-129).
    /** A label (NIP-32): the status the team gives a demand. */
    const val LABEL: Int = 1985
    /** An OpenTimestamps attestation (NIP-03): the timestamp proof of an archive. */
    const val OPENTIMESTAMPS: Int = 1040
    /** The certificate of a working key of the platform, signed by the root; `d` = the working key. Used by no NIP. */
    const val PLATFORM_KEY_CERTIFICATE: Int = 37240
    /** The list of the team members whose approvals the signing service accepts, signed by the root. Used by no NIP. */
    const val PLATFORM_TEAM_LIST: Int = 37241

    fun isRegular(kind: Int): Boolean = kind in 1000..9999 || kind in 4..44 || kind == 1 || kind == 2
    fun isReplaceable(kind: Int): Boolean = kind in 10000..19999 || kind == 0 || kind == 3
    fun isEphemeral(kind: Int): Boolean = kind in 20000..29999
    fun isAddressable(kind: Int): Boolean = kind in 30000..39999
}

/**
 * Stable identifier of an addressable content: `kind:pubkey:identifier` (the `a` tag of NIP-01).
 * It survives every edit of the content, unlike the event id which changes with each version.
 */
data class ContentAddress(val kind: Int, val pubkey: String, val identifier: String) {
    init {
        require(EventKinds.isAddressable(kind)) { "Only addressable kinds (30000-39999) have a stable address" }
        require(isLowercaseHex(pubkey, 64)) { "pubkey must be 64 lowercase hexadecimal characters" }
    }

    override fun toString(): String = "$kind:$pubkey:$identifier"

    companion object {
        /** Parses `kind:pubkey:identifier`; the identifier itself may contain colons. */
        fun parse(value: String): ContentAddress {
            val parts = value.split(':', limit = 3)
            require(parts.size == 3) { "A content address has the form kind:pubkey:identifier" }
            val kind = parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid kind in content address")
            return ContentAddress(kind, parts[1], parts[2])
        }

        /** The address of an addressable event, from its first `d` tag; null for other events. */
        fun of(event: SignedEvent): ContentAddress? {
            if (!EventKinds.isAddressable(event.kind)) return null
            val identifier = event.firstTagValue("d") ?: return null
            return ContentAddress(event.kind, event.pubkey, identifier)
        }
    }
}
