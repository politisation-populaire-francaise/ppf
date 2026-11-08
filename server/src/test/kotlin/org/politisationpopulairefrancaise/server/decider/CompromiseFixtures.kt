package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts

/** Accounts declared compromised in the tests of Décider (S-146, D-134), as their owners would with the phrase. */
object CompromiseFixtures {
    private const val DAY = 24L * 60 * 60

    /** [EXEMPLE] [account] commits to a recovery key 20 days before [at], then declares itself compromised since [since]. */
    fun declare(accounts: CompromisedAccounts, account: LocalEventSigner, since: Long, at: Long) = runBlocking {
        val recovery = LocalEventSigner(IdentityKeys.generate())
        val commitment = AccountRecovery.signCommitment(account, recovery.publicKey, createdAt = at - 20 * DAY)
        check(accounts.admitAndStore(commitment) is CompromisedAccounts.Admission.Stored) { "commitment refused" }
        val notice = AccountRecovery.signNotice(recovery, AccountRecovery.parseCommitment(commitment)!!, createdAt = at, since = since)
        check(accounts.admitAndStore(notice) is CompromisedAccounts.Admission.Stored) { "notice refused" }
    }
}
