package org.politisationpopulairefrancaise.ui.decider

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
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies

/** What "Décider" can show above its home. */
sealed interface DeciderDestination {
    data class Petition(val address: ContentAddress) : DeciderDestination
    data class Pacte(val address: ContentAddress) : DeciderDestination
    /** The comparator of an election (DE-16). */
    data class Comparator(val address: ContentAddress) : DeciderDestination
    /** An election of the personal electoral calendar (DE-28). */
    data class Election(val address: ContentAddress) : DeciderDestination
    /** The past votes of the party of one candidate of a comparator (DE-16, D-117). */
    data class PastVotes(val sheet: ContentAddress, val candidate: ContentAddress) : DeciderDestination
    /** The rules of the official petitions; [platform] opens one of them first. */
    data class Thresholds(val platform: PetitionPlatform? = null) : DeciderDestination
    data object ClosedPetitions : DeciderDestination
}

/**
 * The state of "Décider", kept by the shell for the whole run so that a visit elsewhere does not
 * lose the open page: the stack of open pages (the back button pops it) and the contents the user
 * follows.
 *
 * The follows share the device's list of followed contents with "Voir" ([ContentFollowStore]):
 * following a petition can reveal an opinion, so the list never leaves the device. Without a store
 * (tests, previews) it lives in memory only.
 */
class DeciderState(private val follows: ContentFollowStore?, private val scope: CoroutineScope) {
    private val writes = Mutex()

    val stack = mutableStateListOf<DeciderDestination>()

    val current: DeciderDestination? get() = stack.lastOrNull()

    /** Every followed content of the device (petitions, pacts and the contents of "Voir"), most recent first. */
    var followed: List<ContentAddress> by mutableStateOf(emptyList())
        private set

    fun start() {
        val store = follows ?: return
        scope.launch { followed = withContext(Dispatchers.Default) { store.all() } }
    }

    fun open(destination: DeciderDestination) {
        if (stack.lastOrNull() != destination) stack.add(destination)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() = stack.clear()

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
fun rememberDeciderState(dependencies: AppDependencies): DeciderState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) { DeciderState(dependencies.contentFollowStore, scope).also { it.start() } }
}
