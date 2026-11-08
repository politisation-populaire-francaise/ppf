package org.politisationpopulairefrancaise.core.identity

import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Whether an account is a natural person or a collective (association, union, collective...):
 * only persons vote (decision of the owner, D-133; contract docs/integration/types-de-compte.md).
 * Declared in the account's signed profile (kind 0) by the tag `ppf-account-type`; absent means
 * [PERSON]. Once a key declared [COLLECTIVE], the server refuses any later profile that drops it.
 */
enum class AccountType(val tagValue: String) {
    PERSON("person"),
    COLLECTIVE("collective"),
    ;

    /** The tag to put in the account's profile. */
    fun tag(): List<String> = listOf(TAG, tagValue)

    companion object {
        const val TAG: String = "ppf-account-type"

        /** The type declared by a profile event; anything but a profile, or no tag, is a person. */
        fun of(profile: SignedEvent): AccountType =
            if (profile.kind == EventKinds.METADATA && profile.firstTagValue(TAG) == COLLECTIVE.tagValue) COLLECTIVE else PERSON
    }
}
