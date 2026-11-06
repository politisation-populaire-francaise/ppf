package org.politisationpopulairefrancaise.ui.groups

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.GroupsResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies

/**
 * A server for the rooms that applies the rules of the real one ([InMemoryPublicationPort]), can
 * be switched off, and can leave some rooms out of the full directory (found only by a search), to
 * exercise the server's own duplicate refusal. [published] records what the application sent.
 */
internal class RoomsServer : RemoteEvents {
    private val port = InMemoryPublicationPort()
    var online = true
    val hiddenFromDirectory = mutableSetOf<String>()
    val published = mutableListOf<SignedEvent>()
    val stored: List<SignedEvent> get() = port.events

    /** Puts an event on the server without counting it as sent by the application. */
    fun seed(event: SignedEvent) = runBlocking { check(port.publish(event) is PublicationResult.Accepted) { "seed refused" } }

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!online) return PublicationResult.Unavailable()
        published += event
        return port.publish(event)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult = if (online) port.fetch(filter) else FetchResult.Unavailable()

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult =
        if (online) port.fetchByTags(tags, filter) else FetchResult.Unavailable()

    override suspend fun fetchGroups(query: String?, territory: String?, limit: Int): GroupsResult {
        if (!online) return GroupsResult.Unavailable()
        val result = port.fetchGroups(query, territory, limit)
        if (result !is GroupsResult.Groups || query != null || territory != null) return result
        return GroupsResult.Groups(result.groups.filter { it.id !in hiddenFromDirectory })
    }
}

/** Example rooms and accounts shared by the tests of the rooms; every text is marked [EXEMPLE]. */
internal object RoomsFixtures {
    val phrase: RecoveryPhrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
    val ownKey: String get() = phrase.deriveKeys().publicKey.hex

    /** Another member of the platform, author of the example rooms and messages. */
    val other = LocalEventSigner(IdentityKeys.generate())

    const val SANTE = "collectif-sante-lyon"
    const val LIVREURS = "syndicat-des-livreurs-lyon"
    const val QUARTIER = "assemblee-du-quartier-paris"
    const val FERME = "salon-ferme-marseille"
    const val FIRST_MESSAGE = "[EXEMPLE] Réunion jeudi à 19 h à la maison des associations."
    const val SECOND_MESSAGE = "[EXEMPLE] Pensez à apporter les pétitions signées."

    fun room(id: String, name: String, territory: String, about: String? = null, createdAt: Long = currentEpochSeconds() - 86_400): SignedEvent =
        runBlocking { other.sign(Group.build(other.publicKey, id, name, about, territory, createdAt = createdAt)) }

    fun message(roomId: String, text: String, createdAt: Long): SignedEvent =
        runBlocking { other.sign(GroupMessage.build(other.publicKey, roomId, text, createdAt)) }

    /** Three open rooms (two in Lyon, one in Paris) and two messages in the health collective. */
    fun server(): RoomsServer = RoomsServer().apply {
        val now = currentEpochSeconds()
        seed(room(SANTE, "[EXEMPLE] Collectif santé", "Lyon", about = "[EXEMPLE] Pour des urgences ouvertes la nuit."))
        seed(room(LIVREURS, "[EXEMPLE] Syndicat des livreurs", "Lyon"))
        seed(room(QUARTIER, "[EXEMPLE] Assemblée du quartier", "Paris"))
        seed(message(SANTE, FIRST_MESSAGE, now - 3_600))
        seed(message(SANTE, SECOND_MESSAGE, now - 600))
    }

    fun withAccount(server: RemoteEvents?) = AppDependencies.withDatabase(
        InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase)),
        JvmDatabase.inMemory(),
        remote = server,
    )

    fun withoutAccount(server: RemoteEvents?) = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), remote = server)
}
