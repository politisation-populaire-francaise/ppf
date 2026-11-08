package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.OpenDataDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** The package `semaine` as the device holds it. */
data class WeekHolding(
    val week: ParliamentWeek,
    /** The index announces another version (a newer one can be downloaded). */
    val outdated: Boolean,
)

/**
 * The package `semaine` on the device (VO-31), next to [ParliamentRepository], which keeps the
 * index: the same storage, the same checks (sha256 and size of the signed index before anything
 * is kept), the same server route. The network is used by [download] only, a gesture of the user
 * (or the opening of « En direct » when a version is already on the device and the data saver is
 * off); nothing is sent but the package name.
 */
class ParliamentWeekLoader(
    private val remote: RemoteEvents?,
    private val storage: ParliamentStorage,
    private val bundled: suspend (String) -> ByteArray? = { null },
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private var parsed: Pair<String, ParliamentWeekPackage>? = null

    /** The size announced by the index, null when the index has no package `semaine`. */
    fun ref(snapshot: ParliamentSnapshot): PackageRef? = snapshot.index.packageRef(ParliamentWeekFormat.PACKAGE)

    /**
     * What the device holds for [snapshot]: with the bundled examples, the bundled package checked
     * against the index; otherwise the stored copy, intact (its sha256 recomputed), outdated when
     * the index announces another one. Never the network.
     */
    suspend fun held(snapshot: ParliamentSnapshot): WeekHolding? {
        val ref = ref(snapshot)
        if (snapshot.origin == DataOrigin.BUNDLED) {
            ref ?: return null
            val bytes = runCatching { bundled(ParliamentWeekFormat.PACKAGE) }.getOrNull() ?: return null
            if (!ParliamentIndexReader.matches(ref, bytes)) return null
            val pkg = read(ref.sha256, bytes.decodeToString()) ?: return null
            return WeekHolding(ParliamentWeek(pkg), outdated = false)
        }
        val stored = storage.storedPackage(ParliamentWeekFormat.PACKAGE) ?: return null
        if (ParliamentFormat.sha256Hex(stored.json.encodeToByteArray()) != stored.sha256) return null
        val pkg = read(stored.sha256, stored.json) ?: return null
        return WeekHolding(ParliamentWeek(pkg), outdated = ref == null || ref.sha256 != stored.sha256)
    }

    /** Downloads the package announced by [snapshot]; kept only when it matches the index and reads as the package. */
    suspend fun download(snapshot: ParliamentSnapshot): PackageOutcome {
        val name = ParliamentWeekFormat.PACKAGE
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
        read(ref.sha256, text) ?: return PackageOutcome.Refused(name)
        storage.savePackage(StoredParliamentPackage(name, ref.sha256, text, snapshot.index.importedOn, clock()))
        return PackageOutcome.Done
    }

    private fun read(sha256: String, json: String): ParliamentWeekPackage? {
        parsed?.let { if (it.first == sha256) return it.second }
        return ParliamentWeekFormat.read(json)?.also { parsed = sha256 to it }
    }
}
