package org.politisationpopulairefrancaise.server.urne

/**
 * Who may vote (owner's decision of 7 November 2026, D-116: « Un compte de type collectif ou
 * association ou bref tout truc qui n'est pas personne physique ne peut pas voter »). A key is
 * refused registration and tokens at every level when it is known not to be a natural person:
 * - a key of the editorial team (it speaks for PPF, an association; its members vote with their
 *   own personal accounts);
 * - an organisation of the directory of "Agir" (a key named in an entry signed by the team, D-045);
 * - an account that declared itself a collective in its profile (D-133, `AccountTypes` of the pole
 *   Communauté: once declared, it stays a collective).
 * A key declared compromised by its owner (S-146, D-134) is refused too, with its own reason: it is
 * no longer the person's. Its successor named by the notice votes with the cursor of the old key
 * ([predecessorOf]), so that a box served to the old key is never served again.
 * A second pseudonym of the same person votes like any personal account; the elected officials
 * vote publicly (D-115).
 */
class PersonAccounts(
    private val isEditor: (String) -> Boolean,
    private val isOrganisation: (String) -> Boolean,
    /** The keys that declared themselves collectives (D-133). */
    private val isCollective: (String) -> Boolean = { false },
    /** The keys declared compromised by their owner (S-146, D-134). */
    private val isCompromised: (String) -> Boolean = { false },
    /** The compromised key that named this one its successor, if any (S-146). */
    val predecessorOf: (String) -> String? = { null },
) {
    /** Why [pubkey] may not vote, or null when nothing says it is not a natural person. */
    fun refusal(pubkey: String): String? = when {
        isCompromised(pubkey) -> COMPROMISED
        isEditor(pubkey) -> "editorial_key"
        isOrganisation(pubkey) -> "organisation"
        isCollective(pubkey) -> "collective"
        else -> null
    }

    companion object {
        /** The reason given for a key declared compromised; any other reason is « not a person ». */
        const val COMPROMISED: String = "account_compromised"

        /** Everyone may vote (tests, a server without a database). */
        val ANYONE: PersonAccounts = PersonAccounts({ false }, { false })
    }
}
