package org.politisationpopulairefrancaise.core.groups

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailboxRetentionTest {

    private val signer = LocalEventSigner(IdentityKeys.generate())

    @Test
    fun theSettingIsANip78EventReadBackAsAChoice() = runTest {
        val unsigned = MailboxRetention.build(signer.publicKey, MailboxRetention.SIX_MONTHS, createdAt = 1_700_000_000)
        assertEquals(30078, unsigned.kind)
        assertEquals(listOf(listOf("d", "ppf:mailbox"), listOf("retention_days", "180")), unsigned.tags)
        assertEquals("", unsigned.content)
        assertEquals(MailboxRetention.SIX_MONTHS, MailboxRetention.from(signer.sign(unsigned)))
        assertEquals(MailboxRetention.THREE_MONTHS, MailboxRetention.from(signer.sign(MailboxRetention.build(signer.publicKey, MailboxRetention.THREE_MONTHS, 1))))
        assertEquals(MailboxRetention.ONE_YEAR, MailboxRetention.from(signer.sign(MailboxRetention.build(signer.publicKey, MailboxRetention.ONE_YEAR, 1))))
    }

    @Test
    fun otherClientsValuesAreBoundedAndRounded() = runTest {
        suspend fun setting(days: String) = signer.sign(kind = EventKinds.APP_DATA, tags = listOf(listOf("d", "ppf:mailbox"), listOf("retention_days", days)))
        assertEquals(MailboxRetention.THREE_MONTHS, MailboxRetention.from(setting("30")))
        assertEquals(MailboxRetention.THREE_MONTHS, MailboxRetention.from(setting("120")))
        assertEquals(MailboxRetention.SIX_MONTHS, MailboxRetention.from(setting("200")))
        assertEquals(MailboxRetention.ONE_YEAR, MailboxRetention.from(setting("1000")))
        assertNull(MailboxRetention.from(setting("beaucoup")))
        assertNull(MailboxRetention.from(signer.sign(kind = EventKinds.APP_DATA, tags = listOf(listOf("d", "autre"), listOf("retention_days", "90")))))
        assertNull(MailboxRetention.from(signer.sign(kind = EventKinds.TEXT_NOTE, tags = listOf(listOf("d", "ppf:mailbox"), listOf("retention_days", "90")))))
    }
}
