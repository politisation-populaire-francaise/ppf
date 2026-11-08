package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.RootRequestQr
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.signing.RootRequestResult
import org.politisationpopulairefrancaise.server.signing.SignerAdmin
import java.time.Instant

/** What a root request prepared here asks. */
enum class RootRequestKind { CEREMONY, REVOCATION }

/**
 * A root request this server prepared and deposited in the signing service (D-141), kept in memory for its
 * page as long as the service keeps it (24 hours): its events, and what the page needs to name things
 * (pseudonyms of the accounts, names of the passkeys, accounts left out of the team list).
 */
class PreparedRequest(
    val id: String,
    val sha256: String,
    val createdAt: Long,
    val expiresAt: Long,
    val kind: RootRequestKind,
    val events: List<UnsignedEvent>,
    val preparedBy: String,
    val pseudonyms: Map<String, String> = emptyMap(),
    /** Names of the passkeys by credential id (canonical base64url). */
    val keyNames: Map<String, String> = emptyMap(),
    val excluded: List<Pair<String, Exclusion>> = emptyList(),
)

/** A point of attention of the page « Clés de la plateforme ». */
sealed interface PlatformWarning {
    data object NoTeamList : PlatformWarning

    /** The team list held names no valid end, or its end is past: no approval is possible. */
    data class TeamListExpired(val at: Long?) : PlatformWarning

    data class TeamListEnds(val at: Long) : PlatformWarning

    /** The key in use for [use] ends at [at] (in less than 15 days). */
    data class KeyEnds(val use: String, val key: String, val at: Long) : PlatformWarning

    data class AwaitingCertification(val count: Int) : PlatformWarning

    /** A human or automatic use has no key in use: nothing of it can be signed. */
    data class NoCurrentKey(val use: String) : PlatformWarning
}

/** Everything the page « Clés de la plateforme » shows. */
class PlatformOverview(
    val now: Long,
    val signerConfigured: Boolean,
    /** The root public key of this server (`PPF_SIGNER_ROOT_PUBKEY`), null when none is configured. */
    val root: String?,
    /** What the service says, null when it does not answer. */
    val keys: KeysResponse?,
    val teamList: TeamList?,
    /** What a ceremony prepared now would ask (null without root or without answer of the service). */
    val preview: CeremonyPlan?,
    val warnings: List<PlatformWarning>,
    val prepared: List<PreparedRequest>,
    /** The newest line of the copy of the journal of the service. */
    val copy: AuditRecord?,
    val pseudonyms: Map<String, String>,
)

/** Where one event of a prepared request stands, as the service holds it now. */
enum class EventState { RECEIVED, WAITING }

/**
 * « Clés de la plateforme » (contract docs/integration/service-signature.md, step 4; D-141): the working keys
 * of the signing service, the preparation of the ceremony of the root and of a revocation after a compromise.
 * The root itself never comes near this server: the back-office composes the unsigned events, deposits them
 * in the service, and shows a QR code that the owner's phone scans (step 5); the phone fetches the request
 * from the approval origin of the service, checks its hash, shows everything in clear, signs and posts the
 * signed events to the service directly.
 *
 * Every action needs [Permissions.PLATFORM_KEYS] and a passkey verified less than 5 minutes ago, and enters
 * the journal of the team (`platform.<action>`).
 */
class PlatformKeysAdmin(private val backOffice: BackOffice, private val signer: SignerAdmin?) {
    private val requests = LinkedHashMap<String, PreparedRequest>()

    private fun now(): Long = backOffice.clock.instant().epochSecond

    /** The environment written in the QR code (`production`, `preproduction`, otherwise `development`). */
    val environment: String get() = backOffice.config.environment.takeIf { it in RootRequestQr.ENVIRONMENTS } ?: "development"

    fun qr(request: PreparedRequest): RootRequestQr = RootRequestQr(environment, request.id, request.sha256)

    @Synchronized
    fun prepared(id: String): PreparedRequest? {
        purge()
        return requests[id]
    }

    @Synchronized
    private fun preparedList(): List<PreparedRequest> {
        purge()
        return requests.values.reversed()
    }

    @Synchronized
    private fun keep(request: PreparedRequest) {
        purge()
        while (requests.size >= MAX_KEPT) requests.remove(requests.keys.first())
        requests[request.id] = request
    }

    private fun purge() {
        val time = now()
        requests.values.removeIf { it.expiresAt <= time }
    }

    suspend fun overview(actor: TeamActor): Outcome<PlatformOverview> {
        if (!actor.has(Permissions.PLATFORM_KEYS)) return Outcome.Denied("forbidden")
        val time = now()
        val root = signer?.rootPublicKey
        val keys = signer?.keys()
        val list = if (keys?.teamList != null && root != null) signer.teamList()?.let { TeamList.read(it, root) } else null
        val candidates = io { backOffice.teamListCandidates() }
        val preview = if (root != null && keys != null) RootCeremony.compose(root, keys, candidates, list, time) else null
        val copy = io { backOffice.lastSignerJournalMark() }
        val pseudonyms = candidates.associate { it.account.id.toString() to it.account.pseudonym }
        return Outcome.Ok(PlatformOverview(time, signer != null, root, keys, list, preview, keys?.let { warnings(it, time) }.orEmpty(), preparedList(), copy, pseudonyms))
    }

    /**
     * Prepares the ceremony of the root: certificates of the keys awaiting certification, closing revocations
     * of the keys expired or replaced, a new team list ([RootCeremony.compose]), in one request.
     */
    suspend fun prepareCeremony(auth: Authenticated): Outcome<PreparedRequest> {
        refusal(auth)?.let { return Outcome.Denied(it) }
        val admin = signer ?: return Outcome.Denied("signer_off")
        val root = admin.rootPublicKey ?: return Outcome.Denied("no_root")
        val keys = admin.keys() ?: return Outcome.Denied("signer_unavailable")
        if (!keys.rootConfigured) return Outcome.Denied("no_root")
        val list = if (keys.teamList != null) admin.teamList()?.let { TeamList.read(it, root) } else null
        val candidates = io { backOffice.teamListCandidates() }
        val time = now()
        val plan = RootCeremony.compose(root, keys, candidates, list, time)
        if (plan.events.isEmpty()) return Outcome.Denied("nothing_to_sign")
        val created = when (val result = admin.rootRequest(plan.events)) {
            is RootRequestResult.Refused -> return refused(auth, "ceremony", result)
            is RootRequestResult.Created -> result
        }
        val request = PreparedRequest(
            id = created.id,
            sha256 = created.sha256,
            createdAt = time,
            expiresAt = created.expiresAt,
            kind = RootRequestKind.CEREMONY,
            events = plan.events,
            preparedBy = auth.actor.pseudonym,
            pseudonyms = candidates.associate { it.account.id.toString() to it.account.pseudonym },
            keyNames = candidates.flatMap { candidate -> candidate.keys.map { it.credential.id to it.name } }.toMap(),
            excluded = plan.excluded.map { (candidate, why) -> candidate.account.pseudonym to why },
        )
        keep(request)
        io {
            backOffice.recordPlatformAction(
                auth.actor, "ceremony_prepared", created.id, null,
                mapOf(
                    "request" to created.id,
                    "sha256" to created.sha256,
                    "certificates" to plan.certificates.size.toString(),
                    "revocations" to plan.revocations.size.toString(),
                    "members" to plan.listed.size.toString(),
                    "excluded" to plan.excluded.size.toString(),
                ),
            )
        }
        return Outcome.Ok(request)
    }

    /**
     * Prepares the revocation of [key] after a compromise, from [effectiveFrom] (now by default, possibly in the
     * past, never in the future): [reason] is mandatory and kept in the journal of the team, never in the event.
     * A revocation is definitive.
     */
    suspend fun prepareRevocation(auth: Authenticated, key: String, effectiveFrom: Instant?, reason: String?): Outcome<PreparedRequest> {
        refusal(auth)?.let { return Outcome.Denied(it) }
        val text = BackOffice.cleanReason(reason) ?: return Outcome.Denied("reason_required")
        val admin = signer ?: return Outcome.Denied("signer_off")
        val root = admin.rootPublicKey ?: return Outcome.Denied("no_root")
        val time = now()
        val from = effectiveFrom?.epochSecond ?: time
        if (from > time + FUTURE_TOLERANCE_SECONDS) return Outcome.Denied("revocation_in_future")
        val keys = admin.keys() ?: return Outcome.Denied("signer_unavailable")
        val info = keys.keys.firstOrNull { it.pubkey == key } ?: return Outcome.Denied("unknown_platform_key")
        val event = RootCeremony.revocation(root, info, from, time) ?: return Outcome.Denied("key_not_certified")
        val created = when (val result = admin.rootRequest(listOf(event))) {
            is RootRequestResult.Refused -> return refused(auth, "revocation", result)
            is RootRequestResult.Created -> result
        }
        val request = PreparedRequest(created.id, created.sha256, time, created.expiresAt, RootRequestKind.REVOCATION, listOf(event), auth.actor.pseudonym)
        keep(request)
        io {
            backOffice.recordPlatformAction(
                auth.actor, "revocation_prepared", info.pubkey, text,
                mapOf("key" to info.pubkey, "use" to info.use, "effective_from" to from.toString(), "request" to created.id, "sha256" to created.sha256),
            )
        }
        return Outcome.Ok(request)
    }

    /**
     * Where each event of [request] stands: received when the service holds its signed version (same id: the
     * id of a signed event is the id of the unsigned one); null when the service does not answer.
     */
    suspend fun states(request: PreparedRequest): Map<String, EventState>? {
        val admin = signer ?: return null
        val held = admin.certificates()?.map { it.id }?.toMutableSet() ?: return null
        if (request.events.any { it.kind == org.politisationpopulairefrancaise.core.signing.EventKinds.PLATFORM_TEAM_LIST }) {
            admin.teamList()?.let { held += it.id }
        }
        return request.events.associate { event -> event.idHex() to if (event.idHex() in held) EventState.RECEIVED else EventState.WAITING }
    }

    private fun refusal(auth: Authenticated): String? = when {
        !auth.actor.has(Permissions.PLATFORM_KEYS) -> "forbidden"
        !auth.isFresh(backOffice.clock.instant()) -> "confirmation_required"
        else -> null
    }

    /** A refusal of the service, journaled with its code (never an event), given to the page with its code. */
    private suspend fun refused(auth: Authenticated, what: String, result: RootRequestResult.Refused): Outcome<PreparedRequest> {
        io {
            backOffice.recordPlatformAction(auth.actor, "${what}_refused", null, null, buildMap {
                put("code", result.code.take(100))
                result.detail?.let { put("detail", it.take(100)) }
            })
        }
        return Outcome.Denied("$SIGNER_REFUSAL_PREFIX${result.code}")
    }

    private fun warnings(keys: KeysResponse, time: Long): List<PlatformWarning> = buildList {
        when {
            keys.teamList == null -> add(PlatformWarning.NoTeamList)
            keys.teamListNotAfter == null || keys.teamListNotAfter!! <= time -> add(PlatformWarning.TeamListExpired(keys.teamListNotAfter))
            keys.teamListNotAfter!! - time < RootCeremony.WARNING_SECONDS -> add(PlatformWarning.TeamListEnds(keys.teamListNotAfter!!))
        }
        val awaiting = keys.keys.count { it.state == WorkingKeyStates.AWAITING_CERTIFICATION }
        if (awaiting > 0) add(PlatformWarning.AwaitingCertification(awaiting))
        keys.keys.filter { it.current }.forEach { key ->
            val end = endOf(key)
            if (end != null && end - time < RootCeremony.WARNING_SECONDS) add(PlatformWarning.KeyEnds(key.use, key.pubkey, end))
        }
        val inUse = keys.keys.filter { it.current }.map { it.use }.toSet()
        keys.keys.map { it.use }.distinct().filter { it !in inUse && SigningUse.fromId(it) != null }.forEach { add(PlatformWarning.NoCurrentKey(it)) }
    }

    private fun endOf(key: WorkingKeyInfo): Long? = listOfNotNull(key.notAfter, key.revokedFrom).minOrNull()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        /** The requests kept for their pages (as many as the service keeps). */
        const val MAX_KEPT: Int = 20

        /** A revocation may take effect at most a minute ahead (the clocks of the two machines). */
        const val FUTURE_TOLERANCE_SECONDS: Long = 60

        /** The refusals of the service reach the page as `signer:<code>`. */
        const val SIGNER_REFUSAL_PREFIX: String = "signer:"
    }
}
