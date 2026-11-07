package org.politisationpopulairefrancaise.core.agir.manifestation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The rally point (AG-36): a place agreed in advance to find one's relatives, or a lost child,
 * after a protest. It is typed by the user and kept on the device only: never sent, never
 * synchronized, never part of a backup (the Android application forbids system backups). It is a
 * place name in words, never a position read from the device.
 */
@Serializable
data class RallyPoint(
    /** The place, in words: "Devant la pharmacie de la place de la République". */
    val place: String,
    /** Anything else agreed: a time, who to wait for, what to do if nobody comes. */
    val details: String = "",
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MAX_PLACE: Int = 200
        const val MAX_DETAILS: Int = 500

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The rally point typed by the user, trimmed; null when the place is empty. Texts that are
         * too long are cut, control characters (but line breaks in [details]) are dropped.
         */
        fun of(place: String, details: String = ""): RallyPoint? {
            val cleanPlace = clean(place, keepLineBreaks = false).take(MAX_PLACE).trim()
            if (cleanPlace.isEmpty()) return null
            return RallyPoint(cleanPlace, clean(details, keepLineBreaks = true).take(MAX_DETAILS).trim())
        }

        /** Reads what [encode] wrote; null for anything else (a damaged file is forgotten, not shown). */
        fun decode(text: String?): RallyPoint? {
            if (text.isNullOrBlank()) return null
            val read = runCatching { json.decodeFromString(serializer(), text) }.getOrNull() ?: return null
            return of(read.place, read.details)
        }

        private fun clean(text: String, keepLineBreaks: Boolean): String =
            text.filter { c -> !c.isISOControl() || (keepLineBreaks && c == '\n') }
    }
}

/** Where the device keeps the rally point: a small private file or preference of the application. */
interface RallyPointStorage {
    fun load(): String?

    fun save(value: String)

    fun clear()
}

/** For tests and previews: forgotten when the process ends. */
class InMemoryRallyPointStorage(private var value: String? = null) : RallyPointStorage {
    override fun load(): String? = value

    override fun save(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}
