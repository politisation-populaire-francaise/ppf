package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.core.agir.collectives.Collective
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.web.CallsSource
import org.politisationpopulairefrancaise.web.CollectivesSource
import org.politisationpopulairefrancaise.web.FundsSource
import org.politisationpopulairefrancaise.web.OrganisationsSource

/**
 * The calls shown by the site (`/appels`): those stored in the events table, so published (the calls held for
 * review wait in the queue and never show), the most recent [EventFilter.MAX_LIMIT] at most.
 */
fun callsSource(events: EventRepository): CallsSource = CallsSource {
    withContext(Dispatchers.IO) {
        events.query(EventFilter(kinds = listOf(Call.KIND), tags = mapOf(AgirFormats.TAG_TYPE to listOf(AgirFormats.TYPE_CALL)), limit = EventFilter.MAX_LIMIT))
    }.mapNotNull(Call::from)
}

/**
 * The directory of the site (`/organisations`): the entries signed by an editorial key, as on the device
 * (`Organisation.from` with the editorial trust); the server checked their signature when it stored them.
 */
fun organisationsSource(events: EventRepository, isEditor: (String) -> Boolean): OrganisationsSource = OrganisationsSource {
    withContext(Dispatchers.IO) {
        events.query(EventFilter(kinds = listOf(Organisation.KIND), tags = mapOf(AgirFormats.TAG_TYPE to listOf(AgirFormats.TYPE_ORGANISATION)), limit = EventFilter.MAX_LIMIT))
    }.filter { isEditor(it.pubkey) }.mapNotNull(Organisation::parse)
}

/** The solidarity funds of the site (`/agir/solidarite`): entries signed by an editorial key, as on the device. */
fun fundsSource(events: EventRepository, isEditor: (String) -> Boolean): FundsSource = FundsSource {
    withContext(Dispatchers.IO) {
        events.query(EventFilter(kinds = listOf(SolidarityFund.KIND), tags = mapOf(AgirFormats.TAG_TYPE to listOf(SolidarityFormats.TYPE_FUND)), limit = EventFilter.MAX_LIMIT))
    }.filter { isEditor(it.pubkey) }.mapNotNull(SolidarityFund::parse)
}

/** The collectifs of the site (`/agir/collectifs`): the rooms described as collectifs, filtered by territory as for the rooms. */
fun collectivesSource(groups: GroupDirectory): CollectivesSource = CollectivesSource { territory ->
    withContext(Dispatchers.IO) { groups.search(null, territory, GroupDirectory.MAX_LIMIT) }.mapNotNull(Collective::from)
}
