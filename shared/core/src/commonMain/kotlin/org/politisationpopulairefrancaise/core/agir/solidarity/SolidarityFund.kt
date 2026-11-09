package org.politisationpopulairefrancaise.core.agir.solidarity

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
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
 * An entry of the directory of solidarity funds (M35, AG-10): a strike fund, a legal solidarity
 * fund or another one, published by a key of the editorial team as a kind 30023 event whose
 * `ppf-type` is `caisse` (docs/integration/agir-solidarite.md, section 2.1).
 *
 * PPF never holds nor passes on any money (point of attention 12): an entry only points to the
 * fund's own donation page ([donationLink]), which the interface opens after the warning "Vous
 * quittez PPF". The team checks the fund before publishing it ([verifiedOn]) and states, in the
 * required `ppf-no-party` tag, that it serves neither a candidate nor a party.
 *
 * Required tags: `d`, `title`, `summary`, `published_at`, `ppf-fund-type`, `ppf-territory`,
 * `ppf-beneficiary`, `ppf-verified`, `ppf-no-party` = `oui`, and the manager: its name
 * (`ppf-manager`), the address of its entry in the directory of organisations (`a`), or both.
 * Optional: `r` (the donation page, `https` with an ASCII host only), `ppf-sector`, `ppf-strike`,
 * `ppf-fund-status`, `ppf-origin`, `g`. The content is the presentation, in simple Markdown.
 *
 * The device shows an entry only when it is signed by a key the application trusts; [from]
 * enforces it, like the directory of organisations.
 */
data class SolidarityFund(
    val event: SignedEvent,
    val address: ContentAddress,
    /** The name, as published (with the [EXEMPLE] marker for examples). */
    val name: String,
    val summary: String,
    /** One of [SolidarityFormats.FUND_TYPES]; an unknown value is read as `autre`. */
    val type: String,
    /** A commune, a department or `France`. */
    val territory: String,
    /** Who the fund serves, in words. */
    val beneficiary: String,
    /** When the editorial team checked the fund and its donation page. */
    val verifiedOn: CivilDate,
    /** First publication of the entry, seconds since 1970. */
    val publishedAt: Long,
    /** The name of whoever manages the fund, in words; null when only [managerEntry] names it. */
    val managerName: String?,
    /** The address of the manager's entry in the directory of organisations, when it has one. */
    val managerEntry: ContentAddress?,
    /** The fund's own donation page: `https`, ASCII host; null when the entry has no valid one. */
    val donationLink: String?,
    val sector: String?,
    val strike: String?,
    /** One of [SolidarityFormats.FUND_STATUSES]; an unknown value is read as withdrawn. */
    val status: String,
    /** Who wrote the entry; null when the tag is absent. */
    val origin: ContentOrigin?,
    /** A geohash placed by the team itself, never computed by a third-party service. */
    val geohash: String?,
) {
    /** The identifier of the entry (its `d` tag), stable across its versions. */
    val slug: String get() = address.identifier

    /** The editorial key that published the entry. */
    val editor: String get() = event.pubkey

    val updatedAt: Long get() = event.createdAt

    /** The presentation, in simple Markdown. */
    val presentation: String get() = event.content

    /** Example entries carry the marker required by the cahier des charges in their name. */
    val example: Boolean get() = name.trimStart().startsWith(Organisation.EXAMPLE_MARKER)

    /** The name without the example marker. */
    val displayName: String get() = name.trimStart().removePrefix(Organisation.EXAMPLE_MARKER).trim()

    /** Serves everywhere in France (territory `France`). */
    val isNational: Boolean get() = FrenchText.fold(territory) == Organisation.NATIONAL

    /** The fund still takes donations. */
    val isOpen: Boolean get() = status == SolidarityFormats.FUND_OPEN

    /** The fund no longer takes donations (the strike is over, for example). */
    val isClosed: Boolean get() = status == SolidarityFormats.FUND_CLOSED

    /** The team withdrew the entry: it stays readable, never with a donation link. */
    val withdrawn: Boolean get() = status == SolidarityFormats.FUND_WITHDRAWN

    /** The day (in Paris) of the version that withdrew the entry, or null when it was not withdrawn. */
    val withdrawnOn: CivilDate? get() = if (withdrawn) ParisTime.moment(updatedAt).date else null

    /** The link the user may open: only for an open fund, and only after "Vous quittez PPF". */
    val openableDonationLink: String? get() = donationLink?.takeIf { isOpen }

    /** The host of the donation page, to name it plainly before the user leaves the application. */
    val donationHost: String? get() = donationLink?.let(Organisation::hostOf)

    /**
     * Whoever manages the fund, found among the entries of the directory of organisations the device
     * holds ([directory], trusted entries only): by the exact address of its entry, else by the
     * identifier of that entry (after a rotation of the editorial key, for example; the most recent
     * one wins). Without an entry, the name written in the fund is all there is.
     */
    fun managerIn(directory: List<Organisation>): FundManager {
        val wanted = managerEntry
        val entry = if (wanted == null) {
            null
        } else {
            directory.firstOrNull { it.address == wanted }
                ?: directory.filter { it.slug == wanted.identifier }
                    .maxWithOrNull(compareBy<Organisation> { it.updatedAt }.thenByDescending { it.event.id })
        }
        if (entry != null) {
            return FundManager(entry.displayName, entry, if (entry.revoked) FundManagerState.REVOKED else FundManagerState.VERIFIED)
        }
        return FundManager(managerName, null, FundManagerState.UNLISTED)
    }

    companion object {
        const val KIND: Int = EventKinds.LONG_FORM_ARTICLE
        const val TAG_ORIGIN: String = "ppf-origin"

        private val SLUG_PATTERN = Regex("[a-z0-9._~-]{1,64}")
        private val GEOHASH_PATTERN = Regex("[0-9bcdefghjkmnpqrstuvwxyz]{1,9}")

        /**
         * Reads an entry that the application may show: null unless the event is signed by a key
         * [trust] holds, carries a valid signature ([checkSignature]; the local database stores only
         * events checked on arrival, so its readers may skip it) and is a well-formed entry ([parse]).
         */
        fun from(event: SignedEvent, trust: EditorialTrust, checkSignature: Boolean = true): SolidarityFund? {
            if (!trust.isTrusted(event.pubkey)) return null
            if (checkSignature && !event.verify()) return null
            return parse(event)
        }

        /**
         * Reads the form of an entry, without any check of its author or signature: never show the
         * result as such, use [from]. Null when a required tag is missing or invalid.
         */
        fun parse(event: SignedEvent): SolidarityFund? {
            if (event.kind != KIND) return null
            if (event.firstTagValue(AgirFormats.TAG_TYPE) != SolidarityFormats.TYPE_FUND) return null
            val address = ContentAddress.of(event)?.takeIf { SLUG_PATTERN.matches(it.identifier) } ?: return null
            val name = text(event, AgirFormats.TAG_TITLE)?.takeIf { it.length <= AgirFormats.MAX_TITLE } ?: return null
            val summary = text(event, AgirFormats.TAG_SUMMARY) ?: return null
            val rawType = text(event, SolidarityFormats.TAG_FUND_TYPE) ?: return null
            val territory = text(event, AgirFormats.TAG_TERRITORY) ?: return null
            val beneficiary = text(event, SolidarityFormats.TAG_BENEFICIARY) ?: return null
            val verifiedOn = event.firstTagValue(AgirFormats.TAG_VERIFIED)?.let(Organisation::parseDate) ?: return null
            val publishedAt = event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            // The team's statement that the fund serves neither a candidate nor a party (point of attention 12).
            if (event.firstTagValue(SolidarityFormats.TAG_NO_PARTY)?.trim() != SolidarityFormats.NO_PARTY_YES) return null
            val managerName = text(event, SolidarityFormats.TAG_MANAGER)
            val managerEntry = event.tagValues(AgirFormats.TAG_ADDRESS).firstNotNullOfOrNull(::organisationAddress)
            if (managerName == null && managerEntry == null) return null
            return SolidarityFund(
                event = event,
                address = address,
                name = name,
                summary = summary,
                type = rawType.takeIf { it in SolidarityFormats.FUND_TYPES } ?: SolidarityFormats.FUND_OTHER,
                territory = territory,
                beneficiary = beneficiary,
                verifiedOn = verifiedOn,
                publishedAt = publishedAt,
                managerName = managerName,
                managerEntry = managerEntry,
                donationLink = event.tagValues(AgirFormats.TAG_LINK).map { it.trim() }.firstOrNull(::isDonationLink),
                sector = text(event, SolidarityFormats.TAG_SECTOR),
                strike = text(event, SolidarityFormats.TAG_STRIKE),
                status = readStatus(event.firstTagValue(SolidarityFormats.TAG_FUND_STATUS)),
                origin = event.firstTagValue(TAG_ORIGIN)?.let(ContentOrigin::fromTag),
                geohash = event.firstTagValue(AgirFormats.TAG_GEOHASH)?.trim()?.takeIf { GEOHASH_PATTERN.matches(it) },
            )
        }

        /**
         * Prepares the event of an entry; an editorial key then signs it. Every value is checked as
         * [parse] would read it, so that a prepared entry always reads back whole. An open fund needs
         * its donation link.
         */
        fun build(
            editor: PublicKey,
            slug: String,
            name: String,
            summary: String,
            type: String,
            territory: String,
            beneficiary: String,
            verifiedOn: String,
            publishedAt: Long,
            updatedAt: Long,
            presentation: String,
            managerName: String? = null,
            managerEntry: ContentAddress? = null,
            donationLink: String? = null,
            sector: String? = null,
            strike: String? = null,
            status: String = SolidarityFormats.FUND_OPEN,
            origin: ContentOrigin? = null,
            geohash: String? = null,
        ): UnsignedEvent {
            require(SLUG_PATTERN.matches(slug)) { "slug: 1 to 64 characters among a-z, 0-9, '.', '_', '~', '-'" }
            require(name.isNotBlank() && name.trim().length <= AgirFormats.MAX_TITLE) { "name: 1 to ${AgirFormats.MAX_TITLE} characters" }
            require(summary.isNotBlank()) { "summary is required" }
            require(type in SolidarityFormats.FUND_TYPES) { "type must be one of ${SolidarityFormats.FUND_TYPES}" }
            require(territory.isNotBlank()) { "territory is required" }
            require(beneficiary.isNotBlank() && beneficiary.trim().length <= SolidarityFormats.MAX_SHORT_TEXT) { "beneficiary: 1 to ${SolidarityFormats.MAX_SHORT_TEXT} characters" }
            require(Organisation.parseDate(verifiedOn) != null) { "verifiedOn must be a date AAAA-MM-JJ" }
            require(presentation.length <= AgirFormats.MAX_TEXT) { "presentation: at most ${AgirFormats.MAX_TEXT} characters" }
            require(!managerName.isNullOrBlank() || managerEntry != null) { "a manager is required: its name, its entry in the directory, or both" }
            require(managerEntry == null || isOrganisationAddress(managerEntry)) { "managerEntry must be the address of a directory entry (kind 30023)" }
            require(donationLink == null || isDonationLink(donationLink)) { "donationLink: an https address with an ASCII host" }
            require(status in SolidarityFormats.FUND_STATUSES) { "status must be one of ${SolidarityFormats.FUND_STATUSES}" }
            require(status != SolidarityFormats.FUND_OPEN || donationLink != null) { "an open fund needs its donation link" }
            require(listOfNotNull(managerName, sector, strike).all { it.trim().length <= SolidarityFormats.MAX_SHORT_TEXT }) { "manager, sector, strike: at most ${SolidarityFormats.MAX_SHORT_TEXT} characters" }
            require(geohash == null || GEOHASH_PATTERN.matches(geohash)) { "geohash: 1 to 9 geohash characters" }
            val tags = buildList {
                add(listOf("d", slug))
                add(listOf(AgirFormats.TAG_TITLE, name.trim()))
                add(listOf(AgirFormats.TAG_SUMMARY, summary.trim()))
                add(listOf(AgirFormats.TAG_PUBLISHED_AT, publishedAt.toString()))
                add(listOf(AgirFormats.TAG_TYPE, SolidarityFormats.TYPE_FUND))
                add(listOf(SolidarityFormats.TAG_FUND_TYPE, type))
                add(listOf(AgirFormats.TAG_TERRITORY, territory.trim()))
                add(listOf(SolidarityFormats.TAG_BENEFICIARY, beneficiary.trim()))
                add(listOf(AgirFormats.TAG_VERIFIED, verifiedOn.trim()))
                add(listOf(SolidarityFormats.TAG_NO_PARTY, SolidarityFormats.NO_PARTY_YES))
                // The default status stays implicit, like the status of the directory entries.
                if (status != SolidarityFormats.FUND_OPEN) add(listOf(SolidarityFormats.TAG_FUND_STATUS, status))
                origin?.let { add(listOf(TAG_ORIGIN, it.tagValue)) }
                sector?.takeIf { it.isNotBlank() }?.let { add(listOf(SolidarityFormats.TAG_SECTOR, it.trim())) }
                strike?.takeIf { it.isNotBlank() }?.let { add(listOf(SolidarityFormats.TAG_STRIKE, it.trim())) }
                managerName?.takeIf { it.isNotBlank() }?.let { add(listOf(SolidarityFormats.TAG_MANAGER, it.trim())) }
                managerEntry?.let { add(listOf(AgirFormats.TAG_ADDRESS, it.toString())) }
                donationLink?.let { add(listOf(AgirFormats.TAG_LINK, it.trim())) }
                geohash?.let { add(listOf(AgirFormats.TAG_GEOHASH, it)) }
            }
            return UnsignedEvent.create(editor, KIND, updatedAt, tags, presentation)
        }

        /** The status a `ppf-fund-status` value is read as: absent means open, anything unknown withdrawn. */
        fun readStatus(value: String?): String = when (val trimmed = value?.trim()) {
            null, SolidarityFormats.FUND_OPEN -> SolidarityFormats.FUND_OPEN
            else -> if (trimmed in SolidarityFormats.FUND_STATUSES) trimmed else SolidarityFormats.FUND_WITHDRAWN
        }

        /**
         * A donation page the application may open: an `https` address whose host is plain ASCII,
         * without any "user@" part (the rules of the directory's web sites), and that the common
         * "Vous quittez PPF" accepts to open.
         */
        fun isDonationLink(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.lowercase().startsWith("https://") && Organisation.isWebsite(trimmed) && CallRules.isValidLink(trimmed)
        }

        /** The address of a directory entry, from an `a` tag; null for anything else. */
        fun organisationAddress(value: String): ContentAddress? =
            runCatching { ContentAddress.parse(value.trim()) }.getOrNull()?.takeIf(::isOrganisationAddress)

        private fun isOrganisationAddress(address: ContentAddress): Boolean =
            address.kind == Organisation.KIND && SLUG_PATTERN.matches(address.identifier)

        private fun text(event: SignedEvent, tag: String): String? = event.firstTagValue(tag)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** What the page of a fund says of whoever manages it. */
enum class FundManagerState {
    /** An entry of the directory of organisations, verified by the team. */
    VERIFIED,

    /** An entry of the directory whose verification the team withdrew. */
    REVOKED,

    /** No entry of the directory on this device: only the name written in the fund. */
    UNLISTED,
}

/**
 * Whoever manages a fund: its [name] (the directory's when it has an entry; null when the fund only
 * names an entry this device does not hold yet), its [organisation] entry and its [state].
 */
data class FundManager(val name: String?, val organisation: Organisation?, val state: FundManagerState)
