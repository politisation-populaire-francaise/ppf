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

/**
 * Read side of the groups (NIP-29): the metadata the relay knows. Messages of a group are read with
 * GET /api/v1/events?kind=9&tag=h:<id>; metadata is published through POST /api/v1/events.
 *
 * - GET /api/v1/groups: every group, by name.
 * - GET /api/v1/groups/{id}: one group, or 404 group_not_found.
 *
 * No member list exists on the server, so none is served (D-027).
 */
fun Route.groupsApi(directory: GroupDirectory?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (directory == null) return
    route("/api/v1/groups") {
        get {
            call.respond(withContext(Dispatchers.IO) { directory.list() })
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
