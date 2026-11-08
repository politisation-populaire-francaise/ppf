package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.security.AgirDeviceData
import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.security.ChangeResult
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecuritySettings
import org.politisationpopulairefrancaise.core.agir.security.InMemoryDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.agir.security.PinProblem
import org.politisationpopulairefrancaise.core.agir.security.UnlockResult
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the quick erase stands. */
enum class WipeStatus { IDLE, ERASING, DONE, FAILED }

/**
 * The security of this device for the whole run (M33): the lock of the application (S-43), the
 * content hidden from the system (S-45, Android) and the quick erase (S-44). It drives [AppLock]
 * and runs its slow hash off the main thread. Nothing here ever talks to a server.
 *
 * The quick erase goes through [erasables], where every pole registers what it keeps on the
 * device ([DeviceErasable]); the security settings are erased last, so that a failed erase leaves
 * the lock as it was. [closeApplication] closes the application once the user has read the result.
 */
class DeviceSecurityState(
    private val lock: AppLock,
    private val erasables: DeviceEraseRegistry,
    private val scope: CoroutineScope,
    private val closeApplication: (() -> Unit)? = null,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) {
    var settings: DeviceSecuritySettings by mutableStateOf(lock.settings)
        private set

    var isLocked: Boolean by mutableStateOf(lock.isLocked)
        private set

    /** True while a code is being checked (the slow hash takes a fraction of a second). */
    var busy: Boolean by mutableStateOf(false)
        private set

    var wipeStatus: WipeStatus by mutableStateOf(WipeStatus.IDLE)
        private set

    /** The parts that could not be erased by the last erase (ids of [DeviceErasable]). */
    var failedParts: List<String> by mutableStateOf(emptyList())
        private set

    /** True when the application can close itself after the erase (Android, computer). */
    val canClose: Boolean get() = closeApplication != null

    fun onBackground() = lock.onBackground()

    fun onForeground() {
        lock.onForeground()
        sync()
    }

    fun lockNow() {
        lock.lockNow()
        sync()
    }

    fun waitSeconds(): Long = lock.waitSeconds()

    fun unlock(pin: String, onResult: (UnlockResult) -> Unit) = work({ lock.unlock(pin) }, onResult)

    fun enable(pin: String, confirmation: String, onResult: (PinProblem?) -> Unit) = work({ lock.enable(pin, confirmation) }, onResult)

    fun change(current: String, pin: String, confirmation: String, onResult: (ChangeResult) -> Unit) =
        work({ lock.change(current, pin, confirmation) }, onResult)

    fun disable(current: String, onResult: (Boolean) -> Unit) = work({ lock.disable(current) }, onResult)

    fun setLockAfter(seconds: Int) {
        lock.setLockAfter(seconds)
        sync()
    }

    fun setHideContent(hide: Boolean) {
        lock.setHideContent(hide)
        sync()
    }

    /**
     * The quick erase (S-44), after the user confirmed: every registered part, each on its own,
     * then the security settings. The user restores the account afterwards with the recovery phrase.
     */
    fun wipe() {
        if (wipeStatus == WipeStatus.ERASING) return
        wipeStatus = WipeStatus.ERASING
        scope.launch {
            val report = erasables.eraseAll()
            failedParts = report.failed
            if (report.complete) {
                lock.forget()
                sync()
            }
            wipeStatus = if (report.complete) WipeStatus.DONE else WipeStatus.FAILED
        }
    }

    /** Closes the application after the erase, where the platform can (not in tests nor on iOS yet). */
    fun close() {
        closeApplication?.invoke()
    }

    /** Back from a failed erase to the application (the user may try again). */
    fun dismissWipe() {
        if (wipeStatus == WipeStatus.FAILED) wipeStatus = WipeStatus.IDLE
    }

    private fun <T> work(block: () -> T, onResult: (T) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { withContext(worker) { block() } }
            busy = false
            sync()
            result.onSuccess(onResult)
        }
    }

    private fun sync() {
        settings = lock.settings
        isLocked = lock.isLocked
    }
}

/** The security state of the shell, read by « Mon espace », the AIDE sheet and the protest mode; null outside the shell. */
val LocalDeviceSecurity = staticCompositionLocalOf<DeviceSecurityState?> { null }

@Composable
fun ProvideDeviceSecurity(state: DeviceSecurityState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDeviceSecurity provides state, content = content)
}

/**
 * The security state for the whole run of the shell, on the storage of the platform host. It
 * registers the data of "Agir" in the local database ([AgirDeviceData]) for the quick erase.
 */
@Composable
fun rememberDeviceSecurity(dependencies: AppDependencies): DeviceSecurityState {
    val scope = rememberCoroutineScope()
    val state = remember(dependencies) {
        val storage = dependencies.securityStorage ?: InMemoryDeviceSecurityStorage()
        DeviceSecurityState(AppLock(storage), dependencies.erasables, scope, dependencies.closeApplication)
    }
    dependencies.database?.let { database -> RegisterErasable(dependencies, remember(database) { AgirDeviceData(database) }) }
    return state
}

/**
 * Registers [erasable] for the quick erase for as long as its caller stays on screen (the shell:
 * the whole run). The common way for a pole to hand its data to the quick erase from the interface.
 */
@Composable
fun RegisterErasable(dependencies: AppDependencies, erasable: DeviceErasable) {
    DisposableEffect(dependencies, erasable) {
        val remove = dependencies.erasables.register(erasable)
        onDispose { remove() }
    }
}

/** [RegisterErasable] from a function; [erase] is read again at each erase (the latest one composed). */
@Composable
fun RegisterErasable(dependencies: AppDependencies, id: String, erase: suspend () -> Unit) {
    val latest = remember(id) { LatestErase() }
    latest.erase = erase
    val erasable = remember(id) { deviceErasable(id) { latest.erase() } }
    RegisterErasable(dependencies, erasable)
}

private class LatestErase {
    var erase: suspend () -> Unit = {}
}
