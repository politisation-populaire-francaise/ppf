package org.politisationpopulairefrancaise.core.voir.application

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFixtures
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Applicomètre » (VO-36): the figures of the source, the delays from facts only, the packages on a gesture. */
class LawApplicationTest {
    private fun measure(order: Int, state: MeasureState, planned: String? = null, delay: Int? = null) =
        ApplicationMeasure(order = order, articles = "Article $order", subject = "[EXEMPLE] Mesure $order", state = state, delayDays = delay, plannedMonth = planned)

    private val awaited = LawApplication(
        id = "JORFTEXT000090000001",
        title = "[EXEMPLE] LOI relative au logement abordable",
        publishedOn = "2026-08-18",
        legislature = 17,
        dossier = "an-dossier:DLR5L17N90001",
        dossierPath = "exemple_logement_abordable",
        toApply = 4,
        applied = 1,
        measures = listOf(
            measure(1, MeasureState.APPLIED, delay = 40),
            measure(2, MeasureState.PENDING, planned = "2026-12"),
            measure(3, MeasureState.PENDING, planned = "2027-03"),
            measure(4, MeasureState.PENDING),
            measure(5, MeasureState.NOT_NEEDED),
        ),
    )
    private val direct = LawApplication(id = "JORFTEXT000090000002", title = "[EXEMPLE] LOI portant abrogation", publishedOn = "2026-11-02", legislature = 17, directApplication = true)
    private val older = LawApplication(id = "JORFTEXT000090000003", title = "[EXEMPLE] LOI de programmation", publishedOn = "2023-05-10", legislature = 16, toApply = 29, applied = 28)

    @Test
    fun theAnnouncedMonthIsReadOnlyWhenTheSourceGivesIt() {
        assertEquals("2027-03", ApplicationFormat.plannedMonth(null, "Publication envisagée en mars 2027"))
        assertEquals("2026-12", ApplicationFormat.plannedMonth("", "Publication envisagée en décembre 2026"))
        assertEquals("2027-02", ApplicationFormat.plannedMonth("2027-02-15", "Publication envisagée en mars 2027"), "the planned date first")
        assertEquals("2027-02", ApplicationFormat.plannedMonth("15/2/2027", null))
        assertNull(ApplicationFormat.plannedMonth(null, "Décret n° 2002-1058 du 6/8/2002"), "a text published is no announcement")
        assertNull(ApplicationFormat.plannedMonth(null, "Publication envisagée au premier semestre 2027"), "never guessed")
        assertEquals(MeasureState.APPLIED, MeasureState.of("appliqué"))
        assertEquals(MeasureState.NOT_NEEDED, MeasureState.of("sans objet"))
        assertEquals(MeasureState.PENDING, MeasureState.of("en attente d'application"))
        assertNull(MeasureState.of("abrogé"))
    }

    @Test
    fun theFiguresAreTheSameForEveryLawAndNeverRoundedUpToAll() {
        val today = CivilDate(2027, 1, 10)
        val figures = ApplicationCatalog.figures(awaited, today)
        assertEquals(LawFigures(toApply = 4, applied = 1, pending = 3, notNeeded = 1, pastAnnounced = 1, percent = 25), figures)
        assertEquals(96, older.percent(), "28 of 29: 96 %, never 97 % nor 100 %")
        assertNull(direct.percent(), "no measure expected: no rate")

        val late = assertNotNull(ApplicationCatalog.delay(awaited, awaited.measures[1], today))
        assertEquals(4, late.monthsSinceLaw, "18 August to 10 January: four whole months")
        assertTrue(late.announcedMonthPassed)
        assertEquals(false, ApplicationCatalog.delay(awaited, awaited.measures[2], today)?.announcedMonthPassed)
        assertEquals(false, ApplicationCatalog.delay(awaited, awaited.measures[3], today)?.announcedMonthPassed, "no announced month: nothing said")
        assertNull(ApplicationCatalog.delay(awaited, awaited.measures[0], today), "an applied measure has no delay to show")
    }

    @Test
    fun theCatalogListsTheLawsNewestFirstAndFindsTheLawOfABill() {
        val catalog = ApplicationCatalog(
            listOf(
                ApplicationPackage(example = true, legislature = 16, updatedOn = "2026-11-08", laws = listOf(older)),
                ApplicationPackage(example = true, legislature = 17, updatedOn = "2026-11-09", laws = listOf(awaited, direct)),
            ),
        )
        assertEquals(listOf(direct.id, awaited.id, older.id), catalog.laws.map { it.id })
        assertEquals(listOf(17, 16), catalog.legislatures)
        assertEquals("2026-11-09", catalog.updatedOn)
        assertEquals(awaited, catalog.ofDossier("an-dossier:DLR5L17N90001"))
        assertEquals(listOf(awaited), catalog.search("", pendingOnly = true))
        assertEquals(listOf(awaited), catalog.search("logement"))
        assertEquals(listOf(older), catalog.search("", legislature = 16))
        assertEquals("https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000090000001", awaited.legifranceUrl)
        assertEquals("https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple_logement_abordable", awaited.dossierUrl)
        assertNull(ApplicationFormat.dossierUrl(17, "../autre"), "only a plain path")

        val pkg = ApplicationPackage(example = true, legislature = 17, updatedOn = "2026-11-09", laws = listOf(awaited, direct))
        assertEquals(pkg, ApplicationFormat.read(ApplicationFormat.write(pkg)))
    }

    @Test
    fun onePackagePerLegislatureOnAGestureAndOnlyForItsOwnLegislature() = runTest {
        val keys = ParliamentFixtures.Keys()
        val own = ApplicationFormat.write(ApplicationPackage(example = true, legislature = 17, updatedOn = "2026-11-09", laws = listOf(awaited))).encodeToByteArray()
        val misnamed = ApplicationFormat.write(ApplicationPackage(example = true, legislature = 15, updatedOn = "2026-11-09", laws = listOf(older))).encodeToByteArray()
        val packages = ParliamentFixtures.packages() + mapOf(ApplicationFormat.packageName(17) to own, ApplicationFormat.packageName(16) to misnamed)
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(packages)), packages)
        val storage = InMemoryParliamentStorage()
        val snapshot = assertIs<ParliamentOpening.Ready>(ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentFixtures.NOW }).open()).snapshot
        val loader = ApplicationLoader(server, storage, clock = { ParliamentFixtures.NOW })

        assertEquals(listOf(17, 16), loader.available(snapshot).map { it.first })
        assertEquals(own.size.toLong(), loader.available(snapshot).first().second.bytes)
        assertNull(loader.held(snapshot), "nothing before the gesture")
        assertTrue(server.requests.none { it?.startsWith(ApplicationFormat.PACKAGE_PREFIX) == true })

        assertEquals(PackageOutcome.Done, loader.download(snapshot, 17))
        assertEquals(listOf(ApplicationFormat.packageName(17)), server.requests.filter { it?.startsWith(ApplicationFormat.PACKAGE_PREFIX) == true })
        assertEquals(listOf(awaited.id), loader.held(snapshot)?.catalog?.laws?.map { it.id })

        // A package that says another legislature than its name is never shown.
        assertEquals(PackageOutcome.Done, loader.download(snapshot, 16))
        assertEquals(listOf(awaited.id), loader.held(snapshot)?.catalog?.laws?.map { it.id })
        assertEquals(PackageOutcome.Unknown(ApplicationFormat.packageName(14)), loader.download(snapshot, 14))
    }
}
