package org.politisationpopulairefrancaise.core.contact

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Signaler ce contenu » (S-38, D-234): a case of M30 whose comment only the team reads. */
class ContentReportsTest {
    private val address = "30023:${"a".repeat(64)}:appel-exemple"

    @Test
    fun onlyTheAddressOfAPublishedContentCanBeReported() {
        assertTrue(ContentReports.isContentAddress(address))
        assertTrue(ContentReports.isContentAddress("31923:${"0".repeat(64)}:rassemblement"))
        assertFalse(ContentReports.isContentAddress("30023:${"A".repeat(64)}:appel"))
        assertFalse(ContentReports.isContentAddress("30023:abc:appel"))
        assertFalse(ContentReports.isContentAddress("pas une adresse"))
        assertFalse(ContentReports.isContentAddress("30023:${"a".repeat(64)}:"))
        // A post of the feed or a message of a room: its kind and its id.
        assertTrue(ContentReports.isContentAddress(ContentReports.eventAddress(1, "b".repeat(64))))
        assertFalse(ContentReports.isContentAddress("1:abc"))
    }

    @Test
    fun aReportIsATicketForTheTeamWithItsContentAndReasonInClearAndItsCommentEncrypted() = runTest {
        val prepared = ContentReports.prepare(address, ReportReason.PERSONAL_DATA, "  [EXEMPLE] Mon adresse est publiée.  ", ContactInbox.EXAMPLE, now = 1_794_394_800)
        val creation = prepared.creation
        assertEquals(CaseNature.Ticket(TicketSubtype.REPORT), creation.nature)
        assertEquals(address, creation.reportContent)
        assertEquals("donnees-personnelles", creation.reportReason)
        // Only the inbox of the team opens the envelope: the comment, the address and the reason are inside.
        val opened = CaseEnvelopes.open(creation.envelope, LocalMessagingSigner(ContactInbox.exampleKeys()))
        assertEquals("[EXEMPLE] Mon adresse est publiée.", opened.message.text)
        assertEquals(address, opened.message.content)
        assertEquals("donnees-personnelles", opened.message.subject)
        assertFalse("Mon adresse" in creation.envelope.content, "the comment never travels in clear")
    }

    @Test
    fun withoutACommentTheCaseSaysSoAndAWrongAddressIsRefused() = runTest {
        val prepared = ContentReports.prepare(address, ReportReason.SPAM, "   ", ContactInbox.EXAMPLE, now = 1_794_394_800)
        val opened = CaseEnvelopes.open(prepared.creation.envelope, LocalMessagingSigner(ContactInbox.exampleKeys()))
        assertEquals(ContentReports.NO_COMMENT, opened.message.text)
        assertFailsWith<IllegalArgumentException> { ContentReports.prepare("pas une adresse", ReportReason.SPAM, "", ContactInbox.EXAMPLE) }
    }
}
