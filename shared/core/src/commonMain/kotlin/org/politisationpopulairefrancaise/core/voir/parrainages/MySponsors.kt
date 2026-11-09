package org.politisationpopulairefrancaise.core.voir.parrainages

import org.politisationpopulairefrancaise.core.elus.CommuneOfficials
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.text.FrenchText

/** One of my officials (« Mes élus ») found in the published list, with the presentation as published. */
data class MySponsor(val official: ElectedOfficial, val sponsorship: Sponsorship)

/**
 * « Mon maire, mon député a parrainé » (VO-37, TE-01), on the device only, never on a name alone:
 * - the mayor: the same department, the same commune (its name folded) and the same name;
 * - a deputy: the same department, the same number of constituency and the same name;
 * - a senator, a member of the departmental or regional council: the same department and the same name;
 * - a member of the European Parliament: « Parlement européen » and the same name.
 * An official not found is simply not listed: the page never says « n'a pas parrainé », only what is published.
 */
object MySponsors {
    fun of(officials: CommuneOfficials, catalog: SponsorshipsCatalog): List<MySponsor> {
        val department = FrenchText.fold(officials.department.name)
        val commune = FrenchText.fold(officials.commune.name)
        val everyone = officials.levels.flatMap { level -> level.groups.flatMap { it.officials } }.distinctBy { Triple(it.mandate, it.territory, it.name) }
        val list = catalog.pkg.sponsorships
        return everyone.mapNotNull { official ->
            val found = list.firstOrNull { s -> matches(official, s, department, commune) }
            found?.let { MySponsor(official, it) }
        }.sortedWith(compareBy({ it.official.mandate.ordinal }, { FrenchText.fold(it.official.displayName) }))
    }

    internal fun matches(official: ElectedOfficial, s: Sponsorship, department: String, commune: String): Boolean {
        if (!sameName(official.displayName, s.displayName)) return false
        val inDepartment = FrenchText.fold(s.department) == department
        return when (official.mandate) {
            Mandate.MUNICIPAL -> s.mandate == SponsorMandate.MAIRE && isMayor(official) && inDepartment && FrenchText.fold(s.territory.orEmpty()) == commune
            Mandate.DEPUTE -> s.mandate == SponsorMandate.DEPUTE && inDepartment && constituency(s.territory) == official.territory.takeLast(2).toIntOrNull()
            Mandate.SENATEUR -> s.mandate == SponsorMandate.SENATEUR && inDepartment
            Mandate.DEPARTEMENTAL -> s.mandate == SponsorMandate.CONSEILLER_DEPARTEMENTAL && inDepartment
            Mandate.REGIONAL -> s.mandate == SponsorMandate.CONSEILLER_REGIONAL && inDepartment
            Mandate.EUROPEEN -> s.mandate == SponsorMandate.DEPUTE_EUROPEEN
            Mandate.COMMUNAUTAIRE -> s.mandate == SponsorMandate.PRESIDENT_INTERCOMMUNALITE && inDepartment
            Mandate.COLLECTIVITE -> s.mandate == SponsorMandate.CONSEILLER_CORSE && inDepartment
        }
    }

    /** « 5ème circonscription », « 1ère circonscription » to 5 and 1; null otherwise. */
    internal fun constituency(territory: String?): Int? = territory?.let { Regex("^\\s*(\\d{1,2})").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun isMayor(official: ElectedOfficial): Boolean = official.function?.let { FrenchText.fold(it) == "maire" } == true

    /** The same words once folded (accents, case and order ignored): « DUPONT Jean » is « Jean Dupont ». */
    internal fun sameName(a: String, b: String): Boolean {
        // The marker may sit inside « Prénom [EXEMPLE] NOM »: removed wherever it is.
        val left = FrenchText.words(a.replace(SponsorshipsCatalog.EXAMPLE_MARKER, " ")).toSet()
        return left.isNotEmpty() && left == FrenchText.words(b.replace(SponsorshipsCatalog.EXAMPLE_MARKER, " ")).toSet()
    }
}
