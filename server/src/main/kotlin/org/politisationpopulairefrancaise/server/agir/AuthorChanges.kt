package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Decision of the owner (2026-11-07): an author may always cancel their mobilization or close their
 * call, whoever they are. A new version by the same key at the same address whose only difference
 * with a known version is what ends it is published without review:
 *
 * - a mobilization whose only change is `ppf-event-status` = `annule`;
 * - a call whose only changes are `ppf-call-status` = `clos` and its `ppf-report` (added or changed),
 *   except a call for witnesses, whose report is new text and stays reviewed like the call itself:
 *   for it, only the status may change.
 *
 * The text, every other tag and their order must be identical; `created_at` is not compared. Any other
 * change follows the normal rule.
 */
object AuthorChanges {
    /** The reason given to the author when their cancellation withdraws a version still waiting for review. */
    const val WITHDRAWN_REASON: String = "Annulé par son auteur avant la relecture"

    const val STATUS_CANCELLED: String = "annule"
    const val STATUS_CLOSED: String = "clos"

    /** Whether [event] is a cancelled mobilization or a closed call: cheap, no database. */
    fun isEnding(event: SignedEvent): Boolean = when (AgirRules.categoryOf(event)) {
        AgirCategory.MOBILIZATION -> event.firstTagValue(AgirFormats.TAG_EVENT_STATUS) == STATUS_CANCELLED
        AgirCategory.CALL -> event.firstTagValue(AgirFormats.TAG_CALL_STATUS) == STATUS_CLOSED
        else -> false
    }

    /** Whether [next], an ending version, differs from [previous] (same key, same address) only by what ends it. */
    fun onlyEnds(previous: SignedEvent, next: SignedEvent): Boolean {
        if (!isEnding(next)) return false
        if (previous.pubkey != next.pubkey || previous.kind != next.kind) return false
        if (EventReplacement.replacementKey(previous) != EventReplacement.replacementKey(next)) return false
        if (previous.content != next.content) return false
        val ignored = when (AgirRules.categoryOf(next)) {
            AgirCategory.MOBILIZATION -> setOf(AgirFormats.TAG_EVENT_STATUS)
            AgirCategory.CALL ->
                if (AgirRules.callTypeOf(next) == AgirFormats.CALL_TYPE_WITNESSES) {
                    setOf(AgirFormats.TAG_CALL_STATUS)
                } else {
                    setOf(AgirFormats.TAG_CALL_STATUS, AgirFormats.TAG_REPORT)
                }
            else -> return false
        }
        return previous.tags.filterNot { it.firstOrNull() in ignored } == next.tags.filterNot { it.firstOrNull() in ignored }
    }
}
