package org.politisationpopulairefrancaise.server.events

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.signing.PlatformKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The contents of the platform (D-129, step 6): an editorial content signed by a working key certified by
 * the root for an editorial use is published like one of an editorial key, and its key counts as the
 * team's (out of the legal register); a key certified for another use, revoked at the content's date, or
 * certified by another root is refused.
 */
class PlatformContentPolicyTest {

    private val root = IdentityKeys.generate()
    private val editorialKey = IdentityKeys.generate()
    private val timestampKey = IdentityKeys.generate()
    private val start = 1_794_096_000L
    private val end = start + KeyCertificate.MAX_VALIDITY_SECONDS

    private fun rootSigned(unsigned: UnsignedEvent, by: IdentityKeys = root): SignedEvent {
        val id = unsigned.id()
        return SignedEvent(id.toHexString(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, by.sign(id).toHexString())
    }

    private fun certificate(key: IdentityKeys, use: SigningUse, by: IdentityKeys = root): SignedEvent =
        rootSigned(KeyCertificate.unsigned(by.publicKey.hex, key.publicKey.hex, listOf(use), start, end, start), by)

    private fun revocation(key: IdentityKeys, use: SigningUse, from: Long): SignedEvent =
        rootSigned(KeyCertificate.unsigned(root.publicKey.hex, key.publicKey.hex, listOf(use), start, end, from, KeyStatus.REVOKED, from))

    private fun policyWith(vararg events: SignedEvent, rootHex: String? = root.publicKey.hex): PublicationPolicy {
        val trust = PlatformTrust.of(rootHex, events.toList())
        return PublicationPolicy(emptySet()).withPlatformKeys(PlatformKeys { trust })
    }

    private fun article(by: IdentityKeys, at: Long, type: String = "fiche"): SignedEvent = runBlocking {
        LocalEventSigner(by).sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a"), listOf("ppf-type", type)), createdAt = at)
    }

    @Test
    fun aContentOfACertifiedEditorialKeyIsPublishedAndItsKeyIsTheTeams() {
        val policy = policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE))
        val content = article(editorialKey, start + 10)
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(content))
        assertTrue(policy.isPlatformContent(content))
        assertTrue(policy.isEditor(editorialKey.publicKey.hex))
        assertFalse(policy.isCitizenDemand(article(editorialKey, start + 10, type = "revendication")))
    }

    @Test
    fun aKeyCertifiedForAnotherUseCannotPublishAnEditorialContent() {
        val policy = policyWith(certificate(timestampKey, SigningUse.ARCHIVES_TIMESTAMPS))
        assertIs<PublicationPolicy.Decision.Refused>(policy.check(article(timestampKey, start + 10)))
        // Its contents of its own use still stay out of the legal register.
        assertTrue(policy.isEditor(timestampKey.publicKey.hex))
    }

    @Test
    fun aContentOutsideTheValidityOrAfterTheRevocationIsRefused() {
        val revokedFrom = start + 1_000
        val policy = policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE), revocation(editorialKey, SigningUse.EDITORIAL_COMPRENDRE, revokedFrom))
        assertEquals(PublicationPolicy.Decision.Allowed, policy.check(article(editorialKey, revokedFrom - 1)))
        assertIs<PublicationPolicy.Decision.Refused>(policy.check(article(editorialKey, revokedFrom)))
        assertIs<PublicationPolicy.Decision.Refused>(policy.check(article(editorialKey, start - 1)))
        assertIs<PublicationPolicy.Decision.Refused>(policy.check(article(editorialKey, end)))
    }

    @Test
    fun aContentOfTheFormOfAnotherUseIsRefused() {
        // A Pacte type under an editorial key: the form of decider.pacte, not of editorial.comprendre.
        val policy = policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE))
        assertIs<PublicationPolicy.Decision.Refused>(policy.check(article(editorialKey, start + 10, type = "pacte")))
    }

    @Test
    fun nothingIsTrustedWithoutTheRootOrFromAnotherRoot() {
        val other = IdentityKeys.generate()
        val content = article(editorialKey, start + 10)
        assertIs<PublicationPolicy.Decision.Refused>(policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE), rootHex = null).check(content))
        assertIs<PublicationPolicy.Decision.Refused>(policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE, by = other)).check(content))
        assertFalse(policyWith(certificate(editorialKey, SigningUse.EDITORIAL_COMPRENDRE, by = other)).isEditor(editorialKey.publicKey.hex))
        // The default policy knows no platform key.
        assertIs<PublicationPolicy.Decision.Refused>(PublicationPolicy(emptySet()).check(content))
    }
}
