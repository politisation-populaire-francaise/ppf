package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The closing statement kept for one ballot box (`d` = [identifier]) and the other platform-signed
 * closing statements found for the same box, if any.
 */
data class UrnClosureChoice(
    val identifier: String,
    /** The earliest platform-signed closing statement: the one to show. */
    val closure: SignedEvent,
    /** Any other platform-signed closing statement of the same box: a conflict to show, never a replacement. */
    val conflicts: List<SignedEvent>,
) {
    val hasConflict: Boolean get() = conflicts.isNotEmpty()
}

/**
 * Which closing statement of a ballot box to believe (docs/FORMATS.md, section 4 decies, for « Décider »).
 *
 * A closing statement is an addressable event (kind 30078), and for addressable events the latest
 * version normally wins. Not here: the result of a ballot box is fixed once, and a later statement for the
 * same `d` (signed with a stolen working key, or with the next key after a rotation) must never replace
 * the real one. The signing service refuses forever a second closing statement for the same `d`; the
 * applications keep, among the events that [PlatformTrust] accepts for [SigningUse.DECIDER_URN_CLOSURE],
 * the EARLIEST one per `d` (lowest `created_at`, then lowest id) and report every other one as a conflict.
 */
object UrnClosures {
    /**
     * The closing statements among [events], one per `d`: the earliest platform-signed one, with the other
     * platform-signed ones as conflicts. Events that [trust] does not accept as closing statements are
     * ignored, as are several copies of the same event.
     */
    fun choose(events: Iterable<SignedEvent>, trust: PlatformTrust): Map<String, UrnClosureChoice> =
        events
            .filter { trust.isPlatformContent(it, SigningUse.DECIDER_URN_CLOSURE) }
            .distinctBy { it.id }
            .groupBy { it.tagValues("d").single() }
            .mapValues { (identifier, closures) ->
                val ordered = closures.sortedWith(compareBy<SignedEvent>({ it.createdAt }, { it.id }))
                UrnClosureChoice(identifier, ordered.first(), ordered.drop(1))
            }
}
