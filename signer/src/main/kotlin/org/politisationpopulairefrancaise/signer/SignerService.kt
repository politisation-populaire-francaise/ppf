package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.platform.ApprovalChallenge
import org.politisationpopulairefrancaise.core.platform.ApprovalProof
import org.politisationpopulairefrancaise.core.platform.CertificateOffer
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
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
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.Base64

/** The outcome of a signature request. */
sealed interface SignOutcome {
    data class Signed(val event: SignedEvent) : SignOutcome
    data class Refused(val refusal: Refusal) : SignOutcome
}

/** The outcome of one root-signed event offered to the service (`stored`, `duplicate`, `superseded`, or a refusal code). */
data class LoadOutcome(val id: String?, val status: String)

/**
 * The signing service itself (contract docs/integration/service-signature.md, section 4; D-129). It owns
 * its working keys, the certificates and the team list received from the root, and its journal. It signs
 * an event only when every rule holds: an enabled use; the shape of the use; a date neither in the future
 * nor too old; a key of this use with a valid certificate covering the date of the event; an event never
 * signed before for this use; the approvals the use asks for, each a passkey assertion of a member allowed
 * for this use by the team list, over the challenge of this use and this event; the hourly ceiling.
 * Every signature and every refusal enters the journal before the answer.
 *
 * Thread-safe: every public method is synchronized (the service signs a few events per hour).
 */
class SignerService private constructor(
    private val config: SignerConfig,
    private val clock: Clock,
    private val vault: KeyVault,
    private val approvals: ApprovalVerifier,
    private val journal: SignerJournal,
    private val signed: MutableSet<String>,
    private val recentSignatures: MutableMap<SigningUse, ArrayDeque<Long>>,
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
        Files.createDirectories(certificatesDir)
        vault.loadAll().forEach { keys[it.pubkey] = it }
        val stored = Files.list(certificatesDir).use { stream -> stream.filter { it.fileName.toString().endsWith(".json") }.toList() }
        for (file in stored) {
            val event = readEvent(file) ?: throw SignerStartupException("Unreadable certificate file ${file.fileName}")
            trust = trust.plus(event)
        }
        if (Files.exists(teamListFile)) {
            val event = readEvent(teamListFile) ?: throw SignerStartupException("Unreadable team list file")
            if (root != null) TeamList.read(event, root)?.let(::useTeamList)
        }
        journal.append(now(), JournalActions.STARTED, code = if (root == null) SigningRefusals.NO_ROOT else null)
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

    /** When [certificate] stops covering its key: its end, or its revocation if that comes first. */
    private fun endOf(certificate: KeyCertificate): Long =
        if (certificate.status == KeyStatus.REVOKED) minOf(certificate.notAfter, certificate.revokedFrom ?: Long.MIN_VALUE) else certificate.notAfter

    /** The key the service signs new events of [use] with: the active key whose certificate started last. */
    private fun currentKey(use: SigningUse, time: Long = now()): WorkingKey? =
        keys.values
            .filter { it.use == use && stateOf(it, time) == WorkingKeyStates.ACTIVE }
            .maxWithOrNull(compareBy<WorkingKey>({ trust.certificate(it.pubkey)?.notBefore ?: 0 }, { it.createdAt }))

    /**
     * Rotation and hygiene, at startup, after each certificate and every few minutes: destroys the secrets
     * of the keys revoked or expired; creates the next key of an enabled use when none waits for its
     * certificate and the current one ends within [ROTATION_LEAD_SECONDS] (or there is none); writes the
     * count of the refusals left out of the journal.
     */
    @Synchronized
    fun maintain() {
        val time = now()
        for (key in keys.values.toList()) {
            if (!key.isRetired && stateOf(key, time) in setOf(WorkingKeyStates.REVOKED, WorkingKeyStates.EXPIRED)) {
                keys[key.pubkey] = vault.retire(key, time)
                journal.append(time, JournalActions.KEY_RETIRED, use = key.use.id, key = key.pubkey, code = stateOf(key, time))
            }
        }
        for (use in config.enabledUses) {
            val ofUse = keys.values.filter { it.use == use && !it.isRetired }
            val waiting = ofUse.any { stateOf(it, time) in setOf(WorkingKeyStates.AWAITING_CERTIFICATION, WorkingKeyStates.SCHEDULED) }
            if (waiting) continue
            val current = currentKey(use, time)
            val endsSoon = current == null || trust.certificate(current.pubkey)?.let { endOf(it) - time <= ROTATION_LEAD_SECONDS } ?: true
            if (endsSoon) {
                val created = vault.create(use, time)
                keys[created.pubkey] = created
                journal.append(time, JournalActions.KEY_CREATED, use = use.id, key = created.pubkey)
            }
        }
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
                revokedFrom = certificate?.takeIf { it.status == KeyStatus.REVOKED }?.revokedFrom,
            )
        }
        return KeysResponse(rootConfigured = root != null, teamList = teamList?.event?.id, keys = infos)
    }

    // ---------------------------------------------------------------- root-signed events

    /** Loads certificates and revocations signed by the root, for keys of this service only. */
    @Synchronized
    fun loadCertificates(events: List<SignedEvent>): List<LoadOutcome> {
        val time = now()
        val outcomes = events.map { event ->
            val certificate = root?.let { KeyCertificate.read(event, it) }
            when {
                root == null -> LoadOutcome(event.id, SigningRefusals.NO_ROOT)
                certificate == null -> LoadOutcome(event.id, "not_a_certificate")
                certificate.key !in keys -> LoadOutcome(event.id, "unknown_key")
                else -> {
                    val (next, offer) = trust.offer(event)
                    if (offer == CertificateOffer.STORED) {
                        DurableFiles.writeAtomically(certificatesDir.resolve("${certificate.key}.json"), event.toJson().toByteArray(Charsets.UTF_8))
                        trust = next
                        journal.append(time, JournalActions.CERTIFICATE_STORED, use = keys.getValue(certificate.key).use.id, event = event.id, key = certificate.key)
                    }
                    LoadOutcome(event.id, offer.name.lowercase())
                }
            }
        }
        maintain()
        return outcomes
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
        journal.append(now(), JournalActions.TEAM_LIST_STORED, event = event.id, code = list.members.size.toString())
        return LoadOutcome(event.id, "stored")
    }

    private fun useTeamList(list: TeamList) {
        teamList = list
        val byId = mutableMapOf<String, Pair<TeamMember, TeamCredential>>()
        for (member in list.members.values) {
            for (credential in member.credentials) {
                val id = decodeBase64Url(credential.id)?.toHexString() ?: continue
                byId[id] = member to credential
            }
        }
        credentials = byId
    }

    /** The certificates known (public), for the server to hand them to the applications. */
    @Synchronized
    fun certificates(): List<SignedEvent> = trust.certificates.map { it.event }

    @Synchronized
    fun teamListEvent(): SignedEvent? = teamList?.event

    @Synchronized
    fun journal(after: Long, limit: Int): List<JournalEntry> = journal.read(after, limit)

    @Synchronized
    fun journalHead(): JournalHead = journal.head()

    // ---------------------------------------------------------------- signing

    @Synchronized
    fun sign(request: SignRequest): SignOutcome {
        val time = now()
        val use = SigningUse.fromId(request.use) ?: return refuse(time, null, null, Refusal(SigningRefusals.UNKNOWN_USE))
        val unsigned = try {
            request.event.toUnsigned()
        } catch (e: IllegalArgumentException) {
            return refuse(time, use, null, Refusal(SigningRefusals.MALFORMED_EVENT))
        }
        val eventId = unsigned.idHex()
        val refusal = firstRefusal(time, use, unsigned, eventId, request.approval)
        if (refusal != null) return refuse(time, use, unsigned, refusal.first, eventId, refusal.second)
        val key = keys.getValue(unsigned.pubkey)
        val identity = key.keys ?: return refuse(time, use, unsigned, Refusal(SigningRefusals.KEY_EXPIRED), eventId)
        val id = unsigned.id()
        val event = SignedEvent(eventId, unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, identity.sign(id).toHexString())
        check(event.verify() && trust.check(event, use) == TrustVerdict.TRUSTED) { "The service produced an event its own trust refuses" }
        val approvers = approverAccounts(request.approval)
        journal.append(time, JournalActions.SIGNED, use = use.id, event = eventId, eventCreatedAt = unsigned.createdAt, key = key.pubkey, approvers = approvers)
        signed += "${use.id}:$eventId"
        recentSignatures.getOrPut(use) { ArrayDeque() }.addLast(time)
        return SignOutcome.Signed(event)
    }

    /** The first rule broken (with the accounts that approved, for the journal), null when the event may be signed. */
    private fun firstRefusal(time: Long, use: SigningUse, event: UnsignedEvent, eventId: String, proof: ApprovalProof): Pair<Refusal, List<String>>? {
        fun no(code: String, detail: String? = null) = Refusal(code, detail) to emptyList<String>()
        if (use !in config.enabledUses) return no(SigningRefusals.USE_DISABLED)
        if (root == null) return no(SigningRefusals.NO_ROOT)
        if (event.tags.size > SigningPolicies.MAX_TAGS || event.canonicalJson().encodeToByteArray().size > SigningPolicies.MAX_EVENT_BYTES) return no(SigningRefusals.TOO_LARGE)
        val policy = policies.getValue(use)
        policy.check(event)?.let { return it to emptyList() }
        if (event.createdAt > time + SigningPolicies.FUTURE_TOLERANCE_SECONDS) return no(SigningRefusals.CREATED_AT_IN_FUTURE)
        if (event.createdAt < time - policy.maxAgeSeconds) return no(SigningRefusals.CREATED_AT_TOO_OLD)
        keyRefusal(time, use, event)?.let { return no(it) }
        if ("${use.id}:$eventId" in signed) return no(SigningRefusals.ALREADY_SIGNED)
        approvalRefusal(time, use, eventId, proof, policy)?.let { return it to approverAccounts(proof) }
        val window = recentSignatures.getOrPut(use) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= time - 3_600) window.removeFirst()
        if (window.size >= policy.maxPerHour) return Refusal(SigningRefusals.RATE_LIMITED) to approverAccounts(proof)
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

    /** Why the approvals do not allow the signature, null when they do. */
    private fun approvalRefusal(time: Long, use: SigningUse, eventId: String, proof: ApprovalProof, policy: UsePolicy): Refusal? {
        if (policy.approvals == Approvals.NONE) {
            return if (proof.assertions.isEmpty() && !proof.doubleValidation) null else Refusal(SigningRefusals.UNEXPECTED_APPROVAL)
        }
        if (teamList == null) return Refusal(SigningRefusals.NO_TEAM_LIST)
        val required = if (proof.doubleValidation) 2 else 1
        if (proof.assertions.size != required) return Refusal(SigningRefusals.APPROVALS_MISSING)
        val author = proof.author
        if (proof.doubleValidation && (author == null || (author.account.isNullOrEmpty() && author.pubkey.isNullOrEmpty()))) {
            return Refusal(SigningRefusals.AUTHOR_REQUIRED)
        }
        val members = mutableListOf<Pair<TeamMember, TeamCredential>>()
        for (assertion in proof.assertions) {
            val id = decodeBase64Url(assertion.credentialId)?.toHexString() ?: return Refusal(SigningRefusals.UNKNOWN_CREDENTIAL)
            val found = credentials[id] ?: return Refusal(SigningRefusals.UNKNOWN_CREDENTIAL)
            if (found.first.account != assertion.account) return Refusal(SigningRefusals.ACCOUNT_MISMATCH)
            if (!found.first.mayApprove(use, time)) return Refusal(SigningRefusals.NOT_ALLOWED_FOR_USE)
            members += found
        }
        if (members.map { it.first.account }.distinct().size != members.size) return Refusal(SigningRefusals.SAME_ACCOUNT_TWICE)
        if (proof.doubleValidation && author != null) {
            val authorKey = author.pubkey?.lowercase()
            if (members.any { (member, _) -> member.account == author.account || (authorKey != null && member.nostrKey == authorKey) }) {
                return Refusal(SigningRefusals.AUTHOR_CANNOT_APPROVE)
            }
        }
        val challenge = ApprovalChallenge.of(use, eventId)
        proof.assertions.forEachIndexed { index, assertion ->
            if (!approvals.verify(assertion, members[index].second, challenge)) return Refusal(SigningRefusals.ASSERTION_REFUSED)
        }
        return null
    }

    /** The accounts named by the assertions (verified ones for a signature, as presented for a refusal). */
    private fun approverAccounts(proof: ApprovalProof): List<String> =
        proof.assertions.map { it.account }.filter { ACCOUNT.matches(it) }.distinct().sorted()

    /**
     * Records a refusal (at most [MAX_REFUSALS_PER_HOUR] lines an hour, the others counted in one line, so that a
     * flood of bad requests cannot fill the disk) and returns it.
     */
    private fun refuse(
        time: Long,
        use: SigningUse?,
        event: UnsignedEvent?,
        refusal: Refusal,
        eventId: String? = event?.idHex(),
        approvers: List<String> = emptyList(),
    ): SignOutcome {
        flushDroppedRefusals(time, force = false)
        if (refusalsInWindow < MAX_REFUSALS_PER_HOUR) {
            refusalsInWindow++
            journal.append(time, JournalActions.REFUSED, use = use?.id, event = eventId, eventCreatedAt = event?.createdAt, key = event?.pubkey, code = refusal.code, approvers = approvers)
        } else {
            refusalsDropped++
        }
        return SignOutcome.Refused(refusal)
    }

    private fun flushDroppedRefusals(time: Long, force: Boolean) {
        if (!force && time < refusalWindowStart + 3_600) return
        if (refusalsDropped > 0) journal.append(time, JournalActions.REFUSALS_DROPPED, code = refusalsDropped.toString())
        refusalWindowStart = time
        refusalsInWindow = 0
        refusalsDropped = 0
    }

    companion object {
        /** The next key is created this long before the current one ends (contract, section 4). */
        const val ROTATION_LEAD_SECONDS: Long = 15L * 86_400

        const val MAX_REFUSALS_PER_HOUR: Int = 1_000

        private val ACCOUNT = Regex("^[A-Za-z0-9._-]{1,64}$")

        /** Opens the service on [config]: keys, certificates, team list and journal, every file checked. */
        fun open(
            config: SignerConfig,
            clock: Clock = Clock.systemUTC(),
            approvals: ApprovalVerifier = WebAuthn4jApprovals(config.approvalOrigin, config.rpId),
        ): SignerService {
            Files.createDirectories(config.dataDir)
            DurableFiles.restrictToOwner(config.dataDir)
            val now = clock.instant().epochSecond
            val signed = mutableSetOf<String>()
            val recent = mutableMapOf<SigningUse, ArrayDeque<Long>>()
            val journal = SignerJournal.open(config.dataDir) replay@{ entry ->
                if (entry.action != JournalActions.SIGNED) return@replay
                val use = entry.use?.let(SigningUse::fromId) ?: return@replay
                // An event older than the longest accepted age can never be signed again: no need to remember it.
                if ((entry.eventCreatedAt ?: now) >= now - SigningPolicies.HUMAN_MAX_AGE_SECONDS - 86_400) signed += "${use.id}:${entry.event}"
                if (entry.at > now - 3_600) recent.getOrPut(use) { ArrayDeque() }.addLast(entry.at)
            }
            val service = SignerService(config, clock, KeyVault(config.dataDir, config.kek()), approvals, journal, signed, recent)
            service.load()
            return service
        }

        private fun readEvent(file: Path): SignedEvent? =
            runCatching { SignedEvent.fromJson(String(Files.readAllBytes(file), Charsets.UTF_8)) }.getOrNull()

        internal fun decodeBase64Url(value: String): ByteArray? {
            if (value.isEmpty() || value.length > 2_048) return null
            return try {
                Base64.getUrlDecoder().decode(value.trimEnd('='))
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /** The JSON of the wire format, shared with the server. */
        val json = SigningProtocol.json
    }
}
