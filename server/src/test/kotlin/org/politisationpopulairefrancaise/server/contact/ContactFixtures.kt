package org.politisationpopulairefrancaise.server.contact

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.contact.CaseCode
import org.politisationpopulairefrancaise.core.contact.CaseCreation
import org.politisationpopulairefrancaise.core.contact.CaseEnvelopes
import org.politisationpopulairefrancaise.core.contact.CaseMessage
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseNumber
import org.politisationpopulairefrancaise.core.contact.CaseSecrets
import org.politisationpopulairefrancaise.core.contact.ContactInbox
import org.politisationpopulairefrancaise.core.contact.ContestedDecision
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.TestDatabase
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

/** Cases for the tests: the example inbox (its secret half is public), little proof of work, no periodic task. */
object ContactFixtures {
    /** Enough to check that the work is asked, little enough for fast tests. */
    const val WORK: Int = 6

    val inbox = LocalMessagingSigner(ContactInbox.exampleKeys())

    fun config(workBits: Int = WORK): ContactConfig = ContactConfig(ContactInbox.EXAMPLE, workBits = workBits, retentionInterval = Duration.ZERO)

    fun space(clock: Clock, config: ContactConfig = config()): ContactSpace = ContactSpace(ContactStore(TestDatabase.dataSource), config, clock)

    /** A case as the app will prepare it (step 2): number, code, secrets, opening envelope signed by the key of the case. */
    class Draft(
        val nature: CaseNature,
        val now: Instant,
        val number: CaseNumber = CaseNumber.generate(),
        val code: CaseCode = CaseCode.generate(),
        val decisionAt: Long? = null,
        val work: Int = WORK,
    ) {
        val secrets: CaseSecrets = CaseSecrets.derive(number, code)
        val opening: CaseMessage = CaseMessage.opening(
            number,
            nature,
            "[EXEMPLE] Objet du dossier",
            "[EXEMPLE] Texte du dossier, que le serveur ne lit jamais.",
            decision = decisionAt?.let { ContestedDecision("decision-exemple", it) },
        )
        val envelope: SignedEvent = runBlocking { CaseEnvelopes.opening(opening, secrets.signer(), ContactInbox.EXAMPLE, work, now.epochSecond) }

        fun creation(envelope: SignedEvent = this.envelope): CaseCreation = CaseCreation.of(secrets, nature, envelope, decisionAt)

        fun followUp(text: String = "[EXEMPLE] Une précision.", at: Instant = now): SignedEvent =
            runBlocking { CaseEnvelopes.followUp(CaseMessage.followUp(number, text), secrets.signer(), ContactInbox.EXAMPLE, at.epochSecond) }

        /** A reply of the team for the key of the case. */
        fun reply(text: String = "[EXEMPLE] Réponse de l’équipe.", at: Instant = now): SignedEvent =
            runBlocking { CaseEnvelopes.fromTeam(CaseMessage.reply(number, text), inbox, secrets.publicKey, at.epochSecond) }
    }
}
