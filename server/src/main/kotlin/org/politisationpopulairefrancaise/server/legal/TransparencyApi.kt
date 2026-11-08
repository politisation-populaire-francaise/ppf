package org.politisationpopulairefrancaise.server.legal

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.sql.DataSource

/** The judicial requisitions answered in one month (UTC). */
@Serializable
data class RequisitionMonth(val month: String, val answered: Long)

/** The public counter of the requisitions answered from the legal register (D-077). */
@Serializable
data class RequisitionTransparency(val format: Int = 1, val months: List<RequisitionMonth>, val total: Long)

/**
 * Public transparency of the legal register (D-077): `GET /api/v1/transparence/requisitions` gives only the
 * number of judicial requisitions answered per month (UTC, `YYYY-MM`, months without any are absent), never
 * a reference, a key, a content or a finer date. Mounted inside the public port only. Without a database the
 * catch-all of the events API answers 503, like the other routes of `/api/v1`.
 */
fun Route.transparencyApi(dataSource: DataSource?) {
    if (dataSource == null) return
    get("/api/v1/transparence/requisitions") {
        val months = withContext(Dispatchers.IO) { LegalRegister.requisitionsByMonth(dataSource) }
            .map { (month, answered) -> RequisitionMonth(month, answered) }
        call.respond(RequisitionTransparency(months = months, total = months.sumOf { it.answered }))
    }
}
