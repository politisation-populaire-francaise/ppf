package org.politisationpopulairefrancaise.core.urne

/**
 * The type an account declares in its signed profile (NIP-01, kind 0): `["ppf-account-type", "person" | "collective"]`,
 * absent for a person (contract of the pole Communauté, `docs/integration/types-de-compte.md`, which owns the
 * account types and will provide `core/identity/AccountType`). Only a natural person votes (owner's decision of
 * 7 November 2026, D-116): a collective account gets no voting token at any level. To be replaced by the account
 * types of Communauté when they enter `main`.
 */
object AccountTypes {
    const val TAG: String = "ppf-account-type"
    const val PERSON: String = "person"
    const val COLLECTIVE: String = "collective"

    /** A declared type that is a natural person ([PERSON], or no declaration); anything else is not. */
    fun isPerson(declared: String?): Boolean = declared == null || declared.trim() == PERSON
}
