package org.politisationpopulairefrancaise.server.backoffice

import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.AttestationObject
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput
import org.politisationpopulairefrancaise.server.TestDatabase
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration

/**
 * A software passkey for the tests (EC P-256, ES256), built with WebAuthn4J's own data classes: it
 * answers the options of the server as a browser and an authenticator would, with user presence and
 * user verification flags, so that the real [WebAuthn4jPasskeys] verifier is exercised end to end.
 */
class TestAuthenticator(
    private val rpId: String = BackOfficeTestData.RP_ID,
    private val origin: String = BackOfficeTestData.ORIGIN,
    private val userVerified: Boolean = true,
) {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val converter = ObjectConverter()
    val credentialId: ByteArray = Tokens.randomBytes(32)
    var userHandle: ByteArray? = null
    var counter: Long = 0

    fun register(options: CreationOptionsResponse, name: String? = null, csrf: String? = null, clientOrigin: String = origin): RegistrationResponse {
        userHandle = Tokens.fromBase64Url(options.publicKey.user.id)
        val clientData = clientData("webauthn.create", options.publicKey.challenge, clientOrigin)
        val attested = AttestedCredentialData(AAGUID.ZERO, credentialId, EC2COSEKey.create(keyPair.public as ECPublicKey, COSEAlgorithmIdentifier.ES256))
        val authenticatorData = AuthenticatorData<RegistrationExtensionAuthenticatorOutput>(sha256(rpId.toByteArray()), flags(attested = true), counter, attested)
        val attestation = AttestationObjectConverter(converter).convertToBytes(AttestationObject(authenticatorData, NoneAttestationStatement()))
        return RegistrationResponse(options.ceremony, Tokens.base64Url(credentialId), Tokens.base64Url(clientData), Tokens.base64Url(attestation), name, csrf)
    }

    fun assertion(options: RequestOptionsResponse, csrf: String? = null, clientOrigin: String = origin): AssertionResponse =
        assertion(options.ceremony, options.publicKey.challenge, csrf, clientOrigin)

    fun assertion(ceremony: String, challenge: String, csrf: String? = null, clientOrigin: String = origin): AssertionResponse {
        counter++
        val clientData = clientData("webauthn.get", challenge, clientOrigin)
        val authenticatorData = AuthenticatorDataConverter(converter).convert(
            AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>(sha256(rpId.toByteArray()), flags(attested = false), counter),
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(authenticatorData)
            update(sha256(clientData))
            sign()
        }
        return AssertionResponse(
            ceremony, Tokens.base64Url(credentialId), Tokens.base64Url(clientData), Tokens.base64Url(authenticatorData), Tokens.base64Url(signature),
            userHandle?.let(Tokens::base64Url), csrf,
        )
    }

    private fun flags(attested: Boolean): Byte {
        var value = AuthenticatorData.BIT_UP.toInt()
        if (userVerified) value = value or AuthenticatorData.BIT_UV.toInt()
        if (attested) value = value or AuthenticatorData.BIT_AT.toInt()
        return value.toByte()
    }

    private fun clientData(type: String, challenge: String, clientOrigin: String): ByteArray =
        """{"type":"$type","challenge":"$challenge","origin":"$clientOrigin","crossOrigin":false}""".toByteArray()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}

/** A clock the tests move by hand. */
class MutableClock(var now: Instant = Instant.parse("2026-11-07T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneOffset = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plusSeconds(duration.inWholeSeconds)
    }
}

/** A member created for a test, with their keys and a signed-in session. */
class TestMember(val account: TeamAccount, val keys: List<TestAuthenticator>, val token: String) {
    val id: UUID get() = account.id
}

object BackOfficeTestData {
    const val ORIGIN: String = "http://localhost:8081"
    const val RP_ID: String = "localhost"
    const val PORT: Int = 8081

    /** The back-office settings of the tests: development origin, no periodic task. */
    val CONFIG: BackOfficeConfig = BackOfficeConfig(ORIGIN, PORT, maintenanceInterval = Duration.ZERO)

    /** Empties the tables of the base, except the journal, which refuses it (its chain continues across tests). */
    fun reset() {
        TestDatabase.reset()
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    "TRUNCATE team_validation_session, team_dashboard_alert, team_pending_approval, team_session, team_role_grant, " +
                        "team_invitation, team_credential, team_account CASCADE",
                )
            }
        }
    }

    fun backOffice(
        clock: Clock = Clock.systemUTC(),
        verified: Set<String> = emptySet(),
        queues: BackOfficeQueues = BackOfficeQueues(),
    ): BackOffice = BackOffice(
        config = CONFIG,
        dataSource = TestDatabase.dataSource,
        queues = queues,
        isVerifiedOrganisation = { it in verified },
        verifiedOrganisations = { verified },
        clock = clock,
    )

    /** The stores of the tests, on the same database as the back-office (the back-office keeps its own private). */
    val accounts = TeamAccounts(TestDatabase.dataSource)
    val grants = TeamGrants(TestDatabase.dataSource)
    val sessions = TeamSessions(TestDatabase.dataSource)
    val approvals = PendingApprovals(TestDatabase.dataSource)

    /** A journal reader of the tests (never used to verify the chain: see the tests of truncation). */
    val audit = AuditLog(TestDatabase.dataSource)

    /**
     * A member with [roles] (granted directly, as by the bootstrap) and [keys] passkeys registered through
     * the real verifier, signed in now (at the clock of [backOffice]).
     */
    fun member(
        backOffice: BackOffice,
        pseudonym: String,
        roles: List<TeamRole> = emptyList(),
        keys: Int = 2,
        organisation: String? = null,
    ): TestMember {
        val now = backOffice.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val authenticators = List(keys) { TestAuthenticator() }
        val handle = Tokens.randomBytes(32)
        val verifier = WebAuthn4jPasskeys(CONFIG.origin, CONFIG.rpId)
        return TestDatabase.dataSource.transaction { connection ->
            val account = accounts.create(connection, pseudonym, handle, null, now)
            authenticators.forEachIndexed { index, authenticator ->
                val challenge = Tokens.randomBytes(32)
                val options = CreationOptionsResponse(
                    "test",
                    PublicKeyCreationOptions(
                        challenge = Tokens.base64Url(challenge),
                        rp = RelyingParty(CONFIG.rpId, "test"),
                        user = UserEntity(Tokens.base64Url(handle), pseudonym, pseudonym),
                        pubKeyCredParams = emptyList(),
                        timeout = 1,
                    ),
                )
                val credential = verifier.verifyRegistration(authenticator.register(options), challenge, "Clé $index")
                accounts.addCredential(connection, account.id, credential, now)
            }
            roles.forEach { role ->
                grants.insert(connection, account.id, role, organisation.takeIf { role == TeamRole.VALIDATOR }, null, null, null, now)
            }
            val session = sessions.open(connection, account.id, now)
            TestMember(account, authenticators, session.token)
        }
    }

    fun auth(backOffice: BackOffice, member: TestMember): Authenticated = requireNotNull(backOffice.authenticate(member.token)) { "The test session is alive" }

    /** Adds [newKey] to the account of [auth], proving it first with [existing] (D-127). */
    fun addKey(backOffice: BackOffice, auth: Authenticated, existing: TestAuthenticator, newKey: TestAuthenticator, name: String? = null): Outcome<KeyInfo> {
        val proof = (backOffice.startAddKeyProof(auth) as Outcome.Ok).value
        return when (val creation = backOffice.startAddKey(auth, existing.assertion(proof))) {
            is Outcome.Denied -> creation
            is Outcome.Ok -> backOffice.finishAddKey(auth, newKey.register(creation.value, name))
        }
    }

    /** The journal lines recorded after [seq] (the journal is shared by every test of the run). */
    fun journalAfter(seq: Long): List<AuditRecord> = audit.query(AuditFilter(), AuditLog.MAX_PAGE).filter { it.seq > seq }.sortedBy { it.seq }

    fun lastSeq(): Long = audit.head()?.seq ?: 0
}
