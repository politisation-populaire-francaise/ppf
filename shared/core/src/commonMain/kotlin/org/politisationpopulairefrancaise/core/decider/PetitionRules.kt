package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * What a threshold of signatures really triggers (cahier fonctionnel, M10: « explication claire
 * des seuils et de leurs effets réels »). The words shown to the reader live in the interface's
 * resources, one text for what happens and one for what is not guaranteed.
 */
enum class ThresholdEffect {
    /** Assemblée nationale, 100 000: put online on the site of the Assemblée, open one more year; no examination required. */
    ASSEMBLEE_HIGHLIGHT,
    /** Assemblée nationale, 500 000 from 30 departments: a debate in the chamber may be decided, without a vote. */
    ASSEMBLEE_DEBATE,
    /** Sénat, 100 000 within six months: the Conférence des présidents must examine it, and decides freely what follows. */
    SENAT_CONFERENCE,
    /** CESE: once admissible, the Council gives an opinion in plenary within six months, published in the Journal officiel. */
    CESE_OPINION,
    /** Shared-initiative referendum: the bill goes to referendum if Parliament does not examine it in time. */
    RIP_REFERENDUM,
    /** European citizens' initiative: meeting, hearing at the Parliament, answer of the Commission; no obligation to legislate. */
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
    /** The text says « plus de » (strictly more than [signatures]), as the Règlement de l'Assemblée does. */
    val moreThan: Boolean = false,
) {
    /** The smallest number of signatures that reaches this threshold. */
    val needed: Long get() = if (moreThan) signatures + 1 else signatures

    fun isReachedBy(count: Long): Boolean = count >= needed
}

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
 * The rules of the official petitions, as checked on [VERIFIED_ON] (docs/DECISIONS.md, D-059, with
 * the official sources). Any change in the law means updating this object, its sources and the
 * texts of the interface together.
 */
object PetitionRules {
    val VERIFIED_ON: CivilDate = CivilDate(2026, 11, 7)

    private val rules: Map<PetitionPlatform, PlatformRules> = listOf(
        PlatformRules(
            platform = PetitionPlatform.ASSEMBLEE,
            thresholds = listOf(
                // Article 148, alinéa 1: « plus de 100 000 pétitionnaires » ; alinéa 6: « plus de 500 000 pétitionnaires
                // domiciliés dans trente départements ou collectivités d'outre-mer au moins » (read on 7 November 2026).
                Threshold(100_000, ThresholdEffect.ASSEMBLEE_HIGHLIGHT, guaranteed = true, moreThan = true),
                Threshold(500_000, ThresholdEffect.ASSEMBLEE_DEBATE, guaranteed = false, minDepartments = 30, moreThan = true),
            ),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = 18,
            // Open until the end of the legislature, or one year after 100 000 signatures.
            collectionMonths = null,
            thresholdPerProcedure = false,
            legalReference = "Règlement de l’Assemblée nationale, articles 147 à 151, notamment article 148, alinéas 1 et 6 (résolution n° 281 du 4 juin 2019)",
            sourceUrl = "https://www.assemblee-nationale.fr/dyn/17/divers/texte_reference/02_reglement_assemblee_nationale.html",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.SENAT,
            thresholds = listOf(Threshold(100_000, ThresholdEffect.SENAT_CONFERENCE, guaranteed = true, withinMonths = 6)),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = null,
            collectionMonths = 6,
            thresholdPerProcedure = false,
            legalReference = "Règlement du Sénat, articles 87 et 88 ; Instruction générale du Bureau, chapitre XVIII",
            sourceUrl = "https://www.senat.fr/reglement/reglement_mono.html",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.CESE,
            thresholds = listOf(Threshold(150_000, ThresholdEffect.CESE_OPINION, guaranteed = true)),
            signers = SignerRule.FRANCECONNECT,
            minimumAge = 16,
            collectionMonths = 12,
            thresholdPerProcedure = false,
            legalReference = "Constitution, article 69 ; ordonnance n° 58-1360 du 29 décembre 1958, article 4-1 (loi organique n° 2021-27 du 15 janvier 2021)",
            sourceUrl = "https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000042999544",
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
            sourceUrl = "https://www.conseil-constitutionnel.fr/referendum-d-initiative-partagee/referendum-d-initiative-partagee-rip-mode-d-emploi",
            verifiedOn = VERIFIED_ON,
        ),
        PlatformRules(
            platform = PetitionPlatform.ICE,
            thresholds = listOf(Threshold(1_000_000, ThresholdEffect.ICE_ANSWER, guaranteed = true, minMemberStates = 7, withinMonths = 12)),
            signers = SignerRule.EU_CITIZENS,
            minimumAge = 18,
            collectionMonths = 12,
            thresholdPerProcedure = false,
            legalReference = "Règlement (UE) 2019/788 du 17 avril 2019 ; règlement délégué (UE) 2024/1082 (seuil de la France : 58 320)",
            sourceUrl = "https://eur-lex.europa.eu/legal-content/FR/TXT/HTML/?uri=CELEX:32019R0788",
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
        val reached = thresholds.filter { it.isReachedBy(signatures) }
        val next = thresholds.firstOrNull { !it.isReachedBy(signatures) }
        val fraction = when {
            thresholds.isEmpty() -> 0f
            next == null -> 1f
            else -> (signatures.toDouble() / next.needed).toFloat().coerceIn(0f, 1f)
        }
        val spread = reached.lastOrNull { it.minDepartments != null }
        val departmentsMissing = spread?.minDepartments?.let { needed -> petition.departments?.count?.let { (needed - it).coerceAtLeast(0) } }
        return PetitionProgress(
            count = count,
            thresholds = thresholds,
            reached = reached,
            next = next,
            remaining = next?.let { it.needed - signatures },
            fraction = fraction,
            departmentsMissing = departmentsMissing,
        )
    }
}
