package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * [VoteAlertStorage] in the local database (ParliamentAlerts.sq): the ballots seen, the followed words,
 * the members of the European Parliament chosen and the settings of the alert. On the device only.
 */
class VoteAlertStore(private val database: PpfDatabase) : VoteAlertStorage {

    private val queries get() = database.parliamentAlertsQueries

    override fun startedAt(): Long? = queries.selectOption(STARTED_AT).executeAsOneOrNull()?.toLongOrNull()

    override fun start(at: Long) {
        if (startedAt() == null) queries.saveOption(STARTED_AT, at.toString())
    }

    override fun seen(): Set<String> = queries.selectSeen().executeAsList().toSet()

    override fun markSeen(ids: Collection<String>, at: Long) = database.transaction {
        ids.forEach { queries.insertSeen(it, at) }
    }

    override fun words(): List<String> = queries.selectWords().executeAsList()

    override fun addWord(word: String, at: Long): Boolean {
        val clean = word.trim()
        val folded = FrenchText.fold(clean)
        if (FrenchText.words(clean).isEmpty()) return false
        return database.transactionWithResult {
            if (queries.countWords().executeAsOne() >= VoteAlerts.MAX_FOLLOWED_WORDS) return@transactionWithResult false
            if (words().any { FrenchText.fold(it) == folded }) return@transactionWithResult false
            queries.insertWord(folded, clean, at)
            true
        }
    }

    override fun removeWord(word: String) {
        queries.deleteWord(FrenchText.fold(word.trim()))
    }

    override fun europeans(): Set<String> = queries.selectMembers().executeAsList().toSet()

    override fun setEuropeans(ids: Set<String>) = database.transaction {
        queries.deleteAllMembers()
        ids.forEach { queries.insertMember(it, 0) }
    }

    override fun wifiOnly(): Boolean = queries.selectOption(WIFI_ONLY).executeAsOneOrNull() != "false"

    override fun setWifiOnly(on: Boolean) {
        queries.saveOption(WIFI_ONLY, on.toString())
    }

    override fun forget() = database.transaction {
        queries.deleteAllSeen()
        queries.deleteAllWords()
        queries.deleteAllMembers()
        queries.deleteAllOptions()
    }

    private companion object {
        const val STARTED_AT = "started_at"
        const val WIFI_ONLY = "wifi_only"
    }
}
