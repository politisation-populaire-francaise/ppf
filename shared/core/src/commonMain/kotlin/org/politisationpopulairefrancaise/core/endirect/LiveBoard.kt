package org.politisationpopulairefrancaise.core.endirect

/**
 * The blocks of « En direct » (owner's request of 11 November 2026: a dashboard everyone composes).
 * [id] is stable: it is what the device keeps.
 */
enum class LiveBlock(val id: String) {
    /** The posts of a day of mobilisation, in red. */
    LIVE("en-cours"),
    /** A vote soon in Parliament, a recent ban on demonstrating. */
    ALERTS("alertes"),
    /** The coming mobilisations of the agenda, those of my territory first. */
    UPCOMING("mobilisations"),
    /** The open calls whose deadline is near. */
    CALLS("appels"),
    /** Demands, official petitions, the Constitution: « Décider ensemble ». */
    DECIDER("decider"),
    /** The demand of the moment. */
    HEADLINE("une"),
    /** The key figures of « Voir ». */
    FIGURES("chiffres"),
    /** « Cette semaine au Parlement ». */
    PARLIAMENT("parlement"),
    /** My personal reminders, kept on the device. */
    REMINDERS("rappels"),
    /** The latest posts of PPF. */
    NEWS("actu"),
    /** The latest facts of the chronology. */
    CHRONOLOGY("chronologie"),
    ;

    companion object {
        fun fromId(id: String): LiveBlock? = entries.firstOrNull { it.id == id }
    }
}

/** The views ready to use. The first one is shown until the person chooses another. */
enum class LivePreset(val id: String, val blocks: List<LiveBlock>) {
    ESSENTIEL(
        "essentiel",
        listOf(LiveBlock.LIVE, LiveBlock.ALERTS, LiveBlock.DECIDER, LiveBlock.FIGURES, LiveBlock.UPCOMING, LiveBlock.CALLS, LiveBlock.PARLIAMENT, LiveBlock.HEADLINE, LiveBlock.NEWS, LiveBlock.CHRONOLOGY),
    ),
    MOBILISATION(
        "mobilisation",
        listOf(LiveBlock.LIVE, LiveBlock.UPCOMING, LiveBlock.CALLS, LiveBlock.ALERTS, LiveBlock.HEADLINE, LiveBlock.FIGURES, LiveBlock.REMINDERS),
    ),
    POLITIQUE(
        "politique",
        listOf(LiveBlock.ALERTS, LiveBlock.PARLIAMENT, LiveBlock.DECIDER, LiveBlock.HEADLINE, LiveBlock.CHRONOLOGY, LiveBlock.NEWS),
    ),
    PRES_DE_CHEZ_MOI(
        "pres-de-chez-moi",
        listOf(LiveBlock.UPCOMING, LiveBlock.CALLS, LiveBlock.LIVE, LiveBlock.ALERTS, LiveBlock.REMINDERS),
    ),
    ;

    companion object {
        fun fromId(id: String): LivePreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One view of « En direct »: a ready-made one ([preset], possibly reordered by the person) or a
 * personal one with its [name]. [blocks] are shown in this order, each once.
 */
data class LiveView(val id: String, val blocks: List<LiveBlock>, val preset: LivePreset? = null, val name: String? = null) {
    /** Whether the person changed a ready-made view (it can then be put back as it was). */
    val modified: Boolean get() = preset != null && preset.blocks != blocks
}

/**
 * The dashboard of « En direct » as the person composed it: the ready-made views (always there, in
 * their order), the personal views, and the one on screen. Kept on the device only, as a few lines
 * of text ([encode], [decode]); nothing is sent. Every change returns a new board.
 */
data class LiveBoard(val views: List<LiveView>, val selectedId: String) {
    val selected: LiveView get() = views.firstOrNull { it.id == selectedId } ?: views.first()

    /** The blocks the selected view does not show, in their usual order, to add. */
    val hidden: List<LiveBlock> get() = LiveBlock.entries.filter { it !in selected.blocks }

    fun select(id: String): LiveBoard = if (views.any { it.id == id }) copy(selectedId = id) else this

    private fun edit(change: (List<LiveBlock>) -> List<LiveBlock>): LiveBoard =
        copy(views = views.map { if (it.id == selected.id) it.copy(blocks = change(it.blocks).distinct()) else it })

    fun add(block: LiveBlock): LiveBoard = edit { it + block }

    fun remove(block: LiveBlock): LiveBoard = edit { it - block }

    /** Moves [block] by [delta] places (−1: up, +1: down), within the view. */
    fun move(block: LiveBlock, delta: Int): LiveBoard = edit { blocks ->
        val from = blocks.indexOf(block)
        if (from < 0) return@edit blocks
        val to = (from + delta).coerceIn(0, blocks.lastIndex)
        blocks.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Puts a ready-made view back as it was. */
    fun reset(): LiveBoard = selected.preset?.let { preset -> edit { preset.blocks } } ?: this

    /** A new personal view, a copy of the one on screen, selected. */
    fun addPersonalView(name: String): LiveBoard {
        val clean = cleanName(name).ifEmpty { return this }
        val id = generateSequence(1) { it + 1 }.map { "perso-$it" }.first { candidate -> views.none { it.id == candidate } }
        return copy(views = views + LiveView(id, selected.blocks, name = clean), selectedId = id)
    }

    /** Removes a personal view; the ready-made ones stay. */
    fun removePersonalView(id: String): LiveBoard {
        val view = views.firstOrNull { it.id == id } ?: return this
        if (view.preset != null) return this
        val rest = views - view
        return LiveBoard(rest, if (selectedId == id) rest.first().id else selectedId)
    }

    /** The text kept on the device: the selected view, then one line per view. */
    fun encode(): String = buildString {
        append("selected=").append(selectedId).append('\n')
        views.forEach { view ->
            append("view=").append(view.id).append('|').append(view.name.orEmpty()).append('|')
            append(view.blocks.joinToString(",") { it.id }).append('\n')
        }
    }

    companion object {
        const val MAX_NAME: Int = 40

        private fun cleanName(name: String): String =
            name.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(MAX_NAME)

        /** The board of a new device: the ready-made views, « L'essentiel » on screen. */
        fun default(): LiveBoard = LiveBoard(LivePreset.entries.map { LiveView(it.id, it.blocks, preset = it) }, LivePreset.ESSENTIEL.id)

        /** Reads [text] (from [encode]); anything it does not understand is left out, never an error. */
        fun decode(text: String?): LiveBoard {
            if (text.isNullOrBlank()) return default()
            var selected = LivePreset.ESSENTIEL.id
            val saved = mutableMapOf<String, LiveView>()
            text.lineSequence().forEach { line ->
                when {
                    line.startsWith("selected=") -> selected = line.removePrefix("selected=").trim()
                    line.startsWith("view=") -> {
                        val parts = line.removePrefix("view=").split('|')
                        if (parts.size != 3) return@forEach
                        val id = parts[0].trim()
                        if (id.isEmpty() || id in saved) return@forEach
                        val blocks = parts[2].split(',').mapNotNull { LiveBlock.fromId(it.trim()) }.distinct()
                        val preset = LivePreset.fromId(id)
                        val name = cleanName(parts[1]).ifEmpty { null }
                        if (preset == null && name == null) return@forEach
                        saved[id] = LiveView(id, blocks, preset = preset, name = if (preset == null) name else null)
                    }
                }
            }
            val presets = LivePreset.entries.map { preset -> saved[preset.id] ?: LiveView(preset.id, preset.blocks, preset = preset) }
            val personal = saved.values.filter { it.preset == null }
            val board = LiveBoard(presets + personal, selected)
            return if (board.views.any { it.id == selected }) board else board.copy(selectedId = LivePreset.ESSENTIEL.id)
        }
    }
}
