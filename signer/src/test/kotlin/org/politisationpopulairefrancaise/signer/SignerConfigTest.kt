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
    ) + extra

    @Test
    fun aCompleteConfigurationIsRead() {
        val config = SignerConfig.fromEnvironment(env("PPF_SIGNER_USES" to "opendata.index, editorial.voir", "PPF_SIGNER_MAX_PER_HOUR" to "editorial.voir=5"))
        assertEquals(TEST_TOKEN, config.clientToken)
        assertTrue(config.kek().contentEquals(ByteArray(32) { 0xab.toByte() }))
        assertNull(config.rootPublicKey)
        assertEquals(TEST_RP_ID, config.rpId)
        assertEquals(setOf(SigningUse.OPENDATA_INDEX, SigningUse.EDITORIAL_VOIR), config.enabledUses)
        assertEquals(mapOf(SigningUse.EDITORIAL_VOIR to 5), config.maxPerHour)
        assertEquals(SignerConfig.DEFAULT_PORT, config.port)
        assertEquals("127.0.0.1", config.host)
        assertFalse("abab" in config.toString())
    }

    @Test
    fun secretsAreNeverTakenFromVariablesAndBadValuesStopTheService() {
        val bad = listOf(
            env("PPF_SIGNER_KEK" to "ab".repeat(32)),
            env("PPF_SIGNER_CLIENT_TOKEN" to TEST_TOKEN),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "http://equipe.example"),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://equipe.example/chemin"),
            env("PPF_SIGNER_APPROVAL_ORIGIN" to "https://user@equipe.example"),
            env("PPF_SIGNER_ROOT_PUBKEY" to "ABC"),
            env("PPF_SIGNER_USES" to "annonces"),
            env("PPF_SIGNER_MAX_PER_HOUR" to "editorial.voir"),
            env("PPF_SIGNER_PORT" to "0"),
            env("PPF_SIGNER_KEK_FILE" to dir.resolve("absent").toString()),
            env("PPF_SIGNER_CLIENT_TOKEN_FILE" to kekFile.resolveSibling("short").also { Files.writeString(it, "court") }.toString()),
            env("PPF_SIGNER_KEK_FILE" to tokenFile.toString()),
            env() - "PPF_SIGNER_DATA_DIR",
        )
        for (environment in bad) {
            val error = assertFailsWith<SignerConfigException>(environment.toString()) { SignerConfig.fromEnvironment(environment) }
            assertFalse(TEST_TOKEN in error.message!! || "abab" in error.message!!)
        }
        assertEquals("http://localhost:8081", SignerConfig.fromEnvironment(env("PPF_SIGNER_APPROVAL_ORIGIN" to "http://localhost:8081")).approvalOrigin)
    }
}
