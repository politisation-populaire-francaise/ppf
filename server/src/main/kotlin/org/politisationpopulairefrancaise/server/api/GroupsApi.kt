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

/** At most this many ids in one GET /api/v1/group-creations. */
const val MAX_CREATION_IDS: Int = 20

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
 * - GET /api/v1/groups/{id}: one group, or 404 group_not_found; its name and presentation are those
 *   in force after the edits of its administrators (the latest edit is served in `edit`).
 * - GET /api/v1/groups/{id}/roles: the roles in force (key, role, date, key of who named it) and the
 *   number of excluded accounts (D-131), or 404 group_not_found. The events themselves (9000 to 9005)
 *   are read with GET /api/v1/events?tag=h:<id>&kind=…: the journal of a room is public.
 *
 * - GET /api/v1/group-creations?id=…: where the room creations held for the team stand (D-132),
 *   for their authors: status, dates and, for a refusal, the team's reason; an unknown id is absent.
 *   At most [MAX_CREATION_IDS] ids, each a 64-character hex event id; otherwise 400 bad_parameter.
 *
 * No member list exists on the server, so none is served (D-027).
 */
fun Route.groupsApi(directory: GroupDirectory?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (directory == null) return
    get("/api/v1/group-creations") {
        val ids = call.request.queryParameters.getAll("id").orEmpty()
        if (ids.isEmpty() || ids.size > MAX_CREATION_IDS || ids.any { id -> id.length != 64 || id.any { it !in '0'..'9' && it !in 'a'..'f' } }) {
            return@get badParameter("id")
        }
        call.respond(withContext(Dispatchers.IO) { directory.guardrails.creations.states(ids) })
    }
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
        get("{id}/roles") {
            val id = call.parameters["id"].orEmpty()
            if (!GroupKinds.isValidId(id)) return@get badParameter("id")
            val roles = withContext(Dispatchers.IO) { directory.find(id)?.let { directory.moderation.roles(id) } }
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("group_not_found", "No group with this id on this relay."))
            call.respond(roles)
        }
    }
}
