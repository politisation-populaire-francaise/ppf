package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The status that the team gives a demand (team lead's decision on the review R5, Q3): a label
 * event of NIP-32 (kind 1985) signed by an editorial key, apart from the demand itself, so that a
 * citizen's demand keeps its author's signature while the team moves it forward.
 *
 * ```
 * {"kind":1985,"tags":[["L","ppf-status"],["l","en-debat","ppf-status"],["a","30023:<author>:<d>"]],"content":""}
 * ```
 *
 * For one demand, the most recent label of an editorial key wins; without a label, the status
 * written in the demand counts only if the demand was published by the team. Produced by the file
 * of the back-office (technical director); read by the server and the applications.
 */
object StatusLabels {
    const val KIND: Int = 1985
    const val NAMESPACE: String = "ppf-status"

    /** One label read: the demand it names and the status it gives. */
    data class Label(val demand: ContentAddress, val status: RevendicationStatus, val createdAt: Long, val signer: String)

    /** Reads a label event; null for anything else (another namespace, an unknown status, no demand). */
    fun parse(event: SignedEvent): Label? {
        if (event.kind != KIND) return null
        if (event.tags.none { it.size >= 2 && it[0] == "L" && it[1] == NAMESPACE }) return null
        val status = event.tags.firstOrNull { it.size >= 3 && it[0] == "l" && it[2] == NAMESPACE }?.let { RevendicationStatus.fromTag(it[1]) } ?: return null
        val demands = event.tags.filter { it.size >= 2 && it[0] == "a" }.mapNotNull { runCatching { ContentAddress.parse(it[1]) }.getOrNull() }
        val demand = demands.singleOrNull() ?: return null
        return Label(demand, status, event.createdAt, event.pubkey)
    }

    /** The tags of a label giving [status] to [demand], for the file of the back-office. */
    fun tags(demand: ContentAddress, status: RevendicationStatus): List<List<String>> =
        listOf(listOf("L", NAMESPACE), listOf("l", status.tagValue, NAMESPACE), listOf("a", demand.toString()))

    /**
     * The status of a demand: the most recent label of an editorial key ([isEditor]), else the status
     * written in the demand if its author is an editorial key; a citizen's demand only ever writes
     * « nouvelle » (any other status it wrote is not trusted: null).
     */
    fun effective(
        demandAuthor: String,
        written: RevendicationStatus?,
        labels: List<Label>,
        isEditor: (String) -> Boolean,
    ): RevendicationStatus? =
        labels.filter { isEditor(it.signer) }.maxWithOrNull(compareBy<Label> { it.createdAt })?.status
            ?: written?.takeIf { isEditor(demandAuthor) || it == RevendicationStatus.NOUVELLE }
}
