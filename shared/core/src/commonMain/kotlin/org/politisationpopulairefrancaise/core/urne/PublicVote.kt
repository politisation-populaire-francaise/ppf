package org.politisationpopulairefrancaise.core.urne

import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign

/**
 * The vote of a certified elected official (owner's decision of 7 November 2026: « les élus oui et
 * eux leur vote est public »; D-115): not an anonymous ballot but an event signed with the
 * official's own key, published with their name and mandate, apart from the citizens' anonymous
 * vote and never added to it. Organisations never vote.
 *
 * Application data of NIP-78 (kind 30078), one per official and ballot box (a newer one replaces
 * the older, [UrneInfo.WITHDRAWN] withdraws it):
 * ```
 * {"kind":30078,"tags":[["d","ppf-vote:<box>"],["ppf-urne","<box>"],["ppf-choice","pour"],["a","<what is voted on>"]],"content":""}
 * ```
 */
object PublicVote {
    const val KIND: Int = 30078
    const val D_PREFIX: String = "ppf-vote:"
    const val TAG_URNE: String = "ppf-urne"
    const val TAG_CHOICE: String = "ppf-choice"

    /** One public vote read: who, which box, which choice, when. */
    data class Read(val event: SignedEvent, val official: String, val urne: Long, val choice: String, val createdAt: Long)

    fun tags(urne: UrneInfo, choice: String): List<List<String>> = listOf(
        listOf("d", "$D_PREFIX${urne.number}"),
        listOf(TAG_URNE, urne.number.toString()),
        listOf(TAG_CHOICE, choice),
        listOf("a", urne.subject),
    )

    /** Signs the official's vote [choice] in [urne] with their own key. */
    suspend fun sign(signer: EventSigner, urne: UrneInfo, choice: String, createdAt: Long): SignedEvent =
        signer.sign(kind = KIND, tags = tags(urne, choice), createdAt = createdAt)

    /** Reads a public vote; null for anything else (another kind, a d tag that does not match its box). */
    fun parse(event: SignedEvent): Read? {
        if (event.kind != KIND) return null
        val urne = event.firstTagValue(TAG_URNE)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        if (event.firstTagValue("d") != "$D_PREFIX$urne") return null
        val choice = event.firstTagValue(TAG_CHOICE)?.takeIf { it.isNotEmpty() && it.length <= 100 } ?: return null
        return Read(event, event.pubkey, urne, choice, event.createdAt)
    }
}

/** The public votes of the elected officials in one box, as the server lists them (signed, checkable by anyone). */
@kotlinx.serialization.Serializable
data class PublicVotes(val urne: Long, val votes: List<SignedEvent>)
