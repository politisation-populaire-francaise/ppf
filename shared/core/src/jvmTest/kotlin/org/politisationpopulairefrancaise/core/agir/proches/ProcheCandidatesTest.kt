package org.politisationpopulairefrancaise.core.agir.proches

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What « Mes proches à mobiliser » (AG-63) may suggest, read from the local copy of the contents only:
 * the guides and courses of the editorial team, the mobilizations to come, the open calls; never a
 * content of an unknown editor, a cancelled or past event, a closed call, the person's own proposals,
 * nor anything still waiting for the server. Every content is an [EXEMPLE] of the tests.
 */
class ProcheCandidatesTest {

    private val now = 1_800_000_000L
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())
    private val organiser = LocalEventSigner(IdentityKeys.generate())
    private val me = LocalEventSigner(IdentityKeys.generate())
    private val trust = EditorialTrust(setOf(editor.publicKey.hex))

    private suspend fun article(signer: LocalEventSigner, slug: String, title: String, type: ArticleType, topics: List<String>): SignedEvent =
        signer.sign(
            Article.build(
                author = signer.publicKey,
                slug = slug,
                title = title,
                summary = "[EXEMPLE] Un résumé.",
                topics = topics,
                type = type,
                origin = ContentOrigin.HUMAN,
                publishedAt = now - 86_400,
                updatedAt = now - 86_400,
                body = "[EXEMPLE] Le texte.",
            ),
        )

    private suspend fun mobilization(signer: LocalEventSigner, slug: String, title: String, start: Long, status: String? = null): SignedEvent {
        val tags = buildList {
            add(listOf("d", slug))
            add(listOf(AgirFormats.TAG_TITLE, title))
            add(listOf(AgirFormats.TAG_START, start.toString()))
            add(listOf(AgirFormats.TAG_LOCATION, "[EXEMPLE] Place de la Mairie"))
            add(listOf(AgirFormats.TAG_TERRITORY, "Lille"))
            if (status != null) add(listOf(AgirFormats.TAG_EVENT_STATUS, status))
        }
        return signer.sign(UnsignedEvent(signer.publicKey.hex, now - 3_600, Mobilization.KIND, tags, "[EXEMPLE] Une description."))
    }

    private suspend fun call(signer: LocalEventSigner, slug: String, title: String, status: String? = null, deadline: Long? = null): SignedEvent {
        val tags = buildList {
            add(listOf("d", slug))
            add(listOf(AgirFormats.TAG_TITLE, title))
            add(listOf(AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL))
            add(listOf(AgirFormats.TAG_CALL_TYPE, "se-reunir"))
            add(listOf(AgirFormats.TAG_TERRITORY, "Lille"))
            if (status != null) add(listOf(AgirFormats.TAG_CALL_STATUS, status))
            if (deadline != null) add(listOf(AgirFormats.TAG_DEADLINE, deadline.toString()))
        }
        return signer.sign(UnsignedEvent(signer.publicKey.hex, now - 3_600, Call.KIND, tags, "[EXEMPLE] Le texte de l'appel."))
    }

    @Test
    fun onlyWhatCanBeSharedIsSuggested() = runTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        store.store(article(editor, "faire-greve-vos-droits", "[EXEMPLE] Faire grève : vos droits", ArticleType.FICHE_PRATIQUE, listOf("droits", "greve", "travail")))
        store.store(article(stranger, "fausse-fiche", "[EXEMPLE] Une fiche d'un inconnu", ArticleType.FICHE_PRATIQUE, listOf("travail")))
        store.store(article(editor, "le-droit-de-greve", "[EXEMPLE] Le droit de grève", ArticleType.PARCOURS, listOf("travail")))
        store.store(mobilization(organiser, "marche-hopital", "[EXEMPLE] Marche pour l'hôpital public", now + 86_400))
        store.store(mobilization(organiser, "deja-passee", "[EXEMPLE] Rassemblement passé", now - 10 * 86_400))
        store.store(mobilization(organiser, "annulee", "[EXEMPLE] Rassemblement annulé", now + 86_400, status = "annule"))
        store.store(mobilization(me, "la-mienne", "[EXEMPLE] Ma réunion en relecture", now + 86_400))
        store.store(mobilization(organiser, "en-attente", "[EXEMPLE] Pas encore acceptée", now + 86_400), pending = true)
        store.store(call(organiser, "loyers", "[EXEMPLE] Assemblée sur la hausse des loyers"))
        store.store(call(organiser, "clos", "[EXEMPLE] Un appel clos", status = "clos"))
        store.store(call(organiser, "echu", "[EXEMPLE] Un appel échu", deadline = now - 60))

        val found = ProcheCandidates.fromDevice(store, trust, now, account = me.publicKey.hex)
        val bySlug = found.associateBy { it.key.substringAfterLast(':') }
        assertEquals(setOf("faire-greve-vos-droits", "le-droit-de-greve", "marche-hopital", "loyers"), bySlug.keys)

        val fiche = bySlug.getValue("faire-greve-vos-droits")
        assertEquals(SuggestionKind.FICHE, fiche.kind)
        assertEquals("Faire grève : vos droits", fiche.title)
        assertTrue(fiche.example)
        assertEquals("https://politisation-populaire-francaise.org/comprendre/fiches/faire-greve-vos-droits", fiche.url)
        assertEquals("https://politisation-populaire-francaise.org/comprendre/parcours/le-droit-de-greve", bySlug.getValue("le-droit-de-greve").url)

        val march = bySlug.getValue("marche-hopital")
        assertEquals(SuggestionKind.MOBILIZATION, march.kind)
        assertEquals(now + 86_400, march.at)
        assertEquals("https://politisation-populaire-francaise.org/agir/agenda/${organiser.publicKey.hex}/marche-hopital", march.url)
        assertEquals("https://politisation-populaire-francaise.org/agir/appels/${organiser.publicKey.hex}/loyers", bySlug.getValue("loyers").url)

        // No address carries anything that could tell who shares it.
        found.forEach { assertFalse('?' in it.url || '#' in it.url || me.publicKey.hex in it.url, it.url) }

        // A relative interested in health is offered the march, one interested in housing the call.
        val health = ProcheSuggestions.forEntry(Proche("1", "[EXEMPLE] A", listOf("sante"), ProcheStep.INVITE.id), found)
        assertEquals(listOf("marche-hopital"), health.map { it.key.substringAfterLast(':') })
        val housing = ProcheSuggestions.forEntry(Proche("2", "[EXEMPLE] B", listOf("logement")), found)
        assertEquals(listOf("loyers"), housing.map { it.key.substringAfterLast(':') })
    }

    @Test
    fun anEmptyDeviceSuggestsNothing() {
        assertTrue(ProcheCandidates.fromDevice(LocalEventStore(JvmDatabase.inMemory()), trust, now).isEmpty())
    }
}
