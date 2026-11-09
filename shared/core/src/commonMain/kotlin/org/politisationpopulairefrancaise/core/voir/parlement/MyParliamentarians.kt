package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.elus.CommuneOfficials
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.elus.OfficialGroupKind
import org.politisationpopulairefrancaise.core.text.FrenchText

/** One of my elected officials of a parliament (« Mes élus », TE-01), with the member of the open data it is, when found. */
data class MyParliamentarian(val official: ElectedOfficial, val member: Member?)

/**
 * My parliamentarians (VO-34; docs/integration/voir-alertes-elus.md, section 2), matched on the device between
 * « Mes élus » (the official directory, D-047) and the parliamentary open data (D-064), never on a name alone:
 * - a deputy: the same constituency code (`6901`); a commune split between constituencies has several;
 * - a senator: the same department and the same name once folded, words in any order;
 * - the members of the European Parliament: France elects them on one national list, so only those the
 *   person chose ([europeans]) are followed.
 * Nothing of it ever leaves the device.
 */
data class MyParliamentarians(
    val deputies: List<MyParliamentarian>,
    val senators: List<MyParliamentarian>,
    val europeans: List<Member>,
) {
    /** The members whose votes are followed. */
    val members: List<Member> get() = (deputies + senators).mapNotNull { it.member } + europeans

    /** The commune belongs to several constituencies: every deputy of them is shown, and said so. */
    val sharedCommune: Boolean get() = deputies.size > 1

    /** The assemblies of [members], for the packages to keep up to date. */
    val chambers: Set<Chamber> get() = members.map { it.chamber }.toSet()

    companion object {
        val EMPTY: MyParliamentarians = MyParliamentarians(emptyList(), emptyList(), emptyList())

        /** From the officials of the chosen commune ([CommuneOfficials], given by `ElusFinder.find`) and the members of the open data. */
        fun of(officials: CommuneOfficials, members: List<Member>, europeans: Set<String> = emptySet()): MyParliamentarians {
            val groups = officials.levels.flatMap { it.groups }
            return of(
                deputies = groups.filter { it.kind == OfficialGroupKind.CIRCONSCRIPTION }.flatMap { it.officials }.filter { it.mandate == Mandate.DEPUTE },
                senators = groups.filter { it.kind == OfficialGroupKind.SENATE }.flatMap { it.officials }.filter { it.mandate == Mandate.SENATEUR },
                members = members,
                europeans = europeans,
            )
        }

        fun of(deputies: List<ElectedOfficial>, senators: List<ElectedOfficial>, members: List<Member>, europeans: Set<String> = emptySet()): MyParliamentarians {
            val byChamber = members.groupBy { it.chamber }
            val assembly = byChamber[Chamber.AN].orEmpty()
            val senate = byChamber[Chamber.SENAT].orEmpty()
            return MyParliamentarians(
                deputies = deputies.distinctBy { it.territory to it.name }.map { official ->
                    val sameSeat = assembly.filter { it.territory == official.territory }
                    MyParliamentarian(official, sameSeat.singleOrNull() ?: sameSeat.firstOrNull { sameName(it.name, official.displayName) })
                },
                senators = senators.distinctBy { it.territory to it.name }.map { official ->
                    MyParliamentarian(official, senate.firstOrNull { it.territory == official.territory && sameName(it.name, official.displayName) })
                },
                europeans = byChamber[Chamber.PE].orEmpty().filter { it.id in europeans },
            )
        }

        /** The same words once folded (accents, case and order ignored): « DUPONT Jean » is « Jean Dupont ». */
        internal fun sameName(a: String, b: String): Boolean {
            val left = FrenchText.words(a.removePrefix(EXAMPLE_MARKER)).toSet()
            return left.isNotEmpty() && left == FrenchText.words(b.removePrefix(EXAMPLE_MARKER)).toSet()
        }

        private const val EXAMPLE_MARKER = "[EXEMPLE]"
    }
}
