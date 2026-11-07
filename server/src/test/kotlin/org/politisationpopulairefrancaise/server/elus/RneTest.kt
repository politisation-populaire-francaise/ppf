package org.politisationpopulairefrancaise.server.elus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading the files of the RNE, and the geography around them. */
class RneTest {
    private fun read(file: ElusFile, text: String, epcis: EpciCollector = EpciCollector()): Pair<List<StagedOfficial>, RneFileResult> {
        val staged = ArrayList<StagedOfficial>()
        val result = Rne.read(file, text.toByteArray(Charsets.UTF_8).inputStream(), epcis) { staged += it }
        return staged to result
    }

    private val municipalHeader = "Code du département;Libellé du département;Code de la collectivité à statut particulier;Libellé de la collectivité à statut particulier;Code de la commune;Libellé de la commune;Nom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Code de la catégorie socio-professionnelle;Libellé de la catégorie socio-professionnelle;Date de début du mandat;Libellé de la fonction;Date de début de la fonction;Code nationalité"

    @Test
    fun onlyTheColumnsOfTheContractAreKept() {
        val (staged, result) = read(
            ElusFile.MUNICIPAL,
            "$municipalHeader\r\n69;Rhône;;;69123;Lyon;Exemplaire;[EXEMPLE] Camille;F;1950-01-01;99;Profession secrète;2026-03-15;1er adjoint au Maire;2026-03-21;FR\r\n",
        )
        assertEquals(RneFileResult(staged = 1, skipped = 0, abroad = 0), result)
        assertEquals(StagedOfficial("69", Mandate.MUNICIPAL, "69123", null, "[EXEMPLE] Camille Exemplaire", "1er adjoint au Maire", 1, "2026-03-15", "2026-03-21"), staged.single())
        val text = staged.single().toString()
        listOf("1950-01-01", "Profession secrète", "99", "FR;", "Code sexe").forEach { assertTrue(it !in text, it) }
    }

    @Test
    fun theColumnsOfPersonalDataAreNeverDeclared() {
        val excluded = listOf("Code sexe", "Date de naissance", "Code de la catégorie socio-professionnelle", "Libellé de la catégorie socio-professionnelle", "Code nationalité")
        for (file in ElusFile.RNE) {
            val columns = Rne.columns(file).map(CsvTable::normalizeHeader)
            excluded.forEach { assertTrue(CsvTable.normalizeHeader(it) !in columns, "${file.fileName} declares $it") }
        }
    }

    @Test
    fun aRecordWithoutDateOrNameIsSkippedAndAMissingColumnRefused() {
        val (staged, result) = read(
            ElusFile.MUNICIPAL,
            "$municipalHeader\n69;Rhône;;;69123;Lyon;;;F;;;;2026-03-15;;;\n69;Rhône;;;69123;Lyon;Nom;[EXEMPLE] A;F;;;;31/02/2026;;;\n69;Rhône;;;69123;Lyon;Nom;[EXEMPLE] B;F;;;;22/03/2026;;;\n",
        )
        assertEquals(listOf("2026-03-22"), staged.map { it.since })
        assertEquals(2, result.skipped)
        assertFailsWith<ElusFormatException> { read(ElusFile.MUNICIPAL, "Code du département;Code de la commune\n69;69123\n") }
    }

    @Test
    fun overseasCollectivitiesCorsicaAndTheFrenchAbroadAreFiledApart() {
        val header = "Code du département;Libellé du département;Code de la collectivité à statut particulier;Libellé de la collectivité à statut particulier;Code de la circonscription législative;Libellé de la circonscription législative;Nom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Code de la catégorie socio-professionnelle;Libellé de la catégorie socio-professionnelle;Date de début du mandat"
        val (deputies, result) = read(
            ElusFile.DEPUTE,
            "$header\n;;ZX;Saint-Martin / Saint-Barthélémy;97701;1Ère Circonscription;N;[EXEMPLE] A;M;;;;2024-07-08\nZZ;Français Établis Hors De France;;;ZZ01;1Ère Circonscription;N;[EXEMPLE] B;M;;;;2024-07-08\n",
        )
        assertEquals(listOf("ZX" to "97701"), deputies.map { it.area to it.territory })
        assertEquals(1, result.abroad)

        val assembly = "Code de la région;Libellé de la région;Code du département;Libellé du  département;Code de la collectivité à statut particulier;Libellé de la collectivité à statut particulier;Code de la section - collectivité à statut particulier;Libellé de la section - collectivité à statut particulier;Code de la circonscription métropolitaine;Libellé de la circonscription métropolitaine;Nom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Code de la catégorie socio-professionnelle;Libellé de la catégorie socio-professionnelle;Date de début du mandat;Libellé de la fonction;Date de début de la fonction"
        val (members, _) = read(
            ElusFile.COLLECTIVITE,
            "$assembly\n;;;;94;Corse;;;;;N;[EXEMPLE] C;F;;;;2021-07-01;Président de l'assemblée;2021-07-01\n84;Auvergne-Rhône-Alpes;69;Rhône;69M;Métropole De Lyon;;;69004;Lyon-Centre;N;[EXEMPLE] D;F;;;;2026-03-15;Autre membre;2026-04-02\n",
        )
        // Corsica (94 in the RNE) is not the Val-de-Marne (94): its area is 20R, its territory stays 94.
        assertEquals(listOf(Triple(Rne.CORSICA, "94", 0), Triple("69", "69M", 10_000)), members.map { Triple(it.area, it.territory, it.rank) })
    }

    @Test
    fun intercommunalitiesAreCollectedWithTheirCommunes() {
        val header = "Code du département;Libellé du département;Code de la collectivité à statut particulier;Libellé de la collectivité à statut particulier;N° SIREN;Libellé de l'EPCI;Code de la commune de rattachement;Libellé de la commune de rattachement;Nom de l'élu;Prénom de l'élu;Code sexe;Date de naissance;Code de la catégorie socio-professionnelle;Libellé de la catégorie socio-professionnelle;Date de début du mandat;Libellé de la fonction;Date de début de la fonction"
        val epcis = EpciCollector()
        val (staged, _) = read(
            ElusFile.COMMUNAUTAIRE,
            "$header\n23;Creuse;;;200067544;\"Cc \"\"Creuse Confluence\"\"\";23031;Boussac;N;[EXEMPLE] A;M;;;;2026-03-15;Président du conseil communautaire;2026-04-02\n23;Creuse;;;200067544;\"Cc \"\"Creuse Confluence\"\"\";23031;Boussac;N;[EXEMPLE] B;M;;;;2026-03-15;;\n",
            epcis,
        )
        assertEquals(listOf("23031", "23031"), staged.map { it.commune })
        assertEquals(mapOf("200067544" to "Cc \"Creuse Confluence\""), epcis.names)
        assertEquals(mapOf("23031" to "200067544"), epcis.communeEpcis())
    }

    @Test
    fun sectionsDatesAndRanks() {
        assertEquals("02", Rne.section("2"))
        assertEquals("69M", Rne.section("69M"))
        assertEquals("971", Rne.section("971"))
        assertEquals("2026-03-22", Rne.isoDate("22/03/2026"))
        assertEquals("2026-03-22", Rne.isoDate(" 2026-03-22 "))
        assertNull(Rne.isoDate("2026-02-30"))
        assertNull(Rne.isoDate("22-03-2026"))
        assertEquals(0, Rne.rank("Maire"))
        assertEquals(0, Rne.rank("Président du conseil départemental"))
        assertEquals(0, Rne.rank("Président de l'assemblée"))
        assertEquals(1, Rne.rank("1er adjoint au Maire"))
        assertEquals(12, Rne.rank("12ème adjoint au Maire"))
        assertEquals(5, Rne.rank("5eme Vice-président du conseil communautaire"))
        assertEquals(500, Rne.rank("Vice-président de l'assemblée"))
        assertEquals(1_000, Rne.rank("Président de commission du conseil départemental"))
        assertEquals(1_000, Rne.rank("Maire délégué"))
        assertEquals(10_000, Rne.rank("Autre membre"))
        assertEquals(10_000, Rne.rank(null))
    }

    @Test
    fun codesOfTheMinistryOfTheInterior() {
        assertEquals("01016", Geography.ministryCommune("01", "016"))
        assertEquals("2A004", Geography.ministryCommune("2A", "004"))
        assertEquals("97101", Geography.ministryCommune("ZA", "101"))
        assertEquals("97801", Geography.ministryCommune("ZX", "801"))
        assertEquals("98801", Geography.ministryCommune("ZN", "801"))
        assertNull(Geography.ministryCommune("ZZ", "001"))
        assertEquals("977", Geography.ministryArea("ZX"))
        assertNull(Geography.ministryArea("ZZ"))
    }

    @Test
    fun valuesOfAnOldTableFollowTheMergersOfCommunes() {
        val events = listOf(
            GeoEvent("2022-01-01", "01001", "01002"), // before the election: already in the table
            GeoEvent("2023-01-01", "01003", "01004"), // merger after the election
            GeoEvent("2023-01-01", "01005", "01004"),
            GeoEvent("2024-01-01", "01006", "01007"), // restoration: the restored commune inherits
        )
        val table = mapOf("01001" to setOf("0101"), "01003" to setOf("0101"), "01005" to setOf("0102"), "01006" to setOf("0103"), "01009" to setOf("0104"))
        val current = setOf("01001", "01004", "01006", "01007")
        assertEquals(
            mapOf("01001" to listOf("0101"), "01004" to listOf("0101", "0102"), "01006" to listOf("0103"), "01007" to listOf("0103")),
            Geography.follow(table, events, Geography.LEGISLATIVES_DATE, current),
        )
    }

    @Test
    fun namesOfConstituencies() {
        val geography = Geography(
            communes = emptyMap(),
            departments = mapOf(
                "69" to GeoArea("69", "Rhône", "Rhône", 2, "84"),
                "974" to GeoArea("974", "La Réunion", "Réunion", 3, "04"),
                "35" to GeoArea("35", "Ille-et-Vilaine", "Ille-et-Vilaine", 1, "53"),
                "75" to GeoArea("75", "Paris", "Paris", 0, "11"),
            ),
            collectivities = mapOf("987" to GeoArea("987", "Polynésie française", "Polynésie française", 0, null)),
            regions = emptyMap(),
            cantonNames = emptyMap(),
            postalCodes = emptyMap(),
            cantons = emptyMap(),
            circonscriptions = emptyMap(),
        )
        assertEquals("1re circonscription du Rhône", geography.circonscriptionName("6901"))
        assertEquals("7e circonscription de la Réunion", geography.circonscriptionName("97407"))
        assertEquals("2e circonscription d'Ille-et-Vilaine", geography.circonscriptionName("3502"))
        assertEquals("18e circonscription de Paris", geography.circonscriptionName("7518"))
        assertEquals("3e circonscription de Polynésie française", geography.circonscriptionName("98703"))
        assertEquals("1re circonscription de Saint-Barthélemy et Saint-Martin", geography.circonscriptionName("97701"))
    }
}
