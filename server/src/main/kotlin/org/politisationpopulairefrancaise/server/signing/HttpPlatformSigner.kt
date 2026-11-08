package org.politisationpopulairefrancaise.server.signing

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.DraftRequest
import org.politisationpopulairefrancaise.core.platform.DraftResponse
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.EventDraft
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.PlatformRoot
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TrustVerdict
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.backoffice.Tokens
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where the signing service is (D-129), from environment variables; null when `PPF_SIGNER_URL` is not set
 * (no platform signature on this server yet).
 *
 * - `PPF_SIGNER_URL`: `http://signer:8090` inside the Docker network (http or https, no user, path or query);
 * - `PPF_SIGNER_CLIENT_TOKEN_FILE`: the file holding the token shared with the service (the same file as its
 *   own `PPF_SIGNER_CLIENT_TOKEN_FILE`); a variable holding the token itself is refused;
 * - `PPF_SIGNER_ROOT_PUBKEY`: the root public key of this environment (hex), the same value as the
 *   service's; empty, the constant of the applications ([PlatformRoot.PUBLIC_KEY]) applies, and without
 *   any root no signed event is accepted from the service.
 */
class PlatformSignerConfig(val url: String, val token: String, val rootPublicKey: String? = null, val timeout: Duration = 30.seconds) {
    override fun toString(): String = "PlatformSignerConfig(url=$url, root=${rootPublicKey ?: "none"})"

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): PlatformSignerConfig? {
            check(env["PPF_SIGNER_CLIENT_TOKEN"] == null) { "PPF_SIGNER_CLIENT_TOKEN is refused: give the path of a file in PPF_SIGNER_CLIENT_TOKEN_FILE" }
            val url = env["PPF_SIGNER_URL"]?.trim()?.ifEmpty { null } ?: return null
            check(isValidUrl(url)) { "PPF_SIGNER_URL must be http(s)://<host>:<port>, without user, path or query" }
            val file = env["PPF_SIGNER_CLIENT_TOKEN_FILE"]?.ifBlank { null } ?: error("PPF_SIGNER_CLIENT_TOKEN_FILE is required with PPF_SIGNER_URL")
            val token = try {
                String(Files.readAllBytes(Paths.get(file)), Charsets.UTF_8).trim()
            } catch (e: java.io.IOException) {
                error("PPF_SIGNER_CLIENT_TOKEN_FILE names a file that cannot be read")
            }
            check(token.length in 32..512 && token.all { it in '!'..'~' }) { "PPF_SIGNER_CLIENT_TOKEN_FILE must hold 32 to 512 visible ASCII characters" }
            val root = env["PPF_SIGNER_ROOT_PUBKEY"]?.trim()?.ifEmpty { null } ?: PlatformRoot.PUBLIC_KEY.ifEmpty { null }
            check(root == null || (root.length == 64 && root.all { it in '0'..'9' || it in 'a'..'f' })) { "PPF_SIGNER_ROOT_PUBKEY must be 64 lowercase hexadecimal characters" }
            return PlatformSignerConfig(url.trimEnd('/'), token, root)
        }

        fun isValidUrl(url: String): Boolean {
            val uri = try {
                URI(url)
            } catch (e: java.net.URISyntaxException) {
                return false
            }
            return uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty() && uri.userInfo == null &&
                uri.query == null && uri.fragment == null && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        }
    }
}

/**
 * [PlatformSigner] over the internal HTTP API of the signing service. A request is answered at once by the
 * service; its outcome is kept here for [status] for an hour (1000 at most). Before returning a signed
 * event the server checks that it is the one it asked for, correctly signed, and that its key is certified
 * by the root for the use ([PlatformTrust] over the certificates of the service, kept here and refreshed
 * when a key is not known yet): a signing service that went wrong cannot make this server publish a
 * content the applications would refuse.
 */
class HttpPlatformSigner(
    private val config: PlatformSignerConfig,
    private val client: HttpClient = defaultClient(config),
    private val clock: Clock = Clock.systemUTC(),
) : PlatformSigner, AutoCloseable {
    private val outcomes = object : LinkedHashMap<String, Pair<Instant, SigningStatus>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Instant, SigningStatus>>?): Boolean = size > MAX_OUTCOMES
    }

    /** The trust built from the certificates of the service (monotonic: what was seen is kept), and when it was refreshed. */
    @Volatile
    private var trust: Pair<Instant, PlatformTrust>? = null

    override suspend fun currentKey(use: SigningUse): String? =
        keys()?.keys?.firstOrNull { it.use == use.id && it.current }?.pubkey

    /** The keys of the service and their states (for the back-office), null when it does not answer. */
    suspend fun keys(): KeysResponse? = call {
        val response = client.get("${config.url}/v1/keys") { authorize() }
        if (response.status != HttpStatusCode.OK) null else SigningProtocol.json.decodeFromString(KeysResponse.serializer(), response.bodyAsText())
    }

    override suspend fun submitDraft(unsigned: UnsignedEvent, use: SigningUse, doubleValidation: Boolean, author: DraftAuthor?): DraftSubmission {
        val body = SigningProtocol.json.encodeToString(DraftRequest.serializer(), DraftRequest(use.id, EventDraft.of(unsigned), doubleValidation, author))
        return call {
            val response = client.post("${config.url}/v1/drafts") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status == HttpStatusCode.Unauthorized) return@call DraftSubmission.Refused(SIGNER_UNAUTHORIZED)
            val answer = runCatching { SigningProtocol.json.decodeFromString(DraftResponse.serializer(), response.bodyAsText()) }.getOrNull()
                ?: return@call DraftSubmission.Refused(SIGNER_UNAVAILABLE)
            if (response.status in setOf(HttpStatusCode.Created, HttpStatusCode.OK) && answer.status == DraftResponse.SUBMITTED) {
                val id = unsigned.idHex()
                val url = answer.approvalUrl
                if (answer.draft != id || url == null || !isApprovalUrl(url, use, id)) return@call DraftSubmission.Refused(BAD_ANSWER_FROM_SIGNER)
                DraftSubmission.Submitted(id, url)
            } else {
                DraftSubmission.Refused(answer.code ?: SIGNER_UNAVAILABLE, answer.detail)
            }
        } ?: DraftSubmission.Refused(SIGNER_UNAVAILABLE)
    }

    override suspend fun draftStatus(use: SigningUse, eventId: String): DraftStatusResponse? = call {
        if (eventId.length != 64 || !eventId.all { it in '0'..'9' || it in 'a'..'f' }) return@call null
        val response = client.get("${config.url}/v1/drafts/${use.id}/$eventId") { authorize() }
        if (response.status != HttpStatusCode.OK) null else SigningProtocol.json.decodeFromString(DraftStatusResponse.serializer(), response.bodyAsText())
    }

    override suspend fun request(unsigned: UnsignedEvent, use: SigningUse): SigningTicket {
        val body = SigningProtocol.json.encodeToString(SignRequest.serializer(), SignRequest(use.id, EventDraft.of(unsigned)))
        val status = call {
            val response = client.post("${config.url}/v1/sign") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            interpret(response, unsigned)
        } ?: SigningStatus.Refused(SIGNER_UNAVAILABLE)
        val checked = if (status is SigningStatus.Signed) checkTrust(status, use) else status
        val ticket = SigningTicket(Tokens.generate(18))
        synchronized(outcomes) {
            purge()
            outcomes[ticket.id] = clock.instant() to checked
        }
        return ticket
    }

    override suspend fun status(ticket: SigningTicket): SigningStatus = synchronized(outcomes) {
        purge()
        outcomes[ticket.id]?.second ?: SigningStatus.Refused(UNKNOWN_TICKET)
    }

    /** Hands root-signed certificates and revocations to the service; the status of each, null when it does not answer. */
    suspend fun loadCertificates(events: List<SignedEvent>): String? = call {
        val response = client.post("${config.url}/v1/certificates") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(SigningProtocol.json.encodeToString(ListSerializer(SignedEvent.serializer()), events))
        }
        response.bodyAsText().takeIf { response.status == HttpStatusCode.OK }
    }

    /** Hands the root-signed team list to the service; its status, null when it does not answer. */
    suspend fun loadTeamList(event: SignedEvent): String? = call {
        val response = client.post("${config.url}/v1/team-list") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(event.toJson())
        }
        response.bodyAsText()
    }

    /** The certificates and revocations the service holds (public), to hand them to the applications. */
    suspend fun certificates(): List<SignedEvent>? = call {
        val response = client.get("${config.url}/v1/certificates") { authorize() }
        if (response.status != HttpStatusCode.OK) null else SigningProtocol.json.decodeFromString(ListSerializer(SignedEvent.serializer()), response.bodyAsText())
    }

    override fun close() = client.close()

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer ${config.token}")
    }

    private suspend fun interpret(response: HttpResponse, unsigned: UnsignedEvent): SigningStatus {
        val answer = runCatching { SigningProtocol.json.decodeFromString(SignResponse.serializer(), response.bodyAsText()) }.getOrNull()
        if (response.status == HttpStatusCode.Unauthorized) return SigningStatus.Refused(SIGNER_UNAUTHORIZED)
        if (answer == null) return SigningStatus.Refused(SIGNER_UNAVAILABLE)
        if (response.status == HttpStatusCode.OK && answer.status == SignResponse.SIGNED) {
            val event = answer.event ?: return SigningStatus.Refused(BAD_SIGNATURE_FROM_SIGNER)
            val sameEvent = event.id == unsigned.idHex() && event.unsigned() == unsigned
            return if (sameEvent && event.verify()) SigningStatus.Signed(event) else SigningStatus.Refused(BAD_SIGNATURE_FROM_SIGNER)
        }
        return SigningStatus.Refused(answer.code ?: SIGNER_UNAVAILABLE, answer.detail)
    }

    /** [signed] if its key is certified by the root for [use] at its date, refreshing the certificates once when it is not. */
    private suspend fun checkTrust(signed: SigningStatus.Signed, use: SigningUse): SigningStatus {
        val known = trust
        val fresh = known != null && !known.first.isBefore(clock.instant().minusSeconds(TRUST_KEPT_SECONDS))
        if (fresh && known!!.second.check(signed.event, use) == TrustVerdict.TRUSTED) return signed
        val refreshed = refreshTrust() ?: return SigningStatus.Refused(UNTRUSTED_KEY_FROM_SIGNER, SIGNER_UNAVAILABLE)
        val verdict = refreshed.check(signed.event, use)
        return if (verdict == TrustVerdict.TRUSTED) signed else SigningStatus.Refused(UNTRUSTED_KEY_FROM_SIGNER, verdict.name.lowercase())
    }

    /** Fetches the certificates of the service into the trust (nothing seen before is forgotten); null when it does not answer. */
    private suspend fun refreshTrust(): PlatformTrust? {
        val events = certificates() ?: return null
        val next = (trust?.second ?: PlatformTrust.of(config.rootPublicKey)).plusAll(events)
        trust = clock.instant() to next
        return next
    }

    /** Runs [block] within the timeout; null when the service cannot be reached or answers nonsense. */
    private suspend fun <T> call(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(config.timeout) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun purge() {
        val limit = clock.instant().minusSeconds(KEPT_SECONDS)
        outcomes.entries.removeIf { it.value.first.isBefore(limit) }
    }

    companion object {
        const val SIGNER_UNAVAILABLE: String = "signer_unavailable"
        const val SIGNER_UNAUTHORIZED: String = "signer_unauthorized"
        const val UNKNOWN_TICKET: String = "unknown_ticket"
        const val BAD_SIGNATURE_FROM_SIGNER: String = "bad_signature_from_signer"

        /** The service returned an event whose key the root does not certify for the use (or no root is configured here). */
        const val UNTRUSTED_KEY_FROM_SIGNER: String = "untrusted_key_from_signer"

        /** The service answered a draft with another id or an approval address of another form. */
        const val BAD_ANSWER_FROM_SIGNER: String = "bad_answer_from_signer"

        private const val MAX_OUTCOMES = 1_000
        private const val KEPT_SECONDS = 3_600L
        private const val TRUST_KEPT_SECONDS = 600L

        /** `https://<host>[:port]/approbation/<use>/<id>` (or `http://localhost` in development), nothing else. */
        internal fun isApprovalUrl(url: String, use: SigningUse, eventId: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host ?: return false
            val schemeOk = uri.scheme == "https" || (uri.scheme == "http" && host == "localhost")
            return schemeOk && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == "/approbation/${use.id}/$eventId"
        }

        private fun defaultClient(config: PlatformSignerConfig): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = false
            engine { requestTimeout = config.timeout.inWholeMilliseconds + 1_000 }
        }
    }
}
