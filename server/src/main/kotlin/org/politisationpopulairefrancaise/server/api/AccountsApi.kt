package org.politisationpopulairefrancaise.server.api

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts

/**
 * The status of an account's recovery (S-146, D-134, docs/integration/compte-compromis.md):
 *
 * - GET /api/v1/accounts/{pubkey}/status: `{compromised, since, successor, notice, commitment}`.
 *   The events of a compromised key dated after `since` stay served as they are: the device asks
 *   this status to mark them, instead of a change of the event format.
 *
 * For the keys of its contacts, the device rather reads the notices in mixed batches
 * (`GET /api/v1/events?kind=1777&tag=p:<key>&tag=p:<key>...`), so that the server does not learn its
 * list: the server stores only the notices it accepted. Without a database, nothing is registered
 * here and the catch-all of the events API answers 503.
 */
fun Route.accountsApi(compromised: CompromisedAccounts?) {
    if (compromised == null) return
    get("/api/v1/accounts/{pubkey}/status") {
        val pubkey = call.parameters["pubkey"].orEmpty()
        if (pubkey.length != 64 || !pubkey.all { it in '0'..'9' || it in 'a'..'f' }) return@get badParameter("pubkey")
        val status = withContext(Dispatchers.IO) { compromised.status(pubkey) }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(status)
    }
}
