package org.politisationpopulairefrancaise.core.endirect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveBoardTest {

    @Test
    fun aNewDeviceShowsTheEssentialView() {
        val board = LiveBoard.default()
        assertEquals(LivePreset.ESSENTIEL.id, board.selected.id)
        assertEquals(LivePreset.entries.map { it.id }, board.views.map { it.id })
        assertEquals(LiveBoard.decode(null), board)
        assertEquals(LiveBoard.decode("n'importe quoi"), board)
    }

    @Test
    fun blocksAreAddedRemovedAndMovedThenTheViewIsPutBack() {
        var board = LiveBoard.default().select(LivePreset.POLITIQUE.id)
        board = board.remove(LiveBlock.NEWS).add(LiveBlock.REMINDERS).move(LiveBlock.REMINDERS, -10)
        assertEquals(LiveBlock.REMINDERS, board.selected.blocks.first())
        assertFalse(LiveBlock.NEWS in board.selected.blocks)
        assertTrue(LiveBlock.NEWS in board.hidden)
        assertTrue(board.selected.modified)
        // Adding a block already shown changes nothing.
        assertEquals(board, board.add(LiveBlock.REMINDERS))
        board = board.reset()
        assertEquals(LivePreset.POLITIQUE.blocks, board.selected.blocks)
        assertFalse(board.selected.modified)
    }

    @Test
    fun personalViewsAreKeptOnTheDeviceAndReadBack() {
        val board = LiveBoard.default().select(LivePreset.MOBILISATION.id).remove(LiveBlock.FIGURES)
            .addPersonalView("Ma grève | nuit\n").move(LiveBlock.CALLS, -1)
        assertEquals("Ma grève   nuit", board.selected.name)
        val read = LiveBoard.decode(board.encode())
        assertEquals(board, read)
        // A personal view can be removed, a ready-made one cannot.
        val removed = read.removePersonalView(read.selected.id)
        assertEquals(LivePreset.ESSENTIEL.id, removed.selected.id)
        assertEquals(LivePreset.entries.size, removed.views.size)
        assertEquals(removed, removed.removePersonalView(LivePreset.POLITIQUE.id))
    }

    @Test
    fun unknownBlocksAndBrokenLinesAreLeftOut() {
        val text = "selected=perso-9\nview=politique||alertes,inconnu,alertes,decider\nview=perso-2||une\nview=cassée\n"
        val board = LiveBoard.decode(text)
        assertEquals(LivePreset.ESSENTIEL.id, board.selected.id)
        assertEquals(listOf(LiveBlock.ALERTS, LiveBlock.DECIDER), board.views.first { it.id == "politique" }.blocks)
        // A personal view without a name is not kept.
        assertEquals(LivePreset.entries.size, board.views.size)
    }
}
