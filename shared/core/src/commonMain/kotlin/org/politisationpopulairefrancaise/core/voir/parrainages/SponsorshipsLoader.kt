package org.politisationpopulairefrancaise.core.voir.parrainages

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.OpenDataDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.parlement.DataOrigin
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.StoredParliamentPackage

/** The package of presentations as the device holds it. */
data class SponsorshipsHolding(
    val catalog: SponsorshipsCatalog,
    /** The index announces another version (a newer publication can be downloaded). */
    val outdated: Boolean,
)

/**
 * The package `parrainages-2027` on the device (VO-37), beside the index kept by the parliamentary repository:
 * the same storage, the same checks (sha256 and size of the signed index before anything is kept), the same
 * server route, like the package `semaine`. The network is used by [download] only; nothing is sent but the
 * package name, the same for everyone.
 */
class SponsorshipsLoader(
    private val remote: RemoteEvents?,
    private val storage: ParliamentStorage,
    private val bundled: suspend (String) -> ByteArray? = { null },
    private val clock: () -> Long = ::currentEpochSeconds,
    val name: String = SponsorshipsFormat.PACKAGE_2027,
) {
    private var parsed: Pair<String, SponsorshipsPackage>? = null

    /** The size announced by the index, null while the index has no such package (before the publications). */
    fun ref(snapshot: ParliamentSnapshot): PackageRef? = snapshot.index.packageRef(name)

    /** What the device holds for [snapshot], never the network: the bundled examples, or the stored copy, intact. */
    suspend fun held(snapshot: ParliamentSnapshot): SponsorshipsHolding? {
        val ref = ref(snapshot)
        if (snapshot.origin == DataOrigin.BUNDLED) {
            ref ?: return null
            val bytes = runCatching { bundled(name) }.getOrNull() ?: return null
            if (!ParliamentIndexReader.matches(ref, bytes)) return null
            val pkg = read(ref.sha256, bytes.decodeToString()) ?: return null
            return SponsorshipsHolding(SponsorshipsCatalog(pkg), outdated = false)
        }
        val stored = storage.storedPackage(name) ?: return null
        if (ParliamentFormat.sha256Hex(stored.json.encodeToByteArray()) != stored.sha256) return null
        val pkg = read(stored.sha256, stored.json) ?: return null
        return SponsorshipsHolding(SponsorshipsCatalog(pkg), outdated = ref == null || ref.sha256 != stored.sha256)
    }

    /** Downloads the package announced by [snapshot]; kept only when it matches the index and reads as the package. */
    suspend fun download(snapshot: ParliamentSnapshot): PackageOutcome {
        val ref = ref(snapshot) ?: return PackageOutcome.Unknown(name)
        if (snapshot.origin == DataOrigin.BUNDLED) return if (held(snapshot) != null) PackageOutcome.Done else PackageOutcome.Unavailable(name)
        val server = remote ?: return PackageOutcome.Unavailable(name)
        val answer = try {
            server.fetchParliament(name = name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenDataDownload.Unavailable(e)
        }
        val bytes = when (answer) {
            is OpenDataDownload.Body -> answer.bytes
            OpenDataDownload.UnknownPackage -> return PackageOutcome.Unknown(name)
            OpenDataDownload.NotModified, is OpenDataDownload.Unavailable -> return PackageOutcome.Unavailable(name)
        }
        if (!ParliamentIndexReader.matches(ref, bytes)) return PackageOutcome.Refused(name)
        val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return PackageOutcome.Refused(name)
        val pkg = read(ref.sha256, text) ?: return PackageOutcome.Refused(name)
        storage.savePackage(StoredParliamentPackage(name, ref.sha256, text, pkg.publishedOn ?: snapshot.index.importedOn, clock()))
        return PackageOutcome.Done
    }

    private fun read(sha256: String, json: String): SponsorshipsPackage? {
        parsed?.let { if (it.first == sha256) return it.second }
        return SponsorshipsFormat.read(json)?.also { parsed = sha256 to it }
    }
}
