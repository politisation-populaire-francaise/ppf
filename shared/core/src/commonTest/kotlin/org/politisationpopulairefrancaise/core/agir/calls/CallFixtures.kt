package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** Drafts and calls for the tests: fictitious, at a fixed moment (Friday 6 November 2026, 10:00 in Paris). */
object CallFixtures {
    val NOW: Long = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 6, 10, 0))
    val AUTHOR: PublicKey = IdentityKeys.generate().publicKey

    /** The model does not check signatures: a well-formed id and a dummy signature are enough here. */
    fun UnsignedEvent.unsignedForTest(): SignedEvent = SignedEvent(idHex(), pubkey, createdAt, kind, tags, content, "0".repeat(128))

    private val SPECIFIC_VALUES: Map<String, String> = mapOf(
        CallFields.OBJECT to "[EXEMPLE] Préparer la réunion publique sur les loyers",
        CallFields.ORGANIZER to "[EXEMPLE] Collectif fictif des locataires",
        CallFields.ACCESSIBILITY to "Salle de plain-pied, accessible en fauteuil roulant",
        AgirFormats.TAG_DECLARATION to "faite",
        CallFields.CAUSE to "[EXEMPLE] Le maintien de la ligne de bus fictive 12",
        CallFields.ACTION to "Écrire à l'autorité organisatrice des transports",
        CallFields.PLATFORM to "assemblee-nationale",
        CallFields.TARGETS to "Les conseillers départementaux du canton",
        CallFields.REQUEST to "Voter contre la fermeture de la ligne",
        CallFields.COMPANY to "[EXEMPLE] Entreprise fictive",
        CallFields.SOURCE to "https://exemple.org/rapport",
        CallFields.ALTERNATIVES to "Les commerces indépendants du quartier",
        CallFields.DURATION to "Un mois",
        CallFields.BENEFICIARY to "[EXEMPLE] Association fictive d'entraide",
        CallFields.USE to "Acheter des colis alimentaires",
        CallFields.MISSION to "Tenir la permanence d'accueil",
        CallFields.SKILLS to "Écoute, patience",
        CallFields.PEOPLE to "4",
        CallFields.REMOTE to CallFields.YES,
        CallFields.PERIOD to "Décembre 2026",
        CallFields.EVIDENCE to "Photos, vidéos, récits",
        CallFields.RECIPIENT to "[EXEMPLE] Legal team fictive",
    )

    /** A draft of [type] that meets every rule at [NOW] (a verified organisation for a fundraising). */
    fun validDraft(type: CallType): CallDraft {
        var draft = CallDraft(
            type = type,
            title = "[EXEMPLE] Réunion publique sur les loyers du quartier",
            text = "[EXEMPLE] Venez nombreux pour parler des loyers et de ce que nous pouvons faire ensemble.",
            territory = "Lyon",
            notForCandidateOrParty = true,
        )
        type.requirements.forEach { requirement ->
            when (val tag = requirement.alternatives.first()) {
                AgirFormats.TAG_LOCATION -> draft = draft.copy(location = "Salle des fêtes, place fictive")
                AgirFormats.TAG_LINK -> draft = draft.copy(link = "https://exemple.org/page")
                AgirFormats.TAG_GOAL -> draft = draft.copy(goal = "500")
                AgirFormats.TAG_DEADLINE -> draft = draft.withDate(tag, DateTimeText("12/12/2026", ""))
                AgirFormats.TAG_START -> draft = draft.withDate(
                    tag,
                    if (type.isWitnesses) DateTimeText("01/11/2026", "14:00") else DateTimeText("14/11/2026", "18:30"),
                )
                AgirFormats.TAG_END -> draft = draft.withDate(tag, DateTimeText("01/11/2026", "16:00"))
                else -> draft = draft.withField(tag, SPECIFIC_VALUES.getValue(tag))
            }
        }
        return draft
    }

    /** A call read back from the event of [draft], dated [createdAt]. */
    fun call(draft: CallDraft, slug: String, createdAt: Long = NOW): Call =
        requireNotNull(Call.from(draft.toUnsignedEvent(AUTHOR, createdAt, slug).unsignedForTest()))
}
