package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Signs [event] with [keys] (tests only; the applications sign through EventSigner). */
internal fun signWith(keys: IdentityKeys, event: UnsignedEvent): SignedEvent {
    val id = event.id()
    return SignedEvent(id.toHexString(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, keys.sign(id).toHexString())
}

class PlatformTrustTest {
    private val root = IdentityKeys.generate()
    private val rootHex = root.publicKey.hex
    private val working = IdentityKeys.generate()
    private val day = 86_400L
    private val start = 1_793_000_000L

    private fun certificate(
        key: IdentityKeys = working,
        uses: List<SigningUse> = listOf(SigningUse.EDITORIAL_VOIR),
        notBefore: Long = start,
        notAfter: Long = start + 90 * day,
        createdAt: Long = start - day,
        status: KeyStatus = KeyStatus.ACTIVE,
        revokedFrom: Long? = null,
        signer: IdentityKeys = root,
    ): SignedEvent {
        val unsigned = KeyCertificate.unsigned(signer.publicKey.hex, key.publicKey.hex, uses, notBefore, notAfter, createdAt, status, revokedFrom)
        return signWith(signer, unsigned)
    }

    /** A certificate whose tags are written by hand, to check the reading of malformed ones. */
    private fun rawCertificate(tags: List<List<String>>, createdAt: Long = start - day): SignedEvent =
        signWith(root, UnsignedEvent(rootHex, createdAt, EventKinds.PLATFORM_KEY_CERTIFICATE, listOf(listOf("d", working.publicKey.hex)) + tags, ""))

    private fun content(at: Long, kind: Int = EventKinds.LONG_FORM_ARTICLE, keys: IdentityKeys = working): SignedEvent =
        signWith(keys, UnsignedEvent(keys.publicKey.hex, at, kind, listOf(listOf("d", "exemple"), listOf("title", "[EXEMPLE] Titre")), "[EXEMPLE]"))

    @Test
    fun withoutRootNothingIsTrusted() {
        val cert = certificate()
        assertEquals(TrustVerdict.NO_ROOT, PlatformTrust.of(null, listOf(cert)).check(content(start + day), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.NO_ROOT, PlatformTrust.of("", listOf(cert)).check(content(start + day), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.NO_ROOT, PlatformTrust.of("pas une clé", listOf(cert)).check(content(start + day), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.NO_ROOT, PlatformRoot.trust(listOf(cert)).check(content(start + day), SigningUse.EDITORIAL_VOIR))
        assertFalse(PlatformRoot.trust().isConfigured)
        assertEquals(CertificateOffer.REFUSED, PlatformTrust.of(null).offer(cert).second)
    }

    @Test
    fun aCertifiedKeySpeaksForItsUseAndKindInsideItsValidity() {
        val trust = PlatformTrust.of(rootHex, listOf(certificate()))
        assertTrue(trust.isPlatformContent(content(start), SigningUse.EDITORIAL_VOIR))
        assertTrue(trust.isPlatformContent(content(start + 90 * day - 1), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.NOT_YET_VALID, trust.check(content(start - 1), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.EXPIRED, trust.check(content(start + 90 * day), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.NOT_CERTIFIED_FOR_USE, trust.check(content(start + day), SigningUse.EDITORIAL_COMPRENDRE))
        assertEquals(TrustVerdict.KIND_NOT_ALLOWED, trust.check(content(start + day, kind = EventKinds.TEXT_NOTE), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.UNKNOWN_KEY, trust.check(content(start + day, keys = IdentityKeys.generate()), SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun aTamperedContentIsNotAuthentic() {
        val trust = PlatformTrust.of(rootHex, listOf(certificate()))
        val tampered = content(start + day).copy(content = "[EXEMPLE] autre texte")
        assertEquals(TrustVerdict.NOT_AUTHENTIC, trust.check(tampered, SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun aCertificateOfAnotherKeyThanTheRootIsIgnored() {
        val impostor = IdentityKeys.generate()
        val trust = PlatformTrust.of(rootHex)
        assertEquals(CertificateOffer.REFUSED, trust.offer(certificate(signer = impostor)).second)
        assertEquals(TrustVerdict.UNKNOWN_KEY, trust.plus(certificate(signer = impostor)).check(content(start + day), SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun theRootNeverSignsContentsNorCertifiesItself() {
        val trust = PlatformTrust.of(rootHex, listOf(certificate()))
        assertEquals(TrustVerdict.ROOT_NEVER_SIGNS_CONTENTS, trust.check(content(start + day, keys = root), SigningUse.EDITORIAL_VOIR))
        val selfCertificate = signWith(
            root,
            UnsignedEvent(
                rootHex, start, EventKinds.PLATFORM_KEY_CERTIFICATE,
                listOf(listOf("d", rootHex), listOf("ppf-key-use", "editorial.voir"), listOf("ppf-not-before", "$start"), listOf("ppf-not-after", "${start + day}"), listOf("ppf-key-status", "active")),
                "",
            ),
        )
        assertNull(KeyCertificate.read(selfCertificate, rootHex))
        assertFailsWith<IllegalArgumentException> { KeyCertificate.unsigned(rootHex, rootHex, listOf(SigningUse.EDITORIAL_VOIR), start, start + day, start) }
    }

    @Test
    fun aRevocationTakesEffectAtItsDateAndAnOlderCertificateNeverUndoesIt() {
        val revocation = certificate(createdAt = start + 10 * day, status = KeyStatus.REVOKED, revokedFrom = start + 5 * day)
        val trust = PlatformTrust.of(rootHex, listOf(certificate(), revocation))
        assertTrue(trust.isPlatformContent(content(start + 5 * day - 1), SigningUse.EDITORIAL_VOIR))
        assertEquals(TrustVerdict.REVOKED, trust.check(content(start + 5 * day), SigningUse.EDITORIAL_VOIR))
        val (again, outcome) = trust.offer(certificate())
        assertEquals(CertificateOffer.SUPERSEDED, outcome)
        assertEquals(TrustVerdict.REVOKED, again.check(content(start + 6 * day), SigningUse.EDITORIAL_VOIR))
        assertEquals(CertificateOffer.DUPLICATE, trust.offer(revocation).second)
        // Received in the other order, the result is the same.
        val reversed = PlatformTrust.of(rootHex, listOf(revocation, certificate()))
        assertEquals(TrustVerdict.REVOKED, reversed.check(content(start + 6 * day), SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun aRevocationWithoutDateRevokesEverything() {
        val revocation = certificate(createdAt = start + 10 * day, status = KeyStatus.REVOKED)
        val trust = PlatformTrust.of(rootHex, listOf(certificate(), revocation))
        assertEquals(TrustVerdict.REVOKED, trust.check(content(start), SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun anythingNotUnderstoodRevokesTheKey() {
        val validity = listOf(listOf("ppf-key-use", "editorial.voir"), listOf("ppf-not-before", "$start"), listOf("ppf-not-after", "${start + day}"))
        val cases = listOf(
            validity + listOf(listOf("ppf-key-status", "suspendue")),
            validity,
            validity + listOf(listOf("ppf-key-status", "active"), listOf("ppf-key-status", "active")),
            listOf(listOf("ppf-key-use", "editorial.voir"), listOf("ppf-not-before", "$start"), listOf("ppf-not-after", "${start + 91 * day}"), listOf("ppf-key-status", "active")),
            listOf(listOf("ppf-key-use", "editorial.voir"), listOf("ppf-not-before", "$start"), listOf("ppf-not-after", "$start"), listOf("ppf-key-status", "active")),
            listOf(listOf("ppf-key-use", "editorial.voir"), listOf("ppf-not-before", "-1"), listOf("ppf-not-after", "${start + day}"), listOf("ppf-key-status", "active")),
        )
        for (tags in cases) {
            val certificate = assertNotNull(KeyCertificate.read(rawCertificate(tags), rootHex), "$tags")
            assertFalse(certificate.validAt(start), "$tags")
            val trust = PlatformTrust.of(rootHex, listOf(rawCertificate(tags)))
            assertEquals(TrustVerdict.REVOKED, trust.check(content(start), SigningUse.EDITORIAL_VOIR), "$tags")
        }
        // A newer malformed version revokes a key that an older certificate made valid.
        val trust = PlatformTrust.of(rootHex, listOf(certificate(), rawCertificate(validity, createdAt = start + day)))
        assertEquals(TrustVerdict.REVOKED, trust.check(content(start + 2), SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun unknownUsesAreIgnoredAndKnownOnesKept() {
        val tags = listOf(
            listOf("ppf-key-use", "usage.futur"),
            listOf("ppf-key-use", "archives.timestamps"),
            listOf("ppf-not-before", "$start"),
            listOf("ppf-not-after", "${start + day}"),
            listOf("ppf-key-status", "active"),
        )
        val certificate = assertNotNull(KeyCertificate.read(rawCertificate(tags), rootHex))
        assertEquals(setOf(SigningUse.ARCHIVES_TIMESTAMPS), certificate.uses)
        assertTrue(certificate.covers(SigningUse.ARCHIVES_TIMESTAMPS, start))
    }

    @Test
    fun aCertificateIsReadBackAsWritten() {
        val event = certificate(uses = listOf(SigningUse.OPENDATA_INDEX, SigningUse.DECIDER_PACTE))
        val read = assertNotNull(KeyCertificate.read(event, rootHex))
        assertEquals(working.publicKey.hex, read.key)
        assertEquals(setOf(SigningUse.OPENDATA_INDEX, SigningUse.DECIDER_PACTE), read.uses)
        assertEquals(start, read.notBefore)
        assertEquals(start + 90 * day, read.notAfter)
        assertEquals(KeyStatus.ACTIVE, read.status)
        assertTrue(read.wellFormed)
        assertEquals(KeyCertificate.ALT, event.firstTagValue("alt"))
        assertFailsWith<IllegalArgumentException> {
            KeyCertificate.unsigned(rootHex, working.publicKey.hex, listOf(SigningUse.EDITORIAL_VOIR), start, start + 91 * day, start)
        }
    }

    @Test
    fun theKeyVerdictSaysWhatTheServiceMayDo() {
        val trust = PlatformTrust.of(rootHex, listOf(certificate()))
        assertEquals(TrustVerdict.TRUSTED, trust.keyVerdict(working.publicKey.hex, SigningUse.EDITORIAL_VOIR, start))
        assertEquals(TrustVerdict.ROOT_NEVER_SIGNS_CONTENTS, trust.keyVerdict(rootHex, SigningUse.EDITORIAL_VOIR, start))
        assertEquals(1, trust.certificates.size)
    }

    @Test
    fun anOlderRevocationReceivedAfterANewerActiveCertificateStillRevokes() {
        val revocation = certificate(createdAt = start, status = KeyStatus.REVOKED, revokedFrom = start + 5 * day)
        val renewed = certificate(createdAt = start + 20 * day)
        for (order in listOf(listOf(revocation, renewed), listOf(renewed, revocation))) {
            val trust = PlatformTrust.of(rootHex, order)
            assertTrue(trust.isPlatformContent(content(start + 5 * day - 1), SigningUse.EDITORIAL_VOIR), "$order")
            assertEquals(TrustVerdict.REVOKED, trust.check(content(start + 5 * day), SigningUse.EDITORIAL_VOIR), "$order")
            assertEquals(renewed.id, trust.certificate(working.publicKey.hex)?.event?.id, "the latest version still gives the validity")
            assertEquals(revocation.id, trust.revocation(working.publicKey.hex)?.event?.id)
            assertEquals(setOf(renewed.id, revocation.id), trust.events.map { it.id }.toSet())
            // Rebuilt from its own events, the trust is the same.
            val rebuilt = PlatformTrust.of(rootHex, trust.events.reversed())
            assertEquals(TrustVerdict.REVOKED, rebuilt.check(content(start + 5 * day), SigningUse.EDITORIAL_VOIR))
        }
        // Offered after the newer certificate, the older revocation changes the trust: it must be kept.
        val (_, outcome) = PlatformTrust.of(rootHex, listOf(renewed)).offer(revocation)
        assertEquals(CertificateOffer.STORED, outcome)
    }

    @Test
    fun theEarliestRevocationWinsInEveryOrderAndALaterActiveCertificateNeverUndoesIt() {
        val first = certificate(createdAt = start, status = KeyStatus.REVOKED, revokedFrom = start + 10 * day)
        val earliest = certificate(createdAt = start + 30 * day, status = KeyStatus.REVOKED, revokedFrom = start + 2 * day)
        val later = certificate(createdAt = start + 31 * day, status = KeyStatus.REVOKED, revokedFrom = start + 20 * day)
        val active = certificate(createdAt = start + 40 * day)
        for (order in permutations(listOf(first, earliest, later, active))) {
            val trust = PlatformTrust.of(rootHex, order)
            assertTrue(trust.isPlatformContent(content(start + 2 * day - 1), SigningUse.EDITORIAL_VOIR), "$order")
            assertEquals(TrustVerdict.REVOKED, trust.check(content(start + 2 * day), SigningUse.EDITORIAL_VOIR), "$order")
            assertEquals(earliest.id, trust.revocation(working.publicKey.hex)?.event?.id, "$order")
            assertEquals(active.id, trust.certificate(working.publicKey.hex)?.event?.id, "$order")
        }
        // A revocation without date revokes from the start, before or after a newer active certificate.
        val noDate = certificate(createdAt = start + 1, status = KeyStatus.REVOKED)
        for (order in listOf(listOf(noDate, active), listOf(active, noDate))) {
            assertEquals(TrustVerdict.REVOKED, PlatformTrust.of(rootHex, order).check(content(start), SigningUse.EDITORIAL_VOIR), "$order")
        }
        // A certificate that cannot be read counts as a revocation from the start, for good.
        val unreadable = rawCertificate(listOf(listOf("ppf-key-use", "editorial.voir"), listOf("ppf-key-status", "suspendue")), createdAt = start)
        for (order in listOf(listOf(unreadable, active), listOf(active, unreadable))) {
            assertEquals(TrustVerdict.REVOKED, PlatformTrust.of(rootHex, order).check(content(start + day), SigningUse.EDITORIAL_VOIR), "$order")
        }
    }

    @Test
    fun aKeyCertifiedForOneUseNeverValidatesTheFormOfAnotherUseOfTheSameKind() {
        val trust = PlatformTrust.of(rootHex, listOf(certificate()))
        val organisation = signWith(
            working,
            UnsignedEvent(working.publicKey.hex, start + day, EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("title", "[EXEMPLE]"), listOf("ppf-type", "organisation")), ""),
        )
        assertEquals(TrustVerdict.FORM_OF_ANOTHER_USE, trust.check(organisation, SigningUse.EDITORIAL_VOIR))
        val pacte = signWith(
            working,
            UnsignedEvent(working.publicKey.hex, start + day, EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("ppf-type", "pacte")), ""),
        )
        assertEquals(TrustVerdict.FORM_OF_ANOTHER_USE, trust.check(pacte, SigningUse.EDITORIAL_VOIR))
        // A key of the open data index never validates a closing statement of a ballot box (same kind 30078).
        val index = IdentityKeys.generate()
        val indexTrust = PlatformTrust.of(rootHex, listOf(certificate(key = index, uses = listOf(SigningUse.OPENDATA_INDEX))))
        val closing = signWith(index, UnsignedEvent(index.publicKey.hex, start + day, EventKinds.APP_DATA, listOf(listOf("d", "ppf:urne-cloture:3")), "{}"))
        assertEquals(TrustVerdict.FORM_OF_ANOTHER_USE, indexTrust.check(closing, SigningUse.OPENDATA_INDEX))
        assertEquals(TrustVerdict.NOT_CERTIFIED_FOR_USE, indexTrust.check(closing, SigningUse.DECIDER_URN_CLOSURE))
    }

    @Test
    fun eachUseTellsItsFormApartFromTheOthers() {
        val samples = mapOf(
            SigningUse.EDITORIAL_COMPRENDRE to (EventKinds.LONG_FORM_ARTICLE to listOf(listOf("ppf-type", "fiche"))),
            SigningUse.EDITORIAL_VOIR to (EventKinds.LONG_FORM_ARTICLE to listOf(listOf("ppf-type", "fait"))),
            SigningUse.AGIR_ORGANISATIONS to (EventKinds.LONG_FORM_ARTICLE to listOf(listOf("ppf-type", "organisation"))),
            SigningUse.DECIDER_PACTE to (EventKinds.LONG_FORM_ARTICLE to listOf(listOf("ppf-type", "engagement"))),
            SigningUse.REVENDICATIONS_STATUS to (EventKinds.LABEL to listOf(listOf("L", "ppf-status"))),
            SigningUse.ARCHIVES_TIMESTAMPS to (EventKinds.OPENTIMESTAMPS to listOf(listOf("e", "a".repeat(64)))),
            SigningUse.OPENDATA_INDEX to (EventKinds.APP_DATA to listOf(listOf("d", "ppf:parlement-index"))),
            SigningUse.DECIDER_URN_CLOSURE to (EventKinds.APP_DATA to listOf(listOf("d", "ppf:urne-cloture:12"))),
        )
        val editorial = setOf(SigningUse.EDITORIAL_COMPRENDRE, SigningUse.EDITORIAL_VOIR)
        for ((sampleUse, sample) in samples) {
            for (use in SigningUse.entries) {
                // Only the two editorial spaces share a form: their keys alone tell them apart.
                val expected = use == sampleUse || (use in editorial && sampleUse in editorial)
                assertEquals(expected, use.matches(sample.first, sample.second), "$sampleUse as $use")
            }
        }
        assertFalse(SigningUse.AGIR_ORGANISATIONS.matches(EventKinds.LONG_FORM_ARTICLE, listOf(listOf("ppf-type", "organisation"), listOf("ppf-type", "organisation"))))
        assertFalse(SigningUse.DECIDER_PACTE.matches(EventKinds.LONG_FORM_ARTICLE, emptyList()))
        assertFalse(SigningUse.EDITORIAL_VOIR.matches(EventKinds.LONG_FORM_ARTICLE, listOf(listOf("ppf-type", "fait"), listOf("ppf-type", "organisation"))))
        assertTrue(SigningUse.EDITORIAL_VOIR.matches(EventKinds.LONG_FORM_ARTICLE, emptyList()))
        assertFalse(SigningUse.REVENDICATIONS_STATUS.matches(EventKinds.LABEL, listOf(listOf("L", "autre"))))
        assertFalse(SigningUse.DECIDER_URN_CLOSURE.matches(EventKinds.APP_DATA, listOf(listOf("d", "ppf:urne-cloture:1"), listOf("d", "ppf:urne-cloture:2"))))
        assertFalse(SigningUse.OPENDATA_INDEX.matches(EventKinds.TEXT_NOTE, listOf(listOf("d", "ppf:parlement-index"))))
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items) else items.flatMap { item -> permutations(items - item).map { listOf(item) + it } }

    @Test
    fun theApprovalChallengeSeparatesUsesAndEvents() {
        val id = "a".repeat(64)
        assertEquals("6191c603b63bb16d1acb87d97834ad0872576eac06bb6027bd13929c38674f9a", ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, id).toHexString())
        assertFalse(ApprovalChallenge.of(SigningUse.EDITORIAL_COMPRENDRE, id).contentEquals(ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, id)))
        assertFalse(ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, "b".repeat(64)).contentEquals(ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, id)))
        assertFailsWith<IllegalArgumentException> { ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, "A".repeat(64)) }
    }
}
