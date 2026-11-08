package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * Which contents need a double validation before the platform signs them (contract
 * docs/integration/service-signature.md, section 5; D-129, shape rule of M17 of 8 November 2026).
 *
 * The signing service enforces it by itself, whatever the queue of the main server says: a draft that
 * needs a double validation and does not ask for it is refused (`double_validation_required`), at
 * submission and again at signature, so that a compromised main server cannot ask for a single approval
 * of a content that implicates a person. A queue may call [requiredFor] too, to ask for the right rule.
 *
 * The rule fails closed: one approval is enough only for the shapes listed as ordinary, and everything
 * else (an unknown type, no type, several types, a tag that names a person...) needs two.
 *
 * - [SigningUse.EDITORIAL_VOIR] (rule given by the pole « Voir », module M17): one approval is enough only
 *   for a long-form article (kind 30023) with exactly one `ppf-type` among [VOIR_ORDINARY_TYPES], no `a`
 *   tag whose role (fourth element, as in `["a","30023:<pubkey>:<d>","",<role>]`) is among
 *   [VOIR_PERSON_ROLES], and none of the tags [VOIR_SENSITIVE_TAGS].
 * - Every other use: the service requires no double validation (one approval for a human use, none for
 *   an automatic one). Other poles may add their own rule here later; each use is listed, so that a new
 *   use cannot be added without deciding its rule. A new rule is a change of the contract.
 */
object DoubleValidation {
    /** The `ppf-type` values of « Voir » that never implicate a person by themselves. */
    val VOIR_ORDINARY_TYPES: Set<String> = setOf("indicateur", "texte", "mouvement")

    /** The roles of an `a` tag that point at a person, an official, a party or a target. */
    val VOIR_PERSON_ROLES: Set<String> = setOf("personne", "responsable", "parti", "cible")

    /** The tags of « Voir » that state a procedure, a sentence, a quote, a promise, a vote, a mandate or a fact. */
    val VOIR_SENSITIVE_TAGS: Set<String> = setOf(
        "ppf-procedure",
        "ppf-sentence",
        "ppf-sentence-served",
        "ppf-quote",
        "ppf-promise",
        "ppf-vote",
        "ppf-mandate",
        "ppf-fact-status",
    )

    /** Whether the platform signs [event] for [use] only after two approvals of two different accounts, the author being neither. */
    fun requiredFor(use: SigningUse, event: UnsignedEvent): Boolean = when (use) {
        SigningUse.EDITORIAL_VOIR -> !isOrdinaryVoir(event)
        SigningUse.EDITORIAL_COMPRENDRE, SigningUse.REVENDICATIONS_STATUS, SigningUse.AGIR_ORGANISATIONS, SigningUse.DECIDER_PACTE,
        SigningUse.ARCHIVES_TIMESTAMPS, SigningUse.OPENDATA_INDEX, SigningUse.DECIDER_URN_CLOSURE -> false
    }

    private fun isOrdinaryVoir(event: UnsignedEvent): Boolean {
        if (event.kind != EventKinds.LONG_FORM_ARTICLE) return false
        val tags = event.tags.filter { it.isNotEmpty() }
        val types = tags.filter { it[0] == SigningUse.TAG_TYPE }
        if (types.size != 1 || types[0].getOrNull(1) !in VOIR_ORDINARY_TYPES) return false
        if (tags.any { it[0] == "a" && it.getOrNull(3) in VOIR_PERSON_ROLES }) return false
        return tags.none { it[0] in VOIR_SENSITIVE_TAGS }
    }
}
