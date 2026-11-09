package org.politisationpopulairefrancaise.core.agir.solidarity

import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** Signed funds, missions and directory entries for the tests: an editorial key the trust holds, and a stranger. */
class SolidarityFixtures {
    val editorKeys: IdentityKeys = IdentityKeys.generate()
    val editor = LocalEventSigner(editorKeys)
    val stranger = LocalEventSigner(IdentityKeys.generate())

    /** A second editorial key, as after a rotation of the editorial keys. */
    val otherEditor = LocalEventSigner(IdentityKeys.generate())
    val trust: EditorialTrust = EditorialTrust(setOf(editorKeys.publicKey.hex, otherEditor.publicKey.hex))

    fun fundEvent(
        slug: String,
        name: String = "[EXEMPLE] Caisse $slug",
        type: String = SolidarityFormats.FUND_STRIKE,
        territory: String = "France",
        beneficiary: String = "Grévistes fictifs",
        managerName: String? = "Syndicat fictif",
        managerEntry: ContentAddress? = null,
        donationLink: String? = "https://example.org/$slug",
        status: String = SolidarityFormats.FUND_OPEN,
        sector: String? = null,
        strike: String? = null,
        updatedAt: Long = 1_794_000_000,
        summary: String = "[EXEMPLE] Une caisse fictive.",
        presentation: String = "Présentation fictive.",
        signer: LocalEventSigner = editor,
    ): UnsignedEvent = SolidarityFund.build(
        editor = signer.publicKey,
        slug = slug,
        name = name,
        summary = summary,
        type = type,
        territory = territory,
        beneficiary = beneficiary,
        verifiedOn = "2026-11-05",
        publishedAt = 1_793_000_000,
        updatedAt = updatedAt,
        presentation = presentation,
        managerName = managerName,
        managerEntry = managerEntry,
        donationLink = donationLink,
        sector = sector,
        strike = strike,
        status = status,
        origin = ContentOrigin.AI_ASSISTED,
    )

    suspend fun fund(
        slug: String,
        name: String = "[EXEMPLE] Caisse $slug",
        type: String = SolidarityFormats.FUND_STRIKE,
        territory: String = "France",
        beneficiary: String = "Grévistes fictifs",
        managerName: String? = "Syndicat fictif",
        managerEntry: ContentAddress? = null,
        donationLink: String? = "https://example.org/$slug",
        status: String = SolidarityFormats.FUND_OPEN,
        sector: String? = null,
        strike: String? = null,
        updatedAt: Long = 1_794_000_000,
        summary: String = "[EXEMPLE] Une caisse fictive.",
        signer: LocalEventSigner = editor,
    ): SignedEvent = signer.sign(
        fundEvent(slug, name, type, territory, beneficiary, managerName, managerEntry, donationLink, status, sector, strike, updatedAt, summary, signer = signer),
    )

    fun missionEvent(
        slug: String,
        title: String = "[EXEMPLE] Mission $slug",
        skills: List<String> = listOf("traduction"),
        territory: String = "France",
        remote: Boolean = true,
        location: String? = null,
        commitment: String? = null,
        people: Int? = null,
        deadline: Long? = null,
        status: String = SolidarityFormats.MISSION_OPEN,
        publishedAt: Long = 1_793_000_000,
        updatedAt: Long = 1_794_000_000,
        signer: LocalEventSigner = editor,
    ): UnsignedEvent = VolunteerMission.build(
        editor = signer.publicKey,
        slug = slug,
        title = title,
        summary = "[EXEMPLE] Une mission fictive.",
        skills = skills,
        territory = territory,
        publishedAt = publishedAt,
        updatedAt = updatedAt,
        description = "Description fictive.",
        remote = remote,
        location = location,
        commitment = commitment,
        people = people,
        deadline = deadline,
        status = status,
        origin = ContentOrigin.HUMAN,
    )

    suspend fun mission(
        slug: String,
        title: String = "[EXEMPLE] Mission $slug",
        skills: List<String> = listOf("traduction"),
        territory: String = "France",
        remote: Boolean = true,
        location: String? = null,
        deadline: Long? = null,
        status: String = SolidarityFormats.MISSION_OPEN,
        publishedAt: Long = 1_793_000_000,
        signer: LocalEventSigner = editor,
    ): SignedEvent = signer.sign(
        missionEvent(slug, title, skills, territory, remote, location, deadline = deadline, status = status, publishedAt = publishedAt, signer = signer),
    )

    /** A directory entry of the organisations, read as the device reads it. */
    suspend fun organisation(
        slug: String,
        name: String = "[EXEMPLE] Organisation $slug",
        status: String = "verifiee",
        updatedAt: Long = 1_794_000_000,
        signer: LocalEventSigner = editor,
    ): Organisation {
        val event = signer.sign(
            Organisation.build(
                editor = signer.publicKey,
                slug = slug,
                name = name,
                summary = "[EXEMPLE] Une organisation fictive.",
                type = "syndicat",
                territory = "Lille (59)",
                verifiedOn = "2026-10-12",
                publishedAt = 1_793_000_000,
                updatedAt = updatedAt,
                presentation = "Présentation fictive.",
                origin = ContentOrigin.AI_ASSISTED,
                status = status,
            ),
        )
        return Organisation.from(event, trust) ?: error("entry not accepted: ${event.tags}")
    }

    fun readFund(event: SignedEvent): SolidarityFund = SolidarityFund.from(event, trust) ?: error("fund not accepted: ${event.tags}")

    fun readMission(event: SignedEvent): VolunteerMission = VolunteerMission.from(event, trust) ?: error("mission not accepted: ${event.tags}")

    /** Signs [unsigned] with one tag removed (every tag of that name). */
    suspend fun without(unsigned: UnsignedEvent, tag: String, signer: LocalEventSigner = editor): SignedEvent =
        signer.sign(unsigned.copy(tags = unsigned.tags.filterNot { it.firstOrNull() == tag }))

    /** Signs [unsigned] with the value of one tag replaced. */
    suspend fun replacing(unsigned: UnsignedEvent, tag: String, value: String, signer: LocalEventSigner = editor): SignedEvent =
        signer.sign(unsigned.copy(tags = unsigned.tags.filterNot { it.firstOrNull() == tag } + listOf(listOf(tag, value))))
}
