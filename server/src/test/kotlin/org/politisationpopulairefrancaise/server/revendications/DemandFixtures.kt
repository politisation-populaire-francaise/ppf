package org.politisationpopulairefrancaise.server.revendications

import org.politisationpopulairefrancaise.core.revendications.checks.RevendicationDraft
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Example demands for the tests, all marked [EXEMPLE] in their argument; no real person's data. */
object DemandFixtures {
    fun draft(
        title: String = "Un médecin de garde joignable le soir dans chaque canton",
        summary: String = "",
        why: String = "[EXEMPLE] Le soir, il faut aller aux urgences pour un simple souci.",
        asks: String = "[EXEMPLE] Une permanence médicale par canton, jusqu'à minuit.",
        existing: String = "",
        themes: List<String> = listOf("sante"),
        territory: String = "France",
    ) = RevendicationDraft(title, summary, why, asks, existing, themes, territory)

    /** The event the form of the application would sign for [draft]. */
    suspend fun LocalEventSigner.demand(draft: RevendicationDraft = draft(), slug: String = "medecin-garde-canton-0001", createdAt: Long = nowSeconds() - 60): SignedEvent =
        sign(draft.toUnsignedEvent(publicKey, createdAt, slug))

    /** Same, after [change] edited the event (what a modified application could send). */
    suspend fun LocalEventSigner.alteredDemand(
        draft: RevendicationDraft = draft(),
        slug: String = "medecin-garde-canton-0001",
        createdAt: Long = nowSeconds() - 60,
        change: (UnsignedEvent) -> UnsignedEvent,
    ): SignedEvent = sign(change(draft.toUnsignedEvent(publicKey, createdAt, slug)))

    fun nowSeconds(): Long = Instant.now().epochSecond
}

/** A clock the tests move by hand. */
class MutableClock(var instant: Instant) : Clock() {
    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }
}
