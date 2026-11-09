package org.politisationpopulairefrancaise.ui.agir.solidarity

import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_filter_autre
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_filter_greve
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_filter_juridique
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_status_closed
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_status_open
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_status_withdrawn
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_type_autre
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_type_greve
import org.politisationpopulairefrancaise.ui.resources.solidarite_fund_type_juridique
import org.politisationpopulairefrancaise.ui.resources.solidarite_mission_status_closed
import org.politisationpopulairefrancaise.ui.resources.solidarite_mission_status_filled
import org.politisationpopulairefrancaise.ui.resources.solidarite_mission_status_open
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_accueil
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_autre
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_developpement
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_graphisme
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_juriste
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_moderation
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_traduction
import org.politisationpopulairefrancaise.ui.resources.solidarite_skill_verification

/** The labels of the solidarity rubric, in the order of [SolidarityFormats]. */
object SolidarityLabels {

    /** The fund types offered as filters. */
    val fundTypes: List<String> get() = SolidarityFormats.FUND_TYPES

    /** The skills offered as "Mes compétences". */
    val skills: List<String> get() = SolidarityFormats.SKILLS

    /** Singular, for the badge of a fund ("Caisse de grève"). */
    fun fundType(type: String): StringResource = when (type) {
        SolidarityFormats.FUND_STRIKE -> Res.string.solidarite_fund_type_greve
        SolidarityFormats.FUND_LEGAL -> Res.string.solidarite_fund_type_juridique
        else -> Res.string.solidarite_fund_type_autre
    }

    /** Plural, for the filters ("Caisses de grève"). */
    fun fundFilter(type: String): StringResource = when (type) {
        SolidarityFormats.FUND_STRIKE -> Res.string.solidarite_fund_filter_greve
        SolidarityFormats.FUND_LEGAL -> Res.string.solidarite_fund_filter_juridique
        else -> Res.string.solidarite_fund_filter_autre
    }

    /** The state of a fund, in words (never by colour alone). */
    fun fundStatus(status: String): StringResource = when (status) {
        SolidarityFormats.FUND_OPEN -> Res.string.solidarite_fund_status_open
        SolidarityFormats.FUND_CLOSED -> Res.string.solidarite_fund_status_closed
        else -> Res.string.solidarite_fund_status_withdrawn
    }

    fun skill(skill: String): StringResource = when (skill) {
        "juriste" -> Res.string.solidarite_skill_juriste
        "traduction" -> Res.string.solidarite_skill_traduction
        "developpement" -> Res.string.solidarite_skill_developpement
        "graphisme" -> Res.string.solidarite_skill_graphisme
        "verification" -> Res.string.solidarite_skill_verification
        "moderation" -> Res.string.solidarite_skill_moderation
        "accueil" -> Res.string.solidarite_skill_accueil
        else -> Res.string.solidarite_skill_autre
    }

    /** The state of a mission, in words. */
    fun missionStatus(status: String): StringResource = when (status) {
        SolidarityFormats.MISSION_OPEN -> Res.string.solidarite_mission_status_open
        SolidarityFormats.MISSION_FILLED -> Res.string.solidarite_mission_status_filled
        else -> Res.string.solidarite_mission_status_closed
    }
}
