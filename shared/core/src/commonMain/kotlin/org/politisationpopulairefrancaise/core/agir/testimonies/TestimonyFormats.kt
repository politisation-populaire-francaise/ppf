package org.politisationpopulairefrancaise.core.agir.testimonies

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey

/**
 * Formats of the testimonies (M5, AG-07, AG-08, AG-54; docs/integration/agir-temoignages.md).
 *
 * A testimony never travels in clear: its text and the keys of its files are a rumor (NIP-59)
 * sealed by the witness's account and gift-wrapped for the public key of the moderation team
 * ([TestimonyRecipients.moderation]); the original untouched file, when the witness chooses to give
 * it, goes in a second envelope for the key of the conservation of evidence
 * ([TestimonyRecipients.evidence]), which the moderators do not hold. The server only sees two
 * envelopes signed by one-time keys and encrypted files named by their SHA-256.
 */
object TestimonyFormats {
    /** The testimony itself (rumor inside the envelope for the moderation team). Private kind. */
    const val KIND_TESTIMONY: Int = 8590

    /** The original kept for the partner lawyers (rumor inside the envelope for the evidence key). Private kind. */
    const val KIND_EVIDENCE: Int = 8592

    const val TAG_FACT_DATE: String = "ppf-fact-date"
    const val TAG_FACT_TIME: String = "ppf-fact-time"
    const val TAG_PLACE: String = "location"
    const val TAG_CALL: String = "a"
    const val TAG_CHARTER: String = "ppf-charter"
    const val TAG_MEDIA: String = "imeta"
    const val TAG_PROVENANCE: String = "ppf-provenance"
    const val TAG_ORIGINAL: String = "ppf-original"
    const val TAG_TESTIMONY: String = "e"

    /** `ppf-original`: the untouched original was given for the lawyers, or stays on the witness's device. */
    const val ORIGINAL_GIVEN: String = "evidence"
    const val ORIGINAL_KEPT: String = "device"

    const val CHARTER_VERSION: String = "1.0"

    /** Fields of an `imeta` tag (NIP-92 form: "name value"), the encryption ones as in NIP-17 file messages. */
    const val MEDIA_FILE: String = "ppf-file"
    const val MEDIA_MIME: String = "m"
    const val MEDIA_SHA256: String = "x"
    const val MEDIA_ORIGINAL_SHA256: String = "ox"
    const val MEDIA_SIZE: String = "size"
    const val MEDIA_DIMENSIONS: String = "dim"
    const val MEDIA_KEY: String = "decryption-key"
    const val MEDIA_ALGORITHM: String = "encryption-algorithm"
    const val MEDIA_BLURRED: String = "ppf-blurred"
    const val MEDIA_REENCODED: String = "ppf-reencoded"

    const val MIN_TEXT_LENGTH: Int = 20
    const val MAX_TEXT_LENGTH: Int = 8000
    const val MAX_PLACE_LENGTH: Int = 200

    /** One photo per testimony in this first version. */
    const val MAX_PHOTOS: Int = 1

    /** Largest original accepted on the device, in bytes. */
    const val MAX_ORIGINAL_BYTES: Int = 25 * 1024 * 1024

    /** Largest encrypted file the server accepts, in bytes (the published photo is far smaller). */
    const val MAX_FILE_BYTES: Int = 32 * 1024 * 1024

    /** `ppf-type` of a published testimony (kind 30023, signed by the team after review). */
    const val PUBLISHED_TYPE: String = "temoignage"

    /** Where the server serves the published photos: `/api/v1/media/<sha256>`. */
    const val MEDIA_PATH: String = "/api/v1/media/"

    /** One `imeta` field. */
    fun field(name: String, value: String): String = "$name $value"

    /** The fields of an `imeta` tag by name (the first value of each). */
    fun fields(tag: List<String>): Map<String, String> = tag.drop(1).mapNotNull { entry ->
        val space = entry.indexOf(' ')
        if (space <= 0) null else entry.substring(0, space) to entry.substring(space + 1)
    }.distinctBy { it.first }.toMap()
}

/**
 * The two keys testimonies are encrypted for. In production they come from the team (published,
 * built into the application, and `PPF_MODERATION_KEY`, `PPF_EVIDENCE_KEY` on the server); their
 * secret keys never touch a server. [EXAMPLE] is for development and tests only: its secret keys are
 * derived from sentences written in this file, so **anybody can decrypt what is sent to it**. The
 * application shows it as an example key, and the production server refuses to start with it.
 */
data class TestimonyRecipients(val moderation: PublicKey, val evidence: PublicKey) {
    val isExample: Boolean get() = this == EXAMPLE

    companion object {
        /** Public sentences: the example keys protect nothing, on purpose. */
        const val EXAMPLE_MODERATION_SEED: String = "PPF [EXEMPLE] cle de moderation des temoignages : publique, ne protege rien"
        const val EXAMPLE_EVIDENCE_SEED: String = "PPF [EXEMPLE] cle de conservation des preuves : publique, ne protege rien"

        /** The example key pairs; only the development server and the tests use their secret halves. */
        fun exampleModerationKeys(): IdentityKeys = IdentityKeys.fromSecretKey(Crypto.sha256(EXAMPLE_MODERATION_SEED.encodeToByteArray()))

        fun exampleEvidenceKeys(): IdentityKeys = IdentityKeys.fromSecretKey(Crypto.sha256(EXAMPLE_EVIDENCE_SEED.encodeToByteArray()))

        val EXAMPLE: TestimonyRecipients by lazy {
            val moderation = exampleModerationKeys()
            val evidence = exampleEvidenceKeys()
            try {
                TestimonyRecipients(moderation.publicKey, evidence.publicKey)
            } finally {
                moderation.destroy()
                evidence.destroy()
            }
        }
    }
}
