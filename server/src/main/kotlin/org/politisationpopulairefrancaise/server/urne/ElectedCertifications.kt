package org.politisationpopulairefrancaise.server.urne

import kotlinx.html.FlowContent
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.urne.CertificationAnswer
import org.politisationpopulairefrancaise.core.urne.CertificationRequest
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.elus.ElusLookup
import org.politisationpopulairefrancaise.server.elus.ElusMatch
import org.politisationpopulairefrancaise.server.elus.Mandate
import org.politisationpopulairefrancaise.server.elus.MandateStatus
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The certification of elected officials' accounts (DE-23, D-112, une-personne-une-voix.md 4.3): an
 * account created for the mandate (never served a token, never verified) asks for it with the public
 * reference of the mandate in the official directory (D-047); a team member with the role
 * `elected_certifier` meets the official, checks in person that they hold this account and are this
 * official, then certifies in the queue `elus.certifications`. The account votes as « élu », publicly
 * (D-115), from the next Monday.
 *
 * Only the result and the public reference of the mandate are kept (`rne1:`, type and territory codes),
 * never a name typed by the team, a document or a photo (LCEN rule, urne-contrat.md, 8). A mandate absent
 * from a later import of the directory ends the certification for the boxes opened from the next Monday;
 * a directory not served yet never removes it.
 */
class ElectedCertifications(
    private val store: UrneStore,
    private val service: UrneService,
    /** The directory of elected officials; null without one (nothing can then be certified). */
    private val lookup: ElusLookup?,
    private val clock: Clock = Clock.systemUTC(),
) {
    private fun thisMonday(): LocalDate = UrneService.mondayOf(clock.instant())

    /** The request of the account [pubkey] (authenticated by NIP-98); throws [UrneRefusal] otherwise. */
    fun request(pubkey: String, body: String): CertificationAnswer {
        service.requirePerson(pubkey)
        val request = runCatching { Json.decodeFromString(CertificationRequest.serializer(), body) }.getOrNull() ?: throw UrneRefusal(400, "malformed_request")
        val mandate = Mandate.entries.firstOrNull { it.code == request.mandateType }
        if (!ElusLookup.isValidId(request.mandate) || mandate == null || request.territory.isBlank() || request.territory.length > MAX_TERRITORY) {
            throw UrneRefusal(400, "invalid_mandate")
        }
        store.account(pubkey) ?: throw UrneRefusal(404, "not_registered")
        if (store.certification(pubkey)?.status == CertificationAnswer.CERTIFIED) throw UrneRefusal(409, "already_certified")
        if (!store.isDedicated(pubkey)) throw UrneRefusal(409, "not_a_dedicated_account")
        val directory = lookup ?: throw UrneRefusal(503, "directory_unavailable")
        // A mandate absent from the directory served is refused at once; with no directory served yet, the
        // request waits (the team certifies only once the mandate is found).
        if (directory.mandateStatus(request.mandate) is MandateStatus.NotFound || (directory.sourceUpdatedOn() != null && directory.byId(request.mandate, mandate, request.territory) == null)) {
            throw UrneRefusal(404, "mandate_not_found")
        }
        return when (store.requestCertification(pubkey, request.mandate, mandate.code, request.territory, thisMonday())) {
            CertificationWrite.RECORDED -> CertificationAnswer(CertificationAnswer.PENDING)
            CertificationWrite.ALREADY_CERTIFIED -> throw UrneRefusal(409, "already_certified")
            CertificationWrite.MANDATE_TAKEN -> throw UrneRefusal(409, "mandate_taken")
        }
    }

    /** The mandate of a certification in the directory served, or null (absent, or nothing served). */
    fun match(certification: Certification): ElusMatch? {
        val mandate = Mandate.entries.firstOrNull { it.code == certification.mandateType } ?: return null
        return lookup?.byId(certification.mandate, mandate, certification.territory)
    }

    fun mandateStatus(certification: Certification): MandateStatus = lookup?.mandateStatus(certification.mandate) ?: MandateStatus.Unavailable

    /** Certifies [pubkey]: only a waiting request whose mandate is in the directory served, for a dedicated account. */
    fun certify(pubkey: String): ActionOutcome {
        val certification = store.certification(pubkey) ?: return ActionOutcome.NotFound
        if (certification.status != CertificationAnswer.PENDING) return ActionOutcome.AlreadyDecided
        when (mandateStatus(certification)) {
            is MandateStatus.Current -> Unit
            is MandateStatus.NotFound -> return ActionOutcome.Refused("Ce mandat n'est plus dans l'annuaire officiel des élus : refusez la demande.")
            MandateStatus.Unavailable -> return ActionOutcome.Refused("L'annuaire des élus n'est pas encore chargé : réessayez après sa mise à jour.")
        }
        if (!store.isDedicated(pubkey)) return ActionOutcome.Refused("Ce compte a déjà voté ou été vérifié : seul un compte créé pour le mandat peut être certifié.")
        val week = thisMonday()
        return if (store.decideCertification(pubkey, CertificationAnswer.PENDING, CertificationAnswer.CERTIFIED, null, week, VoteLevel.ELU)) {
            ActionOutcome.Done
        } else {
            ActionOutcome.AlreadyDecided
        }
    }

    fun refuse(pubkey: String, reason: String): ActionOutcome {
        store.certification(pubkey) ?: return ActionOutcome.NotFound
        return if (store.decideCertification(pubkey, CertificationAnswer.PENDING, CertificationAnswer.REFUSED, reason, thisMonday(), null)) ActionOutcome.Done else ActionOutcome.AlreadyDecided
    }

    /** Withdraws a certification: « non vérifié » again for the boxes opened from next Monday. */
    fun withdraw(pubkey: String, reason: String): ActionOutcome {
        store.certification(pubkey) ?: return ActionOutcome.NotFound
        return if (store.decideCertification(pubkey, CertificationAnswer.CERTIFIED, CertificationAnswer.WITHDRAWN, reason, thisMonday(), VoteLevel.NON_VERIFIE)) {
            ActionOutcome.Done
        } else {
            ActionOutcome.AlreadyDecided
        }
    }

    @Volatile
    private var checkedOn: LocalDate? = null

    /**
     * Once a day: a certified mandate absent from the directory served ends its certification (level « non
     * vérifié » for the boxes opened from next Monday). A directory not served never ends anything.
     * Returns how many ended.
     */
    fun checkMandatesDue(): Int {
        val today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
        if (checkedOn == today || lookup == null) return 0
        var ended = 0
        var offset = 0
        while (true) {
            val page = store.certifications(setOf(CertificationAnswer.CERTIFIED), offset, PAGE)
            for (certification in page) {
                if (mandateStatus(certification) is MandateStatus.NotFound &&
                    store.decideCertification(certification.pubkey, CertificationAnswer.CERTIFIED, CertificationAnswer.ENDED, ENDED_REASON, thisMonday(), VoteLevel.NON_VERIFIE)
                ) {
                    ended++
                } else {
                    offset++
                }
            }
            if (page.size < PAGE) break
        }
        checkedOn = today
        return ended
    }

    companion object {
        const val MAX_TERRITORY: Int = 20
        private const val PAGE = 500
        const val ENDED_REASON: String = "Mandat absent de l'annuaire officiel des élus mis à jour."
    }
}

/**
 * The queue `elus.certifications` of the team back-office (contract docs/integration/back-office.md, section
 * 7): the requests waiting, then the accounts certified (to withdraw one). Shows the account's key and the
 * mandate as the official directory gives it, nothing else.
 */
class ElectedCertificationsQueue(private val certifications: ElectedCertifications, private val store: UrneStore) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = "Comptes d'élus à certifier"
    override val viewPermission: String = Permissions.ELECTED_CERTIFY

    override fun pendingCount(): Int = store.countCertifications(CertificationAnswer.PENDING)

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val listed = store.certifications(setOf(CertificationAnswer.PENDING, CertificationAnswer.CERTIFIED), offset, limit + 1)
        val items = listed.take(limit).map { certification ->
            val match = certifications.match(certification)
            QueueItem(
                id = certification.pubkey,
                title = match?.let { "${it.name} (${it.territoryName})" } ?: "Mandat ${certification.mandate}",
                receivedAt = certification.requestedWeek.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
                summary = if (certification.status == CertificationAnswer.PENDING) "En attente de certification" else "Certifié",
            )
        }
        return QueuePage(items, (offset + limit).takeIf { listed.size > limit }?.toString())
    }

    override fun render(itemId: String, html: FlowContent) {
        val certification = store.certification(itemId)
        if (certification == null) {
            html.h1 { +"Cette demande n'existe plus." }
            return
        }
        html.h1 { +"Certification d'un compte d'élu" }
        if (certification.status != CertificationAnswer.PENDING) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +STATUS_TEXT.getValue(certification.status)
                certification.reason?.let { +" Motif : $it" }
            }
        }
        val match = certifications.match(certification)
        html.dl(classes = "bo-facts") {
            dt { +"Compte (clé publique)" }
            dd { code { +npubOf(certification.pubkey) } }
            dt { +"Demande faite la semaine du" }
            dd { +certification.requestedWeek.toString() }
            dt { +"Référence du mandat" }
            dd { code { +certification.mandate } }
            if (match != null) {
                dt { +"Élu" }
                dd { +match.name }
                match.function?.let { function -> dt { +"Fonction" }; dd { +function } }
                dt { +"Territoire" }
                dd { +"${match.territoryName} (${match.territory})" }
                dt { +"Depuis" }
                dd { +match.since }
            }
        }
        html.h2 { +"Annuaire officiel des élus" }
        html.p {
            +when (val status = certifications.mandateStatus(certification)) {
                is MandateStatus.Current -> "Mandat en cours selon le Répertoire national des élus mis à jour le ${status.sourceUpdatedOn}."
                is MandateStatus.NotFound -> "Mandat absent du Répertoire national des élus mis à jour le ${status.sourceUpdatedOn} : ne certifiez pas."
                MandateStatus.Unavailable -> "Annuaire pas encore chargé : certifiez seulement après sa mise à jour."
            }
        }
        if (certification.status == CertificationAnswer.PENDING) {
            html.h2 { +"Avant de certifier" }
            html.p {
                +"Rencontrez la personne. Vérifiez qu'elle est bien cet élu, sans rien saisir, copier ni photographier, et que l'application ouverte devant vous montre ce compte. "
                +"Seul un compte créé pour le mandat, qui n'a jamais voté, peut être certifié : l'élu garde son compte personnel à part."
            }
        }
    }

    override fun actions(itemId: String): List<QueueAction> = when (store.certification(itemId)?.status) {
        CertificationAnswer.PENDING -> listOf(
            QueueAction(CERTIFY, "Certifier (vu en personne, mandat vérifié)", Permissions.ELECTED_CERTIFY, sensitive = true),
            QueueAction(REFUSE, "Refuser (motif montré au compte)", Permissions.ELECTED_CERTIFY, reasonRequired = true),
        )
        CertificationAnswer.CERTIFIED -> listOf(
            QueueAction(WITHDRAW, "Retirer la certification (motif montré au compte)", Permissions.ELECTED_REVOKE, reasonRequired = true, sensitive = true),
        )
        else -> emptyList()
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
        val text = reason?.trim().orEmpty()
        return when (action) {
            CERTIFY -> certifications.certify(itemId)
            REFUSE -> if (text.isEmpty() || text.length > MAX_REASON) ActionOutcome.Refused(REASON_NEEDED) else certifications.refuse(itemId, text)
            WITHDRAW -> if (text.isEmpty() || text.length > MAX_REASON) ActionOutcome.Refused(REASON_NEEDED) else certifications.withdraw(itemId, text)
            else -> ActionOutcome.Refused("Action inconnue.")
        }
    }

    private fun npubOf(hex: String): String = runCatching { PublicKey.fromHex(hex).npub }.getOrDefault(hex)

    companion object {
        const val ID: String = "elus.certifications"
        const val CERTIFY: String = "certify"
        const val REFUSE: String = "refuse"
        const val WITHDRAW: String = "withdraw"
        const val MAX_REASON: Int = 1_000
        private const val REASON_NEEDED = "Un motif de 1 à 1000 caractères est obligatoire : il est montré au compte."
        private val STATUS_TEXT = mapOf(
            CertificationAnswer.CERTIFIED to "Compte certifié.",
            CertificationAnswer.REFUSED to "Demande refusée.",
            CertificationAnswer.WITHDRAWN to "Certification retirée.",
            CertificationAnswer.ENDED to "Certification terminée : mandat absent de l'annuaire mis à jour.",
        )
    }
}
