package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.platform.SigningUse
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignerConfigTest {
    private val dir = Files.createTempDirectory("ppf-signer-config")
    private val kekFile = dir.resolve("kek").also { Files.writeString(it, "ab".repeat(32) + "\n") }
    private val tokenFile = dir.resolve("token").also { Files.writeString(it, TEST_TOKEN + "\n") }

    private fun env(vararg extra: Pair<String, String>): Map<String, String> = mapOf(
        "PPF_SIGNER_DATA_DIR" to dir.resolve("data").toString(),
        "PPF_SIGNER_KEK_FILE" to kekFile.toString(),
        "PPF_SIGNER_CLIENT_TOKEN_FILE" to tokenFile.toString(),
        "PPF_SIGNER_APPROVAL_ORIGIN" to TEST_ORIGIN,
        "PPF_SIGNER_RP_ID" to TEST_RP_ID,
        "PPF_SIGNER_BACKOFFICE_ORIGIN" to TEST_BACKOFFICE,
    ) + extra

    @Test
    fun aCompleteConfigurationIsRead() {
        val config = SignerConfig.fromEnvironment(env("PPF_SIGNER_USES" to "opendata.index, editorial.voir", "PPF_SIGNER_MAX_PER_HOUR" to "editorial.voir=5"))
        assertEquals(TEST_TOKEN, config.clientToken)
        assertTrue(config.kek().contentEquals(ByteArray(32) { 0xab.toByte() }))
        assertNull(config.rootPublicKey)
        assertEquals(TEST_ORIGIN, config.approvalOrigin)
        assertEquals(TEST_RP_ID, config.rpId)
        assertEquals(TEST_BACKOFFICE, config.backOfficeOrigin)
        assertEquals(setOf(SigningUse.OPENDATA_INDEX, SigningUse.EDITORIAL_VOIR), config.enabledUses)
        assertEquals(mapOf(SigningUse.EDITORIAL_VOIR to 5), config.maxPerHour)
        assertEquals(SignerConfig.DEFAULT_PORT, config.port)
        assertEquals(SignerConfig.DEFAULT_APPROVAL_PORT, config.approvalPort)
        assertEquals("127.0.0.1", config.host)
        assertFalse("abab" in config.toString())
        assertEquals("$TEST_ORIGIN/approbation/editorial.voir/${"a".repeat(64)}", config.approvalUrl(SigningUse.EDITORIAL_VOIR, "a".repeat(64)))
        // A final slash and capitals are normalised.
        val normalised = SignerConfig.fromEnvironment(env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://Signature.Equipe.Preprod.Example/", "PPF_SIGNER_RP_ID" to "Equipe.Preprod.Example"))
        assertEquals(TEST_ORIGIN, normalised.approvalOrigin)
        assertEquals(TEST_RP_ID, normalised.rpId)
    }

    @Test
    fun secretsAreNeverTakenFromVariablesAndBadValuesStopTheService() {
        val bad = listOf(
            env("PPF_SIGNER_KEK" to "ab".repeat(32)),
            env("PPF_SIGNER_CLIENT_TOKEN" to TEST_TOKEN),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "http://signature.equipe.preprod.example"),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://signature.equipe.preprod.example/chemin"),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://user@signature.equipe.preprod.example"),
            env("PPF_SIGNER_ROOT_PUBKEY" to "ABC"),
            env("PPF_SIGNER_USES" to "annonces"),
            env("PPF_SIGNER_MAX_PER_HOUR" to "editorial.voir"),
            env("PPF_SIGNER_PORT" to "0"),
            env("PPF_SIGNER_APPROVAL_PORT" to "8090"),
            env("PPF_SIGNER_KEK_FILE" to dir.resolve("absent").toString()),
            env("PPF_SIGNER_CLIENT_TOKEN_FILE" to kekFile.resolveSibling("short").also { Files.writeString(it, "court") }.toString()),
            env("PPF_SIGNER_KEK_FILE" to tokenFile.toString()),
            env() - "PPF_SIGNER_DATA_DIR",
            env() - "PPF_SIGNER_RP_ID",
            env() - "PPF_SIGNER_BACKOFFICE_ORIGIN",
        )
        for (environment in bad) {
            val error = assertFailsWith<SignerConfigException>(environment.toString()) { SignerConfig.fromEnvironment(environment) }
            assertFalse(TEST_TOKEN in error.message!! || "abab" in error.message!!)
        }
    }

    @Test
    fun theApprovalsAreMadeOnASubdomainOfTheBackOfficeNeverOnTheBackOfficeItself() {
        val refused = listOf(
            // The approval pages on the back-office origin: the main server could serve them.
            env("PPF_SIGNER_APPROVAL_ORIGIN" to TEST_BACKOFFICE),
            // Not a subdomain of the relying party: the back-office passkeys would not work there.
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://signature.autre.example"),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://xequipe.preprod.example"),
            // The relying party is the host of the back-office.
            env("PPF_SIGNER_RP_ID" to "preprod.example"),
            env("PPF_SIGNER_RP_ID" to "https://equipe.preprod.example"),
            env("PPF_SIGNER_BACKOFFICE_ORIGIN" to "https://autre.preprod.example"),
            // Local addresses only in development.
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "http://localhost:8091", "PPF_SIGNER_RP_ID" to "localhost", "PPF_SIGNER_BACKOFFICE_ORIGIN" to "http://localhost:8081"),
            env("PPF_ENVIRONMENT" to "development", "PPF_SIGNER_APPROVAL_ORIGIN" to "http://localhost:8081", "PPF_SIGNER_RP_ID" to "localhost", "PPF_SIGNER_BACKOFFICE_ORIGIN" to "http://localhost:8081"),
        )
        for (environment in refused) assertFailsWith<SignerConfigException>(environment.toString()) { SignerConfig.fromEnvironment(environment) }
        val local = SignerConfig.fromEnvironment(
            env("PPF_ENVIRONMENT" to "development", "PPF_SIGNER_APPROVAL_ORIGIN" to "http://localhost:8091", "PPF_SIGNER_RP_ID" to "localhost", "PPF_SIGNER_BACKOFFICE_ORIGIN" to "http://localhost:8081"),
        )
        assertEquals("http://localhost:8091", local.approvalOrigin)
        assertEquals("localhost", local.rpId)
        assertFailsWith<SignerConfigException> { SignerConfig(dir, ByteArray(32), TEST_TOKEN, null, TEST_BACKOFFICE, TEST_RP_ID, TEST_BACKOFFICE) }
    }
}
