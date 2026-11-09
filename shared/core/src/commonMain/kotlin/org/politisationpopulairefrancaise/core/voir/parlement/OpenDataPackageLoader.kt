package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.OpenDataDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** One package of the open data as the device holds it, read. */
data class HeldPackage<T>(
    val value: T,
    /** The index announces another version (a newer one can be downloaded). */
    val outdated: Boolean,
)

/**
 * One package of the signed index kept beside the parliamentary catalog (the presentations of VO-37, the
 * application of the laws of VO-36): the same storage as the repository, the same checks (sha256 and size of
 * the signed index before anything is kept), the same server route. The network is used by [download] only;
 * nothing is sent but the package name, the same for everyone.
 */
class OpenDataPackageLoader<T : Any>(
    val name: String,
    private val remote: RemoteEvents?,
    private val storage: ParliamentStorage,
    private val parse: (String) -> T?,
    /** The day of the source written in the package, kept with the stored copy. */
    private val publishedOn: (T) -> String?,
    private val bundled: suspend (String) -> ByteArray? = { null },
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private var parsed: Pair<String, T>? = null

    /** The size announced by the index, null while the index has no such package. */
    fun ref(snapshot: ParliamentSnapshot): PackageRef? = snapshot.index.packageRef(name)

    /** What the device holds for [snapshot], never the network: the bundled examples, or the stored copy, intact. */
    suspend fun held(snapshot: ParliamentSnapshot): HeldPackage<T>? {
        val ref = ref(snapshot)
        if (snapshot.origin == DataOrigin.BUNDLED) {
            ref ?: return null
            val bytes = runCatching { bundled(name) }.getOrNull() ?: return null
            if (!ParliamentIndexReader.matches(ref, bytes)) return null
            val value = read(ref.sha256, bytes.decodeToString()) ?: return null
            return HeldPackage(value, outdated = false)
        }
        val stored = storage.storedPackage(name) ?: return null
        if (ParliamentFormat.sha256Hex(stored.json.encodeToByteArray()) != stored.sha256) return null
        val value = read(stored.sha256, stored.json) ?: return null
        return HeldPackage(value, outdated = ref == null || ref.sha256 != stored.sha256)
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
        val value = read(ref.sha256, text) ?: return PackageOutcome.Refused(name)
        storage.savePackage(StoredParliamentPackage(name, ref.sha256, text, publishedOn(value) ?: snapshot.index.importedOn, clock()))
        return PackageOutcome.Done
    }

    private fun read(sha256: String, json: String): T? {
        parsed?.let { if (it.first == sha256) return it.second }
        return parse(json)?.also { parsed = sha256 to it }
    }
}
