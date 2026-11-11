package org.politisationpopulairefrancaise.core.voir.application

import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataPackageLoader
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStorage

/** What the device holds of the application of the laws: the packages read, and those the index announces anew. */
data class ApplicationHolding(
    val catalog: ApplicationCatalog,
    /** The legislatures whose package on the device is older than the one of the index. */
    val outdated: Set<Int>,
)

/**
 * The packages `application-lois-<législature>` on the device (VO-36), one [OpenDataPackageLoader] each: the
 * same storage and checks as the other packages of the Parliament, the network on a gesture only, one package
 * at a time, its size shown first.
 */
class ApplicationLoader(
    remote: RemoteEvents?,
    storage: ParliamentStorage,
    bundled: suspend (String) -> ByteArray? = { null },
    clock: () -> Long = ::currentEpochSeconds,
    legislatures: List<Int> = ApplicationFormat.LEGISLATURES,
) {
    private val loaders: Map<Int, OpenDataPackageLoader<ApplicationPackage>> = legislatures.associateWith { legislature ->
        OpenDataPackageLoader(ApplicationFormat.packageName(legislature), remote, storage, ApplicationFormat::read, { it.updatedOn }, bundled, clock)
    }

    /** The legislatures the index announces, with the size of each package; empty before the first import. */
    fun available(snapshot: ParliamentSnapshot): List<Pair<Int, PackageRef>> =
        loaders.mapNotNull { (legislature, loader) -> loader.ref(snapshot)?.let { legislature to it } }.sortedByDescending { it.first }

    /** What the device holds for [snapshot], never the network; null when it holds no package. */
    suspend fun held(snapshot: ParliamentSnapshot): ApplicationHolding? {
        // A package is kept only for the legislature its name says.
        val held = loaders.mapNotNull { (legislature, loader) -> loader.held(snapshot)?.takeIf { it.value.legislature == legislature }?.let { legislature to it } }
        if (held.isEmpty()) return null
        return ApplicationHolding(
            ApplicationCatalog(held.map { it.second.value }),
            held.filter { it.second.outdated }.map { it.first }.toSet(),
        )
    }

    /** Downloads the package of [legislature] announced by [snapshot]. */
    suspend fun download(snapshot: ParliamentSnapshot, legislature: Int): PackageOutcome {
        val loader = loaders[legislature] ?: return PackageOutcome.Unknown(ApplicationFormat.packageName(legislature))
        return loader.download(snapshot)
    }
}
