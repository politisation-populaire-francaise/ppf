package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.voir.PublicRole

/** Tags of the contents of "Comprendre" beyond the common ones (docs/FORMATS.md, section 4 bis). */
object ComprendreTags {
    const val VERDICT: String = "ppf-verdict"
    const val CLAIM: String = "ppf-claim"
    const val CLAIM_CONTEXT: String = "ppf-claim-context"
    const val PERIOD: String = "ppf-period"
    const val COUNTRY: String = "ppf-country"
    const val RESULT: String = "ppf-result"

    /** The role of a link (`a` tag) to a public person of "Voir" concerned by a « Qui décide de ça ? » sheet. */
    const val PERSON_LINK_ROLE: String = "personne"

    /** The rhythm of a course (`ppf-rhythm`, [CourseRhythm]): absent for a course read at once. */
    const val RHYTHM: String = "ppf-rhythm"

    /** The level a teaching sheet serves (`ppf-level`, [TeachingLevel]). */
    const val LEVEL: String = "ppf-level"

    /** The official programme a teaching sheet serves, in one sentence with its Bulletin officiel (`ppf-programme`). */
    const val PROGRAMME: String = "ppf-programme"

    /** The role of the link (`a` tag) from a teaching sheet to the course it brings to the classroom. */
    const val COURSE_LINK_ROLE: String = "parcours"
}

/**
 * How a course is meant to be read (CO-19 « Je découvre », cahier fonctionnel M23): one lesson, that is one
 * step, a day. A suggestion only, never a lock (design loyalty, PP-09): the reader may go on at once, nothing
 * counts days or streaks, and the progress stays on the device like every course.
 */
enum class CourseRhythm(val tagValue: String) {
    DAILY("quotidien"),
    ;

    companion object {
        fun from(tagValue: String?): CourseRhythm? = entries.firstOrNull { it.tagValue == tagValue }
    }
}

/**
 * The verdicts of a fact-check (owner's decision of 7 November 2026, option A of the entry
 * « Décryptages : échelle des verdicts »). A verdict is about a claim, never about a person; « contredit
 * par les faits » rather than « mensonge » (point d'attention n° 7).
 */
enum class Verdict(val tagValue: String) {
    EXACT("exact"),
    MOSTLY_EXACT("plutot-exact"),
    MISLEADING("trompeur"),
    CONTRADICTED("contredit-par-les-faits"),
    UNVERIFIABLE("inverifiable"),
    ;

    companion object {
        fun fromTag(value: String?): Verdict? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/**
 * A fact-check (cahier fonctionnel, M6: « affirmation, verdict, explication, sources »): the claim
 * quoted word for word, where it circulates, the verdict, then the explanation in the body.
 */
data class Decryptage(
    val article: Article,
    val verdict: Verdict,
    val claim: String,
    /** Where and when the claim circulates (never a private person). */
    val claimContext: String?,
) {
    companion object {
        /** Null when the content is not a fact-check or misses its verdict or claim: it is then not shown. */
        fun from(article: Article): Decryptage? {
            if (article.type != ArticleType.DECRYPTAGE) return null
            val event = article.event
            val verdict = Verdict.fromTag(event.firstTagValue(ComprendreTags.VERDICT)) ?: return null
            val claim = event.firstTagValue(ComprendreTags.CLAIM)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return Decryptage(article, verdict, claim, event.firstTagValue(ComprendreTags.CLAIM_CONTEXT)?.trim()?.takeIf { it.isNotEmpty() })
        }
    }
}

/**
 * A story of the history of social movements and revolutions (vision, pillar « Comprendre »;
 * cahier fonctionnel, M7; owner's decision of 7 November 2026). Same tags as a movement of "Voir"
 * (`ppf-country`, `ppf-period`, `ppf-claim`, `ppf-result`), so that both can be read together later.
 */
data class HistoryStory(
    val article: Article,
    val country: String,
    /** Start of the period: `AAAA` or `AAAA-MM-JJ`. */
    val start: String,
    /** End of the period, null when the same as the start or unknown. */
    val end: String?,
    val claims: List<String>,
    val results: List<String>,
) {
    /** « 1936-1938 », « 1848 », or with the full dates written in French. */
    val period: String get() {
        val from = ContentBlocks.frenchDate(start) ?: start
        val to = end?.let { ContentBlocks.frenchDate(it) ?: it }
        return if (to == null || to == from) from else "$from-$to"
    }

    /** The year the period starts, to order the stories. */
    val startYear: Int? get() = start.take(4).toIntOrNull()

    companion object {
        fun from(article: Article): HistoryStory? {
            if (article.type != ArticleType.HISTOIRE) return null
            val event = article.event
            val country = event.firstTagValue(ComprendreTags.COUNTRY)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val period = event.tags.firstOrNull { it.size >= 2 && it[0] == ComprendreTags.PERIOD } ?: return null
            val start = period[1].trim().takeIf { ContentBlocks.frenchDate(it) != null } ?: return null
            val end = period.getOrNull(2)?.trim()?.takeIf { ContentBlocks.frenchDate(it) != null }
            return HistoryStory(
                article = article,
                country = country,
                start = start,
                end = end,
                claims = event.tagValues(ComprendreTags.CLAIM).map { it.trim() }.filter { it.isNotEmpty() },
                results = event.tagValues(ComprendreTags.RESULT).map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
    }
}

/** The levels of the chain of decision, from the closest to the farthest (cahier fonctionnel, M13.1). */
enum class DecisionLevel(val key: String) {
    COMMUNE("commune"),
    INTERCOMMUNALITE("intercommunalite"),
    DEPARTEMENT("departement"),
    REGION("region"),
    ETAT("etat"),
    UNION_EUROPEENNE("union-europeenne"),
    ;

    companion object {
        fun fromKey(key: String?): DecisionLevel? = entries.firstOrNull { it.key == key?.trim() }
    }
}

/** What one level decides on the topic, and which elected officials are concerned. */
data class DecisionStep(val level: DecisionLevel, val roles: List<PublicRole>, val description: String)

/**
 * A « Qui décide de ça ? » sheet (cahier fonctionnel, M13.1): for a topic of daily life, the chain
 * of decision, the elected officials concerned (a box at the start of the content, owner's
 * decision of 7 November 2026), then the explanation and « Et maintenant ? ».
 *
 * The chain is the `::: decideurs` block of the body, one line per level:
 * `- niveau | rôles | ce que ce niveau décide`. The public persons of "Voir" linked by `a` tags of role
 * `personne` are named in the box under the levels whose roles they hold.
 */
data class QuiDecide(
    val article: Article,
    val steps: List<DecisionStep>,
    /** Addresses (`30023:<clé>:<identifiant>`) of the public persons linked to the sheet. */
    val persons: List<String>,
    /** The body without its `::: decideurs` block, to display under the box. */
    val body: String,
) {
    companion object {
        const val BLOCK: String = "decideurs"

        fun from(article: Article): QuiDecide? {
            if (article.type != ArticleType.QUI_DECIDE) return null
            val (steps, body, _) = parse(article.body)
            if (steps.isEmpty()) return null
            val persons = article.event.tags
                .filter { it.size >= 4 && it[0] == "a" && it[3] == ComprendreTags.PERSON_LINK_ROLE }
                .map { it[1] }
            return QuiDecide(article, steps, persons, body)
        }

        /** The chain of decision, the body without it, and the problems found (for the content signer). */
        fun parse(markdown: String): Triple<List<DecisionStep>, String, List<String>> {
            val blocks = mutableListOf<List<String>>()
            val body = ContentBlocks.extract(markdown.lines(), BLOCK, blocks).joinToString("\n").trim()
            val problems = mutableListOf<String>()
            if (blocks.size != 1) problems += "a sheet needs exactly one \"::: $BLOCK\" block, found ${blocks.size}"
            val steps = mutableListOf<DecisionStep>()
            for (raw in blocks.firstOrNull().orEmpty()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val parts = line.removePrefix("-").removePrefix("*").split('|').map { it.trim() }
                val level = DecisionLevel.fromKey(parts.getOrNull(0))
                val roles = parts.getOrNull(1)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val known = roles.mapNotNull { PublicRole.fromTag(it) }
                val description = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                when {
                    !line.startsWith("-") && !line.startsWith("*") -> problems += "a decideurs line starts with \"- \": \"$line\""
                    parts.size != 3 -> problems += "a decideurs line is \"- niveau | rôles | description\": \"$line\""
                    level == null -> problems += "unknown level \"${parts[0]}\" (${DecisionLevel.entries.joinToString { it.key }})"
                    known.size != roles.size -> problems += "unknown role in \"${parts[1]}\" (${PublicRole.entries.filter { it != PublicRole.OTHER }.joinToString { it.tagValue }})"
                    description == null -> problems += "a decideurs line needs a description: \"$line\""
                    steps.any { it.level == level } -> problems += "level \"${level.key}\" appears twice"
                    else -> steps += DecisionStep(level, known, description)
                }
            }
            if (steps.zipWithNext().any { (a, b) -> a.level.ordinal > b.level.ordinal }) problems += "levels go from the closest (commune) to the farthest (union-europeenne)"
            if (blocks.isNotEmpty() && steps.isEmpty() && problems.isEmpty()) problems += "the decideurs block is empty"
            return Triple(steps.sortedBy { it.level.ordinal }, body, problems)
        }
    }
}
