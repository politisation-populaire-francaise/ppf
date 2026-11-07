package org.politisationpopulairefrancaise.core.urne

/**
 * The type an account declares in its profile (NIP-01, kind 0): a tag `["ppf-account-type", "<type>"]`.
 * Only a natural person votes (owner's decision of 7 November 2026, D-116): an account declared as
 * an organisation, a collective or an association gets no voting token at any level. Without the tag,
 * an account is a person's.
 */
object AccountTypes {
    const val TAG: String = "ppf-account-type"
    const val PERSON: String = "personne"
    const val ORGANISATION: String = "organisation"
    const val COLLECTIVE: String = "collectif"
    const val ASSOCIATION: String = "association"

    /** A declared type that is a natural person ([PERSON]); anything else is not. */
    fun isPerson(declared: String?): Boolean = declared == null || declared.trim() == PERSON
}
