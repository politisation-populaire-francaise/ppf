package org.politisationpopulairefrancaise.core.agir.followups

import org.politisationpopulairefrancaise.core.agir.AgirSchemas
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FactStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AG-62 « Et après ? »: the schema of a follow-up accepts a valid [EXEMPLE] follow-up and refuses each fault
 * (unknown field, no source, source without address, unknown kind, unknown status, no mobilization), the links
 * are full addresses read back with their roles, and the reader keeps only what it can show.
 */
class FollowUpSchemaTest {
    private val schema = FollowUpSchema.SUITE
    private val author = IdentityKeys.generate().publicKey

    /** The organiser of the mobilization and the author of the call: other keys than the editorial one. */
    private val organiser = "a".repeat(64)
    private val citizen = "b".repeat(64)

    private val mobilization = "31923:$organiser:projection-debat-rennes-2026-11-03"
    private fun content(slug: String, key: String = author.hex) = "30023:$key:$slug"

    private val context = CheckContext.of(
        author.hex,
        mapOf(
            "camille-exemple" to ArticleType.PERSONNE,
            "transparence-agenda-elus" to ArticleType.REVENDICATION,
            "chrono-49-3-ordre-public" to ArticleType.FAIT,
            "droits-en-manifestation" to ArticleType.FICHE_PRATIQUE,
        ),
    )

    private val body = "[EXEMPLE] Suite fictive de démonstration.\n"

    private val valid: List<Pair<String, String>> = listOf(
        "slug" to "suite-test-reponse",
        "title" to "[EXEMPLE] Une députée fictive répond aux questions de la salle",
        "summary" to "Elle répond par une lettre publique.",
        "type" to "suite",
        "origin" to "ai-assisted",
        "published_at" to "2026-11-06",
        "updated_at" to "2026-11-09",
        "mobilization" to mobilization,
        "suite-kind" to "reponse-elu",
        "date" to "2026-11-06",
        "fact-status" to "etabli",
        "source" to "declaration | Lettre publique de la députée (exemple) | https://example.org/exemple/suites/lettre | 2026-11-06",
        "person" to content("camille-exemple"),
        "demand" to content("transparence-agenda-elus"),
        "call" to content("appel-exemple", citizen),
        "chronology" to content("chrono-49-3-ordre-public"),
    )

    private fun draft(entries: List<Pair<String, String>>) = ContentDraft(ArticleType.SUITE, entries, body)

    private fun check(entries: List<Pair<String, String>>): List<Problem> = EditorialEngine.check(schema, draft(entries), context)

    private fun codes(entries: List<Pair<String, String>>): List<String> = check(entries).map { it.code }

    private fun List<Pair<String, String>>.with(key: String, value: String) = map { if (it.first == key) key to value else it }

    private fun List<Pair<String, String>>.without(key: String) = filter { it.first != key }

    private fun build(entries: List<Pair<String, String>> = valid): UnsignedEvent = EditorialEngine.build(schema, draft(entries), author)

    /** The event as the readers see it, without a signature (they do not check it). */
    private fun readable(unsigned: UnsignedEvent) =
        SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))

    @Test
    fun theTypeHasItsSchemaInTheSpaceOfAgir() {
        assertEquals(ArticleType.SUITE, ArticleType.fromTag("suite"))
        assertEquals(schema, EditorialSchemas.of(ArticleType.SUITE))
        assertEquals(listOf(schema), EditorialSchemas.of(EditorialSpace.AGIR))
        assertEquals(listOf(schema), AgirSchemas.all)
        assertEquals("suites", schema.sourceDirectory)
        assertEquals("suites.json", schema.bundle)
    }

    @Test
    fun aValidFollowUpPasses() {
        assertEquals(emptyList(), check(valid).map { EditorialMessages.french(it) })
        // The other kinds, statuses and an announcement without any person or link pass too.
        val announcement = valid.without("person").without("demand").without("call").without("chronology")
            .with("suite-kind", "annonce").with("fact-status", "allegue")
        assertEquals(emptyList(), codes(announcement))
        for (kind in FollowUpKind.entries) assertEquals(emptyList(), codes(valid.with("suite-kind", kind.tagValue)), kind.tagValue)
        for (status in FactStatus.entries) assertEquals(emptyList(), codes(valid.with("fact-status", status.tagValue)), status.tagValue)
        // A follow-up may follow several mobilizations and give several sources, a date of source being optional.
        val several = valid + listOf(
            "mobilization" to "31923:$organiser:rassemblement-services-publics-paris-2026-11-14",
            "source" to "medias | Article d'un média local (exemple) | https://example.org/exemple/suites/media",
        )
        assertEquals(emptyList(), codes(several))
        assertEquals(emptyList(), codes(valid + ("correction" to "2026-11-10 | Date de la lettre corrigée | correction")))
    }

    @Test
    fun theLinksAreFullAddressesWithTheirRoles() {
        val tags = build().tags
        assertTrue(listOf("ppf-type", "suite") in tags)
        assertTrue(listOf("ppf-suite-kind", "reponse-elu") in tags)
        assertTrue(listOf("ppf-date", "2026-11-06") in tags)
        assertTrue(listOf("ppf-fact-status", "etabli") in tags)
        assertTrue(listOf("ppf-source", "declaration", "Lettre publique de la députée (exemple)", "https://example.org/exemple/suites/lettre", "2026-11-06") in tags)
        val links = tags.filter { it[0] == "a" }
        assertEquals(
            listOf(
                listOf("a", mobilization, "", "mobilisation"),
                listOf("a", content("camille-exemple"), "", "personne"),
                listOf("a", content("transparence-agenda-elus"), "", "revendication"),
                listOf("a", content("appel-exemple", citizen), "", "appel"),
                listOf("a", content("chrono-49-3-ordre-public"), "", "chronologie"),
            ),
            links,
        )
    }

    @Test
    fun aPublishedFollowUpReadsBackAsTheSameDraft() {
        val unsigned = build(valid + ("correction" to "2026-11-10 | Date de la lettre corrigée | correction"))
        assertEquals(unsigned, EditorialEngine.build(schema, EditorialEngine.draftOf(schema, readable(unsigned), context), author))
    }

    @Test
    fun theReaderGivesWhatTheSectionShows() {
        val followUp = assertNotNull(FollowUp.parse(readable(build())))
        assertEquals(FollowUpKind.ELECTED_REPLY, followUp.kind)
        assertEquals(CivilDate(2026, 11, 6), followUp.date)
        assertEquals(FactStatus.ESTABLISHED, followUp.status)
        assertEquals(listOf(ContentAddress.parse(mobilization)), followUp.mobilizations)
        assertTrue(followUp.follows(ContentAddress.parse(mobilization)))
        assertEquals("https://example.org/exemple/suites/lettre", followUp.sources.single().url)
        assertEquals(listOf(ContentAddress.parse(content("camille-exemple"))), followUp.persons)
        assertEquals(listOf(ContentAddress.parse(content("appel-exemple", citizen))), followUp.linksOf(FollowUpLinkKind.CALL))
        assertEquals(
            listOf(FollowUpLinkKind.PERSON, FollowUpLinkKind.DEMAND, FollowUpLinkKind.CALL, FollowUpLinkKind.CHRONOLOGY),
            followUp.links.map { it.kind },
        )
        assertTrue(followUp.example)
        assertEquals("Une députée fictive répond aux questions de la salle", followUp.displayTitle)
        assertEquals("[EXEMPLE] Suite fictive de démonstration.", followUp.text)
    }

    @Test
    fun anUnknownFieldIsRefused() {
        val problems = check(valid + ("montant" to "1000"))
        assertEquals(listOf(Codes.FIELD_UNKNOWN), problems.map { it.code })
        assertEquals("montant", problems.single().field)
    }

    @Test
    fun aFollowUpWithoutSourceIsRefused() {
        val problems = check(valid.without("source"))
        assertEquals(listOf(Codes.FIELD_TOO_FEW), problems.map { it.code })
        assertEquals("source", problems.single().field)
    }

    @Test
    fun aSourceWithoutAddressIsRefused() {
        for (source in listOf("declaration | Lettre publique (exemple) | | 2026-11-06", "declaration | Lettre publique (exemple)")) {
            val problems = check(valid.with("source", source))
            assertEquals(listOf(Codes.VALUE_POSITION_REQUIRED), problems.map { it.code }, source)
            assertEquals("url", problems.single().position)
        }
        // Only https addresses, never a page sent in clear text.
        assertEquals(listOf(Codes.VALUE_URL), codes(valid.with("source", "declaration | Lettre (exemple) | http://example.org/lettre")))
        // An unknown kind of source is refused too.
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(valid.with("source", "rumeur | Lettre (exemple) | https://example.org/lettre")))
    }

    @Test
    fun anUnknownKindIsRefused() {
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(valid.with("suite-kind", "victoire")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(valid.without("suite-kind").without("person")))
    }

    @Test
    fun anUnknownStatusIsRefused() {
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(valid.with("fact-status", "probable")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(valid.without("fact-status")))
    }

    @Test
    fun aFollowUpWithoutMobilizationIsRefused() {
        val problems = check(valid.without("mobilization"))
        assertEquals(listOf(Codes.FIELD_TOO_FEW), problems.map { it.code })
        assertEquals("mobilization", problems.single().field)
    }

    @Test
    fun aLinkThatIsNotAMobilizationOfTheAgendaIsRefused() {
        for (value in listOf(content("projection-debat-rennes-2026-11-03"), "projection-debat-rennes-2026-11-03", "31923:$organiser:Pas Valide", "31923:cle:evenement")) {
            assertEquals(listOf(FollowUpSchema.Rules.MOBILIZATION_ADDRESS), codes(valid.with("mobilization", value)), value)
        }
    }

    @Test
    fun aLinkToAContentIsAFullAddressOfTheExpectedType() {
        // A bare slug is not an address: the key of its author is needed.
        assertEquals(listOf(FollowUpSchema.Rules.CONTENT_ADDRESS), codes(valid.with("chronology", "chrono-49-3-ordre-public")))
        assertEquals(listOf(FollowUpSchema.Rules.CONTENT_ADDRESS), codes(valid.with("demand", mobilization)))
        // A content known to the check must have the right type; an unknown one (another key) is accepted.
        assertEquals(listOf(FollowUpSchema.Rules.LINK_WRONG_TYPE), codes(valid.with("demand", content("droits-en-manifestation"))))
        assertEquals(listOf(FollowUpSchema.Rules.LINK_WRONG_TYPE), codes(valid.with("chronology", content("camille-exemple"))))
        assertEquals(emptyList(), codes(valid.with("demand", content("medecin-garde-soir-3f9a", citizen))))
    }

    @Test
    fun anAnswerOfAnElectedOfficialNamesItsPerson() {
        assertEquals(listOf(FollowUpSchema.Rules.REPLY_NEEDS_PERSON), codes(valid.without("person")))
        assertEquals(emptyList(), codes(valid.without("person").with("suite-kind", "annonce")))
    }

    @Test
    fun theSameLinkIsGivenOnce() {
        assertEquals(listOf(FollowUpSchema.Rules.DUPLICATE), codes(valid + ("demand" to content("transparence-agenda-elus"))))
        assertEquals(listOf(FollowUpSchema.Rules.DUPLICATE), codes(valid + ("mobilization" to mobilization)))
    }

    @Test
    fun theReaderRefusesWhatItCannotShow() {
        val good = build()
        fun without(name: String, role: String? = null) =
            readable(good.copy(tags = good.tags.filterNot { it[0] == name && (role == null || it.getOrNull(3) == role) }))
        assertNull(FollowUp.parse(without("ppf-suite-kind")))
        assertNull(FollowUp.parse(without("ppf-date")))
        assertNull(FollowUp.parse(without("ppf-fact-status")))
        assertNull(FollowUp.parse(without("ppf-source")))
        assertNull(FollowUp.parse(without("a", "mobilisation")))
        // Another type of content is not a follow-up.
        assertNull(FollowUp.parse(readable(good.copy(tags = good.tags.map { if (it[0] == "ppf-type") listOf("ppf-type", "fait") else it }))))
        // A source without an address is not kept; with none left, the follow-up is not shown.
        val noAddress = good.copy(tags = good.tags.map { if (it[0] == "ppf-source") listOf("ppf-source", "declaration", "Lettre (exemple)", "", "2026-11-06") else it })
        assertNull(FollowUp.parse(readable(noAddress)))
    }

    @Test
    fun onlyTheFollowUpsOfATrustedEditorialKeyAreShown() {
        val keys = IdentityKeys.generate()
        val unsigned = EditorialEngine.build(schema, draft(valid), keys.publicKey)
        val event = readable(unsigned)
        assertNull(FollowUp.from(event, EditorialTrust.bundledExamples(), checkSignature = false))
        assertNotNull(FollowUp.from(event, EditorialTrust.bundledExamples().plus(keys.publicKey.hex), checkSignature = false))
        // A forged signature is refused when it is checked.
        assertNull(FollowUp.from(event, EditorialTrust.bundledExamples().plus(keys.publicKey.hex), checkSignature = true))
    }

    @Test
    fun theFollowUpsOfAMobilizationComeByDateNewestVersionOnly() {
        val target = ContentAddress.parse(mobilization)
        val other = "31923:$organiser:rassemblement-services-publics-paris-2026-11-14"
        fun followUp(slug: String, date: String, updated: String = "2026-11-09", follows: String = mobilization) = assertNotNull(
            FollowUp.parse(readable(build(valid.with("slug", slug).with("date", date).with("updated_at", updated).with("mobilization", follows)))),
        )
        val late = followUp("suite-tardive", "2026-11-08")
        val early = followUp("suite-precoce", "2026-11-04")
        val oldVersion = followUp("suite-milieu", "2026-11-05", updated = "2026-11-06")
        val newVersion = followUp("suite-milieu", "2026-11-06", updated = "2026-11-09")
        val elsewhere = followUp("suite-ailleurs", "2026-11-01", follows = other)
        val shown = FollowUps.of(listOf(late, oldVersion, elsewhere, early, newVersion), target)
        assertEquals(listOf("suite-precoce", "suite-milieu", "suite-tardive"), shown.map { it.address.identifier })
        assertEquals(CivilDate(2026, 11, 6), shown[1].date)
    }

    @Test
    fun everyRuleHasItsFrenchText() {
        FollowUpSchema.Rules.all.forEach { assertNotNull(EditorialMessages.template(it), it) }
    }

    @Test
    fun anAddressRoleNeedsTheTagAAndASingleValue() {
        assertFailsWith<IllegalArgumentException> {
            FieldSpec("x", "l", value = ValueSpec.Single(FieldType.Text()), addressRole = "role")
        }
        assertFailsWith<IllegalArgumentException> {
            FieldSpec("x", "l", value = ValueSpec.Positional(listOf(Position("p", "l", FieldType.Text()))), tag = "a", addressRole = "role")
        }
    }
}
