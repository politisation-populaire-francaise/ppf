package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.core.urne.AccountTypes
import javax.sql.DataSource

/**
 * Who may vote (owner's decision of 7 November 2026, D-116: « Un compte de type collectif ou
 * association ou bref tout truc qui n'est pas personne physique ne peut pas voter »). A key is
 * refused registration and tokens at every level when it is known not to be a natural person:
 * - a key of the editorial team (it speaks for PPF, an association; its members vote with their
 *   own personal accounts);
 * - an organisation of the directory of "Agir" (a key named in an entry signed by the team, D-045);
 * - an account that declares itself an organisation, a collective or an association
 *   ([AccountTypes], tag of its profile).
 * A second pseudonym of the same person votes like any personal account; the elected officials
 * vote publicly (D-115).
 */
class PersonAccounts(
    private val dataSource: DataSource?,
    private val isEditor: (String) -> Boolean,
    private val isOrganisation: (String) -> Boolean,
) {
    /** Why [pubkey] may not vote, or null when nothing says it is not a natural person. */
    fun refusal(pubkey: String): String? = when {
        isEditor(pubkey) -> "editorial_key"
        isOrganisation(pubkey) -> "organisation"
        declaredNotAPerson(pubkey) -> "declared_not_a_person"
        else -> null
    }

    /** The latest profile (kind 0) of [pubkey] declares a type other than a person. */
    private fun declaredNotAPerson(pubkey: String): Boolean {
        val source = dataSource ?: return false
        return source.connection.use { c ->
            c.prepareStatement(
                """
                SELECT t.value FROM events e JOIN event_tags t ON t.event_id = e.id AND t.name = ?
                WHERE e.kind = 0 AND e.pubkey = ? ORDER BY e.created_at DESC LIMIT 1
                """.trimIndent(),
            ).use { s ->
                s.setString(1, AccountTypes.TAG)
                s.setString(2, pubkey)
                s.executeQuery().use { r -> r.next() && !AccountTypes.isPerson(r.getString(1)) }
            }
        }
    }

    companion object {
        /** Everyone may vote (tests, a server without a database). */
        val ANYONE: PersonAccounts = PersonAccounts(null, { false }, { false })
    }
}
