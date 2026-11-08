package org.politisationpopulairefrancaise.ui.decider

import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.decider.CellStatus
import org.politisationpopulairefrancaise.core.decider.PacteStatus
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.resources.*

/** Test tags of "Décider", stable across label changes. */
object DeciderTags {
    const val SCREEN = "decider-screen"
    const val BACK = "decider-back"
    const val BOARD = "decider-board"
    const val LEGEND = "decider-legend"
    const val FINGERPRINT = "decider-fingerprint"
    const val FINGERPRINT_COPY = "decider-fingerprint-copy"
    const val PACTE_METHOD = "decider-pacte-method"
    const val PACTE_DATES = "decider-pacte-dates"
    const val PACTE_PROPOSED = "decider-pacte-proposed"
    const val SET_ASIDE = "decider-set-aside"
    const val SIGN = "decider-sign"
    const val SHARE = "decider-share"
    const val SHARE_COPY = "decider-share-copy"
    const val PROGRESS = "decider-progress"
    const val THRESHOLDS_OPEN = "decider-thresholds-open"
    const val CLOSED_OPEN = "decider-closed-open"
    const val PRIVACY = "decider-privacy"
    fun card(slug: String) = "decider-card-$slug"
    fun detail(slug: String) = "decider-detail-$slug"
    fun section(id: String) = "decider-section-$id"
    fun measure(id: String) = "decider-measure-$id"
    fun row(candidate: String) = "decider-row-$candidate"
    fun cell(measure: String) = "decider-cell-$measure"
    fun setAside(reason: String) = "decider-set-aside-$reason"
    fun platform(id: String) = "decider-platform-$id"
    fun threshold(index: Int) = "decider-threshold-$index"
    fun link(id: String) = "decider-link-$id"
}

internal val PacteStatus.label: StringResource
    get() = when (this) {
        PacteStatus.PROPOSED -> Res.string.decider_pacte_status_proposed
        PacteStatus.SENT -> Res.string.decider_pacte_status_sent
        PacteStatus.CLOSED -> Res.string.decider_pacte_status_closed
    }

internal val PacteStatus.badge: BadgeStyle
    get() = when (this) {
        PacteStatus.PROPOSED -> BadgeStyle.WARNING
        PacteStatus.SENT -> BadgeStyle.BRAND
        PacteStatus.CLOSED -> BadgeStyle.NEUTRAL
    }

internal val CellStatus.label: StringResource
    get() = when (this) {
        CellStatus.SIGNED -> Res.string.decider_cell_signed
        CellStatus.REFUSED -> Res.string.decider_cell_refused
        CellStatus.AWAITING -> Res.string.decider_cell_awaiting
        CellStatus.NO_ANSWER -> Res.string.decider_cell_no_answer
    }

internal val PetitionPlatform.label: StringResource
    get() = when (this) {
        PetitionPlatform.ASSEMBLEE -> Res.string.decider_platform_assemblee
        PetitionPlatform.SENAT -> Res.string.decider_platform_senat
        PetitionPlatform.CESE -> Res.string.decider_platform_cese
        PetitionPlatform.RIP -> Res.string.decider_platform_rip
        PetitionPlatform.ICE -> Res.string.decider_platform_ice
    }

internal val PetitionPlatform.shortLabel: StringResource
    get() = when (this) {
        PetitionPlatform.ASSEMBLEE -> Res.string.decider_platform_assemblee_short
        PetitionPlatform.SENAT -> Res.string.decider_platform_senat_short
        PetitionPlatform.CESE -> Res.string.decider_platform_cese_short
        PetitionPlatform.RIP -> Res.string.decider_platform_rip_short
        PetitionPlatform.ICE -> Res.string.decider_platform_ice_short
    }
