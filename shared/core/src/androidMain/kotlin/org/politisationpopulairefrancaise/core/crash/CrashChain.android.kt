package org.politisationpopulairefrancaise.core.crash

actual fun Throwable.crashChain(): List<CrashedException> {
    val chain = mutableListOf<CrashedException>()
    val seen = HashSet<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current) && chain.size <= CrashReports.MAX_CAUSES) {
        // The class and the frames only: getMessage() is never read (it may quote a content).
        chain += CrashedException(current.javaClass.name, current.stackTrace.map { it.toString() })
        current = current.cause
    }
    return chain
}
