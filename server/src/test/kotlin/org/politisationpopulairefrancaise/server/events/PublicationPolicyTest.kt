package org.politisationpopulairefrancaise.server.events

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PublicationPolicyTest {

    private val editor = IdentityKeys.generate()
    private val citizen = IdentityKeys.generate()

    @Test
    fun parsesHexAndNpubKeysInAnyOrderAndSeparator() {
        val keys = PublicationPolicy.parseKeys(" ${editor.publicKey.npub}, ${citizen.publicKey.hex.uppercase()} ;\n")
        assertEquals(setOf(editor.publicKey.hex, citizen.publicKey.hex), keys)
        assertEquals(emptySet(), PublicationPolicy.parseKeys(null))
        assertEquals(emptySet(), PublicationPolicy.parseKeys("  "))
        assertFailsWith<IllegalArgumentException> { PublicationPolicy.parseKeys("not-a-key") }
    }

    @Test
    fun onlyEditorsPublishEditorialContentsAndEphemeralEventsAreRefused() = runBlocking {
        val policy = PublicationPolicy.fromEnvironment { if (it == PublicationPolicy.EDITOR_KEYS_VARIABLE) editor.publicKey.npub else null }
        assertTrue(policy.isEditor(editor.publicKey.hex))

        val article = LocalEventSigner(editor).sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a")))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(article))

        val intruder = LocalEventSigner(citizen).sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a")))
        val refused = assertIs<PublicationPolicy.Decision.Refused>(policy.check(intruder))
        assertEquals(403, refused.status)
        assertEquals("not_allowed", refused.error)

        val note = LocalEventSigner(citizen).sign(kind = EventKinds.TEXT_NOTE, content = "x")
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(note))

        val ephemeral = LocalEventSigner(editor).sign(kind = 20001, content = "x")
        assertEquals(400, assertIs<PublicationPolicy.Decision.Refused>(policy.check(ephemeral)).status)
    }
}
