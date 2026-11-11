package org.politisationpopulairefrancaise.core.agir.collectives

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The format of the collectifs (M24, D-087): tags, validation, reading, immutability and the search. */
class CollectiveTest {

    private val founder = LocalEventSigner(IdentityKeys.generate())

    private fun description(
        id: String = "collectif-sante-lyon",
        name: String = "[EXEMPLE] Collectif santé",
        territory: String = "Lyon",
        scope: CollectiveScope = CollectiveScope.LOCAL,
        type: CollectiveType = CollectiveType.COLLECTIF,
        themes: List<String> = listOf("Santé", "hôpital public"),
        territoryCode: String? = "com:69123",
        charter: String? = "[EXEMPLE] Nous décidons ensemble, en assemblée.",
    ): UnsignedEvent = CollectiveFormat.build(
        founder.publicKey, id, name, "[EXEMPLE] Pour des urgences ouvertes la nuit.", territory, createdAt = 1_700_000_000,
        scope = scope, type = type, themes = themes, territoryCode = territoryCode, internalCharter = charter,
    )

    private fun List<List<String>>.without(name: String) = filterNot { it.firstOrNull() == name }

    private fun List<List<String>>.replacing(name: String, value: String) = without(name) + listOf(listOf(name, value))

    @Test
    fun theTagsFollowTheRoomInTheOrderOfTheFormat() {
        val unsigned = description()
        assertEquals(Group.KIND, unsigned.kind)
        val tags = unsigned.tags
        // The room first, exactly as Group.build writes it.
        assertEquals(listOf("d", "collectif-sante-lyon"), tags[0])
        assertEquals(listOf(Group.TAG_CHARTER, Group.CHARTER_VERSION), tags.first { it[0] == Group.TAG_CHARTER })
        val own = tags.dropWhile { it[0] != CollectiveFormat.TAG_COLLECTIVE }
        assertEquals(
            listOf(
                listOf("ppf-collective", "1"),
                listOf("ppf-scope", "local"),
                listOf("ppf-collective-type", "collectif"),
                listOf("t", "santé"),
                listOf("t", "hôpital public"),
                listOf("l", "fr"),
                listOf("ppf-territory-code", "com:69123"),
                listOf("ppf-internal-charter", "[EXEMPLE] Nous décidons ensemble, en assemblée."),
                listOf("ppf-visibility", "public"),
                listOf("ppf-join", "open"),
            ),
            own,
        )
        assertNull(CollectiveFormat.problem(tags))
        assertTrue(CollectiveFormat.isCollective(tags))
    }

    @Test
    fun buildingRefusesWhatTheServerWouldRefuse() {
        assertFailsWith<IllegalArgumentException> { description(themes = (1..6).map { "thème $it" }) }
        assertFailsWith<IllegalArgumentException> { description(themes = listOf("a".repeat(41))) }
        assertFailsWith<IllegalArgumentException> { description(territoryCode = "quartier de la Guillotière") }
        assertFailsWith<IllegalArgumentException> { description(charter = "x".repeat(3001)) }
        assertFailsWith<IllegalArgumentException> {
            CollectiveFormat.tags(CollectiveScope.LOCAL, CollectiveType.CLUB, language = "fra")
        }
        // Repeated themes and the case of a code are tidied, not refused.
        val tidy = CollectiveFormat.tags(CollectiveScope.QUARTIER, CollectiveType.CLUB, themes = listOf(" #Logement ", "logement", "  Loyers   chers "), territoryCode = "COM:2a004")
        assertEquals(listOf("logement", "loyers chers"), tidy.filter { it[0] == "t" }.map { it[1] })
        assertEquals(listOf("ppf-territory-code", "com:2A004"), tidy.first { it[0] == "ppf-territory-code" })
        // A blank internal charter is simply absent.
        assertTrue(CollectiveFormat.tags(CollectiveScope.NATIONAL, CollectiveType.RESEAU, internalCharter = "  ").none { it[0] == "ppf-internal-charter" })
    }

    @Test
    fun eachFaultHasItsOwnCode() {
        val good = description().tags
        assertNull(CollectiveFormat.problem(good))
        val cases = mapOf(
            good.replacing("ppf-collective", "2") to CollectiveProblem.BAD_MARKER,
            good + listOf(listOf("ppf-collective", "1")) to CollectiveProblem.BAD_MARKER,
            good.without("ppf-scope") to CollectiveProblem.BAD_SCOPE,
            good.replacing("ppf-scope", "planetaire") to CollectiveProblem.BAD_SCOPE,
            good.without("ppf-collective-type") to CollectiveProblem.BAD_TYPE,
            good.replacing("ppf-collective-type", "parti") to CollectiveProblem.BAD_TYPE,
            good.without("t") + (1..6).map { listOf("t", "theme$it") } to CollectiveProblem.TOO_MANY_THEMES,
            good + listOf(listOf("t", "Santé")) to CollectiveProblem.BAD_THEME,
            good + listOf(listOf("t", "santé")) to CollectiveProblem.BAD_THEME,
            good + listOf(listOf("t", "x".repeat(41))) to CollectiveProblem.BAD_THEME,
            good.replacing("l", "FR") to CollectiveProblem.BAD_LANGUAGE,
            good + listOf(listOf("l", "en")) to CollectiveProblem.BAD_LANGUAGE,
            good.replacing("ppf-territory-code", "com:2a004") to CollectiveProblem.BAD_TERRITORY_CODE,
            good.replacing("ppf-territory-code", "lyon") to CollectiveProblem.BAD_TERRITORY_CODE,
            good.replacing("ppf-internal-charter", " ") to CollectiveProblem.BAD_INTERNAL_CHARTER,
            good.replacing("ppf-internal-charter", "x".repeat(3001)) to CollectiveProblem.CHARTER_TOO_LONG,
            good.replacing("ppf-visibility", "secret") to CollectiveProblem.VISIBILITY_UNSUPPORTED,
            good.replacing("ppf-visibility", "prive") to CollectiveProblem.VISIBILITY_UNSUPPORTED,
            good.replacing("ppf-join", "request") to CollectiveProblem.JOIN_UNSUPPORTED,
            good.replacing("ppf-join", "invitation") to CollectiveProblem.JOIN_UNSUPPORTED,
        )
        for ((tags, expected) in cases) assertEquals(expected, CollectiveFormat.problem(tags), tags.toString())
        assertEquals("collective_bad_scope", CollectiveProblem.BAD_SCOPE.code)
        assertEquals("collective_visibility_unsupported", CollectiveProblem.VISIBILITY_UNSUPPORTED.code)
        // Absent visibility, way of joining, language and code are public, open, French and none.
        val minimal = good.without("ppf-visibility").without("ppf-join").without("l").without("ppf-territory-code").without("t").without("ppf-internal-charter")
        assertNull(CollectiveFormat.problem(minimal))
        // A plain room is not a collectif: none of these rules apply.
        assertNull(CollectiveFormat.problem(good.filterNot { it[0].startsWith("ppf-collective") }))
    }

    @Test
    fun aCollectiveIsReadBackFromItsSignedRoom() = runTest {
        val event: SignedEvent = founder.sign(description())
        val collective = assertNotNull(Collective.from(event))
        assertEquals("collectif-sante-lyon", collective.id)
        assertEquals("Collectif santé", collective.displayName)
        assertTrue(collective.example)
        assertEquals(CollectiveScope.LOCAL, collective.scope)
        assertEquals(CollectiveType.COLLECTIF, collective.type)
        assertEquals(listOf("santé", "hôpital public"), collective.themes)
        assertEquals("fr", collective.language)
        assertEquals("com:69123", collective.territoryCode)
        assertEquals("Lyon", collective.territory)
        assertEquals("[EXEMPLE] Nous décidons ensemble, en assemblée.", collective.internalCharter)
        assertEquals(founder.publicKey.hex, collective.founder)
        assertTrue(collective.isFounder(founder.publicKey.hex))
        assertFalse(collective.isFounder(null))
        assertTrue(collective.listed)
        // The same room read as a room keeps working.
        assertEquals("collectif-sante-lyon", Group.from(event)?.id)

        // A plain room is not a collectif.
        val room = founder.sign(Group.build(founder.publicKey, "salon-lyon", "[EXEMPLE] Salon", null, "Lyon", createdAt = 1))
        assertNull(Collective.from(room))

        // Unknown values of a newer version are read tolerantly.
        val newer = founder.sign(description().let { it.copy(tags = it.tags.replacing("ppf-scope", "continental").replacing("ppf-collective-type", "federation").replacing("l", "xyz")) })
        val tolerant = assertNotNull(Collective.from(newer))
        assertNull(tolerant.scope)
        assertEquals(CollectiveType.AUTRE, tolerant.type)
        assertEquals("fr", tolerant.language)
        val secret = founder.sign(description().let { it.copy(tags = it.tags.replacing("ppf-visibility", "secret")) })
        assertFalse(assertNotNull(Collective.from(secret)).listed)
    }

    @Test
    fun whatMakesTheCollectiveNeverChanges() {
        val first = description().tags
        assertNull(CollectiveFormat.immutableChange(first, first))
        // The name, the presentation, the type, the themes, the language and the internal charter may change.
        val edited = description(name = "[EXEMPLE] Collectif santé du Rhône", type = CollectiveType.COMITE, themes = listOf("urgences"), charter = null).tags
        assertNull(CollectiveFormat.immutableChange(first, edited))
        assertEquals("ppf-scope", CollectiveFormat.immutableChange(first, description(scope = CollectiveScope.DEPARTEMENTAL).tags))
        assertEquals("ppf-territory-code", CollectiveFormat.immutableChange(first, description(territoryCode = "dep:69").tags))
        assertEquals("ppf-territory-code", CollectiveFormat.immutableChange(first, description(territoryCode = null).tags))
        assertEquals("ppf-territory", CollectiveFormat.immutableChange(first, description(territory = "Villeurbanne").tags))
        assertEquals("ppf-visibility", CollectiveFormat.immutableChange(first, first.replacing("ppf-visibility", "secret")))
        assertEquals("ppf-join", CollectiveFormat.immutableChange(first, first.replacing("ppf-join", "invitation")))
        // Absent and explicit public / open are the same.
        assertNull(CollectiveFormat.immutableChange(first.without("ppf-visibility").without("ppf-join"), first))
        // A plain room does not become a collectif by a new version, nor does a collectif become a plain room.
        val room = first.filterNot { it[0].startsWith("ppf-") && it[0] != Group.TAG_TERRITORY && it[0] != Group.TAG_CHARTER } + listOf(listOf("ppf-territory-code", "com:69123"))
        assertEquals("ppf-collective", CollectiveFormat.immutableChange(room, first))
        assertEquals("ppf-collective", CollectiveFormat.immutableChange(first, room))
        // Two plain rooms: nothing to compare here (the rules of the rooms apply).
        assertNull(CollectiveFormat.immutableChange(room, room.replacing(Group.TAG_TERRITORY, "Paris")))
    }

    @Test
    fun aMobilizationOrACallNamesItsCollective() = runTest {
        val tags = listOf(listOf("d", "reunion-1"), listOf(AgirFormats.TAG_TITLE, "[EXEMPLE] Réunion publique"))
        val unsigned = UnsignedEvent(founder.publicKey.hex, 1_700_000_000, AgirFormats.KIND_MOBILIZATION, tags)
        val tagged = CollectiveFormat.withCollective(unsigned, "collectif-sante-lyon")
        assertEquals(listOf("ppf-collective", "collectif-sante-lyon"), tagged.tags.last())
        // Set again, the tag is replaced, never doubled.
        val again = CollectiveFormat.withCollective(tagged, "autre-collectif")
        assertEquals(1, again.tags.count { it[0] == "ppf-collective" })
        assertEquals("autre-collectif", CollectiveFormat.collectiveOf(founder.sign(again)))
        assertNull(CollectiveFormat.collectiveOf(founder.sign(unsigned)))
        assertFailsWith<IllegalArgumentException> { CollectiveFormat.withCollective(unsigned, "Pas Valide") }

        assertFalse(CollectiveFormat.badReference(tags))
        assertFalse(CollectiveFormat.badReference(tagged.tags))
        assertTrue(CollectiveFormat.badReference(tags + listOf(listOf("ppf-collective", "Pas Valide"))))
        assertTrue(CollectiveFormat.badReference(tagged.tags + listOf(listOf("ppf-collective", "deuxieme"))))
        assertTrue(CollectiveFormat.badReference(tags + listOf(listOf("ppf-collective"))))
    }

    @Test
    fun theSearchFiltersOnTheDeviceAndPutsTheNearestFirst() = runTest {
        suspend fun collective(id: String, name: String, scope: CollectiveScope, type: CollectiveType, themes: List<String>, code: String?, territory: String = "Lyon") =
            assertNotNull(Collective.from(founder.sign(description(id = id, name = name, territory = territory, scope = scope, type = type, themes = themes, territoryCode = code))))

        val sante = collective("sante-lyon", "[EXEMPLE] Collectif santé", CollectiveScope.LOCAL, CollectiveType.COLLECTIF, listOf("santé"), "com:69123")
        val quartier = collective("guillotiere", "[EXEMPLE] Assemblée de la Guillotière", CollectiveScope.QUARTIER, CollectiveType.ASSEMBLEE, listOf("logement", "santé"), "arm:69387")
        val rhone = collective("rhone-velo", "[EXEMPLE] Réseau vélo du Rhône", CollectiveScope.DEPARTEMENTAL, CollectiveType.RESEAU, listOf("transports"), "dep:69")
        val national = collective("france-ecoles", "[EXEMPLE] Club des écoles", CollectiveScope.NATIONAL, CollectiveType.CLUB, listOf("éducation"), "fr", territory = "France")
        val paris = collective("paris-sante", "[EXEMPLE] Comité santé de Paris", CollectiveScope.LOCAL, CollectiveType.COMITE, listOf("santé"), "com:75056", territory = "Paris")
        val all = listOf(paris, national, rhone, quartier, sante)

        // Without home: by name.
        assertEquals(listOf("guillotiere", "france-ecoles", "sante-lyon", "paris-sante", "rhone-velo"), CollectiveQuery().apply(all).map { it.id })
        assertEquals(listOf("guillotiere", "sante-lyon", "paris-sante"), CollectiveQuery(theme = "Santé").apply(all).map { it.id })
        assertEquals(listOf("paris-sante"), CollectiveQuery(type = CollectiveType.COMITE).apply(all).map { it.id })
        assertEquals(listOf("guillotiere"), CollectiveQuery(scope = CollectiveScope.QUARTIER).apply(all).map { it.id })
        assertEquals(listOf("rhone-velo"), CollectiveQuery(text = "velo").apply(all).map { it.id })
        assertEquals(listOf("guillotiere"), CollectiveQuery(text = "logement").apply(all).map { it.id }, "themes are searched")
        assertEquals(listOf("paris-sante"), CollectiveQuery(text = "Paris").apply(all).map { it.id }, "territories are searched")

        // A home in the 7th district of Lyon: the district, then Lyon, then the Rhône; France is never near.
        val home = CollectiveQuery.home(
            TerritoryCode.parse("arm:69387"),
            listOfNotNull(TerritoryCode.parse("com:69123"), TerritoryCode.parse("epci:200046977"), TerritoryCode.parse("dep:69"), TerritoryCode.FRANCE),
        )
        assertEquals(listOf("arm:69387", "com:69123", "epci:200046977", "dep:69"), home)
        assertEquals(listOf("guillotiere", "sante-lyon", "rhone-velo", "france-ecoles", "paris-sante"), CollectiveQuery().apply(all, home).map { it.id })
        assertEquals(listOf("guillotiere", "sante-lyon", "rhone-velo"), CollectiveQuery(nearMe = true).apply(all, home).map { it.id })
        assertEquals(0, CollectiveQuery.nearRank(quartier, home))
        assertNull(CollectiveQuery.nearRank(national, home))
        assertNull(CollectiveQuery.nearRank(paris, home))
        // "Near me" without a known home filters nothing.
        assertEquals(5, CollectiveQuery(nearMe = true).apply(all).size)

        assertEquals(listOf("santé", "logement", "transports", "éducation"), CollectiveQuery.themesOf(all))
        assertTrue(CollectiveQuery(theme = "santé").isFiltered)
        assertFalse(CollectiveQuery().isFiltered)
    }
}
