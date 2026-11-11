package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.core.urne.Ballot
import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom

/**
 * Settings of the ballot boxes (D-110), from the environment only:
 *
 * - PPF_VOTE_SEEDS_DIR: directory of the seeds of the voting keys, one read-only file of 32 bytes
 *   per version, named by its number (`1`, `2`…), set up by the infrastructure. Without it, in
 *   production and preproduction, the voting API answers 503; in development, an in-memory seed is
 *   drawn at startup (its tokens die with the process).
 * - PPF_VOTE_POW_REGISTER, PPF_VOTE_POW_TOKENS, PPF_VOTE_POW_BALLOT: bits of proof of work asked at
 *   registration, for each token request and for each ballot (private thresholds, D-110, section 5).
 *   The ballot's is at most [Ballot.DIFFICULTY], the same number that every device makes.
 * - PPF_PUBLIC_URL: public address of the server (for example https://politisation-populaire-francaise.org),
 *   the exact URL that NIP-98 authorizations must name. Required outside development: without it,
 *   voting stays unavailable (an authorization made for another server would pass; review R5). In
 *   development, only the path is compared.
 * - PPF_VOTE_MAX_TOKENS_PER_REQUEST: tokens delivered by one request at most (the rest on the next one).
 * - PPF_VOTE_MIN_DISPLAY: while a box is open, a level column with fewer ballots is hidden. Default 1: nothing is
 *   hidden, the results show from the first ballot (decision of the owner of 2026-11-11, D-156).
 */
data class UrneConfig(
    val seeds: VoteSeeds?,
    val registerDifficulty: Int,
    val tokenDifficulty: Int,
    val ballotDifficulty: Int,
    val publicUrl: String?,
    val maxTokensPerRequest: Int = 500,
    /** While a box is open, a level column with fewer ballots is hidden; 1 hides nothing (D-156). */
    val minDisplay: Int = 1,
) {
    companion object {
        private val log = LoggerFactory.getLogger(UrneConfig::class.java)

        fun fromEnvironment(env: (String) -> String? = System::getenv): UrneConfig {
            val environment = env("PPF_ENVIRONMENT") ?: "development"
            val development = environment == "development"
            val publicUrl = env("PPF_PUBLIC_URL")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            val seeds = if (!development && publicUrl == null) {
                log.warn("Voting: PPF_PUBLIC_URL is not set; the voting API answers 503.")
                null
            } else env("PPF_VOTE_SEEDS_DIR")?.let { VoteSeeds.fromDirectory(File(it)) }
                ?: if (development) {
                    log.warn("Voting: PPF_VOTE_SEEDS_DIR is not set; a seed drawn at startup is used (development only).")
                    VoteSeeds.random()
                } else {
                    log.warn("Voting: PPF_VOTE_SEEDS_DIR is not set; the voting API answers 503.")
                    null
                }
            fun bits(name: String, production: Int, dev: Int): Int = env(name)?.toIntOrNull()?.coerceIn(0, 32) ?: if (development) dev else production
            return UrneConfig(
                seeds = seeds,
                registerDifficulty = bits("PPF_VOTE_POW_REGISTER", production = 20, dev = 8),
                tokenDifficulty = bits("PPF_VOTE_POW_TOKENS", production = 12, dev = 4),
                ballotDifficulty = bits("PPF_VOTE_POW_BALLOT", production = Ballot.DIFFICULTY, dev = 4).coerceAtMost(Ballot.DIFFICULTY),
                publicUrl = publicUrl,
                maxTokensPerRequest = env("PPF_VOTE_MAX_TOKENS_PER_REQUEST")?.toIntOrNull()?.coerceIn(1, 5000) ?: 500,
                minDisplay = env("PPF_VOTE_MIN_DISPLAY")?.toIntOrNull()?.coerceIn(1, 1000) ?: 1,
            )
        }
    }
}

/**
 * The seeds of the voting keys, by version (D-110, 6.1). A seed protects the integrity of the votes
 * (whoever has it can make tokens), never their secrecy (the blinding does not depend on it). Its
 * bytes are never logged nor returned.
 */
class VoteSeeds private constructor(private val byVersion: Map<Int, ByteArray>) {
    init {
        require(byVersion.isNotEmpty()) { "at least one seed" }
        require(byVersion.values.all { it.size == SEED_SIZE }) { "a seed is $SEED_SIZE bytes" }
    }

    /** The version used for new ballot boxes: the highest. */
    val current: Int get() = byVersion.keys.max()

    fun seed(version: Int): ByteArray? = byVersion[version]?.copyOf()

    override fun toString(): String = "VoteSeeds(versions=${byVersion.keys.sorted()})"

    companion object {
        const val SEED_SIZE: Int = 32

        fun of(byVersion: Map<Int, ByteArray>): VoteSeeds = VoteSeeds(byVersion.mapValues { it.value.copyOf() })

        fun random(): VoteSeeds = VoteSeeds(mapOf(1 to ByteArray(SEED_SIZE).also { SecureRandom().nextBytes(it) }))

        /** Every file named by a positive number in [directory]; a file of the wrong size stops the server. */
        fun fromDirectory(directory: File): VoteSeeds {
            val files = directory.listFiles().orEmpty().filter { it.isFile && it.name.toIntOrNull()?.let { v -> v > 0 } == true }
            check(files.isNotEmpty()) { "Voting: no seed file in PPF_VOTE_SEEDS_DIR" }
            return VoteSeeds(
                files.associate { file ->
                    val bytes = file.readBytes()
                    check(bytes.size == SEED_SIZE) { "Voting: seed file ${file.name} is not $SEED_SIZE bytes" }
                    file.name.toInt() to bytes
                },
            )
        }
    }
}
