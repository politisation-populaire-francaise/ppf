package org.politisationpopulairefrancaise.core.voir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * « Tableau de bord fédérateur » (M34, PP-07, D-107; docs/integration/tableau-de-bord.md): the strength of the
 * movement in a fixed list of figures, month by month, on any period. Each figure is counted by the server from
 * what it already holds, without any tracker, cookie or address (S-76): never a person, only numbers. A figure
 * without an honest source yet is « non mesuré », never zero.
 */

/** How a figure is read. */
enum class DashboardUnit { COUNT, EUROS }

/** The figures of the cahier fonctionnel (PP-07), always in this order. */
enum class DashboardMetric(val code: String, val unit: DashboardUnit = DashboardUnit.COUNT) {
    MEMBERS("adherents"),
    ACTIVE_ACCOUNTS("comptes-actifs"),
    DEMANDS("revendications"),
    VOTES("votes"),
    CALLS("appels"),
    ORGANISATIONS("organisations"),
    COLLECTIVES("collectifs"),
    SIGNATURES("signatures"),
    COMMITMENTS("engagements"),
    MONEY_COLLECTED("argent-collecte", DashboardUnit.EUROS),
    MONEY_REDISTRIBUTED("argent-redistribue", DashboardUnit.EUROS),
    ;

    companion object {
        fun fromCode(code: String?): DashboardMetric? = entries.firstOrNull { it.code == code }
    }
}

/** What a month added (in cents for euros). */
@Serializable
data class MonthCount(val month: String, val added: Long)

/** One figure: measured or not, and what each month added. */
@Serializable
data class MetricSeries(
    val code: String,
    val measured: Boolean,
    val months: List<MonthCount> = emptyList(),
) {
    val metric: DashboardMetric? get() = DashboardMetric.fromCode(code)

    /** What the months of [from] to [to] (`AAAA-MM`, both included, null for no bound) added; null when not measured. */
    fun total(from: String? = null, to: String? = null): Long? {
        if (!measured) return null
        return months.filter { (from == null || it.month >= from) && (to == null || it.month <= to) }.sumOf { it.added }
    }
}

/** The answer of `GET /api/v1/tableau-de-bord`, the same for everyone. */
@Serializable
data class Dashboard(
    val format: Int = DashboardFormat.FORMAT,
    /** The day it was computed (Paris). */
    val updatedOn: String,
    /** Every figure of [DashboardMetric], in its order. */
    val metrics: List<MetricSeries>,
) {
    fun series(metric: DashboardMetric): MetricSeries = metrics.firstOrNull { it.code == metric.code } ?: MetricSeries(metric.code, measured = false)

    /** Every month that appears in a figure, oldest first. */
    val months: List<String> get() = metrics.flatMap { m -> m.months.map { it.month } }.distinct().sorted()
}

object DashboardFormat {
    const val FORMAT: Int = 1
    const val PATH: String = "/api/v1/tableau-de-bord"

    private val MONTH = Regex("^(19|20)[0-9]{2}-(0[1-9]|1[0-2])$")

    fun isMonth(text: String?): Boolean = text != null && MONTH.matches(text)

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun read(text: String): Dashboard? = runCatching { json.decodeFromString(Dashboard.serializer(), text) }.getOrNull()?.takeIf { it.format == FORMAT }

    fun write(dashboard: Dashboard): String = json.encodeToString(Dashboard.serializer(), dashboard)

    /** Every figure in its order: those of [measured] with their months, the others « non mesuré ». */
    fun of(updatedOn: String, measured: Map<DashboardMetric, Map<String, Long>>): Dashboard = Dashboard(
        updatedOn = updatedOn,
        metrics = DashboardMetric.entries.map { metric ->
            val months = measured[metric]
            if (months == null) {
                MetricSeries(metric.code, measured = false)
            } else {
                MetricSeries(metric.code, measured = true, months = months.filterKeys(::isMonth).entries.sortedBy { it.key }.map { MonthCount(it.key, it.value) })
            }
        },
    )
}
