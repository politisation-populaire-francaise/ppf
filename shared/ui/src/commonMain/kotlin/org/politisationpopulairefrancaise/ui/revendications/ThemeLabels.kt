package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.theme_agriculture
import org.politisationpopulairefrancaise.ui.resources.theme_culture
import org.politisationpopulairefrancaise.ui.resources.theme_democratie
import org.politisationpopulairefrancaise.ui.resources.theme_education
import org.politisationpopulairefrancaise.ui.resources.theme_environnement
import org.politisationpopulairefrancaise.ui.resources.theme_fiscalite
import org.politisationpopulairefrancaise.ui.resources.theme_justice
import org.politisationpopulairefrancaise.ui.resources.theme_logement
import org.politisationpopulairefrancaise.ui.resources.theme_numerique
import org.politisationpopulairefrancaise.ui.resources.theme_pouvoir_d_achat
import org.politisationpopulairefrancaise.ui.resources.theme_sante
import org.politisationpopulairefrancaise.ui.resources.theme_services_publics
import org.politisationpopulairefrancaise.ui.resources.theme_societe
import org.politisationpopulairefrancaise.ui.resources.theme_transports
import org.politisationpopulairefrancaise.ui.resources.theme_travail

/** The text of a theme id of RevendicationThemes, null for an id this version does not know. */
fun themeLabelResource(themeId: String): StringResource? = when (themeId) {
    "societe" -> Res.string.theme_societe
    "sante" -> Res.string.theme_sante
    "education" -> Res.string.theme_education
    "travail" -> Res.string.theme_travail
    "pouvoir-d-achat" -> Res.string.theme_pouvoir_d_achat
    "logement" -> Res.string.theme_logement
    "transports" -> Res.string.theme_transports
    "environnement" -> Res.string.theme_environnement
    "services-publics" -> Res.string.theme_services_publics
    "democratie" -> Res.string.theme_democratie
    "justice" -> Res.string.theme_justice
    "fiscalite" -> Res.string.theme_fiscalite
    "agriculture" -> Res.string.theme_agriculture
    "numerique" -> Res.string.theme_numerique
    "culture" -> Res.string.theme_culture
    else -> null
}

/** The localized label of a theme id of RevendicationThemes; unknown ids are returned as they are. */
@Composable
fun themeLabel(themeId: String): String = themeLabelResource(themeId)?.let { stringResource(it) } ?: themeId
