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
 * - the schema of the type declares two ([ContentSchema.approvals]: the types of M17);
 * - the signing service requires it for this event ([DoubleValidation.requiredFor]; it is authoritative and
 *   refuses a single approval with `double_validation_required`; for « Voir », every link to a person,
 *   an official, a party or a target);
 * - the event links a person with a role that implicates them ([IMPLICATING_ROLES]), whatever the space.
 * Otherwise one ([ORDINARY]). Saying who holds a mandate or a competence is a fact, not an implication (team
 * lead, 8 November 2026): the `persons` of « Qui décide de ça ? », the directory and the sheets need one.
 *
 * Common code: no java.*, compatible iOS.
 */
object EditorialApprovals {
    /** One approval: an ordinary content. */
    const val ORDINARY: Int = 1

    /** Two approvals of two different editors: a content of M17 that implicates a person. */
    const val M17: Int = 2

    /**
     * The roles of a link (`["a", <address>, <relay>, <role>]`) that implicate the person linked, in every space;
     * the one line to change if the rule changes. A plain mention of the holder of a mandate (role `personne` of
     * « Qui décide de ça ? ») is not among them; « Voir » is covered more widely by [DoubleValidation.requiredFor].
     */
    val IMPLICATING_ROLES: Set<String> = setOf("cible")

    /** The approvals the platform signature of [event] for [use] needs, [schema] being the schema of its type (null: unknown type). */
    fun required(schema: ContentSchema?, use: SigningUse, event: UnsignedEvent): Int = when {
        schema == null -> M17
        schema.approvals >= M17 -> M17
        DoubleValidation.requiredFor(use, event) -> M17
        implicatesAPerson(event) -> M17
        else -> ORDINARY
    }

    /** Whether [event] links a person with a role among [IMPLICATING_ROLES]. */
    fun implicatesAPerson(event: UnsignedEvent): Boolean =
        event.tags.any { it.size >= 4 && it[0] == "a" && it[3] in IMPLICATING_ROLES }
}
