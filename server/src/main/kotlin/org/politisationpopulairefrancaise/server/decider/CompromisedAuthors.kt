package org.politisationpopulairefrancaise.server.decider

import kotlinx.html.FlowContent
import kotlinx.html.div
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts.HeldVerdict
import org.politisationpopulairefrancaise.server.backoffice.dateTime

/**
 * How the review queues of Décider (demands, propositions and amendments of deliberations) treat a content
 * held for review once its author's key is declared compromised (S-146, D-134, decision of the team lead of
 * 8 November 2026): signed on or after « compromis depuis », it can only be refused; signed before, it stays
 * in the queue with a mention visible to the team. The server already refuses any new event of such a key.
 */
class CompromisedAuthors(private val compromised: CompromisedAccounts?) {
    /** What the team must know of the author of [event] (blocking: one query). */
    fun verdictOf(event: SignedEvent): HeldVerdict = compromised?.heldVerdict(event.pubkey, event.createdAt) ?: HeldVerdict.Clear

    /** True when [event] was signed after its key was declared compromised: it can only be refused. */
    fun onlyRefusable(event: SignedEvent): Boolean = verdictOf(event) is HeldVerdict.CreatedAfter

    companion object {
        /** No compromised account known (tests, a server without database). */
        val NONE: CompromisedAuthors = CompromisedAuthors(null)

        /** The short mention of the list of a queue. */
        const val SHORT: String = "compte de l'auteur déclaré compromis"
        const val AFTER: String = "Signée après que le compte de son auteur a été déclaré compromis : elle ne peut qu'être refusée. Compromis depuis le"
        const val BEFORE: String = "Le compte de son auteur a été déclaré compromis, mais elle a été signée avant : vous pouvez la relire comme les autres. Compromis depuis le"
        const val REFUSED: String = "Signée après la compromission du compte de son auteur : refusez-la avec un motif."
    }
}

/** The notice shown above a held content whose author's key is declared compromised; nothing otherwise. */
fun FlowContent.compromisedNotice(verdict: HeldVerdict) {
    when (verdict) {
        is HeldVerdict.CreatedAfter -> div(classes = "bo-notice") {
            attributes["role"] = "alert"
            +CompromisedAuthors.AFTER
            +" "
            dateTime(verdict.since)
        }
        is HeldVerdict.CreatedBefore -> div(classes = "bo-notice") {
            attributes["role"] = "status"
            +CompromisedAuthors.BEFORE
            +" "
            dateTime(verdict.since)
        }
        HeldVerdict.Clear -> Unit
    }
}
