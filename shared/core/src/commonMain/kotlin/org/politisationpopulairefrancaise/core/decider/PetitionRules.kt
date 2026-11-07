package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * What a threshold of signatures really triggers (cahier fonctionnel, M10: « explication claire
 * des seuils et de leurs effets réels »). The words shown to the reader live in the interface's
 * resources, one text for what happens and one for what is not guaranteed.
 */
enum class ThresholdEffect {
    /** Assemblée nationale, 100 000: the petition is shown on the site and may be examined. */
    ASSEMBLEE_HIGHLIGHT,
    /** Assemblée nationale, 500 000 from 30 departments: a debate in the chamber may be decided, without a vote. */
    ASSEMBLEE_DEBATE,
    /** Sénat, 100 000 within six months: sent to the Conférence des présidents, which may decide a debate. */
    SENAT_CONFERENCE,
    /** CESE: the Council examines the petition and gives its opinion. */
    CESE_OPINION,
    /** Shared-initiative referendum: the bill goes to referendum if Parliament does not examine it in time. */
    RIP_REFERENDUM,
    /** European citizens' initiative: the Commission must answer, without any obligation to legislate. */
    ICE_ANSWER,
}

/**
 * One threshold: the signatures needed, the conditions that go with them, and its effect.
 * [guaranteed] is false when the effect depends on a decision of the institution afterwards:
 * the interface then says so in so many words.
 */
data class Threshold(
    val signatures: Long,
    val effect: ThresholdEffect,
    val guaranteed: Boolean,
    /** Signatories coming from at least this many departments or overseas collectivities. */
    val minDepartments: Int? = null,
    /** Signatories coming from at least this many Member States, each above its own minimum. */
    val minMemberStates: Int? = null,
    /** The signatures must be gathered within this many months. */
    val withinMonths: Int? = null,
)

/** Who can sign on a platform, as the interface explains it. */
enum class SignerRule {
    /** Anyone, identified through FranceConnect by the platform itself. */
    FRANCECONNECT,
    /** Voters registered on the French electoral rolls. */
    REGISTERED_VOTERS,
    /** Citizens of the European Union old enough to vote at European elections (16 in some states). */
    EU_CITIZENS,
}

/**
 * The rules of an official platform, with the legal text they come from and the day they were
 * last checked against the official sources (CLAUDE.md, section 6: « vérifie le droit en vigueur
 * auprès de sources officielles et cite-les »).
 */
data class PlatformRules(
    val platform: PetitionPlatform,
    /** In increasing order of signatures. */
    val thresholds: List<Threshold>,
    val signers: SignerRule,
    val minimumAge: Int?,
    /** How long a collection may last, in months, when the text sets it. */
    val collectionMonths: Int?,
    /** The threshold depends on each procedure (the shared-initiative referendum): read from the petition. */
    val thresholdPerProcedure: Boolean,
    val legalReference: String,
    val sourceUrl: String,
    val verifiedOn: CivilDate,
)

/** Where a petition stands against the thresholds of its platform. */
data class PetitionProgress(
    val count: SignatureCount?,
    val thresholds: List<Threshold>,
    /** The thresholds whose number of signatures is reached (their other conditions may still be missing). */
    val reached: List<Threshold>,
    /** The next threshold to reach, null when every threshold is passed. */
    val next: Threshold?,
    /** Signatures still missing for [next]. */
    val remaining: Long?,
    /** Share of [next] already reached, between 0 and 1 (1 when every threshold is passed). */
    val fraction: Float,
    /** Departments still missing for a threshold reached in signatures but not in spread, when known. */
    val departmentsMissing: Int?,
)

/**
 * The rules of the official petitions, as checked on [VERIFIED_ON] (docs/DECISIONS.md, D-044, with
 * the official sources). Any change in the law means updating this object, its sources and the
 * texts of the interface together.
 */
object PetitionRules {
    val VERIFIED_ON: CivilDate = CivilDate(2026, 11, 7)

    private val rules: Map<PetitionPlatform, PlatformRules> = listOf(
        PlatformRules(
            platform = PetitionPlatform.ASSEMBLEE,
            thresholds = listOf(
                Threshold(100_000, ThresholdEffect.ASSEMBLEE_HIGHLIGHT, guaranteed = true),
                Threshold(500_000, ThresholdEffect.ASSEMBLEE_DEBATE, guaranteed = false, minDepartments = 30),
            ),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = null,
            collectionMonths = null,
            thresholdPerProcedure = false,
            legalReference = "Règlement de l'Assemblée nationale, articles 147 à 151",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/connaissance/reglement",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.SENAT,
            thresholds = listOf(Threshold(100_000, ThresholdEffect.SENAT_CONFERENCE, guaranteed = false, withinMonths = 6)),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = null,
            collectionMonths = 6,
            thresholdPerProcedure = false,
            legalReference = "Règlement du Sénat, articles 87 à 89 bis",
            sourceUrl = "https://petitions.senat.fr/",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.CESE,
            thresholds = listOf(Threshold(150_000, ThresholdEffect.CESE_OPINION, guaranteed = true)),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = 16,
            collectionMonths = 12,
            thresholdPerProcedure = false,
            legalReference = "Constitution, article 69 ; ordonnance n° 58-1360 du 29 décembre 1958, article 4-1",
            sourceUrl = "https://www.lecese.fr/",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.RIP,
            thresholds = emptyList(),
            signers = SignerRule.REGISTERED_VOTERS,
            minimumAge = 18,
            collectionMonths = 9,
            thresholdPerProcedure = true,
            legalReference = "Constitution, article 11 ; loi organique n° 2013-1114 du 6 décembre 2013",
            sourceUrl = "https://www.referendum.interieur.gouv.fr/",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.ICE,
            thresholds = listOf(Threshold(1_000_000, ThresholdEffect.ICE_ANSWER, guaranteed = true, minMemberStates = 7, withinMonths = 12)),
            signers = SignerRule.EU_CITIZENS,
            minimumAge = 18,
            collectionMonths = 12,
            thresholdPerProcedure = false,
            legalReference = "Règlement (UE) 2019/788 du 17 avril 2019 relatif à l'initiative citoyenne européenne",
            sourceUrl = "https://citizens-initiative.europa.eu/",
            verifiedOn = VERIFIED_ON,
        ),
    ).associateBy { it.platform }

    fun of(platform: PetitionPlatform): PlatformRules = rules.getValue(platform)

    /** The thresholds of a petition: those of its platform, or the one set for its procedure (RIP). */
    fun thresholdsOf(petition: Petition): List<Threshold> {
        val platform = of(petition.platform)
        if (!platform.thresholdPerProcedure) return platform.thresholds
        val required = petition.required ?: return emptyList()
        return listOf(Threshold(required, ThresholdEffect.RIP_REFERENDUM, guaranteed = false, withinMonths = platform.collectionMonths))
    }

    fun progress(petition: Petition): PetitionProgress {
        val thresholds = thresholdsOf(petition).sortedBy { it.signatures }
        val count = petition.latestCount
        val signatures = count?.count ?: 0L
        val reached = thresholds.filter { signatures >= it.signatures }
        val next = thresholds.firstOrNull { signatures < it.signatures }
        val fraction = when {
            thresholds.isEmpty() -> 0f
            next == null -> 1f
            else -> (signatures.toDouble() / next.signatures).toFloat().coerceIn(0f, 1f)
        }
        val spread = reached.lastOrNull { it.minDepartments != null }
        val departmentsMissing = spread?.minDepartments?.let { needed -> petition.departments?.count?.let { (needed - it).coerceAtLeast(0) } }
        return PetitionProgress(
            count = count,
            thresholds = thresholds,
            reached = reached,
            next = next,
            remaining = next?.let { it.signatures - signatures },
            fraction = fraction,
            departmentsMissing = departmentsMissing,
        )
    }
}
