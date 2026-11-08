package org.politisationpopulairefrancaise.core.agir.manifestation

import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.organisations.OrganisationQuery

/**
 * "Besoin d'un avocat" (AG-13, AG-27): the legal teams and the networks of volunteer lawyers of
 * the directory (M3) that can be called, those of the user's [territory] first, then those that
 * act everywhere in France. Without a territory, every one of them, by name. Only verified
 * entries with a telephone number are kept: one gesture must call them. The territory is compared
 * on the device ([OrganisationQuery.near]), never sent.
 */
object LegalContacts {
    val TYPES: Set<String> = setOf("legal-team", "avocats-benevoles")

    fun of(organisations: List<Organisation>, territory: String?): List<Organisation> {
        val query = OrganisationQuery(types = TYPES, near = territory?.trim()?.takeIf { it.isNotEmpty() })
        return query.apply(organisations).filter { it.phoneUri != null }
    }
}
