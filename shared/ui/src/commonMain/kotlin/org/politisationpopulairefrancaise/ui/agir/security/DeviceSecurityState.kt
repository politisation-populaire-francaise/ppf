package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.security.ChangeResult
import org.politisationpopulairefrancaise.core.agir.security.DeviceEraser
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecuritySettings
import org.politisationpopulairefrancaise.core.agir.security.InMemoryDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.agir.security.PinProblem
import org.politisationpopulairefrancaise.core.agir.security.UnlockResult
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the quick erase stands. */
enum class WipeStatus { IDLE, ERASING, DONE, FAILED }

/**
 * The security of this device for the whole run (M33): the lock of the application (S-43), the
 * content hidden from the system (S-45, Android) and the quick erase (S-44). It drives [AppLock]
 * and runs its slow hash off the main thread. Nothing here ever talks to a server.
 *
 * [forgetInApp] is what the shell forgets in memory when the keys are erased (the existing path of
 * « Effacer mes clés » : followed accounts, conversations, rooms, answers, testimonies) and the
 * rally point; [eraser] then erases everything the device keeps and closes the application. With
 * no eraser (tests, iOS not built yet), the identity and the settings are erased and the
 * application says what it could not erase.
 */
class DeviceSecurityState(
    private val lock: AppLock,
    private val identityStore: IdentityStore,
    private val eraser: DeviceEraser?,
    private val scope: CoroutineScope,
    private val forgetInApp: () -> Unit = {},
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

    /** True when this platform can erase every file of the application (Android, computer). */
    val canEraseEverything: Boolean get() = eraser != null

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
     * The quick erase (S-44), after the user confirmed: first what the application holds in memory
     * and the identity (the existing path of « Effacer mes clés »), then every file of the device.
     */
    fun wipe() {
        if (wipeStatus == WipeStatus.ERASING) return
        wipeStatus = WipeStatus.ERASING
        scope.launch {
            val ok = runCatching {
                forgetInApp()
                identityStore.clear()
                lock.forget()
                sync()
                eraser?.eraseAll()
            }.isSuccess
            wipeStatus = if (ok) WipeStatus.DONE else WipeStatus.FAILED
            if (ok) eraser?.closeApplication()
        }
    }

    /** Closes the application after the erase, where the platform can (not in tests nor on iOS yet). */
    fun close() {
        eraser?.closeApplication()
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

/** The security state for the whole run of the shell, on the storage and eraser of the platform host. */
@Composable
fun rememberDeviceSecurity(dependencies: AppDependencies, forgetInApp: () -> Unit): DeviceSecurityState {
    val scope = rememberCoroutineScope()
    val latestForget = remember { ForgetHolder() }
    latestForget.action = forgetInApp
    return remember(dependencies) {
        val storage = dependencies.securityStorage ?: InMemoryDeviceSecurityStorage()
        DeviceSecurityState(AppLock(storage), dependencies.identityStore, dependencies.deviceEraser, scope, forgetInApp = { latestForget.action() })
    }
}

private class ForgetHolder {
    var action: () -> Unit = {}
}
