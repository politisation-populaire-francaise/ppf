package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.AuthorScope
import org.politisationpopulairefrancaise.core.revendications.BuiltInKind
import org.politisationpopulairefrancaise.core.revendications.BuiltInRevendicationView
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationQuery
import org.politisationpopulairefrancaise.core.revendications.RevendicationSort
import org.politisationpopulairefrancaise.core.revendications.RevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.revendication_status_campagne
import org.politisationpopulairefrancaise.ui.resources.revendication_status_en_debat
import org.politisationpopulairefrancaise.ui.resources.revendication_status_nouvelle
import org.politisationpopulairefrancaise.ui.resources.revendication_status_retenue
import org.politisationpopulairefrancaise.ui.resources.revendication_status_tres_soutenue
import org.politisationpopulairefrancaise.ui.resources.rv_authors_anyone
import org.politisationpopulairefrancaise.ui.resources.rv_authors_followed
import org.politisationpopulairefrancaise.ui.resources.rv_authors_mine
import org.politisationpopulairefrancaise.ui.resources.rv_sort_newest
import org.politisationpopulairefrancaise.ui.resources.rv_sort_popular
import org.politisationpopulairefrancaise.ui.resources.rv_sort_updated
import org.politisationpopulairefrancaise.ui.resources.rv_territory_national
import org.politisationpopulairefrancaise.ui.resources.rv_view_all
import org.politisationpopulairefrancaise.ui.resources.rv_view_all_desc
import org.politisationpopulairefrancaise.ui.resources.rv_view_custom_desc
import org.politisationpopulairefrancaise.ui.resources.rv_view_followed
import org.politisationpopulairefrancaise.ui.resources.rv_view_followed_desc
import org.politisationpopulairefrancaise.ui.resources.rv_view_mine
import org.politisationpopulairefrancaise.ui.resources.rv_view_mine_desc
import org.politisationpopulairefrancaise.ui.resources.rv_view_national
import org.politisationpopulairefrancaise.ui.resources.rv_view_national_desc
import org.politisationpopulairefrancaise.ui.resources.rv_view_theme_desc

/** The name of a view as its tab shows it. */
@Composable
fun viewLabel(view: RevendicationView): String = when (view) {
    is CustomRevendicationView -> view.name
    is BuiltInRevendicationView -> when (view.kind) {
        BuiltInKind.ALL -> stringResource(Res.string.rv_view_all)
        BuiltInKind.FOLLOWED -> stringResource(Res.string.rv_view_followed)
        BuiltInKind.MINE -> stringResource(Res.string.rv_view_mine)
        BuiltInKind.NATIONAL -> stringResource(Res.string.rv_view_national)
        BuiltInKind.THEME -> themeLabel(view.themeId.orEmpty())
    }
}

/** One line saying what a view shows. */
@Composable
fun viewDescription(view: RevendicationView): String = when (view) {
    is CustomRevendicationView -> stringResource(Res.string.rv_view_custom_desc)
    is BuiltInRevendicationView -> when (view.kind) {
        BuiltInKind.ALL -> stringResource(Res.string.rv_view_all_desc)
        BuiltInKind.FOLLOWED -> stringResource(Res.string.rv_view_followed_desc)
        BuiltInKind.MINE -> stringResource(Res.string.rv_view_mine_desc)
        BuiltInKind.NATIONAL -> stringResource(Res.string.rv_view_national_desc)
        BuiltInKind.THEME -> stringResource(Res.string.rv_view_theme_desc, themeLabel(view.themeId.orEmpty()))
    }
}

fun sortLabelResource(sort: RevendicationSort): StringResource = when (sort) {
    RevendicationSort.NEWEST -> Res.string.rv_sort_newest
    RevendicationSort.POPULAR -> Res.string.rv_sort_popular
    RevendicationSort.UPDATED -> Res.string.rv_sort_updated
}

fun statusLabelResource(status: RevendicationStatus): StringResource = when (status) {
    RevendicationStatus.NOUVELLE -> Res.string.revendication_status_nouvelle
    RevendicationStatus.EN_DEBAT -> Res.string.revendication_status_en_debat
    RevendicationStatus.TRES_SOUTENUE -> Res.string.revendication_status_tres_soutenue
    RevendicationStatus.RETENUE -> Res.string.revendication_status_retenue
    RevendicationStatus.CAMPAGNE -> Res.string.revendication_status_campagne
}

fun authorsLabelResource(scope: AuthorScope): StringResource = when (scope) {
    AuthorScope.ANYONE -> Res.string.rv_authors_anyone
    AuthorScope.FOLLOWED -> Res.string.rv_authors_followed
    AuthorScope.MINE -> Res.string.rv_authors_mine
}

/** The territory key of the national demands ("france"). */
internal val NATIONAL_KEY: String = RevendicationQuery.territoryKey(RevendicationViews.NATIONAL_TERRITORY)

/**
 * The territories present in [demands], each once, keyed by their folded form and shown with the
 * first spelling met: national first, then in alphabetical order.
 */
internal fun territoriesOf(demands: List<Article>): Map<String, String> =
    demands.mapNotNull { it.territory?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy(RevendicationQuery::territoryKey)
        .sortedWith(compareBy<String> { RevendicationQuery.territoryKey(it) != NATIONAL_KEY }.thenBy(RevendicationQuery::territoryKey))
        .associateBy(RevendicationQuery::territoryKey)

/** The label of a territory key: "France (national)", the spelling of the demands, or the key itself. */
@Composable
fun territoryLabel(key: String, names: Map<String, String>): String =
    if (key == NATIONAL_KEY) stringResource(Res.string.rv_territory_national) else names[key] ?: key
