package org.politisationpopulairefrancaise.tools.content

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Safety net of the editorial circuit (docs/integration/circuit-editorial.md, section 3.3): rebuilds every
 * bundle of content/ with the public key read from the bundled JSON files and compares each event with the
 * bundled one, the signature aside (BIP-340 uses randomness). The id is the digest of everything else, so
 * equal ids mean equal events to the byte; the fields are compared too, for a readable message.
 * Needs no secret: building an event needs only the public key of its author.
 */
class NoRegressionTest {
    private val root = Path.of("").toAbsolutePath()
    private val bundled = root.resolve("shared/ui/src/commonMain/composeResources/files/contents")
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(file: String): List<SignedEvent> =
        json.decodeFromString(ListSerializer(SignedEvent.serializer()), bundled.resolve(file).readText())

    @Test
    fun rebuildingContentGivesTheBundledEvents() {
        assertTrue(bundled.exists(), "bundled contents not found from $root (the test runs from the repository root)")
        val author = read("fiches.json").first().pubkey
        val bundles = prepareBundles(root.resolve("content"), PublicKey.fromHex(author))
        assertEquals(9, bundles.size)
        for (bundle in bundles) {
            val expected = read(bundle.file)
            assertTrue(expected.all { it.pubkey == author }, "${bundle.file}: every bundled event has the same author")
            assertEquals(expected.size, bundle.events.size, "${bundle.file}: number of events")
            expected.zip(bundle.events).forEachIndexed { index, (signed, rebuilt) ->
                val at = "${bundle.file}[$index] (${signed.firstTagValue("d") ?: signed.id})"
                assertEquals(signed.pubkey, rebuilt.pubkey, "$at: pubkey")
                assertEquals(signed.createdAt, rebuilt.createdAt, "$at: created_at")
                assertEquals(signed.kind, rebuilt.kind, "$at: kind")
                assertEquals(signed.tags, rebuilt.tags, "$at: tags (values and order)")
                assertEquals(signed.content, rebuilt.content, "$at: content")
                assertEquals(signed.id, rebuilt.idHex(), "$at: id")
            }
        }
    }
}
