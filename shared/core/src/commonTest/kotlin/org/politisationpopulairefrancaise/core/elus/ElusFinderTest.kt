package org.politisationpopulairefrancaise.core.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The officials of a commune, level by level, in the order of the contract (1.6). */
class ElusFinderTest {

    private val rhone = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.rhone)))
    private val finistere = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.finistere)))
    private val france = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.france)))

    private fun names(level: LevelOfficials): List<String> = level.groups.flatMap { group -> group.officials.map { it.displayName } }

    @Test
    fun theMayorComesFirstThenTheDeputiesInOrderThenTheCouncil() {
        val lyon = assertNotNull(ElusFinder.find(rhone, "69123"))
        val council = lyon.level(DecisionLevel.COMMUNE).groups.single()
        assertEquals(OfficialGroupKind.MUNICIPAL_COUNCIL, council.kind)
        assertEquals(Territory("69123", "Lyon"), council.territory)
        // The package lists them out of order; the members keep the order of the package.
        assertEquals(listOf("Noa Maire", "Lou Premier", "Sacha Second", "Alix Conseil", "Eden Conseil"), council.officials.map { it.displayName })
    }

    @Test
    fun aCommuneSplitBetweenTwoConstituenciesHasBothMembersOfTheAssemblee() {
        val lyon = assertNotNull(ElusFinder.find(rhone, "69123"))
        val state = lyon.level(DecisionLevel.ETAT)
        val constituencies = state.groups.filter { it.kind == OfficialGroupKind.CIRCONSCRIPTION }
        assertEquals(listOf("1re circonscription du Rhône", "2e circonscription du Rhône"), constituencies.map { it.territory?.name })
        assertEquals(listOf("Elie Premiere", "Remi Deuxieme"), constituencies.flatMap { group -> group.officials.map { it.displayName } })
        // Then the members of the Sénat of the department.
        val senate = state.groups.last()
        assertEquals(OfficialGroupKind.SENATE, senate.kind)
        assertEquals(listOf("Kim Senat", "Nour Senat"), senate.officials.map { it.displayName })
        assertEquals(emptyList(), state.notes)
    }

    @Test
    fun aCommuneWithoutIntercommunalitySaysSo() {
        val sein = assertNotNull(ElusFinder.find(finistere, "29083"))
        val level = sein.level(DecisionLevel.INTERCOMMUNALITE)
        assertEquals(emptyList(), level.groups)
        assertEquals(listOf(LevelNote.NO_INTERCOMMUNALITE), level.notes)
        // The other levels are still found.
        assertEquals(listOf("Morgan Sein"), names(sein.level(DecisionLevel.COMMUNE)))
        assertEquals(listOf("Erwan Canton"), names(sein.level(DecisionLevel.DEPARTEMENT)))
        assertEquals(listOf("Gwen Depute", "Loane Senat"), names(sein.level(DecisionLevel.ETAT)))
    }

    @Test
    fun theIntercommunalityShowsItsPresidentThenTheRepresentativesOfTheCommune() {
        val lyon = assertNotNull(ElusFinder.find(rhone, "69123"))
        val group = lyon.level(DecisionLevel.INTERCOMMUNALITE).groups.single()
        assertEquals("Métropole de Lyon", group.territory?.name)
        assertEquals(listOf("Ange Metropole", "Yael Lyon"), group.officials.map { it.displayName })
    }

    @Test
    fun theDepartmentShowsTheCantonThenThePresident() {
        val villefranche = assertNotNull(ElusFinder.find(rhone, "69264"))
        val department = villefranche.level(DecisionLevel.DEPARTEMENT)
        assertEquals(listOf(OfficialGroupKind.CANTON, OfficialGroupKind.DEPARTMENT_PRESIDENCY), department.groups.map { it.kind })
        assertEquals("Villefranche-sur-Saône", department.groups[0].territory?.name)
        // The president of the council comes first in the canton too.
        assertEquals(listOf("Gabi Departement", "Ariel Canton"), department.groups[0].officials.map { it.displayName })
        assertEquals(listOf("Gabi Departement"), department.groups[1].officials.map { it.displayName })

        // Lyon has no canton while other communes of the package have one: no councillor is shown,
        // only the president of the departmental council.
        val lyon = assertNotNull(ElusFinder.find(rhone, "69123")).level(DecisionLevel.DEPARTEMENT)
        assertEquals(listOf(OfficialGroupKind.DEPARTMENT_PRESIDENCY), lyon.groups.map { it.kind })
        assertEquals(emptyList(), lyon.notes)
    }

    @Test
    fun theRegionShowsItsPresidentThenTheVicePresidentsThenTheMembers() {
        val region = assertNotNull(ElusFinder.find(rhone, "69123")).level(DecisionLevel.REGION)
        assertEquals(listOf(OfficialGroupKind.REGION_PRESIDENCY, OfficialGroupKind.REGIONAL_COUNCIL), region.groups.map { it.kind })
        assertEquals(listOf("Sasha Presidence"), region.groups[0].officials.map { it.displayName })
        assertEquals(listOf("Mael Vice", "Lou Region"), region.groups[1].officials.map { it.displayName })
        assertEquals("Auvergne-Rhône-Alpes", region.groups[0].territory?.name)
    }

    @Test
    fun theEuropeanParliamentNeedsTheNationalPackage() {
        val without = assertNotNull(ElusFinder.find(rhone, "69123")).level(DecisionLevel.UNION_EUROPEENNE)
        assertEquals(listOf(LevelNote.NATIONAL_PACKAGE_MISSING), without.notes)
        val with = assertNotNull(ElusFinder.find(rhone, "69123", france))
        assertEquals(listOf("Ana Europe", "Teo Europe"), names(with.level(DecisionLevel.UNION_EUROPEENNE)))
        assertEquals(DecisionLevel.entries, with.levels.map { it.level })
        assertTrue(with.example)
        // Council 5, intercommunality 2, presidency of the department 1, region 3, State 4, Europe 2.
        assertEquals(5 + 2 + 1 + 3 + 4 + 2, with.count)
    }

    @Test
    fun withoutTableOfConstituenciesEveryConstituencyOfTheDepartmentIsListed() {
        val communes = ElusFixtures.rhone["communes"]!!.jsonArray.map { JsonObject(it.jsonObject + mapOf("circonscriptions" to JsonArray(emptyList()), "cantons" to JsonArray(emptyList()))) }
        val pkg = assertNotNull(ElusReader.readPackage(JsonObject(ElusFixtures.rhone + mapOf("communes" to JsonArray(communes))).toString()))
        val lyon = assertNotNull(ElusFinder.find(pkg, "69123"))
        val state = lyon.level(DecisionLevel.ETAT)
        assertEquals(listOf(LevelNote.ALL_CIRCONSCRIPTIONS_OF_DEPARTMENT), state.notes)
        assertEquals(listOf("6901", "6902", "6907", "6909"), state.groups.filter { it.kind == OfficialGroupKind.CIRCONSCRIPTION }.map { it.territory?.code })
        val department = lyon.level(DecisionLevel.DEPARTEMENT)
        assertEquals(listOf(LevelNote.ALL_CANTONS_OF_DEPARTMENT), department.notes)
        assertEquals("Villefranche-sur-Saône", department.groups.first().territory?.name)
    }

    @Test
    fun withoutTheCommuneRepresentedEveryMemberOfTheIntercommunalityIsListed() {
        val officials = ElusFixtures.rhone["officials"]!!.jsonArray.map { element ->
            val official = element.jsonObject
            if (official["mandate"] == JsonPrimitive("communautaire")) JsonObject(official - "commune") else official
        }
        val pkg = assertNotNull(ElusReader.readPackage(JsonObject(ElusFixtures.rhone + mapOf("officials" to JsonArray(officials))).toString()))
        val level = assertNotNull(ElusFinder.find(pkg, "69123")).level(DecisionLevel.INTERCOMMUNALITE)
        assertEquals(listOf(LevelNote.ALL_MEMBERS_OF_INTERCOMMUNALITE), level.notes)
        assertEquals(listOf("Ange Metropole", "Yael Lyon", "Maxime Neuville"), names(level))
    }

    @Test
    fun anUnknownCommuneHasNoOfficials() {
        assertNull(ElusFinder.find(rhone, "75056"))
    }

    @Test
    fun functionsAreRecognisedWhateverTheirSpelling() {
        assertTrue(ElusFinder.isPresident("Présidente du conseil régional"))
        assertTrue(ElusFinder.isPresident("Président"))
        assertTrue(!ElusFinder.isPresident("1er vice-président"))
        assertTrue(!ElusFinder.isPresident(null))
        fun official(function: String?) = ElectedOfficial(Mandate.MUNICIPAL, "69123", null, function ?: "membre", function, null, null)
        val ordered = ElusFinder.ordered(listOf(official(null), official("12e adjoint"), official("Conseiller délégué"), official("2ème adjointe"), official("Premier adjoint"), official("Maire")))
        assertEquals(listOf("Maire", "Premier adjoint", "2ème adjointe", "12e adjoint", "Conseiller délégué", null), ordered.map { it.function })
    }

    @Test
    fun aCommuneOfTheMetropoleDeLyonHasItsAssemblyNotTheDepartmentalCouncil() {
        val communes = ElusFixtures.rhone["communes"]!!.jsonArray.map { commune ->
            val fields = commune.jsonObject.toMutableMap()
            if (fields["code"] == JsonPrimitive("69123")) fields["collectivite"] = JsonPrimitive("69M")
            JsonObject(fields)
        }
        val metropole = ElusFixtures.official("collectivite", "69M", "Dany Metropole", function = "Président du conseil de la Métropole")
        val fields = ElusFixtures.rhone.toMutableMap()
        fields["communes"] = JsonArray(communes)
        fields["officials"] = JsonArray(ElusFixtures.rhone["officials"]!!.jsonArray + metropole)
        fields["collectivites"] = JsonArray(listOf(JsonObject(mapOf("code" to JsonPrimitive("69M"), "name" to JsonPrimitive("Métropole de Lyon")))))
        val pkg = assertNotNull(ElusReader.readPackage(ElusFixtures.text(JsonObject(fields))))

        val lyon = assertNotNull(ElusFinder.find(pkg, "69123")).level(DecisionLevel.DEPARTEMENT)
        assertEquals(listOf(OfficialGroupKind.COLLECTIVITE), lyon.groups.map { it.kind })
        assertEquals("Métropole de Lyon", lyon.groups.single().territory?.name)
        assertEquals(listOf("Dany Metropole"), names(lyon))

        // Villefranche keeps its canton and the president of the departmental council, never the Métropole.
        val villefranche = assertNotNull(ElusFinder.find(pkg, "69264")).level(DecisionLevel.DEPARTEMENT)
        assertTrue(villefranche.groups.none { it.kind == OfficialGroupKind.COLLECTIVITE })
        assertTrue("Ariel Canton" in names(villefranche))
    }
}
