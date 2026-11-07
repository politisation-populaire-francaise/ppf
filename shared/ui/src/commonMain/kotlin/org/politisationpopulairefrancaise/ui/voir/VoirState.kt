package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.LibertyCategory
import org.politisationpopulairefrancaise.core.voir.PublicRole
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.ui.AppDependencies

/** What "Voir" can show above its home: a content, or one of its lists. */
sealed interface VoirDestination {
    /** An indicator, a fact, a person, a bill, a movement or a dossier, by its stable address. */
    data class Item(val address: ContentAddress) : VoirDestination
    data object Chronology : VoirDestination
    data object Liberties : VoirDestination
    data object People : VoirDestination
    data object Bills : VoirDestination
    data object World : VoirDestination
    data object Corrections : VoirDestination
    data class KeyFigures(val axis: VoirAxis) : VoirDestination
}

/**
 * The state of "Voir", kept by the shell for the whole run so that a visit elsewhere does not lose
 * the open sheet: the axis shown, the stack of open pages (the back button pops it), the search
 * and the filters of the lists, and the contents the user follows.
 *
 * The follows stay on the device ([ContentFollowStore]); without a store (tests, previews) they live
 * in memory only.
 */
class VoirState(private val follows: ContentFollowStore?, private val scope: CoroutineScope) {
    private val writes = Mutex()

    var axis: VoirAxis by mutableStateOf(VoirAxis.MOUVEMENT)

    /** The open pages, the last one on top; empty shows the home of the space. */
    val stack = mutableStateListOf<VoirDestination>()

    val current: VoirDestination? get() = stack.lastOrNull()

    var query: String by mutableStateOf("")

    var chronologyFilter: FactKind? by mutableStateOf(null)
    var libertyFilter: LibertyCategory? by mutableStateOf(null)
    var roleFilter: PublicRole? by mutableStateOf(null)

    /** The followed contents, most recently followed first. */
    var followed: List<ContentAddress> by mutableStateOf(emptyList())
        private set

    fun start() {
        val store = follows ?: return
        scope.launch { followed = withContext(Dispatchers.Default) { store.all() } }
    }

    fun open(destination: VoirDestination) {
        if (stack.lastOrNull() != destination) stack.add(destination)
    }

    fun open(address: ContentAddress) = open(VoirDestination.Item(address))

    /** Goes back one page; false when the home is already shown (the caller then leaves the space). */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Back to the home of the space, on the given axis. */
    fun home(axis: VoirAxis? = null) {
        stack.clear()
        if (axis != null) this.axis = axis
    }

    fun isFollowed(address: ContentAddress): Boolean = address in followed

    fun toggleFollow(address: ContentAddress) {
        val following = address in followed
        followed = if (following) followed - address else listOf(address) + followed
        val store = follows ?: return
        scope.launch {
            writes.withLock {
                withContext(Dispatchers.Default) { if (following) store.unfollow(address) else store.follow(address) }
            }
        }
    }
}

@Composable
fun rememberVoirState(dependencies: AppDependencies): VoirState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) { VoirState(dependencies.contentFollowStore, scope).also { it.start() } }
}
