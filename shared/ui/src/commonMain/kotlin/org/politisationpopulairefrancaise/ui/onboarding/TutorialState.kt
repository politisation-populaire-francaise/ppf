package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the device remembers that the tutorial of the first launch was seen (D-216). One flag, on
 * the device only: nothing is sent, nothing says who saw what. The hosts keep it in a small file of
 * the application's private directory; tests and previews give none, and then no tutorial is shown.
 */
interface TutorialStore {
    fun seen(): Boolean

    fun markSeen()

    /** Forgotten by the quick erase: an erased device opens on the tutorial, like a new one. */
    fun forget()
}

/** For tests: a flag forgotten with the process. */
class InMemoryTutorialStore(private var seen: Boolean = false) : TutorialStore {
    override fun seen(): Boolean = seen

    override fun markSeen() {
        seen = true
    }

    override fun forget() {
        seen = false
    }
}

/**
 * Whether the tutorial is on screen: at the first launch (nothing in [store]), or when the person
 * asks to see it again from Mon espace or from the AIDE sheet. Without a store, never by itself.
 */
@Stable
class TutorialState(private val store: TutorialStore?) {
    var isOpen by mutableStateOf(store?.seen() == false)
        private set

    /** Whether the tutorial can be shown again (the hosts of the application, not the tests). */
    val available: Boolean get() = store != null

    fun replay() {
        if (store != null) isOpen = true
    }

    /** Seen, skipped or left: it will not open again by itself. */
    fun finish() {
        isOpen = false
        runCatching { store?.markSeen() }
    }

    companion object {
        /** The id of its part in the quick erase (S-44). */
        const val ERASABLE_ID = "ui-tutorial"
    }
}

@Composable
fun rememberTutorialState(store: TutorialStore?): TutorialState = remember(store) { TutorialState(store) }

/** The tutorial of the shell, for the screens that offer to see it again (Mon espace, the AIDE sheet). */
val LocalTutorial = staticCompositionLocalOf<TutorialState?> { null }

@Composable
fun ProvideTutorial(state: TutorialState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTutorial provides state, content = content)
}
