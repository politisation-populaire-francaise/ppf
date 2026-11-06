package org.politisationpopulairefrancaise.server.revendications

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatusSource
import org.politisationpopulairefrancaise.server.api.badParameter

/** At most this many proposals per status request, as the device asks them. */
const val MAX_STATUS_IDS: Int = ProposalStatusSource.MAX_IDS

/**
 * GET /api/v1/revendications/proposals?id=<hex>&id=<hex>: where the author's proposals stand
 * (1 to [MAX_STATUS_IDS] event ids of 64 lowercase hexadecimal characters), as a JSON list of
 * [ProposalState] (`id`, `status`, `received_at`, `decided_at`, `reason`, `report`, see
 * [RevendicationProposals.statusesForAuthor]); ids found nowhere are left out. No authentication:
 * while a proposal is pending, its event id is known to its author only. Never the content of the
 * event nor its key. The submission itself goes through POST /api/v1/events, the single
 * publication port.
 */
fun Route.revendicationsApi(proposals: RevendicationProposals?) {
    if (proposals == null) return
    get("/api/v1/revendications/proposals") {
        val ids = call.request.queryParameters.getAll("id").orEmpty()
        if (ids.isEmpty() || ids.size > MAX_STATUS_IDS || ids.any { !isEventId(it) }) return@get badParameter("id")
        // A status changes and concerns one author: no cache may keep it.
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(withContext(Dispatchers.IO) { proposals.statusesForAuthor(ids) })
    }
}

private fun isEventId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
