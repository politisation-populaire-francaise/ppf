package org.politisationpopulairefrancaise.core.contact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaseMessageTest {

    private val number = CaseNumber.fromDisplay("PPF-ABCDE-12345")!!

    @Test
    fun anOpeningGoesThereAndBackAndOmitsWhatIsAbsent() {
        val opening = CaseMessage.opening(
            number,
            CaseNature.Claim(ClaimMotif.MODERATION_DECISION),
            subject = "[EXEMPLE] Mon commentaire a été masqué",
            text = "[EXEMPLE] Je conteste : ce commentaire ne contenait aucune insulte.",
            content = "1:${"a".repeat(64)}",
            decision = ContestedDecision("decision-42", 1_780_000_000),
        )
        val json = opening.toJson()
        assertTrue(json.startsWith("{\"format\":\"ppf-case\",\"version\":1,\"case\":\"PPF-ABCDE-12345\",\"kind\":\"opening\""), json)
        assertTrue(json.contains("\"motif\":\"moderation_decision\""))
        listOf("subtype", "category", "technical", "declared_identity", "motives", "automated", "status", "deadline_at", "null").forEach {
            assertFalse(json.contains(it), "$it is omitted")
        }
        val read = CaseMessage.fromJson(json)
        assertEquals(opening, read)
        assertEquals(CaseNature.Claim(ClaimMotif.MODERATION_DECISION), read.nature)
    }

    @Test
    fun everyKindGoesThereAndBack() {
        listOf(
            CaseMessage.opening(number, CaseNature.Ticket(TicketSubtype.BUG), "[EXEMPLE] La carte ne s’ouvre pas", "[EXEMPLE] Rien ne s’affiche.", technical = "android 14; version 0.4.0"),
            CaseMessage.opening(number, CaseNature.Contact(ContactCategory.ASSOCIATION), "[EXEMPLE] Partenariat", "[EXEMPLE] Bonjour…"),
            // CO-18: the claim as seen, where and when, and the address of a close fact-check.
            CaseMessage.opening(
                number,
                CaseNature.Ticket(TicketSubtype.VERIFICATION),
                "[EXEMPLE] La mairie fermerait toutes les écoles lundi",
                "[EXEMPLE] Vu sur un réseau social dimanche soir, sans source ni signature.",
                content = "30023:${"b".repeat(64)}:verification-exemple",
            ),
            CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.DATA_RIGHTS), "[EXEMPLE] Accès à mes données", "[EXEMPLE] Je demande…", declaredIdentity = "[EXEMPLE] Camille"),
            CaseMessage.followUp(number, "[EXEMPLE] Voici la précision demandée."),
            CaseMessage.reply(number, "[EXEMPLE] Votre commentaire est rétabli.", motives = "[EXEMPLE] Aucune règle de la charte n’était enfreinte.", automated = true),
            CaseMessage.statusChange(number, CaseStatus.IN_PROGRESS, "[EXEMPLE] Délai prolongé de deux mois : demande complexe.", deadlineAt = 1_790_000_000),
        ).forEach { message ->
            assertEquals(message, CaseMessage.fromJson(message.toJson()), message.kind.wire)
        }
    }

    @Test
    fun aReaderRefusesAnotherFormatAnUnknownVersionAndAnythingMalformed() {
        val valid = CaseMessage.followUp(number, "[EXEMPLE] Bonjour").toJson()
        listOf(
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"version\":1,", ""),
            valid.replace("ppf-case", "ppf-voir-request"),
            valid.replace("\"kind\":\"message\"", "\"kind\":\"whisper\""),
            valid.replace("PPF-ABCDE-12345", "PPF-ABCDE-1234"),
            valid.replace("\"text\":\"[EXEMPLE] Bonjour\"", "\"text\":\"  \""),
            valid.replace("\"kind\":\"message\"", "\"kind\":\"message\",\"motif\":\"funds\""),
            "[1,2]",
            "not json",
        ).forEach { json -> assertFailsWith<CaseFormatException>(json) { CaseMessage.fromJson(json) } }
        // Unknown fields of version 1 are ignored.
        assertEquals(CaseMessage.fromJson(valid), CaseMessage.fromJson(valid.replace("{", "{\"later\":true,")))
    }

    @Test
    fun theBoundsOfTheContractAreKept() {
        val claim = CaseNature.Claim(ClaimMotif.OTHER)
        CaseMessage.opening(number, claim, "s".repeat(200), "t".repeat(20_000), technical = "x".repeat(16_000))
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, claim, "s".repeat(201), "t") }
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, claim, "s", "t".repeat(20_001)) }
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, claim, "s", "t", technical = "x".repeat(16_001)) }
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, claim, " ", "t") }
        assertFailsWith<IllegalArgumentException> { CaseMessage.followUp(number, "") }
        // A decision belongs to a contested moderation decision, an identity to a right of reply or to rights on data.
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, claim, "s", "t", decision = ContestedDecision("d", 1)) }
        assertFailsWith<IllegalArgumentException> { CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.FUNDS), "s", "t", declaredIdentity = "[EXEMPLE] Camille") }
        CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.RIGHT_OF_REPLY), "s", "t", declaredIdentity = "[EXEMPLE] Camille")
        // An opening has exactly the qualifier of its type.
        val mixed = CaseMessage(caseNumber = number.display, kind = CaseMessageKind.OPENING, text = "t", subject = "s", type = CaseType.TICKET, subtype = TicketSubtype.BUG, motif = ClaimMotif.FUNDS)
        assertFalse(mixed.isValid)
        assertFailsWith<CaseFormatException> { mixed.toJson() }
        assertFalse(CaseMessage(caseNumber = number.display, kind = CaseMessageKind.STATUS, text = "t").isValid)
        assertFalse(CaseMessage(caseNumber = number.display, kind = CaseMessageKind.REPLY, text = "t", status = CaseStatus.CLOSED).isValid)
    }
}
