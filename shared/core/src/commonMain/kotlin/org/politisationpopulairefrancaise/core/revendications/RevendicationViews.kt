package org.politisationpopulairefrancaise.core.revendications

import kotlin.random.Random

/**
 * A view of the demands wall: a named, saved [query], shown as a tab (owner's request of
 * 2026-11-06: "Vue : thème société, médical, revendications portées par ceux que le user suit").
 * Temporary filters are applied on top of it with [RevendicationQuery.overlay].
 */
sealed interface RevendicationView {
    /** Stable id: written in the views saved on the device. */
    val id: String
    val query: RevendicationQuery

    /** The criteria this view fixes: the filter panel shows them locked. */
    val fixedCriteria: Set<RevendicationCriterion> get() = query.constrained
}

/** What a built-in view is about, so that the interface can name and describe it. */
/** MVP 1 (owner, 2026-11-11): Plus de pour, Plus de contre, Récentes, Les plus débattues, Du mouvement, Au Parlement, Rejetées ou closes, Près de chez moi, Mes votes. */
enum class BuiltInKind { ALL, FOLLOWED, MINE, NATIONAL, THEME, MOST_FOR, MOST_AGAINST, NEWEST, MOST_DEBATED, MOVEMENT, PARLIAMENT, CLOSED, NEAR_ME, MY_VOTES }

/** A view provided by the application; its label comes from the interface's texts. */
data class BuiltInRevendicationView(
    override val id: String,
    override val query: RevendicationQuery,
    val kind: BuiltInKind,
    /** The theme of a [BuiltInKind.THEME] view. */
    val themeId: String? = null,
) : RevendicationView

/** A view made by the user: a name of 1 to [RevendicationViews.NAME_MAX_LENGTH] characters and a query. */
data class CustomRevendicationView(
    override val id: String,
    val name: String,
    override val query: RevendicationQuery,
) : RevendicationView {
    init {
        require(id.startsWith(RevendicationViews.CUSTOM_PREFIX)) { "A custom view id starts with ${RevendicationViews.CUSTOM_PREFIX}" }
        require(RevendicationViews.normalizeName(name) == name) { "A view name is trimmed and 1 to ${RevendicationViews.NAME_MAX_LENGTH} characters long" }
    }
}

/** The built-in views, the default layout and the rules every layout follows. */
object RevendicationViews {
    const val ALL_ID: String = "tout"
    const val FOLLOWED_ID: String = "suivis"
    const val MINE_ID: String = "mes-revendications"
    const val NATIONAL_ID: String = "nationales"
    const val THEME_PREFIX: String = "theme:"
    const val CUSTOM_PREFIX: String = "custom:"

    /** The territory of the national demands, as written on them. */
    const val NATIONAL_TERRITORY: String = "France"

    /** At most this many custom views. */
    const val MAX_CUSTOM_VIEWS: Int = 20
    const val NAME_MAX_LENGTH: Int = 40

    /** "Toutes": no criterion. Always present and always first. */
    val ALL = BuiltInRevendicationView(ALL_ID, RevendicationQuery(), BuiltInKind.ALL)

    /** "Comptes suivis": demands carried by the accounts the user follows. */
    val FOLLOWED = BuiltInRevendicationView(FOLLOWED_ID, RevendicationQuery(authors = AuthorScope.FOLLOWED), BuiltInKind.FOLLOWED)

    /** "Mes revendications": listed only when the user has an account. */
    val MINE = BuiltInRevendicationView(MINE_ID, RevendicationQuery(authors = AuthorScope.MINE), BuiltInKind.MINE)

    /** "Nationales": demands for the whole country. */
    val NATIONAL = BuiltInRevendicationView(
        NATIONAL_ID,
        RevendicationQuery(territories = setOf(RevendicationQuery.territoryKey(NATIONAL_TERRITORY))),
        BuiltInKind.NATIONAL,
    )

    val MOST_FOR = BuiltInRevendicationView("plus-de-pour", RevendicationQuery(sort = RevendicationSort.MOST_FOR), BuiltInKind.MOST_FOR)
    val MOST_AGAINST = BuiltInRevendicationView("plus-de-contre", RevendicationQuery(sort = RevendicationSort.MOST_AGAINST), BuiltInKind.MOST_AGAINST)
    val NEWEST = BuiltInRevendicationView("recentes", RevendicationQuery(sort = RevendicationSort.NEWEST), BuiltInKind.NEWEST)
    val MOST_DEBATED = BuiltInRevendicationView("plus-debattues", RevendicationQuery(sort = RevendicationSort.MOST_DEBATED), BuiltInKind.MOST_DEBATED)
    val MOVEMENT = BuiltInRevendicationView("du-mouvement", RevendicationQuery(scopes = setOf(RevendicationScope.MOVEMENT)), BuiltInKind.MOVEMENT)
    val PARLIAMENT = BuiltInRevendicationView("au-parlement", RevendicationQuery(scopes = setOf(RevendicationScope.PARLIAMENT)), BuiltInKind.PARLIAMENT)
    val CLOSED = BuiltInRevendicationView("closes", RevendicationQuery(scopes = setOf(RevendicationScope.CLOSED)), BuiltInKind.CLOSED)
    val NEAR_ME = BuiltInRevendicationView("pres-de-chez-moi", RevendicationQuery(scopes = setOf(RevendicationScope.NEAR_ME)), BuiltInKind.NEAR_ME)
    val MY_VOTES = BuiltInRevendicationView("mes-votes", RevendicationQuery(scopes = setOf(RevendicationScope.MY_VOTES)), BuiltInKind.MY_VOTES)

    /** The views of MVP 1, in the order of the catalog and of the default layout. */
    val USEFUL: List<BuiltInRevendicationView> = listOf(MOST_FOR, NEWEST, MOVEMENT, PARLIAMENT, MOST_DEBATED, MOST_AGAINST, NEAR_ME, CLOSED, MY_VOTES)

    /** One view per theme, in the order of [RevendicationThemes.ALL]. */
    val THEMES: List<BuiltInRevendicationView> = RevendicationThemes.ALL.map { theme ->
        BuiltInRevendicationView(THEME_PREFIX + theme.id, RevendicationQuery(themes = setOf(theme.id)), BuiltInKind.THEME, theme.id)
    }

    /** Every built-in view, in the order of the catalog. */
    val BUILT_IN: List<BuiltInRevendicationView> = listOf(ALL, FOLLOWED, MINE, NATIONAL) + USEFUL + THEMES

    private val builtInById: Map<String, BuiltInRevendicationView> = BUILT_IN.associateBy { it.id }

    fun builtIn(id: String): BuiltInRevendicationView? = builtInById[id]

    fun theme(themeId: String): BuiltInRevendicationView? = builtIn(THEME_PREFIX + themeId)

    /**
     * The layout of a first launch: Toutes, the useful views of MVP 1 that read best first (Plus de pour,
     * Récentes, Du mouvement, Au Parlement), Comptes suivis, Société, Santé (the owner's own examples), then
     * Mes revendications when there is an account. The others wait in the catalog.
     */
    fun defaultLayout(hasAccount: Boolean): List<RevendicationView> = buildList {
        add(ALL)
        addAll(listOf(MOST_FOR, NEWEST, MOVEMENT, PARLIAMENT))
        add(FOLLOWED)
        add(THEMES.first { it.themeId == RevendicationThemes.SOCIETE.id })
        add(THEMES.first { it.themeId == RevendicationThemes.SANTE.id })
        if (hasAccount) add(MINE)
    }

    /**
     * A layout as the rules want it, whatever was read: "Toutes" first (added if missing), each id
     * once, at most [MAX_CUSTOM_VIEWS] custom views (the first ones are kept).
     */
    fun normalize(layout: List<RevendicationView>): List<RevendicationView> {
        val seen = mutableSetOf<String>()
        var custom = 0
        val rest = layout.filter { view ->
            when {
                view.id == ALL_ID || !seen.add(view.id) -> false
                view is CustomRevendicationView -> ++custom <= MAX_CUSTOM_VIEWS
                else -> true
            }
        }
        return listOf(ALL) + rest
    }

    /** The views shown as tabs: "Mes revendications" needs an account. */
    fun visible(layout: List<RevendicationView>, hasAccount: Boolean): List<RevendicationView> =
        if (hasAccount) layout else layout.filter { it.id != MINE_ID }

    /** The built-in views that [layout] does not show yet, for "Ajouter une vue". */
    fun catalog(layout: List<RevendicationView>, hasAccount: Boolean): List<BuiltInRevendicationView> {
        val shown = layout.map { it.id }.toSet()
        return BUILT_IN.filter { it.id !in shown && (hasAccount || it.id != MINE_ID) }
    }

    /** A name as kept: trimmed, inner runs of spaces collapsed; null when empty or longer than [NAME_MAX_LENGTH]. */
    fun normalizeName(name: String): String? {
        val clean = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return clean.takeIf { it.isNotEmpty() && it.length <= NAME_MAX_LENGTH }
    }

    /** A fresh id for a custom view: the prefix and 16 random hexadecimal characters. */
    fun newCustomId(random: Random = Random.Default): String =
        CUSTOM_PREFIX + random.nextBytes(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
