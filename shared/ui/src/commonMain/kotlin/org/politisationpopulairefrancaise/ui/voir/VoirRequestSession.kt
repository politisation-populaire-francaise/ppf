package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.AnonymousDelivery
import org.politisationpopulairefrancaise.core.voir.ErrorReport
import org.politisationpopulairefrancaise.core.voir.ReplyCapacity
import org.politisationpopulairefrancaise.core.voir.ReplyRequest
import org.politisationpopulairefrancaise.core.voir.VoirRequest
import org.politisationpopulairefrancaise.core.voir.VoirRequestOutbox
import org.politisationpopulairefrancaise.core.voir.VoirRequests
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.messages.MessagingSession
import org.politisationpopulairefrancaise.ui.resources.*

/** What became of a request once the user pressed "Envoyer". */
enum class RequestOutcome {
    /** The server took it. */
    SENT,

    /** Kept on the device; it leaves as soon as a server answers. */
    PENDING,

    /** Nothing was sent. */
    FAILED,
}

/** The draft of a right of reply, kept while the user goes to create an account. */
data class ReplyDraft(
    val address: ContentAddress? = null,
    val respondent: String = "",
    val capacity: ReplyCapacity = ReplyCapacity.PERSON_NAMED,
    val text: String = "",
    val statement: Boolean = false,
)

/** The draft of an error report. Anonymous by default (protection by default, principle 10). */
data class ReportDraft(
    val address: ContentAddress? = null,
    val wrong: String = "",
    val correction: String = "",
    val source: String = "",
    val withAccount: Boolean = false,
)

/**
 * The requests of "Voir" to the editorial team, for the whole run of the application (remembered
 * by the shell): the drafts survive a visit to "Mon espace", a request leaves either through the
 * account's private messages ([MessagingSession.sendTo], so that the answer arrives in the same
 * conversation and a request written offline waits on the device like any message) or anonymously
 * through [VoirRequestOutbox] (a single-use key, kept in memory until the server takes it).
 */
class VoirRequestSession(
    private val messaging: MessagingSession?,
    remote: RemoteEvents?,
    private val scope: CoroutineScope,
    teamInbox: String = VoirRequests.TEAM_INBOX,
) {
    val outbox = VoirRequestOutbox(remote, teamInbox)

    /** Public key of the team's inbox, hexadecimal: the conversation the answers arrive in. */
    val teamKey: String get() = outbox.team.hex

    var replyDraft: ReplyDraft by mutableStateOf(ReplyDraft())
    var reportDraft: ReportDraft by mutableStateOf(ReportDraft())

    var sending: Boolean by mutableStateOf(false)
        private set

    /** Whether there is an account able to send private messages on this device. */
    val hasAccount: Boolean get() = messaging?.myKey != null

    /** Whether this device can keep private messages (a local database). */
    val canWriteMessages: Boolean get() = messaging?.canWrite == true

    /** Opens the draft of a reply to [address], keeping the current one when it is about the same content. */
    fun startReply(address: ContentAddress, respondent: String?) {
        if (replyDraft.address != address) replyDraft = ReplyDraft(address = address, respondent = respondent.orEmpty())
    }

    fun startReport(address: ContentAddress) {
        if (reportDraft.address != address) reportDraft = ReportDraft(address = address)
    }

    /** Sends a right of reply from the account; [onDone] receives the outcome. */
    fun sendReply(request: ReplyRequest, subject: String, onDone: (RequestOutcome) -> Unit) {
        launchSend(onDone) {
            val outcome = sendWithAccount(request, subject)
            if (outcome != RequestOutcome.FAILED) replyDraft = ReplyDraft()
            outcome
        }
    }

    /** Sends an error report, from the account or anonymously; [onDone] receives the outcome. */
    fun sendReport(request: ErrorReport, subject: String, onDone: (RequestOutcome) -> Unit) {
        launchSend(onDone) {
            val outcome = if (request.anonymous) {
                outbox.flush()
                when (outbox.sendAnonymously(request, subject).delivery) {
                    AnonymousDelivery.SENT -> RequestOutcome.SENT
                    AnonymousDelivery.PENDING -> RequestOutcome.PENDING
                    AnonymousDelivery.REJECTED -> RequestOutcome.FAILED
                }
            } else {
                sendWithAccount(request, subject)
            }
            if (outcome != RequestOutcome.FAILED) reportDraft = ReportDraft()
            outcome
        }
    }

    /** Opens the conversation with the team in the private messages (the caller then shows "Messages"). */
    fun openTeamConversation() {
        messaging?.openWith(listOf(teamKey))
    }

    /** The quick erase (S-44): the drafts and the anonymous reports still waiting, with their single-use keys. */
    suspend fun forget() {
        replyDraft = ReplyDraft()
        reportDraft = ReportDraft()
        outbox.forget()
    }

    /** Sends the anonymous reports still waiting, when "Voir" opens. */
    fun retryPending() {
        scope.launch { runCatching { outbox.flush() } }
    }

    private suspend fun sendWithAccount(request: VoirRequest, subject: String): RequestOutcome {
        val session = messaging ?: return RequestOutcome.FAILED
        val report = session.sendTo(teamKey, VoirRequests.encode(request), subject) ?: return RequestOutcome.FAILED
        return when {
            report.rejected > 0 -> RequestOutcome.FAILED
            report.queued > 0 -> RequestOutcome.PENDING
            else -> RequestOutcome.SENT
        }
    }

    private fun launchSend(onDone: (RequestOutcome) -> Unit, block: suspend () -> RequestOutcome) {
        if (sending) return
        sending = true
        scope.launch {
            val outcome = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RequestOutcome.FAILED
            } finally {
                sending = false
            }
            onDone(outcome)
        }
    }
}

@Composable
fun rememberVoirRequestSession(dependencies: AppDependencies, messaging: MessagingSession?): VoirRequestSession {
    val scope = rememberCoroutineScope()
    return remember(dependencies, messaging) { VoirRequestSession(messaging, dependencies.remote, scope) }
}

/**
 * The text a private message shows: a request from "Voir" in plain words (the JSON it carries is
 * for the team's tools), any other message as it is.
 */
@Composable
fun voirRequestText(text: String): String {
    val request = VoirRequests.decodeOrNull(text) ?: return text
    val title = request.contentTitle?.takeIf { it.isNotBlank() }?.withoutExample() ?: stringResource(Res.string.voir_req_untitled)
    return when (request) {
        is ReplyRequest -> stringResource(
            Res.string.voir_req_message_reply,
            title,
            request.respondent,
            stringResource(if (request.capacity == ReplyCapacity.PERSON_NAMED) Res.string.voir_req_capacity_person else Res.string.voir_req_capacity_representative),
            request.reply,
        )
        is ErrorReport -> buildString {
            append(stringResource(Res.string.voir_req_message_report, title, request.wrong))
            request.correction?.let { append("\n").append(stringResource(Res.string.voir_req_message_correction, it)) }
            request.source?.let { append("\n").append(stringResource(Res.string.voir_req_message_source, it)) }
        }
    }
}
