package org.politisationpopulairefrancaise.ui.agir.organisations

import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_association
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_avocats
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_collectif
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_legal_team
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_other
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_street_medics
import org.politisationpopulairefrancaise.ui.resources.organisations_filter_syndicat
import org.politisationpopulairefrancaise.ui.resources.organisations_type_association
import org.politisationpopulairefrancaise.ui.resources.organisations_type_avocats
import org.politisationpopulairefrancaise.ui.resources.organisations_type_collectif
import org.politisationpopulairefrancaise.ui.resources.organisations_type_legal_team
import org.politisationpopulairefrancaise.ui.resources.organisations_type_other
import org.politisationpopulairefrancaise.ui.resources.organisations_type_street_medics
import org.politisationpopulairefrancaise.ui.resources.organisations_type_syndicat

/** The labels of the organisation types (M3), in the order of [AgirFormats.ORG_TYPES]. */
object OrganisationLabels {

    /** The types offered as filters. */
    val types: List<String> get() = AgirFormats.ORG_TYPES

    /** Singular, for the badge of an organisation ("Syndicat"). */
    fun type(type: String): StringResource = when (type) {
        "association" -> Res.string.organisations_type_association
        "syndicat" -> Res.string.organisations_type_syndicat
        "collectif" -> Res.string.organisations_type_collectif
        "legal-team" -> Res.string.organisations_type_legal_team
        "street-medics" -> Res.string.organisations_type_street_medics
        "avocats-benevoles" -> Res.string.organisations_type_avocats
        else -> Res.string.organisations_type_other
    }

    /** Plural, for the filters ("Syndicats"). */
    fun filter(type: String): StringResource = when (type) {
        "association" -> Res.string.organisations_filter_association
        "syndicat" -> Res.string.organisations_filter_syndicat
        "collectif" -> Res.string.organisations_filter_collectif
        "legal-team" -> Res.string.organisations_filter_legal_team
        "street-medics" -> Res.string.organisations_filter_street_medics
        "avocats-benevoles" -> Res.string.organisations_filter_avocats
        else -> Res.string.organisations_filter_other
    }

    /** Two letters for the avatar of an organisation, from the first meaningful words of its name. */
    fun initials(name: String): String {
        val words = name.split(' ', '-', '\'', '’').filter { it.length > 2 && it.first().isLetter() }
        val letters = words.take(2).map { it.first().uppercaseChar() }.joinToString("")
        return letters.ifEmpty { name.trim().take(2).uppercase() }
    }
}
