package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/**
 * The administration routes of the signing service for the tests of the back-office (D-141), in memory, in
 * the style of [FakePlatformSigner]: a root generated at each start (the tests sign with [signAsRoot] what
 * the owner's phone would sign), the keys the tests set, the root requests received, and a journal the tests
 * write, chained like the real one (or broken on purpose). [available] false makes every call fail as an
 * unreachable service does.
 */
class FakeSignerAdmin(rootConfigured: Boolean = true, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) : SignerAdmin {
    val root: IdentityKeys = IdentityKeys.generate()

    override val rootPublicKey: String? = root.publicKey.hex.takeIf { rootConfigured }

    var available: Boolean = true
    var keys: KeysResponse = KeysResponse(rootConfigured = rootConfigured, keys = emptyList())
    val certificates: MutableList<SignedEvent> = mutableListOf()
    var teamListEvent: SignedEvent? = null

    /** Every root request received, in order; [refuseWith] makes the next ones refused with this code. */
    val rootRequests: MutableList<List<UnsignedEvent>> = mutableListOf()
    var refuseWith: String? = null

    /** The journal of the service, as `GET /v1/journal` serves it. */
    val lines: MutableList<SignerJournalLine> = mutableListOf()

    /** The head served; by default the last line (a test may make it lie). */
    var head: SignerJournalHead? = null

    private var requestNumber = 0

    fun signAsRoot(event: UnsignedEvent): SignedEvent = LocalEventSigner(root).signNow(event)

    fun key(
        use: SigningUse,
        state: String,
        current: Boolean = false,
        notBefore: Long? = null,
        notAfter: Long? = null,
        lastSignedAt: Long? = null,
        revokedFrom: Long? = null,
    ): WorkingKeyInfo = WorkingKeyInfo(
        use.id, IdentityKeys.generate().publicKey.hex, state, createdAt = 1, current = current,
        notBefore = notBefore, notAfter = notAfter, revokedFrom = revokedFrom, lastSignedAt = lastSignedAt,
    )

    /** Appends a line chained to the previous one, as the service writes it. */
    fun append(action: String, use: String? = null, event: String? = null, approvers: List<String> = emptyList()): SignerJournalLine {
        val seq = (lines.lastOrNull()?.seq ?: 0) + 1
        val previous = lines.lastOrNull()?.hash ?: "0".repeat(64)
        val hash = sha256("$seq|$action|$previous|${System.nanoTime()}")
        return SignerJournalLine(seq, now(), action, use = use, event = event, approvers = approvers, previousHash = previous, hash = hash).also { lines += it }
    }

    override suspend fun keys(): KeysResponse? = keys.takeIf { available }

    override suspend fun rootRequest(events: List<UnsignedEvent>): RootRequestResult {
        if (!available) return RootRequestResult.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE)
        refuseWith?.let { return RootRequestResult.Refused(it) }
        rootRequests += events
        requestNumber++
        val id = sha256("request-$requestNumber-${System.nanoTime()}").take(32)
        return RootRequestResult.Created(id, RootRequests.sha256(events), now() + RootRequests.LIFETIME_SECONDS)
    }

    override suspend fun certificates(): List<SignedEvent>? = certificates.toList().takeIf { available }

    override suspend fun teamList(): SignedEvent? = teamListEvent.takeIf { available }

    override suspend fun journal(after: Long, limit: Int): List<SignerJournalLine>? =
        if (!available) null else lines.filter { it.seq > after }.take(limit)

    override suspend fun journalHead(): SignerJournalHead? =
        if (!available) null else head ?: lines.lastOrNull()?.let { SignerJournalHead(it.seq, it.hash) } ?: SignerJournalHead(0, "0".repeat(64))

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()
}

/** The tests sign as the root outside any coroutine (the signer of the core suspends). */
private fun LocalEventSigner.signNow(event: UnsignedEvent): SignedEvent = runBlocking { sign(event) }
