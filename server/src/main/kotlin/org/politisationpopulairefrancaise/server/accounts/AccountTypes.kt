package org.politisationpopulairefrancaise.server.accounts

import org.politisationpopulairefrancaise.core.identity.AccountType
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import javax.sql.DataSource

/**
 * The accounts that declared themselves collectives (D-133, docs/integration/types-de-compte.md):
 * they never vote, and a collective cannot become a "person" again. Only public keys are kept;
 * the profile itself is public.
 */
class AccountTypes(private val dataSource: DataSource) {

    /** True when [pubkeyHex] declared a collective profile. Called by Décider before issuing vote tokens. */
    fun isCollective(pubkeyHex: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM account_type WHERE pubkey = ? AND type = ?").use { statement ->
            statement.setString(1, pubkeyHex)
            statement.setString(2, AccountType.COLLECTIVE.tagValue)
            statement.executeQuery().use { it.next() }
        }
    }

    /** Refuses a profile that would turn a collective back into a person; null when the event may go on. */
    fun check(event: SignedEvent): PublicationPolicy.Decision.Refused? {
        if (event.kind != EventKinds.METADATA) return null
        if (AccountType.of(event) == AccountType.COLLECTIVE) return null
        return if (isCollective(event.pubkey)) {
            PublicationPolicy.Decision.Refused(409, "account_type_locked", "A collective account stays a collective account.")
        } else {
            null
        }
    }

    /** Records a stored collective profile (idempotent: the first declaration date is kept). */
    fun stored(event: SignedEvent) {
        if (event.kind != EventKinds.METADATA || AccountType.of(event) != AccountType.COLLECTIVE) return
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO account_type(pubkey, type, declared_at) VALUES (?, ?, ?) ON CONFLICT (pubkey) DO NOTHING",
            ).use { statement ->
                statement.setString(1, event.pubkey)
                statement.setString(2, AccountType.COLLECTIVE.tagValue)
                statement.setLong(3, event.createdAt)
                statement.executeUpdate()
            }
        }
    }
}
