package org.politisationpopulairefrancaise.core.crash

// iOS is not compiled yet (no Apple Developer account): classes only, frames to add with the iOS host.
actual fun Throwable.crashChain(): List<CrashedException> {
    val chain = mutableListOf<CrashedException>()
    var current: Throwable? = this
    while (current != null && chain.size <= CrashReports.MAX_CAUSES) {
        chain += CrashedException(current::class.qualifiedName ?: "Throwable", emptyList())
        current = current.cause
    }
    return chain
}
