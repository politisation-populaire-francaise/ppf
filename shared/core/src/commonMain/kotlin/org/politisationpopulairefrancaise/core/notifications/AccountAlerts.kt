package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.NoticeCheck
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The alerts of the category [NotificationCategory.ACCOUNT] (S-146, decision of the team lead of
 * 8 November 2026): always kept in the centre, shown by the system only when the user enabled the
 * category. Their titles are the codes below, which the interface and the platform translate; the
 * detail is at most a public key, shortened by the interface.
 */
object AccountAlerts {
    /** The check of the recovery phrase postponed at signup is due (Mon espace, « Vérifier ma phrase »). */
    const val PHRASE_CHECK: String = "account:phrase-check"

    /** The account of this device is declared compromised (status or refusal of the server). */
    const val OWN_COMPROMISED: String = "account:own-compromised"

    /** A contact's account is declared compromised; the detail is the contact's key. */
    const val CONTACT_COMPROMISED: String = "account:contact-compromised"

    /** Where an alert about the account of this device leads: Mon espace. */
    const val MY_SPACE_TARGET: String = "ppf:mon-espace"

    /** The alert to deliver once the account [account] of this device is known to be compromised. */
    fun ownCompromised(account: String, detectedAt: Long): NotificationCandidate = NotificationCandidate(
        id = "$OWN_COMPROMISED:$account",
        category = NotificationCategory.ACCOUNT,
        title = OWN_COMPROMISED,
        target = MY_SPACE_TARGET,
        createdAt = detectedAt,
    )
}

/**
 * The reminder of a check of the recovery phrase postponed at signup, once its date has come
 * (D-134, decision 1). Read from the device's [IdentityStore], so it works on an empty wake-up too.
 * Records written before S-146 have no date: Mon espace still shows their reminder card.
 */
class PhraseReminderSource(
    private val identityStore: IdentityStore,
    private val clock: () -> Long = ::currentEpochSeconds,
) : NotificationSource {

    override val name: String = "account-phrase"

    override suspend fun collect(after: Long?): List<NotificationCandidate> {
        val record = identityStore.load() ?: return emptyList()
        if (!record.awaitsPhraseCheck) return emptyList()
        val due = record.phraseCheckDueAt ?: return emptyList()
        if (due > clock()) return emptyList()
        return listOf(
            NotificationCandidate(
                id = "${AccountAlerts.PHRASE_CHECK}:$due",
                category = NotificationCategory.ACCOUNT,
                title = AccountAlerts.PHRASE_CHECK,
                target = AccountAlerts.MY_SPACE_TARGET,
                createdAt = due,
            ),
        )
    }
}

/**
 * The compromise notices (kind 1777) the device keeps with its public events, checked again against
 * the commitment they name, in the order the device received them: an alert for each contact
 * declared compromised. Dated by their reception, so a notice learnt late is still an alert.
 * Nothing without an account ([me] null); a notice about [me] itself is the alert of the device's
 * own account.
 */
class CompromiseNoticesSource(
    private val database: PpfDatabase,
    private val me: () -> String?,
    private val clock: () -> Long = ::currentEpochSeconds,
) : NotificationSource {

    override val name: String = "account-notices"

    override suspend fun collect(after: Long?): List<NotificationCandidate> {
        val self = me() ?: return emptyList()
        val store = LocalEventStore(database, clock)
        val now = clock()
        val queries = database.notificationsQueries
        return queries.selectEventIdsReceivedAfter(AccountRecovery.NOTICE_KIND.toLong(), after ?: Long.MIN_VALUE, MAX_PER_ROUND)
            .executeAsList()
            .mapNotNull { row ->
                val event = store.find(row.id) ?: return@mapNotNull null
                val commitment = event.firstTagValue("e")?.let(store::find) ?: return@mapNotNull null
                val notice = (AccountRecovery.checkNotice(event, commitment, now) as? NoticeCheck.Valid)?.notice ?: return@mapNotNull null
                if (notice.compromised == self) {
                    AccountAlerts.ownCompromised(self, row.received_at)
                } else {
                    val conversation = queries.latestConversationOf(notice.compromised).executeAsOneOrNull()
                    NotificationCandidate(
                        id = "${AccountAlerts.CONTACT_COMPROMISED}:${notice.id}",
                        category = NotificationCategory.ACCOUNT,
                        title = AccountAlerts.CONTACT_COMPROMISED,
                        detail = notice.compromised,
                        target = conversation?.let { "ppf:messages/$it" },
                        createdAt = row.received_at,
                    )
                }
            }
    }

    private companion object {
        const val MAX_PER_ROUND: Long = 50
    }
}
