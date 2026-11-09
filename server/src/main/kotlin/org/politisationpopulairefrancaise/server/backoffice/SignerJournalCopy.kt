package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.server.signing.SignerAdmin
import org.politisationpopulairefrancaise.server.signing.SignerJournalLine
import org.slf4j.Logger
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The copy of the journal of the signing service into the journal of the team (contract
 * docs/integration/service-signature.md, sections 4 and 6; D-141), so that a journal of the service
 * rewritten or shortened behind its back is seen: each line of the service becomes a line `signer.<action>`
 * of the server with its number, its hashes, its use, its event, its key, its code and its accounts (never a
 * content: the service journals none).
 *
 * Where the copy stands is read from the journal of the team itself (the newest line kept under [targetType]),
 * so a restart of the server continues from the last line copied, without any table of its own:
 * - a copied line: continue after its number, from its hash;
 * - `signer.journal_resumed`: continue from the point it names;
 * - `signer.journal_break`: stopped, until an administrator marks the alert treated; then the copy resumes
 *   (a line `signer.journal_resumed`) from the line where it stopped as the service now shows it, or from the
 *   start of the journal of the service when it is now shorter than the copy (a new journal).
 *
 * Continuity is checked line by line (each line follows the last copied one, number and previous hash) and,
 * once everything is copied, against the head of the service (`GET /v1/journal/head`): the same number with
 * another hash, or a lower number, is a break. A break writes one line `signer.journal_break` and an alert of
 * the highest level (5) for [Permissions.PLATFORM_KEYS].
 */
class SignerJournalCopy(
    private val backOffice: BackOffice,
    private val signer: SignerAdmin,
    /** The target type of the lines of the copy in the journal of the team (another one per test). */
    private val targetType: String = TARGET,
) {
    private val mutex = Mutex()

    /** What one pass did. */
    sealed interface Pass {
        /** [copied] lines copied; the copy now ends at [seq]. */
        data class Copied(val copied: Int, val seq: Long) : Pass

        /** A break was found now (one line `signer.journal_break` and an alert). */
        data class Broken(val problem: String) : Pass

        /** Stopped since a break, until an administrator marks the alert [alert] treated. */
        data class Halted(val alert: UUID?) : Pass

        /** The service did not answer: nothing changed, the next pass tries again. */
        data object Unavailable : Pass
    }

    /** Where the copy stands: the line after which it continues, and that line's hash. */
    private data class Anchor(val seq: Long, val hash: String)

    /** One pass: copies every new line (1000 at a time), then compares the head of the service. */
    suspend fun runOnce(): Pass = mutex.withLock {
        var anchor = when (val state = io { currentState() }) {
            is State.At -> state.anchor
            is State.Stopped -> {
                if (state.alert != null && io { backOffice.isAlertOpen(state.alert) }) return@withLock Pass.Halted(state.alert)
                resume(state) ?: return@withLock Pass.Unavailable
            }
        }
        var copied = 0
        while (true) {
            val lines = signer.journal(anchor.seq, PAGE) ?: return@withLock Pass.Unavailable
            if (lines.isEmpty()) break
            val kept = mutableListOf<SignerJournalLine>()
            var broken: Map<String, String>? = null
            for (line in lines) {
                if (line.seq != anchor.seq + 1 || line.previousHash != anchor.hash) {
                    broken = breakDetails(if (line.seq != anchor.seq + 1) "missing_line" else "previous_hash", anchor, line.seq, line.previousHash)
                    break
                }
                kept += line
                anchor = Anchor(line.seq, line.hash)
            }
            io { backOffice.copySignerJournal(kept, targetType) }
            copied += kept.size
            if (broken != null) return@withLock breakNow(broken)
            if (lines.size < PAGE) break
        }
        val head = signer.journalHead() ?: return@withLock Pass.Unavailable
        if (head.seq < anchor.seq) return@withLock breakNow(breakDetails("head_below_copy", anchor, head.seq, head.hash))
        if (head.seq == anchor.seq && head.hash != anchor.hash) return@withLock breakNow(breakDetails("head_hash", anchor, head.seq, head.hash))
        Pass.Copied(copied, anchor.seq)
    }

    private suspend fun breakNow(details: Map<String, String>): Pass {
        io { backOffice.signerJournalBroken(details, targetType) }
        return Pass.Broken(details.getValue("problem"))
    }

    /**
     * The point to resume from after a break was treated: the line where the copy stopped, as the service
     * now shows it; or the start when the journal of the service is now shorter than that (a new journal).
     * Null when the service does not answer.
     */
    private suspend fun resume(stopped: State.Stopped): Anchor? {
        val head = signer.journalHead() ?: return null
        val anchor = if (stopped.after.seq == 0L || head.seq < stopped.after.seq) {
            Anchor(0, GENESIS)
        } else {
            val line = signer.journal(stopped.after.seq - 1, 1)?.firstOrNull() ?: return null
            if (line.seq == stopped.after.seq) Anchor(line.seq, line.hash) else Anchor(0, GENESIS)
        }
        io {
            backOffice.signerJournalResumed(
                buildMap {
                    put("after_seq", anchor.seq.toString())
                    put("previous_hash", anchor.hash)
                    stopped.alert?.let { put("alert", it.toString()) }
                },
                targetType,
            )
        }
        return anchor
    }

    private sealed interface State {
        data class At(val anchor: Anchor) : State
        data class Stopped(val after: Anchor, val alert: UUID?) : State
    }

    /** Where the copy stands, from the newest line of the copy in the journal of the team. */
    private fun currentState(): State {
        val last = backOffice.lastSignerJournalMark(targetType) ?: return State.At(Anchor(0, GENESIS))
        val details = last.details
        return when (last.action) {
            ACTION_BREAK -> State.Stopped(
                Anchor(details["after_seq"]?.toLongOrNull() ?: 0, details["after_hash"] ?: GENESIS),
                details["alert"]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            )
            ACTION_RESUMED -> State.At(Anchor(details["after_seq"]?.toLongOrNull() ?: 0, details["previous_hash"] ?: GENESIS))
            else -> State.At(Anchor(details["seq"]?.toLongOrNull() ?: 0, details["hash"] ?: GENESIS))
        }
    }

    private fun breakDetails(problem: String, anchor: Anchor, foundSeq: Long, foundHash: String): Map<String, String> = mapOf(
        "problem" to problem,
        "after_seq" to anchor.seq.toString(),
        "after_hash" to anchor.hash,
        "found_seq" to foundSeq.toString(),
        "found_hash" to foundHash.take(MAX_VALUE),
    )

    /** At start, then every [interval]; only counts and codes are logged. */
    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                when (val pass = runOnce()) {
                    is Pass.Copied -> if (pass.copied > 0) log.info("Signing service journal: ${pass.copied} line(s) copied.")
                    is Pass.Broken -> log.warn("Signing service journal: break of the chain (${pass.problem}); copy stopped until the alert is treated.")
                    is Pass.Halted -> Unit
                    Pass.Unavailable -> log.warn("Signing service journal: the service did not answer; next attempt in $interval.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Signing service journal: copy failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }

    companion object {
        /** The target type of the copied lines and of the marks of the copy. */
        const val TARGET: String = "signer_journal"

        const val ACTION_BREAK: String = "signer.journal_break"
        const val ACTION_RESUMED: String = "signer.journal_resumed"

        /** The source of the alert of a break. */
        const val ALERT_SOURCE: String = "backoffice.signer_journal"

        /** Every 10 minutes (and at start). */
        val INTERVAL: Duration = 10.minutes

        const val PAGE: Int = 1_000
        val GENESIS: String = "0".repeat(64)

        private const val MAX_VALUE = 2_000
        private val ACTION = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$")

        /** The line of the journal of the team that copies [line] (actor « système »). */
        fun entryOf(line: SignerJournalLine, targetType: String): AuditEntry {
            val action = "signer." + line.action
            val safe = if (ACTION.matches(action) && action.length <= 120 && action != ACTION_BREAK && action != ACTION_RESUMED) action else "signer.unknown_action"
            val details = buildMap {
                put("seq", line.seq.toString())
                put("hash", line.hash.take(MAX_VALUE))
                put("previous_hash", line.previousHash.take(MAX_VALUE))
                put("at", line.at.toString())
                if (safe != action) put("action", line.action.take(200))
                line.use?.let { put("use", it.take(200)) }
                line.event?.let { put("event", it.take(200)) }
                line.eventCreatedAt?.let { put("event_created_at", it.toString()) }
                line.key?.let { put("key", it.take(200)) }
                line.code?.let { put("code", it.take(200)) }
                if (line.approvers.isNotEmpty()) put("accounts", line.approvers.joinToString(",").take(MAX_VALUE))
                line.doubleValidation?.let { put("double_validation", it.toString()) }
                line.authorAccount?.let { put("author_account", it.take(200)) }
                line.authorPubkey?.let { put("author_pubkey", it.take(200)) }
                line.credential?.let { put("credential", it.take(MAX_VALUE)) }
                line.d?.let { put("d", it.take(200)) }
            }
            return AuditEntry(AuditActor.System, safe, targetType, line.seq.toString(), null, details)
        }

        private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    }
}
