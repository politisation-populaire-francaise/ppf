package org.politisationpopulairefrancaise.signer

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.contentType
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.platform.RootRequestView
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import java.util.Base64

@Serializable
private data class CredentialDescriptor(val type: String = "public-key", val id: String)

/** The WebAuthn request options, in the JSON shape of the specification (byte fields in base64url). */
@Serializable
private data class PublicKeyRequestOptions(
    val challenge: String,
    val rpId: String,
    val timeout: Long,
    val allowCredentials: List<CredentialDescriptor>,
    val userVerification: String = "required",
)

@Serializable
private data class RequestOptionsResponse(val publicKey: PublicKeyRequestOptions)

@Serializable
private data class ApprovalAnswer(
    val status: String,
    val code: String? = null,
    val message: String,
    val approvals: Int? = null,
    val required: Int? = null,
    val ready: Boolean? = null,
)

/** The security headers of every answer of the approval port (HTTPS and HSTS are the reverse proxy's). */
private val ApprovalHeaders = createApplicationPlugin("ApprovalHeaders") {
    onCall { call ->
        val headers = call.response.headers
        headers.append(
            "Content-Security-Policy",
            "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'self'; " +
                "frame-ancestors 'none'; base-uri 'none'",
        )
        headers.append("X-Content-Type-Options", "nosniff")
        headers.append("X-Frame-Options", "DENY")
        headers.append("Referrer-Policy", "no-referrer")
        headers.append("Cross-Origin-Opener-Policy", "same-origin")
        headers.append("Cross-Origin-Resource-Policy", "same-origin")
        headers.append("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), publickey-credentials-create=(), publickey-credentials-get=(self)")
        headers.append(HttpHeaders.Server, "PPF")
    }
}

/**
 * The approval site of the service (contract, section 5; version 1.2), on its own port and its own origin
 * (`PPF_SIGNER_APPROVAL_ORIGIN`), behind the reverse proxy: the only place where an approval is given.
 * The main server never serves this page nor its script, so a compromised main server can neither choose
 * the challenge nor show one content while another is approved.
 *
 * - `GET /approbation/{use}/{id}`: the page, rendered by the service from the draft it holds;
 * - `GET /approbation/static/approbation.js`, `GET /approbation/static/approbation.css`: its script and style, self-hosted;
 * - `POST /approbation/{use}/{id}/options`: the WebAuthn request options (challenge of the approval, relying
 *   party of the back-office passkeys, passkeys of the members who may approve, user verification required);
 * - `POST /approbation/{use}/{id}`: the assertion, checked by the service with the approval origin only;
 * - `POST /racine/certificats`, `POST /racine/liste-equipe`: root-signed certificates, revocations and team
 *   list, the same as `POST /v1/certificates` and `POST /v1/team-list` (self-authenticating events), so that
 *   the owner's administration app reaches the service without going through the main server;
 * - `GET /racine/demandes/{id}`: a root request prepared by the back-office (`{"id","created_at","expires_at",
 *   "sha256","events"}`), `404` when unknown or expired (D-141).
 *
 * Nothing of the internal API answers here. POST bodies are JSON (anything else is `415`), bounded; no
 * request is logged, no address is kept.
 */
fun Application.approvalModule(service: SignerService, config: SignerConfig) {
    install(ApprovalHeaders)
    val script = resource("approbation/approbation.js")
    val style = resource("approbation/approbation.css")
    routing {
        get(ApprovalPage.SCRIPT_PATH) {
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytes(script, ContentType.Text.JavaScript.withCharset(Charsets.UTF_8))
        }
        get(ApprovalPage.STYLE_PATH) {
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytes(style, ContentType.Text.CSS.withCharset(Charsets.UTF_8))
        }
        get("/approbation/{use}/{id}") {
            val use = call.parameters["use"].orEmpty()
            val id = call.parameters["id"].orEmpty()
            val view = withContext(Dispatchers.IO) { service.approvalView(use, id) }
            call.response.header(HttpHeaders.CacheControl, "no-store")
            if (view == null) {
                call.respondText(ApprovalPage.unknown(config), ContentType.Text.Html.withCharset(Charsets.UTF_8), HttpStatusCode.NotFound)
            } else {
                call.respondText(ApprovalPage.render(view, config), ContentType.Text.Html.withCharset(Charsets.UTF_8))
            }
        }
        post("/approbation/{use}/{id}/options") {
            if (!call.isJson()) return@post
            call.boundedBody(MAX_OPTIONS_BODY) ?: return@post
            val use = call.parameters["use"].orEmpty()
            val id = call.parameters["id"].orEmpty()
            when (val outcome = withContext(Dispatchers.IO) { service.approvalOptions(use, id) }) {
                is OptionsOutcome.Options -> {
                    val options = PublicKeyRequestOptions(
                        challenge = BASE64URL.encodeToString(outcome.challenge),
                        rpId = outcome.rpId,
                        timeout = WEBAUTHN_TIMEOUT_MILLIS,
                        allowCredentials = outcome.credentialIds.map { CredentialDescriptor(id = it) },
                    )
                    call.respondJson(HttpStatusCode.OK, encode(RequestOptionsResponse.serializer(), RequestOptionsResponse(options)))
                }
                is OptionsOutcome.Refused -> call.answerRefusal(outcome.refusal)
            }
        }
        post("/approbation/{use}/{id}") {
            if (!call.isJson()) return@post
            val body = call.boundedBody(MAX_ASSERTION_BODY) ?: return@post
            val assertion = try {
                SigningProtocol.json.decodeFromString(PasskeyAssertion.serializer(), body)
            } catch (e: Exception) {
                return@post call.answerRefusal(Refusal(SigningRefusals.MALFORMED_REQUEST))
            }
            val use = call.parameters["use"].orEmpty()
            val id = call.parameters["id"].orEmpty()
            when (val outcome = withContext(Dispatchers.IO) { service.approve(use, id, assertion) }) {
                is ApprovalOutcome.Recorded -> {
                    val answer = ApprovalAnswer(
                        status = "approved",
                        message = if (outcome.ready) ApprovalTexts.READY else ApprovalTexts.MSG_DONE,
                        approvals = outcome.approvals,
                        required = outcome.required,
                        ready = outcome.ready,
                    )
                    call.respondJson(HttpStatusCode.OK, encode(ApprovalAnswer.serializer(), answer))
                }
                is ApprovalOutcome.Refused -> call.answerRefusal(outcome.refusal)
            }
        }
        post("/racine/certificats") {
            if (!call.isJson()) return@post
            call.loadCertificates(service)
        }
        post("/racine/liste-equipe") {
            if (!call.isJson()) return@post
            call.loadTeamList(service)
        }
        // The root request the owner's phone fetches after scanning the QR code of the back-office (D-141):
        // it checks the hash of the QR code against these events before showing them and signing.
        get("/racine/demandes/{id}") {
            val view = withContext(Dispatchers.IO) { service.rootRequest(call.parameters["id"].orEmpty()) }
                ?: return@get call.refusal(HttpStatusCode.NotFound, SigningRefusals.UNKNOWN_REQUEST)
            call.respondJson(HttpStatusCode.OK, encode(RootRequestView.serializer(), view))
        }
    }
}

private const val MAX_OPTIONS_BODY = 1_024
private const val MAX_ASSERTION_BODY = 64 * 1024
private const val WEBAUTHN_TIMEOUT_MILLIS = 120_000L
private val BASE64URL: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

private fun resource(path: String): ByteArray =
    SignerService::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
        ?: error("The resource $path is missing from the jar")

/** Whether the body is declared JSON; answers 415 otherwise (no simple cross-site form ever reaches a route). */
private suspend fun ApplicationCall.isJson(): Boolean {
    if (request.contentType().withoutParameters() == ContentType.Application.Json) return true
    answerRefusal(Refusal(SigningRefusals.MALFORMED_REQUEST), HttpStatusCode.UnsupportedMediaType)
    return false
}

private suspend fun ApplicationCall.answerRefusal(refusal: Refusal, status: HttpStatusCode = statusOf(refusal.code)) {
    val answer = ApprovalAnswer(status = "refused", code = refusal.code, message = ApprovalTexts.refusal(refusal.code))
    respondJson(status, encode(ApprovalAnswer.serializer(), answer))
}
