package org.politisationpopulairefrancaise.server.archive

import io.ktor.http.ContentType
import org.bouncycastle.asn1.cmp.PKIStatus
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TSPException
import org.bouncycastle.tsp.TSPValidationException
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.util.Selector
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.util.Random
import kotlin.time.Duration.Companion.seconds

/** A token received and checked: its DER bytes, the time it attests, the SHA-256 of the signing certificate. */
class Rfc3161Token(val token: ByteArray, val time: Instant, val tsaCertSha256: ByteArray)

/** What a request to the timestamping authority gave. The codes are short and never quote a value received. */
sealed interface Rfc3161Result {
    class Accepted(val token: Rfc3161Token) : Rfc3161Result

    /** An answer came but was refused (status, nonce, imprint, signature, certificate). */
    data class Refused(val code: String) : Rfc3161Result

    /** No usable answer (network, HTTP status, size). */
    data class Unavailable(val code: String) : Rfc3161Result
}

/**
 * RFC 3161 timestamps of batch roots (D-111, "preuve immédiate en complément"), server side only,
 * with BouncyCastle. The request holds only the root, its algorithm (SHA-256), a random nonce and
 * `certReq`; it does not identify the requester (RFC 3161, 2.4.1). At most one request per second,
 * as FreeTSA asks, retries included.
 */
class Rfc3161Client(
    private val http: ArchiveHttp,
    private val url: String,
    private val random: Random,
    private val spacing: MinimumSpacing,
) {
    suspend fun timestamp(imprint: ByteArray): Rfc3161Result {
        require(imprint.size == 32) { "a SHA-256 imprint is 32 bytes" }
        val request = Rfc3161Verifier.request(imprint, BigInteger(NONCE_BITS, random))
        val answer = http.post(url, request.encoded, QUERY, REPLY, MAX_RESPONSE_BYTES, beforeAttempt = spacing::await)
        return when (answer) {
            is HttpAnswer.Ok -> Rfc3161Verifier.verify(request, answer.body)
            is HttpAnswer.Status -> Rfc3161Result.Unavailable("http_${answer.code}")
            is HttpAnswer.Failed -> Rfc3161Result.Unavailable(answer.reason)
            HttpAnswer.TooLarge -> Rfc3161Result.Unavailable("too_large")
        }
    }

    companion object {
        val QUERY: ContentType = ContentType("application", "timestamp-query")
        const val REPLY: String = "application/timestamp-reply"

        /** A token with its certificate weighs a few kilobytes. */
        const val MAX_RESPONSE_BYTES: Int = 32 * 1024

        /** FreeTSA: "one request per second" at most. */
        val SPACING = 1.seconds

        private const val NONCE_BITS = 64
    }
}

/**
 * Checks of a timestamp response against its request: granted status, a token present, same nonce,
 * same imprint and algorithm, a signing-certificate attribute (all by [TimeStampResponse.validate]),
 * then the signature of the token with the certificate it carries, the hash of that certificate
 * against the signing-certificate attribute, its extended key usage (time stamping only, critical)
 * and its validity at the attested time (by `TimeStampToken.validate`).
 *
 * Follow-up, needing a file download approved by the owner: pin the FreeTSA root certificate
 * (cacert.pem) and check the chain up to it. Until then the certificate is only the one included in
 * the response, and its SHA-256 fingerprint is recorded with the token so that anyone can compare it
 * with the certificate FreeTSA publishes.
 */
object Rfc3161Verifier {

    fun request(imprint: ByteArray, nonce: BigInteger): TimeStampRequest =
        TimeStampRequestGenerator().apply { setCertReq(true) }.generate(TSPAlgorithms.SHA256, imprint, nonce)

    fun verify(request: TimeStampRequest, responseBytes: ByteArray): Rfc3161Result = try {
        val response = TimeStampResponse(responseBytes)
        val status = response.status
        val token = response.timeStampToken
        when {
            status != PKIStatus.GRANTED && status != PKIStatus.GRANTED_WITH_MODS -> Rfc3161Result.Refused("status_$status")
            token == null -> Rfc3161Result.Refused("no_token")
            else -> {
                response.validate(request)
                @Suppress("UNCHECKED_CAST")
                val matches = token.certificates.getMatches(token.sid as Selector<X509CertificateHolder>)
                val certificate = matches.singleOrNull()
                if (certificate == null) {
                    Rfc3161Result.Refused("no_certificate")
                } else {
                    token.validate(JcaSimpleSignerInfoVerifierBuilder().build(certificate))
                    Rfc3161Result.Accepted(
                        Rfc3161Token(token.encoded, token.timeStampInfo.genTime.toInstant(), sha256(certificate.encoded)),
                    )
                }
            }
        }
    } catch (e: TSPValidationException) {
        Rfc3161Result.Refused("invalid_token")
    } catch (e: TSPException) {
        Rfc3161Result.Refused("unreadable_token")
    } catch (e: Exception) {
        // IOException (not DER), IllegalArgumentException, operator or certificate errors: the class only.
        Rfc3161Result.Refused("refused_${e::class.simpleName}")
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
