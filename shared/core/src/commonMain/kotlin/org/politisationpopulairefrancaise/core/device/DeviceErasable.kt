package org.politisationpopulairefrancaise.core.device

/**
 * Something the application keeps on the device and that the quick erase (S-44, M33) must
 * erase. **Every pole implements it for its own data** (its tables of the local database, its
 * preferences, its files) and registers it in the [DeviceEraseRegistry] of the application
 * (`AppDependencies.erasables`): the quick erase erases nothing it was not handed, so that each
 * pole stays the only one to touch its own data.
 *
 * [eraseFromDevice] must erase for good what it covers (rows deleted, files deleted, keys
 * destroyed), without any network call, and throw when it could not: the user is then told. It
 * may be called more than once (a retry after a failure) and must stay idempotent.
 */
interface DeviceErasable {
    /** A stable name, in English kebab-case, prefixed by the pole: "agir-data", "core-identity"… */
    val erasableId: String

    suspend fun eraseFromDevice()
}

/** A [DeviceErasable] from a function. */
fun deviceErasable(id: String, erase: suspend () -> Unit): DeviceErasable = object : DeviceErasable {
    override val erasableId: String = id

    override suspend fun eraseFromDevice() = erase()
}

/** What the quick erase did: the parts erased and those that failed (by [DeviceErasable.erasableId]). */
data class EraseReport(val erased: List<String>, val failed: List<String>) {
    val complete: Boolean get() = failed.isEmpty()
}

/**
 * The parts of the device the quick erase goes through, in the order they were registered;
 * registering an id again replaces the previous one. Used from the main thread.
 */
class DeviceEraseRegistry {
    private val parts = mutableListOf<DeviceErasable>()

    /** Adds [erasable]; returns what removes it again (a screen that leaves). */
    fun register(erasable: DeviceErasable): () -> Unit {
        parts.removeAll { it.erasableId == erasable.erasableId }
        parts += erasable
        return { parts.remove(erasable) }
    }

    val ids: List<String> get() = parts.map { it.erasableId }

    /** Erases every part, each on its own: a failing part never stops the others. */
    suspend fun eraseAll(): EraseReport {
        val erased = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (part in parts.toList()) {
            if (runCatching { part.eraseFromDevice() }.isSuccess) erased += part.erasableId else failed += part.erasableId
        }
        return EraseReport(erased, failed)
    }
}
