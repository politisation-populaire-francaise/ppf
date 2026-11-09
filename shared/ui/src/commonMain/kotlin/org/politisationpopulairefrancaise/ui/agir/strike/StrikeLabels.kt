package org.politisationpopulairefrancaise.ui.agir.strike

import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.agir.strike.StrikeNote
import org.politisationpopulairefrancaise.core.agir.strike.StrikeObligation
import org.politisationpopulairefrancaise.core.agir.strike.StrikeRule
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSector
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSource
import org.politisationpopulairefrancaise.core.agir.strike.StrikeStatus
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.greve_note_accessories
import org.politisationpopulairefrancaise.ui.resources.greve_note_bonuses
import org.politisationpopulairefrancaise.ui.resources.greve_note_family_supplement
import org.politisationpopulairefrancaise.ui.resources.greve_note_fixed_days
import org.politisationpopulairefrancaise.ui.resources.greve_note_guards
import org.politisationpopulairefrancaise.ui.resources.greve_note_leave
import org.politisationpopulairefrancaise.ui.resources.greve_note_month
import org.politisationpopulairefrancaise.ui.resources.greve_note_no_discrimination
import org.politisationpopulairefrancaise.ui.resources.greve_note_no_rotating
import org.politisationpopulairefrancaise.ui.resources.greve_note_not_contributed
import org.politisationpopulairefrancaise.ui.resources.greve_note_payslip
import org.politisationpopulairefrancaise.ui.resources.greve_note_retirement_local
import org.politisationpopulairefrancaise.ui.resources.greve_note_retirement_state
import org.politisationpopulairefrancaise.ui.resources.greve_note_seizable
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_air_navigation_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_air_navigation_title
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_air_transport_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_declaration_48_title
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_declaration_title
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_land_transport_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_no_notice_private_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_no_notice_private_title
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_none_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_notice_school_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_notice_territorial_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_notice_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_notice_title
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_school_text
import org.politisationpopulairefrancaise.ui.resources.greve_obligation_territorial_text
import org.politisationpopulairefrancaise.ui.resources.greve_rule_local
import org.politisationpopulairefrancaise.ui.resources.greve_rule_not_simulated
import org.politisationpopulairefrancaise.ui.resources.greve_rule_private
import org.politisationpopulairefrancaise.ui.resources.greve_rule_state
import org.politisationpopulairefrancaise.ui.resources.greve_rule_transport
import org.politisationpopulairefrancaise.ui.resources.greve_sector_air_navigation
import org.politisationpopulairefrancaise.ui.resources.greve_sector_air_navigation_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_air_transport
import org.politisationpopulairefrancaise.ui.resources.greve_sector_air_transport_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_general
import org.politisationpopulairefrancaise.ui.resources.greve_sector_general_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_land_transport
import org.politisationpopulairefrancaise.ui.resources.greve_sector_land_transport_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_primary_school
import org.politisationpopulairefrancaise.ui.resources.greve_sector_primary_school_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_private_public_service
import org.politisationpopulairefrancaise.ui.resources.greve_sector_private_public_service_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_territorial_continuity
import org.politisationpopulairefrancaise.ui.resources.greve_sector_territorial_continuity_text
import org.politisationpopulairefrancaise.ui.resources.greve_source_cassation_1992
import org.politisationpopulairefrancaise.ui.resources.greve_source_cassation_2008
import org.politisationpopulairefrancaise.ui.resources.greve_source_cassation_2014
import org.politisationpopulairefrancaise.ui.resources.greve_source_cassation_2020
import org.politisationpopulairefrancaise.ui.resources.greve_source_cgfp_l114
import org.politisationpopulairefrancaise.ui.resources.greve_source_cgfp_l7
import org.politisationpopulairefrancaise.ui.resources.greve_source_cgfp_l711
import org.politisationpopulairefrancaise.ui.resources.greve_source_education_l133_4
import org.politisationpopulairefrancaise.ui.resources.greve_source_labour_l2511_1
import org.politisationpopulairefrancaise.ui.resources.greve_source_labour_l2512
import org.politisationpopulairefrancaise.ui.resources.greve_source_labour_l3121_27
import org.politisationpopulairefrancaise.ui.resources.greve_source_labour_l3242_1
import org.politisationpopulairefrancaise.ui.resources.greve_source_senate_1999
import org.politisationpopulairefrancaise.ui.resources.greve_source_service_public_private
import org.politisationpopulairefrancaise.ui.resources.greve_source_service_public_public
import org.politisationpopulairefrancaise.ui.resources.greve_source_state_council_1978
import org.politisationpopulairefrancaise.ui.resources.greve_source_state_council_2009
import org.politisationpopulairefrancaise.ui.resources.greve_source_state_council_2013
import org.politisationpopulairefrancaise.ui.resources.greve_source_transport_l1114_3
import org.politisationpopulairefrancaise.ui.resources.greve_source_transport_l1324_1
import org.politisationpopulairefrancaise.ui.resources.greve_source_transport_l1324_11
import org.politisationpopulairefrancaise.ui.resources.greve_source_transport_l1324_7
import org.politisationpopulairefrancaise.ui.resources.greve_status_hospital
import org.politisationpopulairefrancaise.ui.resources.greve_status_hospital_text
import org.politisationpopulairefrancaise.ui.resources.greve_status_private
import org.politisationpopulairefrancaise.ui.resources.greve_status_private_text
import org.politisationpopulairefrancaise.ui.resources.greve_status_state
import org.politisationpopulairefrancaise.ui.resources.greve_status_state_text
import org.politisationpopulairefrancaise.ui.resources.greve_status_territorial
import org.politisationpopulairefrancaise.ui.resources.greve_status_territorial_text

/** The labels of the strike simulator's vocabulary (core/agir/strike), in strings_greve.xml. */
object StrikeLabels {
    fun status(status: StrikeStatus): StringResource = when (status) {
        StrikeStatus.PRIVATE -> Res.string.greve_status_private
        StrikeStatus.STATE -> Res.string.greve_status_state
        StrikeStatus.TERRITORIAL -> Res.string.greve_status_territorial
        StrikeStatus.HOSPITAL -> Res.string.greve_status_hospital
    }

    fun statusText(status: StrikeStatus): StringResource = when (status) {
        StrikeStatus.PRIVATE -> Res.string.greve_status_private_text
        StrikeStatus.STATE -> Res.string.greve_status_state_text
        StrikeStatus.TERRITORIAL -> Res.string.greve_status_territorial_text
        StrikeStatus.HOSPITAL -> Res.string.greve_status_hospital_text
    }

    fun sector(sector: StrikeSector): StringResource = when (sector) {
        StrikeSector.GENERAL -> Res.string.greve_sector_general
        StrikeSector.LAND_TRANSPORT -> Res.string.greve_sector_land_transport
        StrikeSector.AIR_TRANSPORT -> Res.string.greve_sector_air_transport
        StrikeSector.PRIVATE_PUBLIC_SERVICE -> Res.string.greve_sector_private_public_service
        StrikeSector.PRIMARY_SCHOOL -> Res.string.greve_sector_primary_school
        StrikeSector.AIR_NAVIGATION -> Res.string.greve_sector_air_navigation
        StrikeSector.TERRITORIAL_CONTINUITY -> Res.string.greve_sector_territorial_continuity
    }

    fun sectorText(sector: StrikeSector): StringResource = when (sector) {
        StrikeSector.GENERAL -> Res.string.greve_sector_general_text
        StrikeSector.LAND_TRANSPORT -> Res.string.greve_sector_land_transport_text
        StrikeSector.AIR_TRANSPORT -> Res.string.greve_sector_air_transport_text
        StrikeSector.PRIVATE_PUBLIC_SERVICE -> Res.string.greve_sector_private_public_service_text
        StrikeSector.PRIMARY_SCHOOL -> Res.string.greve_sector_primary_school_text
        StrikeSector.AIR_NAVIGATION -> Res.string.greve_sector_air_navigation_text
        StrikeSector.TERRITORIAL_CONTINUITY -> Res.string.greve_sector_territorial_continuity_text
    }

    /** The explanation of a rule; the private ones take the monthly hours and the weekly hours. */
    fun rule(rule: StrikeRule): StringResource = when (rule) {
        StrikeRule.PRIVATE_PROPORTIONAL -> Res.string.greve_rule_private
        StrikeRule.TRANSPORT_PROPORTIONAL -> Res.string.greve_rule_transport
        StrikeRule.STATE_THIRTIETH -> Res.string.greve_rule_state
        StrikeRule.PUBLIC_PROPORTIONAL -> Res.string.greve_rule_local
        StrikeRule.NOT_SIMULATED -> Res.string.greve_rule_not_simulated
    }

    fun obligationTitle(obligation: StrikeObligation): StringResource = when (obligation) {
        StrikeObligation.NO_NOTICE_PRIVATE -> Res.string.greve_obligation_no_notice_private_title
        StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NOTICE_TERRITORIAL, StrikeObligation.NOTICE_PRIMARY_SCHOOL ->
            Res.string.greve_obligation_notice_title
        StrikeObligation.DECLARATION_LAND_TRANSPORT, StrikeObligation.DECLARATION_AIR_TRANSPORT,
        StrikeObligation.DECLARATION_PRIMARY_SCHOOL, StrikeObligation.DECLARATION_TERRITORIAL ->
            Res.string.greve_obligation_declaration_48_title
        StrikeObligation.DECLARATION_AIR_NAVIGATION -> Res.string.greve_obligation_air_navigation_title
        StrikeObligation.NO_KNOWN_DECLARATION -> Res.string.greve_obligation_declaration_title
    }

    fun obligationText(obligation: StrikeObligation): StringResource = when (obligation) {
        StrikeObligation.NO_NOTICE_PRIVATE -> Res.string.greve_obligation_no_notice_private_text
        StrikeObligation.NOTICE_PUBLIC_SERVICE -> Res.string.greve_obligation_notice_text
        StrikeObligation.NOTICE_TERRITORIAL -> Res.string.greve_obligation_notice_territorial_text
        StrikeObligation.NOTICE_PRIMARY_SCHOOL -> Res.string.greve_obligation_notice_school_text
        StrikeObligation.DECLARATION_LAND_TRANSPORT -> Res.string.greve_obligation_land_transport_text
        StrikeObligation.DECLARATION_AIR_TRANSPORT -> Res.string.greve_obligation_air_transport_text
        StrikeObligation.DECLARATION_PRIMARY_SCHOOL -> Res.string.greve_obligation_school_text
        StrikeObligation.DECLARATION_TERRITORIAL -> Res.string.greve_obligation_territorial_text
        StrikeObligation.DECLARATION_AIR_NAVIGATION -> Res.string.greve_obligation_air_navigation_text
        StrikeObligation.NO_KNOWN_DECLARATION -> Res.string.greve_obligation_none_text
    }

    fun note(note: StrikeNote): StringResource = when (note) {
        StrikeNote.ACCESSORIES -> Res.string.greve_note_accessories
        StrikeNote.PAYSLIP_SILENT -> Res.string.greve_note_payslip
        StrikeNote.NO_DISCRIMINATION -> Res.string.greve_note_no_discrimination
        StrikeNote.BONUSES -> Res.string.greve_note_bonuses
        StrikeNote.FIXED_DAYS_NOT_SIMULATED -> Res.string.greve_note_fixed_days
        StrikeNote.FAMILY_SUPPLEMENT_KEPT -> Res.string.greve_note_family_supplement
        StrikeNote.NOT_CONTRIBUTED -> Res.string.greve_note_not_contributed
        StrikeNote.SEIZABLE_CAP -> Res.string.greve_note_seizable
        StrikeNote.MONTH_OF_STRIKE -> Res.string.greve_note_month
        StrikeNote.LEAVE_BEFORE_NOTICE -> Res.string.greve_note_leave
        StrikeNote.GUARDS_NOT_SIMULATED -> Res.string.greve_note_guards
        StrikeNote.RETIREMENT_STATE -> Res.string.greve_note_retirement_state
        StrikeNote.RETIREMENT_LOCAL -> Res.string.greve_note_retirement_local
        StrikeNote.NO_ROTATING_STRIKES -> Res.string.greve_note_no_rotating
    }

    fun source(source: StrikeSource): StringResource = when (source) {
        StrikeSource.SERVICE_PUBLIC_PRIVATE -> Res.string.greve_source_service_public_private
        StrikeSource.SERVICE_PUBLIC_PUBLIC -> Res.string.greve_source_service_public_public
        StrikeSource.LABOUR_CODE_L2511_1 -> Res.string.greve_source_labour_l2511_1
        StrikeSource.LABOUR_CODE_L2512 -> Res.string.greve_source_labour_l2512
        StrikeSource.LABOUR_CODE_L3242_1 -> Res.string.greve_source_labour_l3242_1
        StrikeSource.LABOUR_CODE_L3121_27 -> Res.string.greve_source_labour_l3121_27
        StrikeSource.CASSATION_1992 -> Res.string.greve_source_cassation_1992
        StrikeSource.CASSATION_2008_FIXED_DAYS -> Res.string.greve_source_cassation_2008
        StrikeSource.CASSATION_2014_BONUSES -> Res.string.greve_source_cassation_2014
        StrikeSource.CASSATION_2020 -> Res.string.greve_source_cassation_2020
        StrikeSource.TRANSPORT_CODE_L1324_1 -> Res.string.greve_source_transport_l1324_1
        StrikeSource.TRANSPORT_CODE_L1324_7 -> Res.string.greve_source_transport_l1324_7
        StrikeSource.TRANSPORT_CODE_L1324_11 -> Res.string.greve_source_transport_l1324_11
        StrikeSource.TRANSPORT_CODE_L1114_3 -> Res.string.greve_source_transport_l1114_3
        StrikeSource.EDUCATION_CODE_L133_4 -> Res.string.greve_source_education_l133_4
        StrikeSource.CGFP_L711 -> Res.string.greve_source_cgfp_l711
        StrikeSource.CGFP_L114 -> Res.string.greve_source_cgfp_l114
        StrikeSource.CGFP_L7 -> Res.string.greve_source_cgfp_l7
        StrikeSource.STATE_COUNCIL_1978 -> Res.string.greve_source_state_council_1978
        StrikeSource.STATE_COUNCIL_2009 -> Res.string.greve_source_state_council_2009
        StrikeSource.STATE_COUNCIL_2013 -> Res.string.greve_source_state_council_2013
        StrikeSource.SENATE_ANSWER_1999 -> Res.string.greve_source_senate_1999
    }
}
