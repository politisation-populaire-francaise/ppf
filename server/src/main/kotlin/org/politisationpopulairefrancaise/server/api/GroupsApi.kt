package org.politisationpopulairefrancaise.server.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupKinds

/** Longest `q` or `territory` parameter of the group search, in characters. */
const val MAX_GROUP_QUERY_LENGTH: Int = 200

/**
 * Read side of the groups (NIP-29): the metadata the relay knows. Messages of a group are read with
 * GET /api/v1/events?kind=9&tag=h:<id>; metadata is published through POST /api/v1/events.
 *
 * - GET /api/v1/groups?q=&territory=&limit=: the groups by name (accents and case ignored) then id.
 *   `q` keeps the groups whose name, territory or description contains the text, `territory` those of
 *   one territory, both compared once normalized (accents, case and punctuation ignored; a value with
 *   no letter or digit does not filter); `limit` from 1 to 500, 100 by default. Each parameter at most
 *   once, `q` and `territory` of at most [MAX_GROUP_QUERY_LENGTH] characters; otherwise 400 bad_parameter.
 * - GET /api/v1/groups/{id}: one group, or 404 group_not_found.
 *
 * No member list exists on the server, so none is served (D-027).
 */
fun Route.groupsApi(directory: GroupDirectory?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (directory == null) return
    route("/api/v1/groups") {
        get {
            val parameters = call.request.queryParameters
            val text = parameters.getAll("q").orEmpty()
            if (text.size > 1 || text.any { it.length > MAX_GROUP_QUERY_LENGTH }) return@get badParameter("q")
            val territory = parameters.getAll("territory").orEmpty()
            if (territory.size > 1 || territory.any { it.length > MAX_GROUP_QUERY_LENGTH }) return@get badParameter("territory")
            val limits = parameters.getAll("limit").orEmpty()
            if (limits.size > 1) return@get badParameter("limit")
            val limit = limits.singleOrNull()?.let { raw ->
                raw.toIntOrNull()?.takeIf { it in 1..GroupDirectory.MAX_LIMIT } ?: return@get badParameter("limit")
            } ?: GroupDirectory.DEFAULT_LIMIT
            call.respond(withContext(Dispatchers.IO) { directory.search(text.singleOrNull(), territory.singleOrNull(), limit) })
        }
        get("{id}") {
            val id = call.parameters["id"].orEmpty()
            if (!GroupKinds.isValidId(id)) return@get badParameter("id")
            val group = withContext(Dispatchers.IO) { directory.find(id) }
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("group_not_found", "No group with this id on this relay."))
            call.respond(group)
        }
    }
}
