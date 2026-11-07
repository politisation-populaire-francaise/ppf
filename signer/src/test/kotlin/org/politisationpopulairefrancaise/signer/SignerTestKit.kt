package org.politisationpopulairefrancaise.signer

import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.ApprovalAssertion
import org.politisationpopulairefrancaise.core.platform.ApprovalChallenge
import org.politisationpopulairefrancaise.core.platform.ApprovalProof
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.EventDraft
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamCredential
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.TeamMember
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64

const val TEST_ORIGIN: String = "https://equipe.preprod.example"
const val TEST_RP_ID: String = "equipe.preprod.example"
const val TEST_TOKEN: String = "test-token-of-the-server-0123456789abcdef"
const val DAY: Long = 86_400

fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** Signs [event] with [keys] (tests only). */
fun signWith(keys: IdentityKeys, event: UnsignedEvent): SignedEvent {
    val id = event.id()
    return SignedEvent(id.toHexString(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, keys.sign(id).toHexString())
}

/** A clock the tests move by hand. */
class MutableClock(var now: Instant = Instant.parse("2026-11-07T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneOffset = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    val seconds: Long get() = now.epochSecond

    fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

/**
 * A software passkey (EC P-256, ES256) built with WebAuthn4J's own data classes, as in the back-office
 * tests: it signs a challenge as a browser and an authenticator would, with user presence and (unless
 * told otherwise) user verification, so that the real verifier of the service is exercised end to end.
 */
class TestPasskey(private val rpId: String = TEST_RP_ID, private val userVerified: Boolean = true) {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val converter = ObjectConverter()
    val credentialId: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)
    private var counter = 0L

    /** As the root lists it in the team list: id and COSE public key, base64url. */
    val credential: TeamCredential by lazy {
        val attested = AttestedCredentialData(AAGUID.ZERO, credentialId, EC2COSEKey.create(keyPair.public as ECPublicKey, COSEAlgorithmIdentifier.ES256))
        val bytes = AttestedCredentialDataConverter(converter).convert(attested)
        TeamCredential(b64url(credentialId), b64url(bytes.copyOfRange(16 + 2 + credentialId.size, bytes.size)))
    }

    fun assert(account: String, challenge: ByteArray, origin: String = TEST_ORIGIN): ApprovalAssertion {
        counter++
        val clientData = """{"type":"webauthn.get","challenge":"${b64url(challenge)}","origin":"$origin","crossOrigin":false}""".toByteArray()
        var flags = AuthenticatorData.BIT_UP.toInt()
        if (userVerified) flags = flags or AuthenticatorData.BIT_UV.toInt()
        val authenticatorData = AuthenticatorDataConverter(converter).convert(
            AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>(sha256(rpId.toByteArray()), flags.toByte(), counter),
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(authenticatorData)
            update(sha256(clientData))
            sign()
        }
        return ApprovalAssertion(account, b64url(credentialId), b64url(authenticatorData), b64url(clientData), b64url(signature))
    }

    /** An approval of [event] for [use] by [account]. */
    fun approve(account: String, use: SigningUse, event: UnsignedEvent, origin: String = TEST_ORIGIN): ApprovalAssertion =
        assert(account, ApprovalChallenge.of(use, event.idHex()), origin)
}

/** A member of the test team with their passkey. */
class TestMember(val account: String, val uses: Set<SigningUse>, val validFrom: Long = 0, val nostr: IdentityKeys = IdentityKeys.generate()) {
    val passkey = TestPasskey()
    val spare = TestPasskey()

    fun member(withSpare: Boolean = false): TeamMember =
        TeamMember(account, validFrom, nostr.publicKey.hex, uses, listOfNotNull(passkey.credential, if (withSpare) spare.credential else null))

    fun approve(use: SigningUse, event: UnsignedEvent, origin: String = TEST_ORIGIN): ApprovalAssertion = passkey.approve(account, use, event, origin)
}

/** A service in a temporary data directory, its root key and a team, all fictional. */
class SignerFixture {
    val dir: Path = Files.createTempDirectory("ppf-signer-test")
    val root: IdentityKeys = IdentityKeys.generate()
    val rootHex: String = root.publicKey.hex
    val clock = MutableClock()
    val kek: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)
    val alice = TestMember("alice", setOf(SigningUse.EDITORIAL_VOIR, SigningUse.EDITORIAL_COMPRENDRE))
    val bob = TestMember("bob", setOf(SigningUse.EDITORIAL_VOIR))
    val carole = TestMember("carole", setOf(SigningUse.AGIR_ORGANISATIONS))

    fun config(
        root: String? = rootHex,
        uses: Set<SigningUse> = SigningUse.entries.toSet(),
        maxPerHour: Map<SigningUse, Int> = emptyMap(),
        kek: ByteArray = this.kek,
    ): SignerConfig = SignerConfig(dir, kek, TEST_TOKEN, root, TEST_ORIGIN, enabledUses = uses, maxPerHour = maxPerHour)

    fun open(config: SignerConfig = config()): SignerService = SignerService.open(config, clock)

    fun now(): Long = clock.seconds

    /** The key of [use] in [state] (the newest one). */
    fun key(service: SignerService, use: SigningUse, state: String? = null): String =
        service.keys().keys.filter { it.use == use.id && (state == null || it.state == state) }.maxBy { it.createdAt }.pubkey

    fun currentKey(service: SignerService, use: SigningUse): String? = service.keys().keys.firstOrNull { it.use == use.id && it.current }?.pubkey

    fun certificate(
        key: String,
        uses: List<SigningUse>,
        notBefore: Long = now(),
        notAfter: Long = notBefore + 90 * DAY,
        createdAt: Long = now(),
        status: KeyStatus = KeyStatus.ACTIVE,
        revokedFrom: Long? = null,
    ): SignedEvent = signWith(root, KeyCertificate.unsigned(rootHex, key, uses, notBefore, notAfter, createdAt, status, revokedFrom))

    /** Certifies the key of [use] awaiting its certificate, and returns it. */
    fun certify(service: SignerService, use: SigningUse, notBefore: Long = now(), notAfter: Long = notBefore + 90 * DAY): String {
        val key = key(service, use, WorkingKeyStates.AWAITING_CERTIFICATION)
        val outcome = service.loadCertificates(listOf(certificate(key, listOf(use), notBefore, notAfter))).single()
        check(outcome.status == "stored") { "certificate not stored: ${outcome.status}" }
        return key
    }

    fun certifyAll(service: SignerService) {
        SigningUse.entries.forEach { certify(service, it) }
    }

    fun teamList(members: List<TeamMember>, createdAt: Long = now()): SignedEvent = signWith(root, TeamList.unsigned(rootHex, members, createdAt))

    fun loadTeam(service: SignerService, vararg members: TeamMember = arrayOf(alice.member(), bob.member(), carole.member())) {
        check(service.loadTeamList(teamList(members.toList())).status == "stored")
    }

    /** A ready service: every use certified, the team loaded. */
    fun ready(config: SignerConfig = config()): SignerService = open(config).also {
        certifyAll(it)
        loadTeam(it)
    }

    fun editorial(pubkey: String, createdAt: Long = now(), title: String = "[EXEMPLE] Un fait sourcé", extra: List<List<String>> = emptyList()): UnsignedEvent =
        UnsignedEvent(
            pubkey, createdAt, EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", "exemple-fait"), listOf("title", title), listOf("ppf-origin", "human"), listOf("ppf-type", "fait")) + extra,
            "[EXEMPLE] Texte confidentiel du contenu, jamais dans le journal.",
        )

    fun request(use: SigningUse, event: UnsignedEvent, assertions: List<ApprovalAssertion> = emptyList(), double: Boolean = false, author: DraftAuthor? = null): SignRequest =
        SignRequest(use.id, EventDraft.of(event), ApprovalProof(double, author, assertions))
}
