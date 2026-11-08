package org.politisationpopulairefrancaise.core.agir.manifestation

/** How a number is reached: a call, or a text message (the 114 for deaf and hard-of-hearing people). */
enum class EmergencyChannel { CALL, SMS }

/**
 * A public number of the emergency services or of a listening line. [id] is stable (tests,
 * labels); [number] is what is shown, [digits] what the call or the text message dials.
 */
data class EmergencyNumber(
    val id: String,
    val number: String,
    val channel: EmergencyChannel = EmergencyChannel.CALL,
    /** True for the numbers of a life-threatening emergency, offered before anything else. */
    val vital: Boolean = false,
) {
    val digits: String get() = number.filter(Char::isDigit)

    /** What the system opens: `tel:15`, `sms:114`. No permission is needed, the user confirms in the dialer. */
    val uri: String get() = (if (channel == EmergencyChannel.SMS) "sms:" else "tel:") + digits
}

/**
 * The public numbers shown by the AIDE button and the protest mode (M25, AG-13, AG-27), in their
 * order: PPF never replaces the emergency services, so the 15 and the 112 always come first.
 *
 * Source, checked on 7 November 2026: service-public.gouv.fr, « Quels sont les numéros en cas
 * d'urgence (Samu, pompiers...) et les numéros d'écoute ? » (F33954, verified by the DILA on
 * 25 August 2026), and « Porter plainte » (F1435, verified on 8 July 2026) for the 116 006.
 */
object EmergencyNumbers {
    /** SAMU: medical emergencies. */
    val SAMU = EmergencyNumber("samu", "15", vital = true)

    /** The European emergency number, from any country of the European Union. */
    val EUROPE = EmergencyNumber("europe", "112", vital = true)

    /** Police secours. */
    val POLICE = EmergencyNumber("police", "17")

    /** Firefighters. */
    val FIREFIGHTERS = EmergencyNumber("pompiers", "18")

    /** Deaf, hard-of-hearing, deafblind and aphasic people: by text message (also by application or website). */
    val DEAF = EmergencyNumber("sourds", "114", EmergencyChannel.SMS)

    /** Children in danger, or anyone worried about a child. */
    val CHILDREN = EmergencyNumber("enfance", "119")

    /** Missing children: information and support for the families. */
    val MISSING_CHILD = EmergencyNumber("enfant-disparu", "116 000")

    /** Suicide prevention, for people in distress or worried about someone else. */
    val SUICIDE = EmergencyNumber("suicide", "31 14")

    /** Help for the victims of an offence, every day from 9:00 to 20:00, free. */
    val VICTIMS = EmergencyNumber("victimes", "116 006")

    /** The emergency services, in the order of the AIDE button: 15 and 112 first. */
    val emergency: List<EmergencyNumber> = listOf(SAMU, EUROPE, POLICE, FIREFIGHTERS, DEAF)

    /** The other useful numbers of the protest mode (lost child, support after an injury, distress). */
    val listening: List<EmergencyNumber> = listOf(CHILDREN, MISSING_CHILD, VICTIMS, SUICIDE)

    val all: List<EmergencyNumber> = emergency + listening

    const val SOURCE_URL: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/F33954"
}
