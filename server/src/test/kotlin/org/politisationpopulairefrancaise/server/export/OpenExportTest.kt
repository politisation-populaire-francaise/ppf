package org.politisationpopulairefrancaise.server.export

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import org.politisationpopulairefrancaise.server.voir.VoirDatasets
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The open export (M34, S-48) on the real PostgreSQL of the tests, from the example contents. */
class OpenExportTest {
    private val events = EventRepository(TestDatabase.dataSource)
    private val examples = BundledContents.FILES.flatMap(BundledContents::load)
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR, team.publicKey.hex))
    private val datasets = OpenDatasets().also(VoirDatasets::registerOn)

    private fun article(signer: LocalEventSigner, slug: String, title: String, updatedAt: Long): SignedEvent = runBlocking {
        signer.sign(Article.build(signer.publicKey, slug, title, "[EXEMPLE] Résumé.", emptyList(), ArticleType.ARTICLE, ContentOrigin.HUMAN, 1_793_000_000, updatedAt, "[EXEMPLE] Texte."))
    }

    private lateinit var older: SignedEvent
    private lateinit var newer: SignedEvent
    private lateinit var strangers: SignedEvent

    @BeforeTest
    fun storeTheExamplesTwoVersionsAndAStrangersContent() {
        TestDatabase.reset()
        examples.forEach(events::store)
        older = article(team, "exemple-versions", "[EXEMPLE] Première version", 1_793_000_000)
        newer = article(team, "exemple-versions", "[EXEMPLE] Version corrigée", 1_794_000_000)
        strangers = article(stranger, "exemple-etranger", "[EXEMPLE] Contenu d'un compte citoyen", 1_794_000_000)
        listOf(older, newer, strangers).forEach(events::store)
    }

    private fun export(policy: PublicationPolicy = this.policy) = OpenExport(events, policy, datasets, ttl = Duration.ZERO)

    @Test
    fun onlyTheLatestVersionOfTheTeamsContentsIsExportedAndEveryOneIsAuthentic() = runBlocking {
        val build = export().current()
        val contents = Json.parseToJsonElement(build.files.getValue("contenus.json").bytes.decodeToString()).jsonArray
            .map { SignedEvent.fromJson(it.toString()) }
        assertTrue(contents.isNotEmpty())
        assertTrue(contents.all { it.verify() }, "every exported event keeps its signature")
        assertTrue(contents.all { policy.isEditor(it.pubkey) }, "only the editorial keys")
        assertTrue(contents.none { it.id == strangers.id }, "a citizen's content is never gathered in the export")
        assertTrue(contents.any { it.id == newer.id })
        assertFalse(contents.any { it.id == older.id }, "only the latest version of an address")
        val addresses = contents.map { "${it.kind}:${it.pubkey}:${it.firstTagValue("d")}" }
        assertEquals(addresses.toSet().size, addresses.size, "one version per address")
        assertEquals(contents.sortedWith(compareBy({ it.kind }, { it.pubkey }, { it.firstTagValue("d") })), contents, "sorted by address")
        assertEquals(contents.maxOf { it.createdAt }, build.catalog.updatedAt)
        // A key removed from the team stops being exported, even though its contents stay stored.
        assertEquals("[]", export(PublicationPolicy(emptySet())).current().files.getValue("contenus.json").bytes.decodeToString())
    }

    @Test
    fun theCatalogDescribesEveryFileWithItsFingerprintAndThePendingLicence() = runBlocking {
        val build = export().current()
        val catalog = build.catalog
        assertEquals(1, catalog.format)
        assertEquals(ExportLicense.CHOSEN, catalog.license)
        assertEquals("CC-BY-4.0", catalog.datasets.first { it.id == "contenus" }.license?.id)
        assertTrue(catalog.datasets.filter { it.id != "contenus" }.all { it.license?.id == "etalab-2.0" }, "the tables are data: Licence Ouverte 2.0")
        assertEquals(policy.editorKeys().sorted(), catalog.editorialKeys)
        assertEquals(listOf("contenus", "voir-mesures", "voir-partis-aides", "voir-partis-sieges"), catalog.datasets.map { it.id })
        catalog.datasets.flatMap { it.files }.forEach { file ->
            val served = build.files.getValue(file.url.removePrefix("${OpenExport.PATH}/"))
            assertEquals(sha256Hex(served.bytes), file.sha256, file.url)
            assertEquals(served.bytes.size, file.bytes, file.url)
        }
        val catalogJson = Json.parseToJsonElement(build.catalogFile.bytes.decodeToString()).jsonObject
        assertEquals(JsonPrimitive(1), catalogJson["format"])
        assertEquals(JsonPrimitive("choisie"), catalogJson["license"]!!.jsonObject["status"])
        // The same contents give the same bytes: the ETag only changes when something is published.
        assertTrue(build.catalogFile.bytes.contentEquals(export().current().catalogFile.bytes))
    }

    @Test
    fun everyLineOfTheTablesOfVoirHasItsSourceItsVersionAndFollowsItsSchema() = runBlocking {
        val build = export().current()
        val versions = Json.parseToJsonElement(build.files.getValue("contenus.json").bytes.decodeToString()).jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        VoirDatasets.all.forEach { dataset ->
            val rows = Json.parseToJsonElement(build.files.getValue("${dataset.id}.json").bytes.decodeToString()).jsonArray
            assertTrue(rows.isNotEmpty(), "${dataset.id} has lines from the examples")
            val schema = Json.parseToJsonElement(build.files.getValue("schemas/${dataset.id}-${dataset.version}.schema.json").bytes.decodeToString()).jsonObject
            rows.forEach { row -> conforms(schema, row.jsonObject, dataset.id) }
            rows.forEach { row ->
                assertTrue(row.jsonObject["version"]!!.jsonPrimitive.content in versions, "${dataset.id}: the version is in contenus.json")
                assertTrue(row.jsonObject["content"]!!.jsonPrimitive.content.startsWith("30023:"), dataset.id)
                assertTrue(row.jsonObject["source_url"] != null, "${dataset.id}: a column of source")
            }
            val csv = build.files.getValue("${dataset.id}.csv").bytes.decodeToString()
            val lines = csv.split("\r\n").dropLast(1)
            assertEquals(dataset.columns.joinToString(",") { it.name }, lines.first(), dataset.id)
            assertEquals(rows.size, lines.size - 1, "${dataset.id}: one CSV line per JSON line (no line break in the examples)")
            assertTrue(csv.endsWith("\r\n"))
        }
        // The values of the indicators keep each source apart; the amounts keep their cents.
        val measures = Json.parseToJsonElement(build.files.getValue("voir-mesures.json").bytes.decodeToString()).jsonArray.map { it.jsonObject }
        assertTrue(measures.map { it["source_kind"]!!.jsonPrimitive.content }.toSet().size > 1, "several kinds of sources")
        val funding = Json.parseToJsonElement(build.files.getValue("voir-partis-aides.json").bytes.decodeToString()).jsonArray.map { it.jsonObject }
        val first = funding.first { it["year"] == JsonPrimitive(2026) && it["party_name"]!!.jsonPrimitive.content.contains("Parti d'exemple A") }
        assertEquals("4210345.60", first["amount_eur"]!!.jsonPrimitive.content)
        assertTrue(build.files.getValue("voir-partis-aides.csv").bytes.decodeToString().contains(",2026,4210345.60,"))
    }

    /** A small check of a line against the schema the export writes (types, required, nothing more). */
    private fun conforms(schema: JsonObject, row: JsonObject, dataset: String) {
        val items = schema["items"]!!.jsonObject
        val properties = items["properties"]!!.jsonObject
        val required = items["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(required.toSet(), row.keys, "$dataset: every column, nothing else")
        row.forEach { (name, value) ->
            val type = properties.getValue(name).jsonObject["type"]!!
            val allowed = if (type is JsonArray) type.map { it.jsonPrimitive.content } else listOf(type.jsonPrimitive.content)
            val actual = when {
                value is JsonNull -> "null"
                value is JsonPrimitive && value.isString -> "string"
                value is JsonPrimitive && value.content.toLongOrNull() != null -> "integer"
                value is JsonPrimitive && value.content.toDoubleOrNull() != null -> "number"
                else -> "other"
            }
            assertTrue(actual in allowed || (actual == "integer" && "number" in allowed), "$dataset.$name: $actual not in $allowed")
        }
    }

    @Test
    fun theApiServesTheFilesWithTheirFingerprintAndWithoutAccount() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir()) }
        val catalog = client.get("/api/v1/export")
        assertEquals(HttpStatusCode.OK, catalog.status)
        assertTrue(catalog.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        val etag = catalog.headers[HttpHeaders.ETag]!!
        assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/export") { header(HttpHeaders.IfNoneMatch, etag) }.status)
        assertNull(catalog.headers[HttpHeaders.SetCookie], "no account, no cookie")

        val csv = client.get("/api/v1/export/voir-mesures.csv")
        assertEquals(HttpStatusCode.OK, csv.status)
        assertTrue(csv.headers[HttpHeaders.ContentType]!!.startsWith("text/csv"))
        assertTrue(csv.headers[HttpHeaders.ContentDisposition]!!.contains("ppf-voir-mesures.csv"))
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/export/contenus.json").status)
        val schema = client.get("/api/v1/export/schemas/voir-mesures-1.schema.json")
        assertEquals(HttpStatusCode.OK, schema.status)
        assertTrue(schema.headers[HttpHeaders.ContentType]!!.startsWith("application/schema+json"))
        listOf("/api/v1/export/inconnu.csv", "/api/v1/export/schemas/inconnu-1.schema.json", "/api/v1/export/..%2Fcontenus.json").forEach { path ->
            val missing = client.get(path)
            assertEquals(HttpStatusCode.NotFound, missing.status, path)
        }

        // The page of the site lists the same datasets, without script, with their licences (D-103).
        val page = client.get("/donnees")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        listOf("Données ouvertes", "Chiffres de l’observatoire", "Creative Commons Attribution 4.0", "Licence Ouverte 2.0", "/api/v1/export/voir-mesures.csv", "Tableur (CSV)", "restent à leurs auteurs").forEach {
            assertTrue(html.contains(it), it)
        }
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("style=\""))
        assertTrue(client.get("/").bodyAsText().contains("href=\"/donnees\""), "linked from the footer of every page")
    }
}
