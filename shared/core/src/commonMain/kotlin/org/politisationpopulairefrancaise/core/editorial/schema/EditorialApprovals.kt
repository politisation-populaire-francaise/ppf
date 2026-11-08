package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.platform.DoubleValidation
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * How many approvals an editorial content needs before the platform signs it (D-095; decision of the owner of
 * 7 November 2026, relayed by the team lead: any member of the group of editors publishes with ONE approval,
 * two only for the contents of M17 that implicate a person).
 *
 * An approval is always given by an editor who wrote no version of the draft (D-094: the author plus a
 * reviewer). The reviewer who approves in the queue `editorial.<space>` is the person who confirms on the page
 * of the signing service: one approval in all for an ordinary content, not one more.
 *
 * Two approvals of two editors who wrote nothing ([M17]) when, checked on the event built at the hand-over:
 * - the type has no schema (fails closed);
 * - the schema of the type declares two ([ContentSchema.approvals]);
 * - the signing service requires it for this event ([DoubleValidation.requiredFor]; it is authoritative and
 *   refuses a single approval with `double_validation_required`);
 * - the event links a person, an official, a party or a target (an `a` tag whose role, fourth element, is
 *   among [DoubleValidation.VOIR_PERSON_ROLES]), whatever the space: a content that points at a person
 *   implicates them (for example « Qui décide de ça ? » with its `persons`).
 * Otherwise one ([ORDINARY]).
 *
 * Common code: no java.*, compatible iOS.
 */
object EditorialApprovals {
    /** One approval: an ordinary content. */
    const val ORDINARY: Int = 1

    /** Two approvals of two different editors: a content of M17 that implicates a person. */
    const val M17: Int = 2

    /** The approvals the platform signature of [event] for [use] needs, [schema] being the schema of its type (null: unknown type). */
    fun required(schema: ContentSchema?, use: SigningUse, event: UnsignedEvent): Int = when {
        schema == null -> M17
        schema.approvals >= M17 -> M17
        DoubleValidation.requiredFor(use, event) -> M17
        linksAPerson(event) -> M17
        else -> ORDINARY
    }

    /** Whether [event] has a link (`["a", <address>, <relay>, <role>]`) whose role points at a person, an official, a party or a target. */
    fun linksAPerson(event: UnsignedEvent): Boolean =
        event.tags.any { it.size >= 4 && it[0] == "a" && it[3] in DoubleValidation.VOIR_PERSON_ROLES }
}
