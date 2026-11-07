package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate

/*
 * The vocabulary of "Voir" (vision, pillar 1; cahier fonctionnel M2, M4, M9, M13.2): the axes,
 * the kinds of sources, the reliability levels, the status of a fact, the stages of a judicial
 * procedure... Every value has a stable tag value written in the signed events (docs/FORMATS.md,
 * section 4 quinquies); the labels shown to the reader live in the interface's resources.
 */

/** The three axes of "Voir" (vision, section 6). */
enum class VoirAxis(val tagValue: String) {
    /** Mobilisations, repression, chronology, archives, the world. */
    MOUVEMENT("mouvement"),
    /** Elected representatives, votes, promises, cases, interests, public money, lobbies, bills. */
    POUVOIR("pouvoir"),
    /** Independence of justice, ownership of the media, public freedoms, counter-powers. */
    LIBERTES("libertes"),
    ;

    companion object {
        fun fromTag(value: String?): VoirAxis? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Where a figure or a fact comes from (M2: "type de source"; M17: primary sources first). */
enum class SourceKind(val tagValue: String) {
    /** Prefecture, ministry, police: the authorities' own figures. */
    AUTHORITIES("autorites"),
    /** Trade unions, organisers of the mobilisation. */
    ORGANISERS("organisateurs"),
    /** An independent count (media consortium, research team, citizen counters with a public method). */
    INDEPENDENT("comptage-independant"),
    MEDIA("medias"),
    /** Reports of the users, checked before they count (VO-03). */
    CITIZENS("signalements"),
    /** Journal officiel, decree, prefectoral order, circular. */
    OFFICIAL_TEXT("texte-officiel"),
    /** A court decision. */
    JUSTICE("justice"),
    /** Parliamentary records: public votes, reports, debates. */
    PARLIAMENT("parlement"),
    /** Report of a public body or an independent authority (Cour des comptes, HATVP, Défenseur des droits...). */
    REPORT("rapport"),
    /** A public statement, a programme, an interview of the person concerned. */
    STATEMENT("declaration"),
    /** An identified press investigation. */
    INVESTIGATION("enquete-presse"),
    ;

    companion object {
        fun fromTag(value: String?): SourceKind? = entries.firstOrNull { it.tagValue == value }
    }
}

/** How much a value can be relied upon (M2: "niveau de fiabilité"); the method of each indicator says why. */
enum class Reliability(val tagValue: String) {
    /** Primary source or count with a public, checked method. */
    HIGH("elevee"),
    /** Estimate from a known source whose method is partly public. */
    MEDIUM("moyenne"),
    /** Declarative or unchecked: shown, never merged with the others. */
    LOW("faible"),
    ;

    companion object {
        fun fromTag(value: String?): Reliability? = entries.firstOrNull { it.tagValue == value }
    }
}

/** What is established, alleged or under investigation is always visible (M17, method). */
enum class FactStatus(val tagValue: String) {
    ESTABLISHED("etabli"),
    ALLEGED("allegue"),
    UNDER_INVESTIGATION("enquete"),
    ;

    companion object {
        fun fromTag(value: String?): FactStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** What a sourced fact is about. */
enum class FactKind(val tagValue: String, val axis: VoirAxis, val inChronology: Boolean = false) {
    // The chronology of the movement (M4, VO-06).
    MOBILISATION("mobilisation", VoirAxis.MOUVEMENT, inChronology = true),
    OFFICIAL_STATEMENT("declaration-officielle", VoirAxis.MOUVEMENT, inChronology = true),
    OFFICIAL_DECISION("decision-officielle", VoirAxis.MOUVEMENT, inChronology = true),
    GOVERNMENT_RESPONSE("reponse-gouvernement", VoirAxis.MOUVEMENT, inChronology = true),

    // The power (VO-11 to VO-18).
    VOTE("vote", VoirAxis.POUVOIR),
    PROMISE("promesse", VoirAxis.POUVOIR),
    STATEMENT_VS_VOTE("discours-vote", VoirAxis.POUVOIR),
    CASE("affaire", VoirAxis.POUVOIR),
    CONTRADICTED_STATEMENT("declaration-contredite", VoirAxis.POUVOIR),
    PUBLIC_MONEY("argent-public", VoirAxis.POUVOIR),
    INTERESTS("interets", VoirAxis.POUVOIR),
    LOBBYING("influence", VoirAxis.POUVOIR),

    // Public freedoms (VO-26).
    LIBERTY("liberte", VoirAxis.LIBERTES),
    ;

    companion object {
        fun fromTag(value: String?): FactKind? = entries.firstOrNull { it.tagValue == value }

        /** The kinds shown in the chronology of the movement. */
        val chronology: List<FactKind> = entries.filter { it.inChronology }
    }
}

/**
 * Where a judicial procedure stands (M4: "le statut de chaque procédure est toujours affiché
 * clairement"). Until a conviction is final the person is presumed innocent.
 */
enum class ProcedureStage(val tagValue: String, val presumedInnocent: Boolean) {
    INVESTIGATION("enquete", presumedInnocent = true),
    INDICTMENT("mise-en-examen", presumedInnocent = true),
    TRIAL("proces", presumedInnocent = true),
    FIRST_INSTANCE_CONVICTION("condamnation-premiere-instance", presumedInnocent = true),
    APPEAL("appel", presumedInnocent = true),
    FINAL_CONVICTION("condamnation-definitive", presumedInnocent = false),
    ACQUITTAL("relaxe", presumedInnocent = true),
    DISMISSAL("non-lieu", presumedInnocent = true),
    NO_FURTHER_ACTION("classement-sans-suite", presumedInnocent = true),
    ;

    /** The procedure is over without a conviction. */
    val endedWithoutConviction: Boolean get() = this == ACQUITTAL || this == DISMISSAL || this == NO_FURTHER_ACTION

    companion object {
        fun fromTag(value: String?): ProcedureStage? = entries.firstOrNull { it.tagValue == value }
    }
}

/** What became of an electoral promise (VO-12). */
enum class PromiseStatus(val tagValue: String) {
    KEPT("tenue"),
    IN_PROGRESS("en-cours"),
    ABANDONED("abandonnee"),
    CONTRARY("contraire"),
    ;

    companion object {
        fun fromTag(value: String?): PromiseStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** How a representative voted in a public ballot (VO-11). */
enum class VotePosition(val tagValue: String) {
    FOR("pour"),
    AGAINST("contre"),
    ABSTENTION("abstention"),
    NOT_VOTING("non-votant"),
    ABSENT("absent"),
    ;

    companion object {
        fun fromTag(value: String?): VotePosition? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The families of the observatory of public freedoms (VO-26). */
enum class LibertyCategory(val tagValue: String) {
    PROTEST_BAN("interdiction-manifester"),
    DISSOLUTION("dissolution"),
    RESTRICTIVE_LAW("loi-restrictive"),
    SURVEILLANCE("surveillance"),
    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): LibertyCategory? = entries.firstOrNull { it.tagValue == value }
    }
}

/** Where the appeals against a measure stand (VO-26: "recours engagés"). */
enum class RecourseStatus(val tagValue: String) {
    NONE("aucun"),
    PENDING("en-cours"),
    SUSPENDED("suspendu"),
    ANNULLED("annule"),
    REJECTED("rejete"),
    ;

    companion object {
        fun fromTag(value: String?): RecourseStatus? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The public functions, to filter the sheets (VO-10). Every sheet has the same sections, whatever the function. */
enum class PublicRole(val tagValue: String) {
    DEPUTY("depute"),
    SENATOR("senateur"),
    MEP("eurodepute"),
    MINISTER("ministre"),
    PRESIDENT("president"),
    MAYOR("maire"),
    LOCAL_ELECTED("elu-local"),
    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): PublicRole? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The stages of a bill (législomètre, VO-20), in their usual order. */
enum class TextStage(val tagValue: String, val order: Int) {
    TABLED("depot", 0),
    COMMITTEE("commission", 1),
    ASSEMBLY("assemblee", 2),
    SENATE("senat", 3),
    JOINT_COMMITTEE("cmp", 4),
    FINAL_READING("lecture-definitive", 5),
    ADOPTED("adopte", 6),
    CONSTITUTIONAL_COUNCIL("conseil-constitutionnel", 7),
    PROMULGATED("promulgue", 8),
    REJECTED("rejete", 9),
    WITHDRAWN("retire", 9),
    ;

    /** The text left the parliamentary process. */
    val closed: Boolean get() = this == PROMULGATED || this == REJECTED || this == WITHDRAWN

    companion object {
        fun fromTag(value: String?): TextStage? = entries.firstOrNull { it.tagValue == value }

        /** The stages a text goes through when nothing stops it, as drawn on its progress bar. */
        val path: List<TextStage> = listOf(TABLED, COMMITTEE, ASSEMBLY, SENATE, JOINT_COMMITTEE, ADOPTED, PROMULGATED)
    }
}

/** Whether a group of the population gains or loses with a law (VO-19). */
enum class ImpactDirection(val tagValue: String) {
    GAINS("gagne"),
    LOSES("perd"),
    MIXED("mitige"),
    ;

    companion object {
        fun fromTag(value: String?): ImpactDirection? = entries.firstOrNull { it.tagValue == value }
    }
}

/** A source of a figure or a fact: its kind, its name, its address and its date when known. */
data class Source(val kind: SourceKind, val name: String, val url: String?, val date: CivilDate?)

/** An archived copy of an official document (VO-07): SHA-256 of the original, timestamp proof, archive link. */
data class ArchivedCopy(val sha256: String, val proof: String?, val archiveUrl: String?, val archivedAt: CivilDate?)

/** A correction made to a content (VO-22): when and why. */
data class Correction(val date: CivilDate, val reason: String)

/** Where "Et maintenant ?" leads (rule of the cahier fonctionnel, section 3): another space or a content. */
sealed interface NextTarget {
    data class ToSpace(val space: Space) : NextTarget
    data class ToContent(val address: ContentAddress) : NextTarget

    companion object {
        /** Reads "space:<id>" or a content address "kind:pubkey:identifier"; null otherwise. */
        fun parse(value: String): NextTarget? {
            if (value.startsWith(SPACE_PREFIX)) return Space.fromId(value.removePrefix(SPACE_PREFIX))?.let(::ToSpace)
            return runCatching { ContentAddress.parse(value) }.getOrNull()?.let(::ToContent)
        }

        const val SPACE_PREFIX: String = "space:"
    }
}

/** One step of "Et maintenant ?": a label and where it leads. */
data class NextStep(val label: String, val target: NextTarget)
