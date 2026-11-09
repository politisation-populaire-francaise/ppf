package org.politisationpopulairefrancaise.core.agir.proches

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.random.Random

/**
 * The ladder of « Mes proches à mobiliser » (AG-63): what the person plans to do next with one of
 * their relatives, from talking to going together. The ids are stable: they are written on the device.
 */
enum class ProcheStep(val id: String) {
    TALK("en-parler"),
    SHARE("partager"),
    INVITE("inviter"),
    GO_TOGETHER("y-aller-ensemble"),
    DONE("fait"),
    ;

    /** The rung after this one; null for [DONE]. */
    val next: ProcheStep? get() = ProcheStep.entries.getOrNull(ordinal + 1)

    companion object {
        fun fromId(id: String?): ProcheStep? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One relative of the list, as the device keeps it (AG-63). Never a real name asked, never a phone
 * number, an address, a contact of the address book or an account key: nothing here links to anyone's
 * identity. [nickname] is what the person typed (« un surnom suffit »); [subjects] are theme ids of the
 * contents ([RevendicationThemes]) chosen in a closed list, never free text about the relative's
 * opinions and never a political label; [step] is the id of a [ProcheStep]; [reminder] an ISO date
 * ("2026-11-12") or null. [id] is random: it says nothing of the person.
 */
@Serializable
data class Proche(
    val id: String,
    val nickname: String,
    val subjects: List<String> = emptyList(),
    val step: String = ProcheStep.TALK.id,
    val reminder: String? = null,
) {
    val nextStep: ProcheStep get() = ProcheStep.fromId(step) ?: ProcheStep.TALK

    val reminderDate: CivilDate? get() = CivilDate.parseIso(reminder)

    /** A reminder whose day has come on [today], unless the ladder is done. */
    fun isDue(today: CivilDate): Boolean = nextStep != ProcheStep.DONE && reminderDate?.let { it <= today } == true

    // Never the nickname in a log or a crash report.
    override fun toString(): String = "Proche(subjects=${subjects.size}, step=$step, reminder=${reminder != null})"
}

/** What the form gives; [ProchesRules] cleans it before it is kept. */
data class ProcheInput(
    val nickname: String,
    val subjects: List<String> = emptyList(),
    val step: ProcheStep = ProcheStep.TALK,
    val reminder: CivilDate? = null,
) {
    override fun toString(): String = "ProcheInput(subjects=${subjects.size}, step=${step.id})"
}

/** What a change of the list did. */
sealed interface ProcheChange {
    /** The entry was added or updated, as it is now kept. */
    data class Saved(val entry: Proche) : ProcheChange

    data object Removed : ProcheChange

    /** The list already holds [ProchesRules.MAX_ENTRIES] relatives: nothing was added. */
    data object Full : ProcheChange

    /** No nickname once cleaned: nothing was kept. */
    data object NoNickname : ProcheChange

    /** The entry is no longer on the device (erased meanwhile). */
    data object Unknown : ProcheChange
}

/** The limits of the list: short on purpose (a few relatives, not a file of people). */
object ProchesRules {
    const val MAX_ENTRIES: Int = 12
    const val MAX_NICKNAME: Int = 40
    const val MAX_SUBJECTS: Int = 3

    /** The subjects that can be chosen: the themes of the contents, in the order the interface lists them. */
    val SUBJECTS: List<String> = RevendicationThemes.ALL.map { it.id }

    /** What the field keeps while the person types: one line, [MAX_NICKNAME] characters at most. */
    fun typed(text: String): String = text.map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }.joinToString("").takeChars(MAX_NICKNAME)

    /** The nickname as kept: no control character, spaces collapsed, trimmed, [MAX_NICKNAME] characters at most. */
    fun cleanNickname(text: String): String {
        val oneLine = buildString { text.forEach { append(if (it.isISOControl() || it.isWhitespace()) ' ' else it) } }
        return oneLine.split(' ').filter { it.isNotEmpty() }.joinToString(" ").takeChars(MAX_NICKNAME).trim()
    }

    /** The subjects as kept: known theme ids only, without repetition, [MAX_SUBJECTS] at most, in the order chosen. */
    fun cleanSubjects(ids: List<String>): List<String> = ids.filter { it in SUBJECTS }.distinct().take(MAX_SUBJECTS)

    /** Cuts after [max] characters without splitting a pair of surrogates (an emoji). */
    private fun String.takeChars(max: Int): String {
        if (length <= max) return this
        val cut = if (this[max - 1].isHighSurrogate()) max - 1 else max
        return substring(0, cut)
    }
}

/**
 * The list of « Mes proches à mobiliser » as the device keeps it, in one small JSON document (no
 * database table, so no migration), like the timer of AG-55. It never leaves the device.
 */
@Serializable
data class ProchesData(
    val version: Int = 1,
    val entries: List<Proche> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    val isFull: Boolean get() = entries.size >= ProchesRules.MAX_ENTRIES

    /** Adds a relative at the end of the list; refused when the list is full or the nickname empty. */
    fun add(input: ProcheInput, random: Random = Random.Default): Pair<ProchesData, ProcheChange> {
        val nickname = ProchesRules.cleanNickname(input.nickname)
        if (nickname.isEmpty()) return this to ProcheChange.NoNickname
        if (isFull) return this to ProcheChange.Full
        var id = newId(random)
        while (entries.any { it.id == id }) id = newId(random)
        val entry = Proche(id, nickname, ProchesRules.cleanSubjects(input.subjects), input.step.id, input.reminder?.toIso())
        return copy(entries = entries + entry) to ProcheChange.Saved(entry)
    }

    /** Replaces what is known of the relative [id], keeping its place in the list. */
    fun update(id: String, input: ProcheInput): Pair<ProchesData, ProcheChange> {
        val nickname = ProchesRules.cleanNickname(input.nickname)
        if (nickname.isEmpty()) return this to ProcheChange.NoNickname
        val current = entries.firstOrNull { it.id == id } ?: return this to ProcheChange.Unknown
        val entry = current.copy(
            nickname = nickname,
            subjects = ProchesRules.cleanSubjects(input.subjects),
            step = input.step.id,
            reminder = input.reminder?.toIso(),
        )
        return copy(entries = entries.map { if (it.id == id) entry else it }) to ProcheChange.Saved(entry)
    }

    /** Moves the relative [id] one rung up the ladder; nothing changes once « C'est fait ». */
    fun advance(id: String): Pair<ProchesData, ProcheChange> {
        val current = entries.firstOrNull { it.id == id } ?: return this to ProcheChange.Unknown
        val next = current.nextStep.next ?: return this to ProcheChange.Saved(current)
        val entry = current.copy(step = next.id)
        return copy(entries = entries.map { if (it.id == id) entry else it }) to ProcheChange.Saved(entry)
    }

    fun remove(id: String): Pair<ProchesData, ProcheChange> =
        if (entries.none { it.id == id }) this to ProcheChange.Unknown else copy(entries = entries.filter { it.id != id }) to ProcheChange.Removed

    /**
     * The list as the screen shows it on [today]: the reminders whose day has come first (« À faire
     * aujourd'hui », the oldest first), then the others in the order they were added, those already
     * done at the end.
     */
    fun ordered(today: CivilDate): List<Proche> {
        val due = entries.filter { it.isDue(today) }.sortedBy { it.reminderDate }
        val rest = entries.filter { !it.isDue(today) }
        return due + rest.filter { it.nextStep != ProcheStep.DONE } + rest.filter { it.nextStep == ProcheStep.DONE }
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    override fun toString(): String = "ProchesData(entries=${entries.size})"

    /** The same list, every entry cleaned again: what a damaged or older file holds is never shown as it is. */
    private fun sanitized(): ProchesData {
        val clean = entries.asSequence()
            .map { entry ->
                entry.copy(
                    nickname = ProchesRules.cleanNickname(entry.nickname),
                    subjects = ProchesRules.cleanSubjects(entry.subjects),
                    step = entry.nextStep.id,
                    reminder = entry.reminderDate?.toIso(),
                )
            }
            .filter { it.id.isNotBlank() && it.nickname.isNotEmpty() }
            .distinctBy { it.id }
            .take(ProchesRules.MAX_ENTRIES)
            .toList()
        return ProchesData(version = 1, entries = clean)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** What [encode] wrote; empty when nothing was written or it cannot be read (a damaged file is forgotten, not shown). */
        fun decode(text: String?): ProchesData =
            text?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
                ?.sanitized()
                ?: ProchesData()

        /** Sixteen random hexadecimal characters: an id that says nothing of the person. */
        private fun newId(random: Random): String = random.nextBytes(8).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

/**
 * Where the device keeps [ProchesData]: a private preference of the Android application (system
 * backups refused by the manifest), a private file on a computer, like the timer. Nothing is ever sent.
 */
interface ProchesStorage {
    fun load(): String?

    fun save(value: String)

    fun clear()
}

/** For tests and previews: forgotten when the process ends. */
class InMemoryProchesStorage(initial: String? = null) : ProchesStorage {
    var value: String? = initial
        private set

    /** How many times something was written, for the tests. */
    var writes: Int = 0
        private set

    override fun load(): String? = value

    override fun save(value: String) {
        this.value = value
        writes++
    }

    override fun clear() {
        value = null
    }
}

/**
 * One reader and writer of [ProchesStorage] at a time. The calls read and write the platform's
 * storage: run them off the main thread. An empty list is not written: the storage is cleared.
 */
class ProchesStore(private val storage: ProchesStorage) {
    private val lock = Mutex()

    suspend fun read(): ProchesData = lock.withLock { load() }

    /** Applies [change] to what is stored and writes the result when it differs; returns the list and what [change] says. */
    suspend fun <T> update(change: (ProchesData) -> Pair<ProchesData, T>): Pair<ProchesData, T> = lock.withLock {
        val current = load()
        val (next, result) = change(current)
        if (next != current) {
            if (next.isEmpty) storage.clear() else storage.save(next.encode())
        }
        next to result
    }

    /** Forgets the whole list (the quick erase, « Effacer mes clés »). */
    suspend fun clear() = lock.withLock { storage.clear() }

    private fun load(): ProchesData = ProchesData.decode(runCatching { storage.load() }.getOrNull())
}
