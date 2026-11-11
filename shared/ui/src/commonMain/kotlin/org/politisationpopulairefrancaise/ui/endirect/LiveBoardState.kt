package org.politisationpopulairefrancaise.ui.endirect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.core.endirect.LiveBlock
import org.politisationpopulairefrancaise.core.endirect.LiveBoard

/**
 * Where the device keeps how the person composed « En direct »: a few lines of text in the
 * application's private directory, never sent. Tests and previews give none: the board then lives
 * in memory and starts from « L'essentiel ».
 */
interface LiveBoardStore {
    fun load(): String?

    fun save(text: String)

    /** Forgotten by the quick erase: « En direct » starts again from « L'essentiel ». */
    fun forget()
}

/** For tests: forgotten with the process. */
class InMemoryLiveBoardStore(private var text: String? = null) : LiveBoardStore {
    override fun load(): String? = text

    override fun save(text: String) {
        this.text = text
    }

    override fun forget() {
        text = null
    }
}

/** The board of « En direct » on screen, and whether it is being composed. Every change is kept at once. */
@Stable
class LiveBoardState(private val store: LiveBoardStore?) {
    var board: LiveBoard by mutableStateOf(runCatching { LiveBoard.decode(store?.load()) }.getOrDefault(LiveBoard.default()))
        private set

    var editing: Boolean by mutableStateOf(false)

    private fun update(next: LiveBoard) {
        if (next == board) return
        board = next
        runCatching { store?.save(next.encode()) }
    }

    fun select(id: String) = update(board.select(id))
    fun add(block: LiveBlock) = update(board.add(block))
    fun remove(block: LiveBlock) = update(board.remove(block))
    fun move(block: LiveBlock, delta: Int) = update(board.move(block, delta))
    fun reset() = update(board.reset())
    fun addPersonalView(name: String) = update(board.addPersonalView(name))
    fun removePersonalView(id: String) = update(board.removePersonalView(id))

    /** The quick erase: back to the board of a new device. */
    fun forget() {
        runCatching { store?.forget() }
        board = LiveBoard.default()
        editing = false
    }

    companion object {
        /** The id of its part in the quick erase (S-44). */
        const val ERASABLE_ID = "ui-live-board"
    }
}

@Composable
fun rememberLiveBoardState(store: LiveBoardStore?): LiveBoardState = remember(store) { LiveBoardState(store) }
