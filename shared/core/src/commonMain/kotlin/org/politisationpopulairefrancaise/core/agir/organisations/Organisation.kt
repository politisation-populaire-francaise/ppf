package org.politisationpopulairefrancaise.core.agir.organisations

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
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
 * An entry of the directory of organisations (M3, AG-03, AG-17): a kind 30023 event whose
 * `ppf-type` is `organisation`, published by a key of the editorial team (contract of the Agir
 * space, docs/integration/agir-contrat.md, section 4). The team checks the organisation before
 * publishing the entry; [verifiedOn] is the date of that check.
 *
 * Required tags: `d`, `title` (the name), `summary`, `ppf-org-type`, `ppf-territory`,
 * `ppf-verified` (`AAAA-MM-JJ`) and `published_at`. Optional: `p` (the organisation's own key,
 * when it has an account), `r` (its web sites), `ppf-contact-phone`, `ppf-contact-email`,
 * `ppf-contact-address` (public contacts of the organisation, never those of a person), `g`, and
 * `ppf-origin` (who wrote the entry, as for every editorial content), and `ppf-org-status`
 * (contract, version 2: `verifiee` by default, `revoquee` when the team withdrew the
 * verification). The content is the presentation, in the simple Markdown of the practical guides.
 *
 * The device shows an entry only when it is signed by a key the application trusts
 * ([EditorialTrust]); [from] enforces it. Contacts are cleaned on reading: only `http` and `https`
 * sites, a plain e-mail address and a telephone number made of digits are kept, so that a link
 * built from them can only open a web page, a mail program or the dialler.
 */
data class Organisation(
    val event: SignedEvent,
    val address: ContentAddress,
    /** The name, as published (with the [EXEMPLE] marker for examples). */
    val name: String,
    val summary: String,
    /** One of [AgirFormats.ORG_TYPES]; an unknown value is read as `autre`. */
    val type: String,
    /** A commune, a department or `France`. */
    val territory: String,
    /** When the editorial team checked the organisation. */
    val verifiedOn: CivilDate,
    /** First publication of the entry, seconds since 1970. */
    val publishedAt: Long,
    /** The organisation's own public key (lowercase hex) when it has an account; its verified key. */
    val accountKey: String?,
    /** Its web sites, `http` or `https` only. */
    val websites: List<String>,
    /** Its public telephone number, as written ("04 65 71 20 14"). */
    val phone: String?,
    /** Its public e-mail address. */
    val email: String?,
    /** Its public postal address, in clear. */
    val postalAddress: String?,
    /** A geohash placed by the team itself, never computed by a third-party service. */
    val geohash: String?,
    /** Who wrote the entry; null when the tag is absent. */
    val origin: ContentOrigin?,
    /**
     * [AgirFormats.ORG_STATUS_VERIFIED] or [AgirFormats.ORG_STATUS_REVOKED]. An unknown value (a
     * newer version of the format) is read as revoked: it never extends the trust.
     */
    val status: String = AgirFormats.ORG_STATUS_VERIFIED,
) {
    /** The team withdrew the verification: the entry stays readable, its key is no longer verified. */
    val revoked: Boolean get() = status != AgirFormats.ORG_STATUS_VERIFIED

    /** The day (in Paris) of the version that withdrew the verification, or null when it was not withdrawn. */
    val revokedOn: CivilDate? get() = if (revoked) ParisTime.moment(updatedAt).date else null

    /** The identifier of the entry (its `d` tag), stable across its versions. */
    val slug: String get() = address.identifier

    /** The editorial key that published the entry. */
    val editor: String get() = event.pubkey

    val updatedAt: Long get() = event.createdAt

    /** The presentation, in simple Markdown. */
    val presentation: String get() = event.content

    /** Example entries carry the marker required by the cahier des charges in their name. */
    val example: Boolean get() = name.trimStart().startsWith(EXAMPLE_MARKER)

    /** The name without the example marker. */
    val displayName: String get() = name.trimStart().removePrefix(EXAMPLE_MARKER).trim()

    /** Acts everywhere in France (territory `France`). */
    val isNational: Boolean get() = FrenchText.fold(territory) == NATIONAL

    /** The `tel:` link of [phone]: its digits, with the leading `+` of an international number. */
    val phoneUri: String? get() = phone?.let(::telUri)

    /** The `mailto:` link of [email]. */
    val emailUri: String? get() = email?.let { "mailto:$it" }

    companion object {
        const val KIND: Int = EventKinds.LONG_FORM_ARTICLE
        const val TAG_ORIGIN: String = "ppf-origin"
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        /** The folded territory of the organisations that act everywhere in France. */
        const val NATIONAL: String = "france"

        /** The type an unknown `ppf-org-type` is read as, so that a newer value never hides an entry. */
        const val OTHER_TYPE: String = "autre"

        /** At most this many web sites are kept per entry. */
        const val MAX_WEBSITES: Int = 5

        private val SLUG_PATTERN = Regex("[a-z0-9._~-]{1,64}")
        private val DATE_PATTERN = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
        private val GEOHASH_PATTERN = Regex("[0-9bcdefghjkmnpqrstuvwxyz]{1,9}")
        private val HEX_KEY_PATTERN = Regex("[0-9a-f]{64}")
        private val PHONE_PATTERN = Regex("\\+?[0-9 .()-]{4,30}")

        /**
         * Reads a directory entry that the application may show: null unless the event is signed by
         * a key [trust] holds, carries a valid signature ([checkSignature]; the local database
         * stores only events checked on arrival, so its readers may skip it) and is a well-formed
         * entry ([parse]).
         */
        fun from(event: SignedEvent, trust: EditorialTrust, checkSignature: Boolean = true): Organisation? {
            if (!trust.isTrusted(event.pubkey)) return null
            if (checkSignature && !event.verify()) return null
            return parse(event)
        }

        /**
         * Reads the form of an entry, without any check of its author or signature: never show
         * the result as such, use [from]. Null when a required tag is missing or invalid.
         */
        fun parse(event: SignedEvent): Organisation? {
            if (event.kind != KIND) return null
            if (event.firstTagValue(AgirFormats.TAG_TYPE) != AgirFormats.TYPE_ORGANISATION) return null
            val address = ContentAddress.of(event)?.takeIf { SLUG_PATTERN.matches(it.identifier) } ?: return null
            val name = event.firstTagValue(AgirFormats.TAG_TITLE)?.trim()?.takeIf { it.isNotEmpty() && it.length <= AgirFormats.MAX_TITLE } ?: return null
            val summary = event.firstTagValue(AgirFormats.TAG_SUMMARY)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val rawType = event.firstTagValue(AgirFormats.TAG_ORG_TYPE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val territory = event.firstTagValue(AgirFormats.TAG_TERRITORY)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val verifiedOn = event.firstTagValue(AgirFormats.TAG_VERIFIED)?.let(::parseDate) ?: return null
            val publishedAt = event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return Organisation(
                event = event,
                address = address,
                name = name,
                summary = summary,
                type = rawType.takeIf { it in AgirFormats.ORG_TYPES } ?: OTHER_TYPE,
                territory = territory,
                verifiedOn = verifiedOn,
                publishedAt = publishedAt,
                accountKey = event.tagValues(AgirFormats.TAG_PUBKEY).firstOrNull(::isAccountKey),
                websites = event.tagValues(AgirFormats.TAG_LINK).map { it.trim() }.filter(::isWebsite).distinct().take(MAX_WEBSITES),
                phone = event.firstTagValue(AgirFormats.TAG_CONTACT_PHONE)?.trim()?.takeIf(::isPhone),
                email = event.firstTagValue(AgirFormats.TAG_CONTACT_EMAIL)?.trim()?.takeIf(::isEmail),
                postalAddress = event.firstTagValue(AgirFormats.TAG_CONTACT_ADDRESS)?.trim()?.takeIf { it.isNotEmpty() },
                geohash = event.firstTagValue(AgirFormats.TAG_GEOHASH)?.trim()?.takeIf { GEOHASH_PATTERN.matches(it) },
                origin = event.firstTagValue(TAG_ORIGIN)?.let(ContentOrigin::fromTag),
                status = readStatus(event.firstTagValue(AgirFormats.TAG_ORG_STATUS)),
            )
        }

        /**
         * Prepares the event of a directory entry; an editorial key then signs it. Every value is
         * checked as [parse] would read it, so that a prepared entry always reads back whole.
         */
        fun build(
            editor: PublicKey,
            slug: String,
            name: String,
            summary: String,
            type: String,
            territory: String,
            verifiedOn: String,
            publishedAt: Long,
            updatedAt: Long,
            presentation: String,
            accountKey: String? = null,
            websites: List<String> = emptyList(),
            phone: String? = null,
            email: String? = null,
            postalAddress: String? = null,
            geohash: String? = null,
            origin: ContentOrigin? = null,
            status: String = AgirFormats.ORG_STATUS_VERIFIED,
        ): UnsignedEvent {
            require(SLUG_PATTERN.matches(slug)) { "slug: 1 to 64 characters among a-z, 0-9, '.', '_', '~', '-'" }
            require(name.isNotBlank() && name.trim().length <= AgirFormats.MAX_TITLE) { "name: 1 to ${AgirFormats.MAX_TITLE} characters" }
            require(summary.isNotBlank()) { "summary is required" }
            require(type in AgirFormats.ORG_TYPES) { "type must be one of ${AgirFormats.ORG_TYPES}" }
            require(territory.isNotBlank()) { "territory is required" }
            require(parseDate(verifiedOn) != null) { "verifiedOn must be a date AAAA-MM-JJ" }
            require(presentation.length <= AgirFormats.MAX_TEXT) { "presentation: at most ${AgirFormats.MAX_TEXT} characters" }
            require(accountKey == null || isAccountKey(accountKey)) { "accountKey must be 64 lowercase hexadecimal characters" }
            require(websites.all(::isWebsite) && websites.size <= MAX_WEBSITES) { "websites: at most $MAX_WEBSITES http or https addresses" }
            require(phone == null || isPhone(phone)) { "phone: digits, spaces, dots, dashes and a leading +" }
            require(email == null || isEmail(email)) { "email: a plain address" }
            require(geohash == null || GEOHASH_PATTERN.matches(geohash)) { "geohash: 1 to 9 geohash characters" }
            require(status in AgirFormats.ORG_STATUSES) { "status must be one of ${AgirFormats.ORG_STATUSES}" }
            val tags = buildList {
                add(listOf("d", slug))
                add(listOf(AgirFormats.TAG_TITLE, name.trim()))
                add(listOf(AgirFormats.TAG_SUMMARY, summary.trim()))
                add(listOf(AgirFormats.TAG_PUBLISHED_AT, publishedAt.toString()))
                add(listOf(AgirFormats.TAG_TYPE, AgirFormats.TYPE_ORGANISATION))
                add(listOf(AgirFormats.TAG_ORG_TYPE, type))
                add(listOf(AgirFormats.TAG_TERRITORY, territory.trim()))
                add(listOf(AgirFormats.TAG_VERIFIED, verifiedOn))
                // The default status stays implicit, so that entries written before version 2 read the same.
                if (status != AgirFormats.ORG_STATUS_VERIFIED) add(listOf(AgirFormats.TAG_ORG_STATUS, status))
                origin?.let { add(listOf(TAG_ORIGIN, it.tagValue)) }
                accountKey?.let { add(listOf(AgirFormats.TAG_PUBKEY, it)) }
                websites.forEach { add(listOf(AgirFormats.TAG_LINK, it)) }
                phone?.let { add(listOf(AgirFormats.TAG_CONTACT_PHONE, it.trim())) }
                email?.let { add(listOf(AgirFormats.TAG_CONTACT_EMAIL, it.trim())) }
                postalAddress?.takeIf { it.isNotBlank() }?.let { add(listOf(AgirFormats.TAG_CONTACT_ADDRESS, it.trim())) }
                geohash?.let { add(listOf(AgirFormats.TAG_GEOHASH, it)) }
            }
            return UnsignedEvent.create(editor, KIND, updatedAt, tags, presentation)
        }

        /** The status a `ppf-org-status` value is read as: absent means verified, anything unknown revoked. */
        fun readStatus(value: String?): String = when (val trimmed = value?.trim()) {
            null, AgirFormats.ORG_STATUS_VERIFIED -> AgirFormats.ORG_STATUS_VERIFIED
            else -> if (trimmed in AgirFormats.ORG_STATUSES) trimmed else AgirFormats.ORG_STATUS_REVOKED
        }

        /** A date `AAAA-MM-JJ` that exists in the calendar, or null. */
        fun parseDate(text: String): CivilDate? {
            val match = DATE_PATTERN.matchEntire(text.trim()) ?: return null
            val (year, month, day) = match.destructured.toList().map(String::toInt)
            if (year < 1900 || month !in 1..12) return null
            if (day !in 1..daysInMonth(year, month)) return null
            return CivilDate(year, month, day)
        }

        /** A public key as the `p` tag carries it: 64 lowercase hexadecimal characters. */
        fun isAccountKey(value: String): Boolean = HEX_KEY_PATTERN.matches(value)

        /** An `http` or `https` address with a host and nothing that could break out of a link. */
        fun isWebsite(value: String): Boolean {
            val lower = value.lowercase()
            val rest = when {
                lower.startsWith("https://") -> value.substring(8)
                lower.startsWith("http://") -> value.substring(7)
                else -> return false
            }
            if (value.length > 2048 || value.any { it.isWhitespace() || it.code < 0x20 || it.code >= 0x7f || it == '"' || it == '<' || it == '>' || it == '\\' }) return false
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
            // No "user@" part: it would let a link show one host and open another.
            if ('@' in authority) return false
            val host = authority.substringBefore(':')
            val port = authority.substringAfter(':', "")
            if (port.isNotEmpty() && (port.length > 5 || !port.all(Char::isDigit))) return false
            // ASCII only: an international host is written in its "xn--" form, which cannot imitate another name.
            return host.isNotEmpty() && host.length <= 253 && '.' in host && !host.startsWith('.') && !host.endsWith('.') &&
                host.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' }
        }

        /** The host of a web site, to show it plainly before the user leaves the application. */
        fun hostOf(website: String): String =
            website.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }.substringBefore(':').lowercase()

        /** A telephone number: digits with spaces, dots, dashes or brackets, a leading `+`, 4 to 20 digits. */
        fun isPhone(value: String): Boolean {
            val trimmed = value.trim()
            if (!PHONE_PATTERN.matches(trimmed) || '+' in trimmed.drop(1)) return false
            return trimmed.count { it.isDigit() } in 4..20
        }

        /** A plain e-mail address: one `@`, a dotted domain, nothing a `mailto:` link would read as a header. */
        fun isEmail(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.length > 254 || trimmed.count { it == '@' } != 1) return false
            val local = trimmed.substringBefore('@')
            val domain = trimmed.substringAfter('@')
            val localOk = local.isNotEmpty() && local.length <= 64 && local.all { it.isLetterOrDigit() || it in ".-_+" } && !local.startsWith('.') && !local.endsWith('.')
            val labels = domain.split('.')
            val domainOk = labels.size >= 2 && labels.all { label -> label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } && !label.startsWith('-') && !label.endsWith('-') }
            return localOk && domainOk && trimmed.all { it.code < 0x7f }
        }

        private fun telUri(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return "tel:" + (if (phone.trimStart().startsWith('+')) "+" else "") + digits
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}
