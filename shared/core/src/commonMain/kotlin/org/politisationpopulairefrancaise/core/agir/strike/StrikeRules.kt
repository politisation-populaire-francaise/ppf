package org.politisationpopulairefrancaise.core.agir.strike

import org.politisationpopulairefrancaise.core.time.CivilDate

/*
 * The vocabulary of the strike simulator of "Agir" (M35, AG-60), docs/integration/agir-simulateur-greve.md.
 * Every rule, obligation and note names the official sources it rests on (checked on 9 November 2026);
 * the labels and explanations live in the string resources of the interface (strings_greve.xml).
 */

/** When the rules of the simulator were last checked on the official sources. */
val STRIKE_RULES_VERIFIED_ON: CivilDate = CivilDate(2026, 11, 9)

/**
 * Who employs the person on strike: the rule of the retention follows the status. Contract agents
 * under public law follow the rule of their civil service (CGFP, article L7, and L711-3 for the State;
 * by the same reasoning for the territorial and hospital civil services, to be confirmed by a lawyer).
 * The ids are stable (tests, tags).
 */
enum class StrikeStatus(val id: String) {
    /** An employee of a company, under private law (including public companies such as transport operators). */
    PRIVATE("prive"),

    /** A public agent of the State (fonction publique de l'État): civil servant or contract agent. */
    STATE("etat"),

    /** A public agent of a local authority (fonction publique territoriale). */
    TERRITORIAL("territoriale"),

    /** A public agent of a hospital or medico-social public establishment (fonction publique hospitalière). */
    HOSPITAL("hospitaliere"),
    ;

    val isPublic: Boolean get() = this != PRIVATE
}

/**
 * The sector, when it changes the notice or the individual declaration (or, for a company in charge
 * of a public service, the rule of the retention). [GENERAL] fits every status. The ids are stable.
 */
enum class StrikeSector(val id: String, private val statusIds: Set<String>) {
    GENERAL("general", setOf("prive", "etat", "territoriale", "hospitaliere")),

    /** Regular land passenger transport (code des transports, L1324-1): bus, tram, metro, train. */
    LAND_TRANSPORT("transport-terrestre", setOf("prive")),

    /** Air passenger transport and aerodromes (code des transports, L1114-3). */
    AIR_TRANSPORT("transport-aerien", setOf("prive")),

    /** Another public service run by a company (code du travail, L2512-1): its retention rule is not simulated. */
    PRIVATE_PUBLIC_SERVICE("service-public-entreprise", setOf("prive")),

    /** Teaching in a public nursery or primary school (code de l'éducation, L133-4). */
    PRIMARY_SCHOOL("ecole", setOf("etat")),

    /** Air navigation of the State: control, flight information, alert (CGFP, L114-5-1). */
    AIR_NAVIGATION("navigation-aerienne", setOf("etat")),

    /** The territorial services of the continuity of public service (CGFP, L114-7). */
    TERRITORIAL_CONTINUITY("continuite-territoriale", setOf("territoriale")),
    ;

    fun fits(status: StrikeStatus): Boolean = status.id in statusIds

    companion object {
        /** The sectors offered for [status], [GENERAL] first. */
        fun forStatus(status: StrikeStatus): List<StrikeSector> = entries.filter { it.fits(status) }
    }
}

/** One day of strike within the month: the whole day, half of it, or a number of minutes. */
sealed interface StrikeDay {
    data object FullDay : StrikeDay

    data object HalfDay : StrikeDay

    /** A part of the day, in minutes (1 to 24 hours). */
    data class Hours(val minutes: Int) : StrikeDay {
        init {
            require(minutes in 1..StrikeSimulator.MAX_DAY_MINUTES) { "minutes: 1 to ${StrikeSimulator.MAX_DAY_MINUTES}" }
        }
    }
}

/** The rule the estimate applies. */
enum class StrikeRule(val sources: List<StrikeSource>) {
    /** Private sector: strictly proportional to the hours not worked. */
    PRIVATE_PROPORTIONAL(
        listOf(StrikeSource.SERVICE_PUBLIC_PRIVATE, StrikeSource.CASSATION_1992, StrikeSource.LABOUR_CODE_L3242_1, StrikeSource.LABOUR_CODE_L3121_27),
    ),

    /** Regular land passenger transport: reduced according to the time not worked (code des transports, L1324-11). */
    TRANSPORT_PROPORTIONAL(
        listOf(StrikeSource.TRANSPORT_CODE_L1324_11, StrikeSource.CASSATION_2020, StrikeSource.LABOUR_CODE_L3242_1, StrikeSource.LABOUR_CODE_L3121_27),
    ),

    /** State: one indivisible thirtieth per day of strike, days without service in between included. */
    STATE_THIRTIETH(
        listOf(StrikeSource.CGFP_L711, StrikeSource.SERVICE_PUBLIC_PUBLIC, StrikeSource.STATE_COUNCIL_1978, StrikeSource.CGFP_L7),
    ),

    /** Territorial and hospital civil services: proportional (1/30 a day, 1/60 a half-day, 1/151.67 an hour). */
    PUBLIC_PROPORTIONAL(
        listOf(StrikeSource.STATE_COUNCIL_2009, StrikeSource.SERVICE_PUBLIC_PUBLIC, StrikeSource.SENATE_ANSWER_1999),
    ),

    /** A company in charge of a public service outside land transport: a particular rule, not simulated (to be confirmed). */
    NOT_SIMULATED(listOf(StrikeSource.LABOUR_CODE_L2512, StrikeSource.CASSATION_2020)),
}

/** What the law asks before a strike, for the status and the sector chosen. */
enum class StrikeObligation(val sources: List<StrikeSource>) {
    /** Private sector outside public services: no notice. */
    NO_NOTICE_PRIVATE(listOf(StrikeSource.SERVICE_PUBLIC_PRIVATE, StrikeSource.LABOUR_CODE_L2512)),

    /** Public services: a notice by a representative union, received five clear days before. */
    NOTICE_PUBLIC_SERVICE(listOf(StrikeSource.LABOUR_CODE_L2512, StrikeSource.CGFP_L114)),

    /** The same, except in communes of 10,000 inhabitants or fewer (CGFP, L114-2). */
    NOTICE_TERRITORIAL(listOf(StrikeSource.LABOUR_CODE_L2512, StrikeSource.CGFP_L114)),

    /** Public nursery and primary schools: a prior negotiation, then the notice. */
    NOTICE_PRIMARY_SCHOOL(listOf(StrikeSource.LABOUR_CODE_L2512, StrikeSource.SERVICE_PUBLIC_PUBLIC)),

    /** At the latest 48 hours before (code des transports, L1324-7). */
    DECLARATION_LAND_TRANSPORT(listOf(StrikeSource.TRANSPORT_CODE_L1324_7, StrikeSource.TRANSPORT_CODE_L1324_1)),

    /** At the latest 48 hours before (code des transports, L1114-3). */
    DECLARATION_AIR_TRANSPORT(listOf(StrikeSource.TRANSPORT_CODE_L1114_3)),

    /** At least 48 hours before, with at least one working day (code de l'éducation, L133-4). */
    DECLARATION_PRIMARY_SCHOOL(listOf(StrikeSource.EDUCATION_CODE_L133_4)),

    /** At the latest 48 hours before, with at least one working day (CGFP, L114-9). */
    DECLARATION_TERRITORIAL(listOf(StrikeSource.CGFP_L114)),

    /** At the latest at noon two days before each day of strike (CGFP, L114-5-1). */
    DECLARATION_AIR_NAVIGATION(listOf(StrikeSource.CGFP_L114)),

    /** No individual declaration known to the simulator for this sector. */
    NO_KNOWN_DECLARATION(emptyList()),
}

/** What the person should also know, only when an official source says it. */
enum class StrikeNote(val sources: List<StrikeSource>) {
    // Private sector
    ACCESSORIES(listOf(StrikeSource.SERVICE_PUBLIC_PRIVATE)),
    PAYSLIP_SILENT(listOf(StrikeSource.SERVICE_PUBLIC_PRIVATE)),
    NO_DISCRIMINATION(listOf(StrikeSource.LABOUR_CODE_L2511_1, StrikeSource.CASSATION_1992)),
    BONUSES(listOf(StrikeSource.CASSATION_2014_BONUSES)),
    FIXED_DAYS_NOT_SIMULATED(listOf(StrikeSource.CASSATION_2008_FIXED_DAYS)),

    // Civil services
    FAMILY_SUPPLEMENT_KEPT(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC, StrikeSource.CGFP_L711)),
    NOT_CONTRIBUTED(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC)),
    SEIZABLE_CAP(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC)),
    MONTH_OF_STRIKE(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC)),
    LEAVE_BEFORE_NOTICE(listOf(StrikeSource.STATE_COUNCIL_2013)),
    GUARDS_NOT_SIMULATED(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC, StrikeSource.STATE_COUNCIL_2009)),
    RETIREMENT_STATE(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC)),
    RETIREMENT_LOCAL(listOf(StrikeSource.SERVICE_PUBLIC_PUBLIC)),

    // Public services
    NO_ROTATING_STRIKES(listOf(StrikeSource.LABOUR_CODE_L2512)),
}

/**
 * An official source, with the address the interface opens after "Vous quittez PPF". Every page was
 * read on 9 November 2026 ([STRIKE_RULES_VERIFIED_ON]); its label lives in the string resources.
 */
enum class StrikeSource(val url: String) {
    /** service-public.gouv.fr, F117, "Droit de grève d'un salarié du secteur privé" (vérifiée le 10 août 2026). */
    SERVICE_PUBLIC_PRIVATE("https://www.service-public.gouv.fr/particuliers/vosdroits/F117"),

    /** service-public.gouv.fr, F499, "Droit de grève dans la fonction publique" (vérifiée le 3 mars 2026). */
    SERVICE_PUBLIC_PUBLIC("https://www.service-public.gouv.fr/particuliers/vosdroits/F499"),

    LABOUR_CODE_L2511_1("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006902372"),

    /** Code du travail, L2512-1 to L2512-5 (public services). */
    LABOUR_CODE_L2512("https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000006072050/LEGISCTA000006177989/"),

    LABOUR_CODE_L3242_1("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006902858"),

    /** Code du travail, L3121-27 (35 hours a week), first article of its section. */
    LABOUR_CODE_L3121_27("https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000006072050/LEGISCTA000006189631/"),

    /** Cour de cassation, chambre sociale, 8 juillet 1992, n° 89-42.563. */
    CASSATION_1992("https://www.legifrance.gouv.fr/juri/id/JURITEXT000007028788/"),

    /** Cour de cassation, chambre sociale, 13 novembre 2008, n° 06-44.608 (forfait en jours). */
    CASSATION_2008_FIXED_DAYS("https://www.legifrance.gouv.fr/juri/id/JURITEXT000019772498"),

    /** Cour de cassation, chambre sociale, 26 mars 2014, n° 12-18.125 (primes). */
    CASSATION_2014_BONUSES("https://www.legifrance.gouv.fr/juri/id/JURITEXT000028797769/"),

    /** Cour de cassation, chambre sociale, 8 juillet 2020, n° 19-13.767 (transports, L2512-5). */
    CASSATION_2020("https://www.legifrance.gouv.fr/juri/id/JURITEXT000042128076/"),

    TRANSPORT_CODE_L1324_1("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000038582777"),
    TRANSPORT_CODE_L1324_7("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000025542901"),

    /** Code des transports, L1324-7 to L1324-11, the section that holds L1324-11. */
    TRANSPORT_CODE_L1324_11("https://www.legifrance.gouv.fr/affichCode.do?idSectionTA=LEGISCTA000023085508&cidTexte=LEGITEXT000023086525"),

    TRANSPORT_CODE_L1114_3("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000025541651"),
    EDUCATION_CODE_L133_4("https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000019346685"),

    /** Code général de la fonction publique, L711-3 (with L711-1 and L711-2 in the section before it). */
    CGFP_L711("https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044423747/"),

    /** Code général de la fonction publique, L114-1 to L114-10 (droit de grève). */
    CGFP_L114("https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044420629/"),

    /** Code général de la fonction publique, L1 to L9 (L7: "agent public"). */
    CGFP_L7("https://www.legifrance.gouv.fr/codes/section_lc/LEGITEXT000044416551/LEGISCTA000044420579/"),

    /** Conseil d'État, 7 juillet 1978, n° 03918. */
    STATE_COUNCIL_1978("https://www.legifrance.gouv.fr/ceta/id/CETATEXT000007654174/"),

    /** Conseil d'État, Section, 17 juillet 2009, n° 303588. */
    STATE_COUNCIL_2009("https://www.legifrance.gouv.fr/ceta/id/CETATEXT000020871105/"),

    /** Conseil d'État, 4 décembre 2013, n° 351229. */
    STATE_COUNCIL_2013("https://www.legifrance.gouv.fr/ceta/id/CETATEXT000028275591/"),

    /** Réponse ministérielle à la question écrite n° 05471, JO Sénat du 7 janvier 1999, p. 38. */
    SENATE_ANSWER_1999("https://www.senat.fr/questions/base/1998/qSEQ980105471.html"),
}
