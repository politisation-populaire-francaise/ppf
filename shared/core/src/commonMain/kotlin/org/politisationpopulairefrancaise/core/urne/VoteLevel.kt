package org.politisationpopulairefrancaise.core.urne

/**
 * The three levels of voters decided by the owner on 7 November 2026 (D-112). A ballot carries
 * only its level, never its account: the level is the key that issued its token.
 */
enum class VoteLevel(val code: String) {
    /** Registered with a proof of work only. */
    NON_VERIFIE("non-verifie"),

    /** Validated in person by a volunteer approved by PPF through their organisation. */
    VERIFIE("verifie"),

    /** Certified elected official (DE-23): their votes are shown apart, never added to the citizens' total. */
    ELU("elu"),
    ;

    /** The citizens' vote groups the two citizen levels; elected officials are counted apart. */
    val isCitizen: Boolean get() = this != ELU

    companion object {
        fun of(code: String): VoteLevel? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The `info` of DeriveKeyPair for one ballot box and one level (D-110): `ppf-urne-v1` ␟ urne ␟ salt
 * ␟ level. The salt (16 random bytes drawn at the opening, in hexadecimal) makes the keys of a box
 * its own even if its identifier came back (a box deleted and opened again, a seed shared by mistake
 * between two environments): a key published at a closing never fits another box (review R5).
 */
object UrneKeyInfo {
    const val VERSION: String = "ppf-urne-v1"
    private const val SEPARATOR: Char = '\u001f'

    fun of(urneId: String, salt: ByteArray, level: VoteLevel): ByteArray {
        require(SEPARATOR !in urneId) { "the identifier of a ballot box never contains U+001F" }
        require(salt.size == SALT_SIZE) { "a salt of $SALT_SIZE bytes" }
        return "$VERSION$SEPARATOR$urneId$SEPARATOR${salt.toHexString()}$SEPARATOR${level.code}".encodeToByteArray()
    }

    const val SALT_SIZE: Int = 16
}
