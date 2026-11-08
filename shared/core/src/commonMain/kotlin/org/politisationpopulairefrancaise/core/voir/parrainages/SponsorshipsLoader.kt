package org.politisationpopulairefrancaise.core.voir.parrainages

import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataPackageLoader
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStorage

/** The package of presentations as the device holds it. */
data class SponsorshipsHolding(
    val catalog: SponsorshipsCatalog,
    /** The index announces another version (a newer publication can be downloaded). */
    val outdated: Boolean,
)

/**
 * The package `parrainages-2027` on the device (VO-37), beside the index kept by the parliamentary repository
 * ([OpenDataPackageLoader]: the same storage, the same checks, the same server route). The network is used by
 * [download] only; nothing is sent but the package name, the same for everyone.
 */
class SponsorshipsLoader(
    remote: RemoteEvents?,
    storage: ParliamentStorage,
    bundled: suspend (String) -> ByteArray? = { null },
    clock: () -> Long = ::currentEpochSeconds,
    val name: String = SponsorshipsFormat.PACKAGE_2027,
) {
    private val loader = OpenDataPackageLoader(name, remote, storage, SponsorshipsFormat::read, { it.publishedOn }, bundled, clock)

    /** The size announced by the index, null while the index has no such package (before the publications). */
    fun ref(snapshot: ParliamentSnapshot): PackageRef? = loader.ref(snapshot)

    /** What the device holds for [snapshot], never the network: the bundled examples, or the stored copy, intact. */
    suspend fun held(snapshot: ParliamentSnapshot): SponsorshipsHolding? =
        loader.held(snapshot)?.let { SponsorshipsHolding(SponsorshipsCatalog(it.value), it.outdated) }

    /** Downloads the package announced by [snapshot]; kept only when it matches the index and reads as the package. */
    suspend fun download(snapshot: ParliamentSnapshot): PackageOutcome = loader.download(snapshot)
}
