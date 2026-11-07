package org.politisationpopulairefrancaise.ui.voir

import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.ImpactDirection
import org.politisationpopulairefrancaise.core.voir.LibertyCategory
import org.politisationpopulairefrancaise.core.voir.ProcedureStage
import org.politisationpopulairefrancaise.core.voir.PromiseStatus
import org.politisationpopulairefrancaise.core.voir.PublicRole
import org.politisationpopulairefrancaise.core.voir.RecourseStatus
import org.politisationpopulairefrancaise.core.voir.Reliability
import org.politisationpopulairefrancaise.core.voir.SourceKind
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VotePosition
import org.politisationpopulairefrancaise.ui.resources.*

/** The labels of the vocabulary of "Voir" (core/voir), from the interface's resources. */
internal val VoirAxis.label: StringResource get() = when (this) {
    VoirAxis.MOUVEMENT -> Res.string.voir_axis_mouvement
    VoirAxis.POUVOIR -> Res.string.voir_axis_pouvoir
    VoirAxis.LIBERTES -> Res.string.voir_axis_libertes
}

internal val VoirAxis.tab: StringResource get() = when (this) {
    VoirAxis.MOUVEMENT -> Res.string.voir_tab_mouvement
    VoirAxis.POUVOIR -> Res.string.voir_tab_pouvoir
    VoirAxis.LIBERTES -> Res.string.voir_tab_libertes
}

internal val VoirAxis.intro: StringResource get() = when (this) {
    VoirAxis.MOUVEMENT -> Res.string.voir_axis_mouvement_intro
    VoirAxis.POUVOIR -> Res.string.voir_axis_pouvoir_intro
    VoirAxis.LIBERTES -> Res.string.voir_axis_libertes_intro
}

internal val SourceKind.label: StringResource get() = when (this) {
    SourceKind.AUTHORITIES -> Res.string.voir_source_autorites
    SourceKind.ORGANISERS -> Res.string.voir_source_organisateurs
    SourceKind.INDEPENDENT -> Res.string.voir_source_independant
    SourceKind.MEDIA -> Res.string.voir_source_medias
    SourceKind.CITIZENS -> Res.string.voir_source_signalements
    SourceKind.OFFICIAL_TEXT -> Res.string.voir_source_texte_officiel
    SourceKind.JUSTICE -> Res.string.voir_source_justice
    SourceKind.PARLIAMENT -> Res.string.voir_source_parlement
    SourceKind.REPORT -> Res.string.voir_source_rapport
    SourceKind.STATEMENT -> Res.string.voir_source_declaration
    SourceKind.INVESTIGATION -> Res.string.voir_source_enquete
}

internal val Reliability.label: StringResource get() = when (this) {
    Reliability.HIGH -> Res.string.voir_reliability_high
    Reliability.MEDIUM -> Res.string.voir_reliability_medium
    Reliability.LOW -> Res.string.voir_reliability_low
}

internal val FactStatus.label: StringResource get() = when (this) {
    FactStatus.ESTABLISHED -> Res.string.voir_status_established
    FactStatus.ALLEGED -> Res.string.voir_status_alleged
    FactStatus.UNDER_INVESTIGATION -> Res.string.voir_status_investigation
}

internal val FactKind.label: StringResource get() = when (this) {
    FactKind.MOBILISATION -> Res.string.voir_kind_mobilisation
    FactKind.OFFICIAL_STATEMENT -> Res.string.voir_kind_official_statement
    FactKind.OFFICIAL_DECISION -> Res.string.voir_kind_official_decision
    FactKind.GOVERNMENT_RESPONSE -> Res.string.voir_kind_government_response
    FactKind.VOTE -> Res.string.voir_kind_vote
    FactKind.PROMISE -> Res.string.voir_kind_promise
    FactKind.STATEMENT_VS_VOTE -> Res.string.voir_kind_statement_vote
    FactKind.CASE -> Res.string.voir_kind_case
    FactKind.CONTRADICTED_STATEMENT -> Res.string.voir_kind_contradicted
    FactKind.PUBLIC_MONEY -> Res.string.voir_kind_public_money
    FactKind.INTERESTS -> Res.string.voir_kind_interests
    FactKind.LOBBYING -> Res.string.voir_kind_lobbying
    FactKind.LIBERTY -> Res.string.voir_kind_liberty
}

/** The section of a person's sheet that lists the facts of this kind (identical for everyone). */
internal val FactKind.section: StringResource get() = when (this) {
    FactKind.VOTE -> Res.string.voir_section_votes
    FactKind.PROMISE -> Res.string.voir_section_promises
    FactKind.STATEMENT_VS_VOTE -> Res.string.voir_section_statements_votes
    FactKind.CASE -> Res.string.voir_section_cases
    FactKind.CONTRADICTED_STATEMENT -> Res.string.voir_section_contradicted
    FactKind.INTERESTS -> Res.string.voir_section_interests
    FactKind.LOBBYING -> Res.string.voir_section_lobbying
    FactKind.PUBLIC_MONEY -> Res.string.voir_section_public_money
    else -> label
}

internal val ProcedureStage.label: StringResource get() = when (this) {
    ProcedureStage.INVESTIGATION -> Res.string.voir_procedure_enquete
    ProcedureStage.INDICTMENT -> Res.string.voir_procedure_mise_en_examen
    ProcedureStage.TRIAL -> Res.string.voir_procedure_proces
    ProcedureStage.FIRST_INSTANCE_CONVICTION -> Res.string.voir_procedure_premiere_instance
    ProcedureStage.APPEAL -> Res.string.voir_procedure_appel
    ProcedureStage.FINAL_CONVICTION -> Res.string.voir_procedure_definitive
    ProcedureStage.ACQUITTAL -> Res.string.voir_procedure_relaxe
    ProcedureStage.DISMISSAL -> Res.string.voir_procedure_non_lieu
    ProcedureStage.NO_FURTHER_ACTION -> Res.string.voir_procedure_classement
}

internal val ProcedureStage.explanation: StringResource get() = when (this) {
    ProcedureStage.INVESTIGATION -> Res.string.voir_glossary_enquete
    ProcedureStage.INDICTMENT -> Res.string.voir_glossary_mise_en_examen
    ProcedureStage.TRIAL -> Res.string.voir_glossary_proces
    ProcedureStage.FIRST_INSTANCE_CONVICTION -> Res.string.voir_glossary_premiere_instance
    ProcedureStage.APPEAL -> Res.string.voir_glossary_appel
    ProcedureStage.FINAL_CONVICTION -> Res.string.voir_glossary_definitive
    ProcedureStage.ACQUITTAL -> Res.string.voir_glossary_relaxe
    ProcedureStage.DISMISSAL -> Res.string.voir_glossary_non_lieu
    ProcedureStage.NO_FURTHER_ACTION -> Res.string.voir_glossary_classement
}

internal val PromiseStatus.label: StringResource get() = when (this) {
    PromiseStatus.KEPT -> Res.string.voir_promise_kept
    PromiseStatus.IN_PROGRESS -> Res.string.voir_promise_in_progress
    PromiseStatus.ABANDONED -> Res.string.voir_promise_abandoned
    PromiseStatus.CONTRARY -> Res.string.voir_promise_contrary
}

internal val VotePosition.label: StringResource get() = when (this) {
    VotePosition.FOR -> Res.string.voir_vote_for
    VotePosition.AGAINST -> Res.string.voir_vote_against
    VotePosition.ABSTENTION -> Res.string.voir_vote_abstention
    VotePosition.NOT_VOTING -> Res.string.voir_vote_not_voting
    VotePosition.ABSENT -> Res.string.voir_vote_absent
}

internal val LibertyCategory.label: StringResource get() = when (this) {
    LibertyCategory.PROTEST_BAN -> Res.string.voir_liberty_protest_ban
    LibertyCategory.DISSOLUTION -> Res.string.voir_liberty_dissolution
    LibertyCategory.RESTRICTIVE_LAW -> Res.string.voir_liberty_restrictive_law
    LibertyCategory.SURVEILLANCE -> Res.string.voir_liberty_surveillance
    LibertyCategory.OTHER -> Res.string.voir_liberty_other
}

internal val RecourseStatus.label: StringResource get() = when (this) {
    RecourseStatus.NONE -> Res.string.voir_recourse_none
    RecourseStatus.PENDING -> Res.string.voir_recourse_pending
    RecourseStatus.SUSPENDED -> Res.string.voir_recourse_suspended
    RecourseStatus.ANNULLED -> Res.string.voir_recourse_annulled
    RecourseStatus.REJECTED -> Res.string.voir_recourse_rejected
}

internal val PublicRole.label: StringResource get() = when (this) {
    PublicRole.DEPUTY -> Res.string.voir_role_depute
    PublicRole.SENATOR -> Res.string.voir_role_senateur
    PublicRole.MEP -> Res.string.voir_role_eurodepute
    PublicRole.MINISTER -> Res.string.voir_role_ministre
    PublicRole.PRESIDENT -> Res.string.voir_role_president
    PublicRole.MAYOR -> Res.string.voir_role_maire
    PublicRole.LOCAL_ELECTED -> Res.string.voir_role_elu_local
    PublicRole.OTHER -> Res.string.voir_role_autre
}

internal val TextStage.label: StringResource get() = when (this) {
    TextStage.TABLED -> Res.string.voir_stage_depot
    TextStage.COMMITTEE -> Res.string.voir_stage_commission
    TextStage.ASSEMBLY -> Res.string.voir_stage_assemblee
    TextStage.SENATE -> Res.string.voir_stage_senat
    TextStage.JOINT_COMMITTEE -> Res.string.voir_stage_cmp
    TextStage.FINAL_READING -> Res.string.voir_stage_lecture_definitive
    TextStage.ADOPTED -> Res.string.voir_stage_adopte
    TextStage.CONSTITUTIONAL_COUNCIL -> Res.string.voir_stage_conseil_constitutionnel
    TextStage.PROMULGATED -> Res.string.voir_stage_promulgue
    TextStage.REJECTED -> Res.string.voir_stage_rejete
    TextStage.WITHDRAWN -> Res.string.voir_stage_retire
}

internal val ImpactDirection.label: StringResource get() = when (this) {
    ImpactDirection.GAINS -> Res.string.voir_impact_gains
    ImpactDirection.LOSES -> Res.string.voir_impact_loses
    ImpactDirection.MIXED -> Res.string.voir_impact_mixed
}
