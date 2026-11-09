package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.platform.ApprovalChallenge
import org.politisationpopulairefrancaise.core.platform.CertificateOffer
import org.politisationpopulairefrancaise.core.platform.DoubleValidation
import org.politisationpopulairefrancaise.core.platform.DraftApproval
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.DraftRequest
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.MAX_CREDENTIAL_ID_LENGTH
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.EventDraft
import org.politisationpopulairefrancaise.core.platform.GroupTakeover
import org.politisationpopulairefrancaise.core.platform.RootRequestQr
import org.politisationpopulairefrancaise.core.platform.RootRequestView
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamCredential
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.TeamMember
import org.politisationpopulairefrancaise.core.platform.TrustVerdict
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.platform.decodeCanonicalBase64Url
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

/** The outcome of a signature request. */
sealed interface SignOutcome {
    data class Signed(val event: SignedEvent) : SignOutcome
    data class Refused(val refusal: Refusal) : SignOutcome
}

/** The outcome of a draft submitted for approval. */
sealed interface DraftOutcome {
    /** Known now: [created] is false when the same draft was already known (its content is then restored if needed). */
    data class Submitted(val id: String, val approvalUrl: String, val created: Boolean) : DraftOutcome
    data class Refused(val refusal: Refusal) : DraftOutcome
}

/** The outcome of an approval posted on the approval page. */
sealed interface ApprovalOutcome {
    data class Recorded(val account: String, val approvals: Int, val required: Int) : ApprovalOutcome {
        val ready: Boolean get() = approvals >= required
    }
    data class Refused(val refusal: Refusal) : ApprovalOutcome
}

/** The WebAuthn request options of an approval, or why none can be given. */
sealed interface OptionsOutcome {
    class Options(val challenge: ByteArray, val rpId: String, val credentialIds: List<String>) : OptionsOutcome
    data class Refused(val refusal: Refusal) : OptionsOutcome
}

/** Where a draft stands for `GET /v1/drafts/{use}/{id}`. */
sealed interface DraftState {
    data class Pending(val status: DraftStatusResponse) : DraftState
    data object Signed : DraftState
    data object Unknown : DraftState
}

/** One approval recorded by the service itself, on its own page. */
data class RecordedApproval(val account: String, val credentialId: String, val at: Long)

/**
 * What the approval page shows, rendered by the service from the draft it holds: never anything the
 * main server could change between the page and the signature.
 */
class ApprovalView(
    val use: SigningUse,
    val eventId: String,
    /** The content to read before approving; null once signed, or after a restart until the server submits the draft again. */
    val event: UnsignedEvent?,
    val doubleValidation: Boolean,
    val required: Int,
    val approvals: List<RecordedApproval>,
    val signed: Boolean,
)

/** The outcome of one root-signed event offered to the service (`stored`, `duplicate`, `superseded`, or a refusal code). */
data class LoadOutcome(val id: String?, val status: String)

/** The outcome of a root request deposited by the back-office (D-141). */
sealed interface RootRequestOutcome {
    data class Created(val id: String, val sha256: String, val expiresAt: Long) : RootRequestOutcome
    data class Refused(val refusal: Refusal) : RootRequestOutcome
}

/** A root request kept in memory for the owner's phone, 24 hours at most. */
internal class PendingRootRequest(val id: String, val createdAt: Long, val sha256: String, val events: List<UnsignedEvent>) {
    val expiresAt: Long get() = createdAt + RootRequests.LIFETIME_SECONDS
}

/** A content waiting for its approvals; [event] is null when it was rebuilt from the journal (the content is never journaled). */
internal class Draft(
    val use: SigningUse,
    val id: String,
    val doubleValidation: Boolean,
    val author: DraftAuthor?,
    val submittedAt: Long,
    val eventCreatedAt: Long?,
    val key: String?,
    var event: UnsignedEvent?,
) {
    val approvals: MutableList<RecordedApproval> = mutableListOf()
    val required: Int get() = if (doubleValidation) 2 else 1
}

/**
 * The signing service itself (contract docs/integration/service-signature.md, sections 4 and 5; D-129).
 * It owns its working keys, the certificates and the team list received from the root, the drafts waiting
 * for approval, and its journal.
 *
 * Human uses (version 1.2 of the contract): the server submits a draft ([submitDraft]), the service applies
 * the whole policy at once and keeps it (7 days at most); the members read it and approve it on the
 * service's own page, on its own origin ([approvalView], [approvalOptions], [approve]): the service checks
 * each passkey assertion itself and records the approval; the server then asks for the signature ([sign]),
 * granted only if the recorded approvals satisfy the rule fixed by the draft (one approval, or two of two
 * different accounts other than the author for a double validation), still valid with the current team
 * list. The service itself decides which contents need the double validation ([DoubleValidation], the
 * contents of M17 that implicate a person): a draft of such a content that does not ask for it is
 * refused, and so is its signature. Automatic uses are signed directly, without a draft.
 *
 * Every rule is checked again at signature: an enabled use; the shape of the use; a date neither in the
 * future nor too old; a key of this use with a valid certificate covering the date of the event; an event
 * never signed before for this use; for a closing statement, a ballot box never closed before; the hourly
 * ceiling. Every draft, approval, signature and refusal enters the journal before the answer.
 *
 * Thread-safe: every public method is synchronized (the service signs a few events per hour).
 */
class SignerService private constructor(
    private val config: SignerConfig,
    private val clock: Clock,
    private val vault: KeyVault,
    private val approvals: ApprovalVerifier,
    private val journal: SignerJournal,
    private val memory: Memory,
) {
    private val root: String? = config.rootPublicKey
    private val certificatesDir: Path = config.dataDir.resolve("certificates")
    private val teamListFile: Path = config.dataDir.resolve("team-list.json")
    private val policies: Map<SigningUse, UsePolicy> = SigningUse.entries.associateWith { SigningPolicies.of(it, config.maxPerHour) }
    private val keys = mutableMapOf<String, WorkingKey>()
    private var trust: PlatformTrust = PlatformTrust.of(root)
    private var teamList: TeamList? = null
    private var credentials: Map<String, Pair<TeamMember, TeamCredential>> = emptyMap()
    private var refusalWindowStart: Long = 0
    private var refusalsInWindow: Int = 0
    private var refusalsDropped: Int = 0

    private fun now(): Long = clock.instant().epochSecond

    private fun load() {
        DurableFiles.privateDirectory(certificatesDir)
        vault.loadAll().forEach { keys[it.pubkey] = it }
        Files.list(certificatesDir).use { stream -> stream.filter { it.fileName.toString().endsWith(".tmp") }.toList() }.forEach { Files.deleteIfExists(it) }
        val stored = Files.list(certificatesDir).use { stream -> stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList() }
        for (file in stored) {
            val event = readEvent(file) ?: throw SignerStartupException("Unreadable certificate file ${file.fileName}")
            trust = trust.plus(event)
        }
        if (Files.exists(teamListFile)) {
            val event = readEvent(teamListFile) ?: throw SignerStartupException("Unreadable team list file")
            if (root != null) TeamList.read(event, root)?.let(::useTeamList)
        }
        journal.append(now(), JournalActions.STARTED, JournalFields(code = if (root == null) SigningRefusals.NO_ROOT else null))
        maintain()
    }

    // ---------------------------------------------------------------- keys and rotation

    /** The state of [key] now, one of [WorkingKeyStates]. */
    private fun stateOf(key: WorkingKey, time: Long = now()): String {
        val verdict = trust.keyVerdict(key.pubkey, key.use, time)
        return when {
            verdict == TrustVerdict.REVOKED -> WorkingKeyStates.REVOKED
            verdict == TrustVerdict.EXPIRED -> WorkingKeyStates.EXPIRED
            key.isRetired -> WorkingKeyStates.EXPIRED
            verdict == TrustVerdict.TRUSTED -> WorkingKeyStates.ACTIVE
            verdict == TrustVerdict.NOT_YET_VALID -> WorkingKeyStates.SCHEDULED
            else -> WorkingKeyStates.AWAITING_CERTIFICATION
        }
    }

    /** When the certificate of [key] stops covering it: its end, or its earliest revocation if that comes first. */
    private fun endOf(key: String): Long? {
        val certificate = trust.certificate(key) ?: return null
        val revokedFrom = trust.revocation(key)?.let { it.revokedFrom ?: Long.MIN_VALUE }
        return if (revokedFrom != null) minOf(certificate.notAfter, revokedFrom) else certificate.notAfter
    }

    /** The key the service signs new events of [use] with: the active key whose certificate started last. */
    private fun currentKey(use: SigningUse, time: Long = now()): WorkingKey? =
        keys.values
            .filter { it.use == use && stateOf(it, time) == WorkingKeyStates.ACTIVE }
            .maxWithOrNull(compareBy<WorkingKey>({ trust.certificate(it.pubkey)?.notBefore ?: 0L }, { it.createdAt }))

    /**
     * Rotation and hygiene, at startup, after each certificate and every few minutes: destroys the secrets
     * of the keys revoked or expired; creates the next key of an enabled use when none waits for its
     * certificate and the current one ends within [ROTATION_LEAD_SECONDS] (or there is none); forgets the
     * drafts older than [DRAFT_LIFETIME_SECONDS]; writes the count of the refusals left out of the journal.
     */
    @Synchronized
    fun maintain() {
        val time = now()
        for (key in keys.values.toList()) {
            if (!key.isRetired && stateOf(key, time) in setOf(WorkingKeyStates.REVOKED, WorkingKeyStates.EXPIRED)) {
                keys[key.pubkey] = vault.retire(key, time)
                journal.append(time, JournalActions.KEY_RETIRED, JournalFields(use = key.use.id, key = key.pubkey, code = stateOf(key, time)))
            }
        }
        for (use in config.enabledUses) {
            val ofUse = keys.values.filter { it.use == use && !it.isRetired }
            val waiting = ofUse.any { stateOf(it, time) in setOf(WorkingKeyStates.AWAITING_CERTIFICATION, WorkingKeyStates.SCHEDULED) }
            if (waiting) continue
            val current = currentKey(use, time)
            val endsSoon = current == null || endOf(current.pubkey)?.let { it - time <= ROTATION_LEAD_SECONDS } ?: true
            if (endsSoon) {
                val created = vault.create(use, time)
                keys[created.pubkey] = created
                journal.append(time, JournalActions.KEY_CREATED, JournalFields(use = use.id, key = created.pubkey))
            }
        }
        purgeDrafts(time)
        flushDroppedRefusals(time, force = false)
    }

    /** Public data of every key, for `GET /v1/keys`: what the back-office shows and sends to the root for certification. */
    @Synchronized
    fun keys(): KeysResponse {
        val time = now()
        val current = SigningUse.entries.mapNotNull { currentKey(it, time)?.pubkey }.toSet()
        val infos = keys.values.sortedWith(compareBy({ it.use.ordinal }, { it.createdAt }, { it.pubkey })).map { key ->
            val certificate = trust.certificate(key.pubkey)?.takeIf { key.use in it.uses }
            WorkingKeyInfo(
                use = key.use.id,
                pubkey = key.pubkey,
                state = stateOf(key, time),
                createdAt = key.createdAt,
                current = key.pubkey in current,
                notBefore = certificate?.notBefore?.takeIf { certificate.wellFormed },
                notAfter = certificate?.notAfter?.takeIf { certificate.wellFormed },
                revokedFrom = trust.revocation(key.pubkey)?.revokedFrom,
                lastSignedAt = memory.lastSigned[key.pubkey],
            )
        }
        return KeysResponse(rootConfigured = root != null, teamList = teamList?.event?.id, teamListNotAfter = teamList?.notAfter, keys = infos)
    }

    // ---------------------------------------------------------------- root-signed events

    /**
     * Loads certificates and revocations signed by the root, for keys of this service only. A certificate
     * that makes a key active must name exactly the use of that key; a revocation is always taken.
     */
    @Synchronized
    fun loadCertificates(events: List<SignedEvent>): List<LoadOutcome> {
        val time = now()
        val outcomes = events.map { event ->
            val certificate = root?.let { KeyCertificate.read(event, it) }
            val key = certificate?.let { keys[it.key] }
            when {
                root == null -> LoadOutcome(event.id, SigningRefusals.NO_ROOT)
                certificate == null -> LoadOutcome(event.id, "not_a_certificate")
                key == null -> LoadOutcome(event.id, "unknown_key")
                certificate.status == KeyStatus.ACTIVE && event.tagValues(KeyCertificate.TAG_USE) != listOf(key.use.id) -> LoadOutcome(event.id, "wrong_uses")
                else -> {
                    val (next, offer) = trust.offer(event)
                    if (offer == CertificateOffer.STORED) {
                        trust = next
                        storeCertificates(certificate.key)
                        journal.append(time, JournalActions.CERTIFICATE_STORED, JournalFields(use = key.use.id, event = event.id, key = certificate.key))
                    }
                    LoadOutcome(event.id, offer.name.lowercase())
                }
            }
        }
        maintain()
        return outcomes
    }

    /** Writes the events the trust keeps for [key]: `<key>.json` (latest), `<key>.revocation.json` (earliest revocation, when another event). */
    private fun storeCertificates(key: String) {
        val events = trust.eventsOf(key)
        DurableFiles.writeAtomically(certificatesDir.resolve("$key.json"), events[0].toJson().toByteArray(Charsets.UTF_8))
        val revocationFile = certificatesDir.resolve("$key.revocation.json")
        val revocation = events.getOrNull(1)
        if (revocation != null) {
            DurableFiles.writeAtomically(revocationFile, revocation.toJson().toByteArray(Charsets.UTF_8))
        } else {
            Files.deleteIfExists(revocationFile)
        }
    }

    /** Loads the team list signed by the root (the latest version wins). */
    @Synchronized
    fun loadTeamList(event: SignedEvent): LoadOutcome {
        if (root == null) return LoadOutcome(event.id, SigningRefusals.NO_ROOT)
        val list = TeamList.read(event, root) ?: return LoadOutcome(event.id, "not_a_team_list")
        val known = teamList
        if (known != null) {
            if (known.event.id == event.id) return LoadOutcome(event.id, "duplicate")
            if (!EventReplacement.supersedes(event, known.event)) return LoadOutcome(event.id, "superseded")
        }
        DurableFiles.writeAtomically(teamListFile, event.toJson().toByteArray(Charsets.UTF_8))
        useTeamList(list)
        journal.append(now(), JournalActions.TEAM_LIST_STORED, JournalFields(event = event.id, code = list.members.size.toString()))
        return LoadOutcome(event.id, "stored")
    }

    private fun useTeamList(list: TeamList) {
        teamList = list
        credentials = list.members.values.flatMap { member -> member.credentials.map { it.id to (member to it) } }.toMap()
    }

    /** The certificates and revocations the trust rests on (public), for the server to hand them to the applications. */
    @Synchronized
    fun certificates(): List<SignedEvent> = trust.events

    @Synchronized
    fun teamListEvent(): SignedEvent? = teamList?.event

    @Synchronized
    fun journal(after: Long, limit: Int): List<JournalEntry> = journal.read(after, limit)

    @Synchronized
    fun journalHead(): JournalHead = journal.head()

    // ---------------------------------------------------------------- root requests (D-141)

    /** The root requests waiting for the owner's phone, oldest first (in memory only: a restart loses them). */
    private val rootRequests = LinkedHashMap<String, PendingRootRequest>()

    /**
     * Keeps the unsigned events the root must sign, prepared by the back-office, for the owner's phone: each
     * a well-formed certificate, revocation or team list of the root ([RootRequests.check]), each certificate
     * and revocation for a key of this service with its use. Kept 24 hours at most, [RootRequests.MAX_PENDING]
     * at most (the oldest is dropped). Journaled with its id, its hash and its number of events, never the events.
     */
    @Synchronized
    fun createRootRequest(drafts: List<EventDraft>): RootRequestOutcome {
        val time = now()
        purgeRootRequests(time)
        val root = root ?: return refuseRootRequest(time, Refusal(SigningRefusals.NO_ROOT))
        val events = try {
            drafts.map { it.toUnsigned() }
        } catch (e: IllegalArgumentException) {
            return refuseRootRequest(time, Refusal(SigningRefusals.MALFORMED_EVENT))
        }
        RootRequests.check(events, root, { key -> keys[key]?.use }, time)?.let { return refuseRootRequest(time, Refusal(it.code, it.detail)) }
        val id = ByteArray(16).also(random::nextBytes).toHexString()
        val sha256 = RootRequests.sha256(events)
        while (rootRequests.size >= RootRequests.MAX_PENDING) rootRequests.remove(rootRequests.keys.first())
        val request = PendingRootRequest(id, time, sha256, events)
        rootRequests[id] = request
        journal.append(time, JournalActions.ROOT_REQUEST_CREATED, JournalFields(event = sha256, d = id, code = events.size.toString()))
        return RootRequestOutcome.Created(id, sha256, request.expiresAt)
    }

    /** The pending root request [id], for `GET /racine/demandes/{id}`; null when unknown or expired. */
    @Synchronized
    fun rootRequest(id: String): RootRequestView? {
        purgeRootRequests(now())
        if (!RootRequestQr.isRequestId(id)) return null
        val request = rootRequests[id] ?: return null
        return RootRequestView(request.id, request.createdAt, request.expiresAt, request.sha256, request.events.map(EventDraft::of))
    }

    private fun purgeRootRequests(time: Long) {
        rootRequests.values.removeIf { it.expiresAt <= time }
    }

    private fun refuseRootRequest(time: Long, refusal: Refusal): RootRequestOutcome {
        refuse(time, JournalActions.ROOT_REQUEST_REFUSED, JournalFields(), refusal)
        return RootRequestOutcome.Refused(refusal)
    }

    // ---------------------------------------------------------------- drafts and approvals

    /** Submits a draft of a human use for approval: the whole policy applies now, so that a refusal comes early. */
    @Synchronized
    fun submitDraft(request: DraftRequest): DraftOutcome {
        val time = now()
        purgeDrafts(time)
        val use = SigningUse.fromId(request.use) ?: return refuseDraft(time, JournalFields(), Refusal(SigningRefusals.UNKNOWN_USE))
        val unsigned = try {
            request.event.toUnsigned()
        } catch (e: IllegalArgumentException) {
            return refuseDraft(time, JournalFields(use = use.id), Refusal(SigningRefusals.MALFORMED_EVENT))
        }
        val eventId = unsigned.idHex()
        val author = try {
            checkedAuthor(request.author)
        } catch (e: IllegalArgumentException) {
            return refuseDraft(time, JournalFields(use = use.id, event = eventId), Refusal(SigningRefusals.MALFORMED_REQUEST, "author"))
        }
        val fields = JournalFields(
            use = use.id, event = eventId, eventCreatedAt = unsigned.createdAt, key = unsigned.pubkey,
            doubleValidation = request.doubleValidation, authorAccount = author?.account, authorPubkey = author?.pubkey,
        )
        if (use.automatic) return refuseDraft(time, fields, Refusal(SigningRefusals.AUTOMATIC_USE))
        eventRefusal(time, use, unsigned, eventId)?.let { return refuseDraft(time, fields, it) }
        // The service decides which contents need two approvals, never the queue of the main server alone.
        if (!request.doubleValidation && DoubleValidation.requiredFor(use, unsigned)) {
            return refuseDraft(time, fields, Refusal(SigningRefusals.DOUBLE_VALIDATION_REQUIRED))
        }
        // D-142: the author is named for a double validation, and always for the editorial uses (the reviewer is
        // never the author, « règle des deux personnes » of the editorial circuit).
        if (author == null && (request.doubleValidation || use in SigningUse.AUTHOR_REQUIRED_USES)) {
            return refuseDraft(time, fields, Refusal(SigningRefusals.AUTHOR_REQUIRED))
        }
        teamListRefusal(time)?.let { return refuseDraft(time, fields, it) }
        val slot = draftKey(use, eventId)
        memory.drafts[slot]?.let { existing ->
            if (existing.doubleValidation != request.doubleValidation || existing.author != author) {
                return refuseDraft(time, fields, Refusal(SigningRefusals.DRAFT_CONFLICT))
            }
            if (existing.event == null) existing.event = unsigned
            return DraftOutcome.Submitted(eventId, config.approvalUrl(use, eventId), created = false)
        }
        if (memory.drafts.size >= MAX_DRAFTS) return refuseDraft(time, fields, Refusal(SigningRefusals.TOO_MANY_DRAFTS))
        memory.drafts[slot] = Draft(use, eventId, request.doubleValidation, author, time, unsigned.createdAt, unsigned.pubkey, unsigned)
        journal.append(time, JournalActions.DRAFT_SUBMITTED, fields)
        return DraftOutcome.Submitted(eventId, config.approvalUrl(use, eventId), created = true)
    }

    /** Where the draft of [eventId] for [useId] stands, for the back-office. */
    @Synchronized
    fun draftState(useId: String, eventId: String): DraftState {
        val time = now()
        purgeDrafts(time)
        val use = SigningUse.fromId(useId) ?: return DraftState.Unknown
        if (memory.isSigned(use, eventId)) return DraftState.Signed
        val draft = memory.drafts[draftKey(use, eventId)] ?: return DraftState.Unknown
        val valid = validApprovals(draft)
        return DraftState.Pending(
            DraftStatusResponse(
                use = use.id,
                draft = eventId,
                doubleValidation = draft.doubleValidation,
                required = draft.required,
                approvals = valid.map { DraftApproval(it.account, it.at) },
                ready = valid.size >= draft.required && teamListRefusal(time) == null,
                contentKnown = draft.event != null,
                submittedAt = draft.submittedAt,
                expiresAt = draft.submittedAt + DRAFT_LIFETIME_SECONDS,
                approvalUrl = config.approvalUrl(use, eventId),
            ),
        )
    }

    /** What the approval page of [eventId] for [useId] shows; null when no such draft is known. */
    @Synchronized
    fun approvalView(useId: String, eventId: String): ApprovalView? {
        purgeDrafts(now())
        val use = SigningUse.fromId(useId)?.takeIf { !it.automatic } ?: return null
        if (!isHex64(eventId)) return null
        if (memory.isSigned(use, eventId)) return ApprovalView(use, eventId, null, false, 0, emptyList(), signed = true)
        val draft = memory.drafts[draftKey(use, eventId)] ?: return null
        return ApprovalView(use, eventId, draft.event, draft.doubleValidation, draft.required, validApprovals(draft), signed = false)
    }

    /**
     * The WebAuthn request options of an approval: the challenge of this use and this event, the relying
     * party of the back-office passkeys, and the passkeys of the members who may still approve it.
     */
    @Synchronized
    fun approvalOptions(useId: String, eventId: String): OptionsOutcome {
        val time = now()
        purgeDrafts(time)
        val (use, draft) = when (val found = approvableDraft(useId, eventId)) {
            is Found.Refused -> return OptionsOutcome.Refused(found.refusal)
            is Found.Approvable -> found.use to found.draft
        }
        teamListRefusal(time)?.let { return OptionsOutcome.Refused(it) }
        val list = teamList ?: return OptionsOutcome.Refused(Refusal(SigningRefusals.NO_TEAM_LIST))
        val ids = list.members.values
            .filter { it.mayApprove(use, time) && !isAuthor(it, draft.author) && draft.approvals.none { a -> a.account == it.account } }
            .flatMap { member -> member.credentials.map { it.id } }
            .sorted()
        if (ids.isEmpty()) return OptionsOutcome.Refused(Refusal(SigningRefusals.NOT_ALLOWED_FOR_USE))
        return OptionsOutcome.Options(ApprovalChallenge.of(use, eventId), config.rpId, ids)
    }

    /**
     * Records the approval of [eventId] for [useId] by the member whose passkey made [assertion]: a passkey
     * of the team list signed by the root, an assertion over the challenge of this use and this event made
     * on the approval origin with user verification, a member allowed for the use, never the author of the
     * draft (D-142: in a single validation too), once per account.
     */
    @Synchronized
    fun approve(useId: String, eventId: String, assertion: PasskeyAssertion): ApprovalOutcome {
        val time = now()
        purgeDrafts(time)
        val (use, draft) = when (val found = approvableDraft(useId, eventId)) {
            is Found.Refused -> return refuseApproval(time, JournalFields(use = SigningUse.fromId(useId)?.id, event = eventId.takeIf(::isHex64)), found.refusal)
            is Found.Approvable -> found.use to found.draft
        }
        val fields = JournalFields(use = use.id, event = eventId, eventCreatedAt = draft.eventCreatedAt, key = draft.key)
        teamListRefusal(time)?.let { return refuseApproval(time, fields, it) }
        val found = assertion.credentialId.takeIf { decodeCanonicalBase64Url(it, MAX_CREDENTIAL_ID_LENGTH) != null }?.let { credentials[it] }
            ?: return refuseApproval(time, fields, Refusal(SigningRefusals.UNKNOWN_CREDENTIAL))
        val (member, credential) = found
        if (!approvals.verify(assertion, credential, ApprovalChallenge.of(use, eventId))) {
            return refuseApproval(time, fields, Refusal(SigningRefusals.ASSERTION_REFUSED))
        }
        val named = fields.copy(approvers = listOf(member.account), credential = credential.id)
        if (!member.mayApprove(use, time)) return refuseApproval(time, named, Refusal(SigningRefusals.NOT_ALLOWED_FOR_USE))
        if (isAuthor(member, draft.author)) return refuseApproval(time, named, Refusal(SigningRefusals.AUTHOR_CANNOT_APPROVE))
        if (draft.approvals.any { it.account == member.account }) return refuseApproval(time, named, Refusal(SigningRefusals.ALREADY_APPROVED))
        draft.approvals += RecordedApproval(member.account, credential.id, time)
        journal.append(time, JournalActions.APPROVAL_RECORDED, named)
        return ApprovalOutcome.Recorded(member.account, validApprovals(draft).size, draft.required)
    }

    private sealed interface Found {
        class Approvable(val use: SigningUse, val draft: Draft) : Found
        class Refused(val refusal: Refusal) : Found
    }

    /** The draft an approval may be given to: a human use enabled, not signed yet, with its content known. */
    private fun approvableDraft(useId: String, eventId: String): Found {
        val use = SigningUse.fromId(useId) ?: return Found.Refused(Refusal(SigningRefusals.UNKNOWN_USE))
        if (use.automatic) return Found.Refused(Refusal(SigningRefusals.AUTOMATIC_USE))
        if (use !in config.enabledUses) return Found.Refused(Refusal(SigningRefusals.USE_DISABLED))
        if (!isHex64(eventId)) return Found.Refused(Refusal(SigningRefusals.UNKNOWN_DRAFT))
        if (memory.isSigned(use, eventId)) return Found.Refused(Refusal(SigningRefusals.ALREADY_SIGNED))
        val draft = memory.drafts[draftKey(use, eventId)] ?: return Found.Refused(Refusal(SigningRefusals.UNKNOWN_DRAFT))
        // After a restart the content is unknown until the server submits the draft again: nobody approves what the page cannot show.
        if (draft.event == null) return Found.Refused(Refusal(SigningRefusals.UNKNOWN_DRAFT, "content"))
        return Found.Approvable(use, draft)
    }

    /**
     * The approvals of [draft] that still count: by a member of the current team list, with a passkey still
     * listed for that account, allowed for the use at the time of the approval, never the author of the draft
     * (D-142, whatever the rule: this recount also sets aside an approval an older version recorded); one per account.
     */
    private fun validApprovals(draft: Draft): List<RecordedApproval> {
        val list = teamList ?: return emptyList()
        return draft.approvals.filter { approval ->
            val member = list.members[approval.account] ?: return@filter false
            member.credentials.any { it.id == approval.credentialId } &&
                member.mayApprove(draft.use, approval.at) &&
                !isAuthor(member, draft.author)
        }.distinctBy { it.account }
    }

    private fun isAuthor(member: TeamMember, author: DraftAuthor?): Boolean =
        author != null && (author.account == member.account || (author.pubkey != null && member.nostrKey == author.pubkey))

    /** Why approvals cannot rely on the team list now, null when they can. */
    private fun teamListRefusal(time: Long): Refusal? {
        val list = teamList ?: return Refusal(SigningRefusals.NO_TEAM_LIST)
        return if (list.usableAt(time)) null else Refusal(SigningRefusals.TEAM_LIST_EXPIRED)
    }

    private fun purgeDrafts(time: Long) {
        memory.drafts.values.removeIf { it.submittedAt <= time - DRAFT_LIFETIME_SECONDS }
    }

    // ---------------------------------------------------------------- signing

    /**
     * Signs the event of [request] if every rule holds. [carriesApproval] tells that the body still carried
     * an approval (the form before version 1.2): refused, the approvals are collected by the service only.
     */
    @Synchronized
    fun sign(request: SignRequest, carriesApproval: Boolean = false): SignOutcome {
        val time = now()
        purgeDrafts(time)
        val use = SigningUse.fromId(request.use) ?: return refuse(time, JournalActions.REFUSED, JournalFields(), Refusal(SigningRefusals.UNKNOWN_USE))
        val unsigned = try {
            request.event.toUnsigned()
        } catch (e: IllegalArgumentException) {
            return refuse(time, JournalActions.REFUSED, JournalFields(use = use.id), Refusal(SigningRefusals.MALFORMED_EVENT))
        }
        val eventId = unsigned.idHex()
        val draft = memory.drafts[draftKey(use, eventId)]
        val approvers = draft?.let { validApprovals(it).map(RecordedApproval::account).sorted() }.orEmpty()
        val closing = if (use == SigningUse.DECIDER_URN_CLOSURE) unsigned.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) else null
        val fields = JournalFields(
            use = use.id, event = eventId, eventCreatedAt = unsigned.createdAt, key = unsigned.pubkey, approvers = approvers,
            doubleValidation = draft?.doubleValidation ?: false, authorAccount = draft?.author?.account, authorPubkey = draft?.author?.pubkey, d = closing,
        )
        if (carriesApproval) return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.UNEXPECTED_APPROVAL))
        eventRefusal(time, use, unsigned, eventId)?.let { return refuse(time, JournalActions.REFUSED, fields, it) }
        val policy = policies.getValue(use)
        if (policy.approvals == Approvals.HUMAN) {
            if (draft == null) return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.UNKNOWN_DRAFT))
            // Again at signature: a draft recorded with a single approval (by an older version, or a journal
            // written by hand) never makes a content that needs two approvals signed with one.
            if (!draft.doubleValidation && DoubleValidation.requiredFor(use, unsigned)) {
                return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.DOUBLE_VALIDATION_REQUIRED))
            }
            teamListRefusal(time)?.let { return refuse(time, JournalActions.REFUSED, fields, it) }
            if (approvers.size < draft.required) return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.APPROVALS_MISSING))
        }
        val window = memory.recent.getOrPut(use) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= time - 3_600) window.removeFirst()
        if (window.size >= policy.maxPerHour) return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.RATE_LIMITED))
        val key = keys.getValue(unsigned.pubkey)
        val identity = key.keys ?: return refuse(time, JournalActions.REFUSED, fields, Refusal(SigningRefusals.KEY_EXPIRED))
        val id = unsigned.id()
        val event = SignedEvent(eventId, unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, identity.sign(id).toHexString())
        check(event.verify() && trust.check(event, use) == TrustVerdict.TRUSTED) { "The service produced an event its own trust refuses" }
        journal.append(time, JournalActions.SIGNED, fields)
        memory.recordSigned(use, eventId, unsigned.pubkey, time, unsigned.createdAt, closing)
        window.addLast(time)
        return SignOutcome.Signed(event)
    }

    /** The first rule the event itself breaks (whatever the approvals), null when it may be signed. */
    private fun eventRefusal(time: Long, use: SigningUse, event: UnsignedEvent, eventId: String): Refusal? {
        if (use !in config.enabledUses) return Refusal(SigningRefusals.USE_DISABLED)
        if (root == null) return Refusal(SigningRefusals.NO_ROOT)
        if (event.tags.size > SigningPolicies.MAX_TAGS || event.canonicalJson().encodeToByteArray().size > SigningPolicies.MAX_EVENT_BYTES) return Refusal(SigningRefusals.TOO_LARGE)
        val policy = policies.getValue(use)
        policy.check(event)?.let { return it }
        if (event.createdAt > time + SigningPolicies.FUTURE_TOLERANCE_SECONDS) return Refusal(SigningRefusals.CREATED_AT_IN_FUTURE)
        if (event.createdAt < time - policy.maxAgeSeconds) return Refusal(SigningRefusals.CREATED_AT_TOO_OLD)
        keyRefusal(time, use, event)?.let { return Refusal(it) }
        if (memory.isSigned(use, eventId)) return Refusal(SigningRefusals.ALREADY_SIGNED)
        // D-144: a key of the platform never receives a role in a room, neither a working key of this service nor the root.
        if (use == SigningUse.GROUPS_TAKEOVER) {
            val target = GroupTakeover.target(event.tags)
            if (target == null || target in keys || target == root) return Refusal(SigningRefusals.BAD_TAG, "p")
        }
        if (use == SigningUse.DECIDER_URN_CLOSURE) {
            val d = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)
            if (d != null && d in memory.closedUrns) return Refusal(SigningRefusals.URN_ALREADY_CLOSED)
        }
        return null
    }

    /** Why the key of [event] may not sign it now, null when it may. */
    private fun keyRefusal(time: Long, use: SigningUse, event: UnsignedEvent): String? {
        val key = keys[event.pubkey]
        if (key == null || key.use != use) {
            return if (currentKey(use, time) == null) SigningRefusals.NO_ACTIVE_KEY else SigningRefusals.WRONG_KEY
        }
        when (stateOf(key, time)) {
            WorkingKeyStates.ACTIVE -> Unit
            WorkingKeyStates.REVOKED -> return SigningRefusals.KEY_REVOKED
            WorkingKeyStates.EXPIRED -> return SigningRefusals.KEY_EXPIRED
            WorkingKeyStates.SCHEDULED -> return SigningRefusals.WRONG_KEY
            else -> return SigningRefusals.KEY_NOT_CERTIFIED
        }
        if (key.isRetired) return SigningRefusals.KEY_EXPIRED
        if (trust.keyVerdict(key.pubkey, use, event.createdAt) != TrustVerdict.TRUSTED) return SigningRefusals.OUTSIDE_CERTIFICATE
        return null
    }

    private fun refuseDraft(time: Long, fields: JournalFields, refusal: Refusal): DraftOutcome {
        refuse(time, JournalActions.DRAFT_REFUSED, fields, refusal)
        return DraftOutcome.Refused(refusal)
    }

    private fun refuseApproval(time: Long, fields: JournalFields, refusal: Refusal): ApprovalOutcome {
        refuse(time, JournalActions.APPROVAL_REFUSED, fields, refusal)
        return ApprovalOutcome.Refused(refusal)
    }

    /**
     * Records a refusal (at most [MAX_REFUSALS_PER_HOUR] lines an hour, the others counted in one line, so that a
     * flood of bad requests cannot fill the disk) and returns it.
     */
    private fun refuse(time: Long, action: String, fields: JournalFields, refusal: Refusal): SignOutcome {
        flushDroppedRefusals(time, force = false)
        if (refusalsInWindow < MAX_REFUSALS_PER_HOUR) {
            refusalsInWindow++
            journal.append(time, action, fields.copy(code = refusal.code))
        } else {
            refusalsDropped++
        }
        return SignOutcome.Refused(refusal)
    }

    private fun flushDroppedRefusals(time: Long, force: Boolean) {
        if (!force && time < refusalWindowStart + 3_600) return
        if (refusalsDropped > 0) journal.append(time, JournalActions.REFUSALS_DROPPED, JournalFields(code = refusalsDropped.toString()))
        refusalWindowStart = time
        refusalsInWindow = 0
        refusalsDropped = 0
    }

    /**
     * What the service rebuilds from its journal at startup: the events signed (within the longest accepted
     * age), the closed ballot boxes (forever), the last signature of each key, the signatures of the last
     * hour, and the drafts of the last 7 days with their approvals (without their content).
     */
    internal class Memory(private val now: Long) {
        val signed: MutableSet<String> = mutableSetOf()
        val recent: MutableMap<SigningUse, ArrayDeque<Long>> = mutableMapOf()
        val closedUrns: MutableSet<String> = mutableSetOf()
        val lastSigned: MutableMap<String, Long> = mutableMapOf()
        val drafts: LinkedHashMap<String, Draft> = LinkedHashMap()

        fun isSigned(use: SigningUse, eventId: String): Boolean = draftKey(use, eventId) in signed

        fun recordSigned(use: SigningUse, eventId: String, key: String?, at: Long, eventCreatedAt: Long?, closing: String?) {
            signed += draftKey(use, eventId)
            if (use == SigningUse.DECIDER_URN_CLOSURE && closing != null) closedUrns += closing
            if (key != null) lastSigned[key] = maxOf(lastSigned[key] ?: Long.MIN_VALUE, at, eventCreatedAt ?: at)
            drafts.remove(draftKey(use, eventId))
        }

        fun replay(entry: JournalEntry) {
            val use = entry.use?.let(SigningUse::fromId) ?: return
            val eventId = entry.event ?: return
            val key = draftKey(use, eventId)
            when (entry.action) {
                JournalActions.SIGNED -> {
                    recordSigned(use, eventId, entry.key, entry.at, entry.eventCreatedAt, entry.d)
                    // An event older than the longest accepted age can never be signed again: no need to remember it.
                    if ((entry.eventCreatedAt ?: now) < now - SigningPolicies.HUMAN_MAX_AGE_SECONDS - 86_400) signed -= key
                    if (entry.at > now - 3_600) recent.getOrPut(use) { ArrayDeque() }.addLast(entry.at)
                }
                JournalActions.DRAFT_SUBMITTED -> {
                    if (entry.at > now - DRAFT_LIFETIME_SECONDS && key !in drafts) {
                        val author = DraftAuthor(entry.authorAccount, entry.authorPubkey).takeIf { it.isNamed }
                        drafts[key] = Draft(use, eventId, entry.doubleValidation == true, author, entry.at, entry.eventCreatedAt, entry.key, null)
                    }
                }
                JournalActions.APPROVAL_RECORDED -> {
                    val account = entry.approvers.singleOrNull() ?: return
                    val credential = entry.credential ?: return
                    drafts[key]?.approvals?.add(RecordedApproval(account, credential, entry.at))
                }
            }
        }
    }

    companion object {
        /** The next key is created this long before the current one ends (contract, section 4). */
        const val ROTATION_LEAD_SECONDS: Long = 15L * 86_400

        /** A draft waits 7 days at most for its approvals (D-123), then it must be submitted again as a new event. */
        const val DRAFT_LIFETIME_SECONDS: Long = 7L * 86_400

        /** The most drafts the service holds at once. */
        const val MAX_DRAFTS: Int = 1_000

        const val MAX_REFUSALS_PER_HOUR: Int = 1_000

        private val ACCOUNT = Regex("^[A-Za-z0-9._-]{1,64}$")

        private val random = java.security.SecureRandom()

        internal fun draftKey(use: SigningUse, eventId: String): String = "${use.id}:$eventId"

        /** [author] with empty fields as absent; null when it names nobody; throws when a field is malformed. */
        internal fun checkedAuthor(author: DraftAuthor?): DraftAuthor? {
            val account = author?.account?.ifEmpty { null }
            val pubkey = author?.pubkey?.ifEmpty { null }
            require(account == null || ACCOUNT.matches(account)) { "malformed author account" }
            require(pubkey == null || isHex64(pubkey)) { "malformed author key" }
            return if (account == null && pubkey == null) null else DraftAuthor(account, pubkey)
        }

        /** Opens the service on [config]: keys, certificates, team list and journal, every file checked. */
        fun open(
            config: SignerConfig,
            clock: Clock = Clock.systemUTC(),
            approvals: ApprovalVerifier = WebAuthn4jApprovals(config.approvalOrigin, config.rpId),
        ): SignerService {
            DurableFiles.privateDirectory(config.dataDir)
            val now = clock.instant().epochSecond
            val memory = Memory(now)
            val journal = SignerJournal.open(config.dataDir, now, memory::replay)
            val service = SignerService(config, clock, KeyVault(config.dataDir, config.kek()), approvals, journal, memory)
            service.load()
            return service
        }

        private fun readEvent(file: Path): SignedEvent? =
            runCatching { SignedEvent.fromJson(String(Files.readAllBytes(file), Charsets.UTF_8)) }.getOrNull()

        /** The JSON of the wire format, shared with the server. */
        val json = SigningProtocol.json
    }
}
