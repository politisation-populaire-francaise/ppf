package org.politisationpopulairefrancaise.ui.agir.followups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpLinkKind
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpSchema
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirSchemas
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaClock
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaDevice
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaKit
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaTags
import org.politisationpopulairefrancaise.ui.agir.agenda.TestAgenda
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An editorial key of the tests: it signs follow-ups (and rights of reply) of [EXEMPLE] mobilizations. */
private class FollowUpEditor(val keys: IdentityKeys = IdentityKeys.generate()) {
    private val signer = LocalEventSigner(keys)
    val pubkey: String get() = keys.publicKey.hex

    fun followUp(
        slug: String,
        mobilization: SignedEvent,
        date: String,
        kind: String = "annonce",
        status: String = "etabli",
        more: List<Pair<String, String>> = emptyList(),
    ): SignedEvent {
        val entries = listOf(
            "slug" to slug,
            "title" to "[EXEMPLE] Suite $slug",
            "summary" to "Résumé de la suite $slug.",
            "type" to "suite",
            "origin" to "ai-assisted",
            "published_at" to date,
            "updated_at" to date,
            "mobilization" to checkNotNull(Mobilization.from(mobilization)).address.toString(),
            "suite-kind" to kind,
            "date" to date,
            "fact-status" to status,
            "source" to "medias | Article (exemple) | https://example.org/exemple/$slug | $date",
        ) + more
        return sign(EditorialEngine.build(FollowUpSchema.SUITE, ContentDraft(ArticleType.SUITE, entries, "[EXEMPLE] Texte de démonstration.\n"), keys.publicKey))
    }

    /** A right of reply of « Voir » whose target is the follow-up [slug] of this key. */
    fun reply(slug: String, target: String): SignedEvent {
        val entries = listOf(
            "slug" to slug,
            "title" to "[EXEMPLE] Droit de réponse",
            "type" to "reponse",
            "origin" to "human",
            "published_at" to "2026-11-10",
            "updated_at" to "2026-11-10",
            "respondent" to "[EXEMPLE] Camille Exemple",
            "target" to target,
        )
        return sign(EditorialEngine.build(VoirSchemas.REPONSE, ContentDraft(ArticleType.REPONSE, entries, "[EXEMPLE] Ma réponse.\n"), keys.publicKey))
    }

    fun sign(unsigned: UnsignedEvent): SignedEvent = runBlocking { signer.sign(unsigned) }
}

/**
 * AG-62 « Et après ? » on the detail of a mobilization (Friday 13 November 2026, 10 h in Paris): a past one shows
 * its follow-ups by date with their status in words, a future one shows nothing, a past one without follow-up and
 * an absent bundle show the short sentence; the library imports only authentic follow-ups of trusted keys.
 */
@OptIn(ExperimentalTestApi::class)
class FollowUpsTest {
    private val organiser = AgendaKit()
    private val editor = FollowUpEditor()
    private val trust = EditorialTrust.bundledExamples().plus(editor.pubkey)

    private fun ComposeUiTest.openDetail(device: AgendaDevice, mobilization: SignedEvent, width: Int) {
        var library: AgendaLibrary? = null
        setContent { TestAgenda(device, width, 900, trust = trust, onReady = { agenda, _ -> library = agenda }) }
        val identifier = checkNotNull(Mobilization.from(mobilization)).identifier
        waitUntil(timeoutMillis = 20_000) { library?.mobilizations?.any { it.identifier == identifier } == true }
        val agenda = checkNotNull(library)
        runOnIdle { agenda.open(agenda.mobilizations.first { it.identifier == identifier }) }
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
    }

    @Test
    fun aPastMobilizationShowsItsFollowUpsInDateOrderWithTheStatusInWords() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val past = organiser.mobilization("rassemblement-passe-suites", "[EXEMPLE] Rassemblement passé", start = AgendaClock.at(2026, 11, 1, 10))
        val other = organiser.mobilization("autre-rassemblement-passe", "[EXEMPLE] Autre rassemblement passé", start = AgendaClock.at(2026, 10, 20, 10))
        listOf(
            past,
            other,
            // Stored in the wrong order on purpose.
            editor.followUp("suite-tardive", past, "2026-11-08", status = "allegue"),
            editor.followUp("suite-precoce", past, "2026-11-02", kind = "recul"),
            editor.followUp("suite-ailleurs", other, "2026-10-25"),
            // An untrusted key's follow-up is never shown.
            FollowUpEditor().followUp("suite-non-fiable", past, "2026-11-03"),
        ).forEach { device.store.store(it) }
        openDetail(device, past, width = 1000)

        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(FollowUpTags.ITEM + "suite-tardive").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(FollowUpTags.SECTION).assertExists()
        onNodeWithTag(FollowUpTags.EMPTY).assertDoesNotExist()
        // What happened first comes first.
        val early = onNodeWithTag(FollowUpTags.ITEM + "suite-precoce").fetchSemanticsNode().positionInRoot.y
        val late = onNodeWithTag(FollowUpTags.ITEM + "suite-tardive").fetchSemanticsNode().positionInRoot.y
        assertTrue(early < late, "the follow-ups are listed by date")
        onNodeWithTag(FollowUpTags.ITEM + "suite-ailleurs").assertDoesNotExist()
        onNodeWithTag(FollowUpTags.ITEM + "suite-non-fiable").assertDoesNotExist()
        // The status in words, the kind and the date.
        onNodeWithTag(FollowUpTags.STATUS + "suite-precoce").assertTextContains("Établi")
        onNodeWithTag(FollowUpTags.STATUS + "suite-tardive").assertTextContains("Allégué")
        onNodeWithTag(FollowUpTags.KIND + "suite-precoce").assertTextContains("Recul")
        onNodeWithTag(FollowUpTags.DATE + "suite-precoce").assertTextContains("Le 2 novembre 2026")
    }

    @Test
    fun aSourceOpensOnlyAfterTheWarning() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val past = organiser.mobilization("rassemblement-passe-source", "[EXEMPLE] Rassemblement passé", start = AgendaClock.at(2026, 11, 1, 10))
        listOf(past, editor.followUp("suite-sourcee", past, "2026-11-04")).forEach { device.store.store(it) }
        val opened = mutableListOf<String>()
        var library: AgendaLibrary? = null
        setContent { TestAgenda(device, 360, 740, trust = trust, onOpenLink = { opened += it }, onReady = { agenda, _ -> library = agenda }) }
        waitUntil(timeoutMillis = 20_000) { library?.mobilizations?.any { it.identifier == "rassemblement-passe-source" } == true }
        val agenda = checkNotNull(library)
        runOnIdle { agenda.open(agenda.mobilizations.first { it.identifier == "rassemblement-passe-source" }) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(FollowUpTags.SOURCE + "suite-sourcee-0").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(FollowUpTags.SOURCE + "suite-sourcee-0").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(FollowUpTags.LEAVE_DIALOG).assertExists()
        assertEquals(emptyList(), opened, "nothing opens before the choice")
        onNodeWithTag(FollowUpTags.LEAVE_OPEN).performClick()
        waitForIdle()
        assertEquals(listOf("https://example.org/exemple/suite-sourcee"), opened)
    }

    @Test
    fun aFutureMobilizationShowsNoFollowUp() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val future = organiser.mobilization("rassemblement-a-venir-suites", "[EXEMPLE] Rassemblement à venir", start = AgendaClock.at(2026, 11, 20, 14))
        listOf(future, editor.followUp("suite-trop-tot", future, "2026-11-12")).forEach { device.store.store(it) }
        openDetail(device, future, width = 1000)
        onNodeWithTag(FollowUpTags.SECTION).assertDoesNotExist()
        onNodeWithTag(FollowUpTags.ITEM + "suite-trop-tot").assertDoesNotExist()
    }

    @Test
    fun aPastMobilizationWithoutFollowUpSaysSoOnAPhone() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val past = organiser.mobilization("rassemblement-passe-sans-suite", "[EXEMPLE] Rassemblement sans suite", start = AgendaClock.at(2026, 11, 2, 18))
        device.store.store(past)
        openDetail(device, past, width = 360)
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(FollowUpTags.EMPTY).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(FollowUpTags.EMPTY).assertTextContains("Aucune suite n’est encore connue pour cette mobilisation", substring = true)
    }

    @Test
    fun anAbsentOrUnreadableBundleShowsTheEmptySentence() {
        for (bundle in listOf<ByteArray?>(null, "pas du JSON".encodeToByteArray())) {
            runComposeUiTest {
                val library = FollowUpLibrary(LocalEventStore(JvmDatabase.inMemory()), trust, bundled = { bundle })
                val address = ContentAddress(Mobilization.KIND, organiser.pubkey, "rassemblement-quelconque")
                setContent {
                    PpfTheme(darkTheme = true) {
                        Box(Modifier.requiredSize(360.dp, 740.dp)) { FollowUpsSection(address, library, onOpenLink = {}) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(FollowUpTags.EMPTY).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag(FollowUpTags.EMPTY).assertTextContains("Aucune suite", substring = true)
            }
        }
    }

    @Test
    fun theBundleBringsOnlyAuthenticFollowUpsOfTrustedKeysWithTheirKnownLinksAndReplies() = runBlocking {
        val past = organiser.mobilization("rassemblement-du-paquet", "[EXEMPLE] Rassemblement passé", start = AgendaClock.at(2026, 11, 1, 10))
        val address = checkNotNull(Mobilization.from(past)).address
        // A demand of a citizen on the device, and a fact of the chronology that is not.
        val citizen = IdentityKeys.generate()
        val demand = FollowUpEditor(citizen).sign(
            Article.build(
                author = citizen.publicKey, slug = "demande-exemple", title = "[EXEMPLE] Une revendication", summary = null, topics = emptyList(),
                type = ArticleType.REVENDICATION, origin = ContentOrigin.HUMAN, publishedAt = AgendaClock.NOW, updatedAt = AgendaClock.NOW, body = "[EXEMPLE] Texte.",
            ),
        )
        val links = listOf(
            "person" to "30023:${editor.pubkey}:camille-exemple",
            "demand" to ContentAddress(Article.KIND, citizen.publicKey.hex, "demande-exemple").toString(),
            "chronology" to "30023:${editor.pubkey}:fait-absent",
        )
        val trusted = editor.followUp("suite-du-paquet", past, "2026-11-04", kind = "reponse-elu", more = links)
        val untrusted = FollowUpEditor().followUp("suite-d-une-autre-cle", past, "2026-11-05")
        val forged = editor.followUp("suite-falsifiee", past, "2026-11-06").let { it.copy(content = it.content + "Ajout.") }
        val bundle = Json.encodeToString(ListSerializer(SignedEvent.serializer()), listOf(trusted, untrusted, forged)).encodeToByteArray()

        val store = LocalEventStore(JvmDatabase.inMemory())
        store.store(demand)
        store.store(editor.reply("reponse-a-la-suite", "suite-du-paquet"))
        val entries = FollowUpLibrary(store, trust, bundled = { bundle }).entriesOf(address)
        val entry = entries.single()
        assertEquals("suite-du-paquet", entry.followUp.address.identifier)
        // Only the links whose content is on the device, with their title.
        assertEquals(listOf(FollowUpLinkKind.DEMAND to "[EXEMPLE] Une revendication"), entry.links.map { it.link.kind to it.title })
        assertEquals(listOf("[EXEMPLE] Camille Exemple"), entry.replies.map { it.respondent })

        // Without a database, the trusted follow-ups of the bundle are kept in memory.
        val inMemory = FollowUpLibrary(null, trust, bundled = { bundle }).entriesOf(address)
        assertEquals(listOf("suite-du-paquet"), inMemory.map { it.followUp.address.identifier })
        assertTrue(inMemory.single().links.isEmpty())
    }
}
