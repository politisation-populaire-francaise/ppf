package org.politisationpopulairefrancaise.server.voir

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The contents of "Voir" read from PostgreSQL for the site: the editorial keys only, checked at reading time. */
class EditorialVoirSourceTest {
    private val events = EventRepository(TestDatabase.dataSource)
    private val examples = BundledContents.load(BundledContents.VOIR_FILE)
    private val stranger = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun storeTheExamplesAndAStrangersFact() {
        TestDatabase.reset()
        examples.forEach(events::store)
        val unsigned = Article.build(
            stranger.publicKey, "faux-fait", "[EXEMPLE] Faux fait", null, emptyList(), ArticleType.FAIT, ContentOrigin.HUMAN,
            1_793_000_000, 1_794_000_000, "Corps.",
            extraTags = listOf(listOf(VoirTags.FACT, "mobilisation"), listOf(VoirTags.DATE, "2026-09-18"), listOf(VoirTags.FACT_STATUS, "etabli")),
        )
        events.store(runBlocking { stranger.sign(unsigned) })
    }

    @Test
    fun onlyTheEditorialKeysAreRead() = runBlocking {
        val catalog = EditorialVoirSource(events, PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR)), ttl = Duration.ZERO).catalog()
        assertEquals(examples.size, catalog.indicators.size + catalog.facts.size + catalog.persons.size + catalog.texts.size + catalog.movements.size + catalog.dossiers.size + catalog.replies.size)
        assertFalse(catalog.facts.any { it.address.identifier == "faux-fait" })
        assertNotNull(catalog.indicators.firstOrNull { it.address.identifier == "manifestants-journees" })
        // A key removed from the team stops being shown, even though its contents stay stored.
        assertTrue(EditorialVoirSource(events, PublicationPolicy(emptySet()), ttl = Duration.ZERO).catalog().isEmpty)
    }

    @Test
    fun theSiteReadsTheDatabaseAndHidesTheOthers() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR)), TEST_RELAY, testRevendications(), testAgir()) }
        val shown = client.get("/voir/faits/affaire-dominique-marche-public")
        assertEquals(HttpStatusCode.OK, shown.status)
        assertTrue(shown.bodyAsText().contains("Présomption d’innocence"))
        val hidden = client.get("/voir/faits/faux-fait")
        assertEquals(HttpStatusCode.NotFound, hidden.status)
        assertTrue(hidden.bodyAsText().contains("Contenu introuvable"))
        assertFalse(client.get("/voir/chronologie").bodyAsText().contains("Faux fait"))
    }
}
