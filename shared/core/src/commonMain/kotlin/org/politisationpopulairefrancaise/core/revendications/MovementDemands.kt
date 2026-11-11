package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.Article

/**
 * The demands already carried by the social movement, gathered by PPF so that the wall is not empty on
 * the first day (MVP 1, jalon 3, decision of the owner of 2026-11-11 relayed by the lead: option A).
 * They are ordinary demands of the editorial team, put to the vote like the others, with two more tags:
 * - `["ppf-from","mouvement"]`: shown « Du mouvement », written « Relevé par PPF », so that no one takes
 *   them for a proposal of a citizen of the platform;
 * - `["ppf-carried-by","<who carries it, where and when>"]`, once per organisation or call, in the order
 *   of the sources (never an order of preference).
 * Their sources are listed in the body (`## Sources`), read the day they were written.
 *
 * Any demand of the team may also name the organisations that publicly support it, apart from its origin
 * (`["ppf-supported-by","<who, when>"]`, shown « Également portée par », the same for every demand; decision
 * of the owner of 2026-11-11, D-152: « informer des partis / syndicats etc qui sont également pour »), with
 * their sources in the body.
 *
 * Only the team may mark a demand this way: a reader honours the tags for a trusted editorial key only.
 */
object MovementDemands {
    const val TAG_FROM: String = "ppf-from"
    const val FROM_MOVEMENT: String = "mouvement"
    const val TAG_CARRIED_BY: String = "ppf-carried-by"
    const val TAG_SUPPORTED_BY: String = "ppf-supported-by"
    const val MAX_CARRIER: Int = 200

    /** The demand says it comes from the movement (to be trusted only for an editorial author). */
    fun isMarked(article: Article): Boolean = article.event.firstTagValue(TAG_FROM) == FROM_MOVEMENT

    /** Who carries it, in the order of the tags; blank and overlong values are left out. */
    fun carriers(article: Article): List<String> =
        article.event.tagValues(TAG_CARRIED_BY).map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_CARRIER }

    /** Who else publicly supports it (parties, unions, collectives), in the order of the tags. */
    fun supporters(article: Article): List<String> =
        article.event.tagValues(TAG_SUPPORTED_BY).map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_CARRIER }

    /** The tags of the public supporters of a demand (tools/content-signer). */
    fun supporterTags(supporters: List<String>): List<List<String>> {
        val cleaned = supporters.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleaned.all { it.length <= MAX_CARRIER }) { "a supporter holds in $MAX_CARRIER characters" }
        return cleaned.map { listOf(TAG_SUPPORTED_BY, it) }
    }

    /** The tags to add when the team writes such a demand (tools/content-signer). */
    fun tags(carriers: List<String>): List<List<String>> {
        val cleaned = carriers.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleaned.isNotEmpty()) { "a demand of the movement names who carries it" }
        require(cleaned.all { it.length <= MAX_CARRIER }) { "a carrier holds in $MAX_CARRIER characters" }
        return listOf(listOf(TAG_FROM, FROM_MOVEMENT)) + cleaned.map { listOf(TAG_CARRIED_BY, it) }
    }
}
