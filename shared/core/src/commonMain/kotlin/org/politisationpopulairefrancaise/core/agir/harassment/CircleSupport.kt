package org.politisationpopulairefrancaise.core.agir.harassment

/**
 * « Prévenir mon cercle » (AG-59, « soutien du cercle »): the plug point for the circles of M28
 * (pole Communauté, not coded yet). When they exist, the person will be able to tell the people of
 * a circle she chooses that she is targeted, end-to-end encrypted like the private messages, with
 * what she decides to say; never the evidence itself unless she attaches it, never automatically.
 *
 * Step 1 has no implementation: [NoCircleSupport] says it is not available, and the screen shows
 * one sentence « bientôt ». Nothing is sent.
 */
interface CircleSupport {
    /** False until the circles of M28 exist on this device. */
    val available: Boolean

    /** The circles the person may warn, by their identifier on the device and the name she gave them. */
    suspend fun circles(): List<CircleChoice>

    /** Tells the circle [circleId] that the person needs support, with [message] in her words. */
    suspend fun warn(circleId: String, message: String): Boolean
}

/** A circle of M28 the person may choose. */
data class CircleChoice(val id: String, val name: String)

/** No circle yet (step 1): nothing to choose, nothing sent. */
object NoCircleSupport : CircleSupport {
    override val available: Boolean = false

    override suspend fun circles(): List<CircleChoice> = emptyList()

    override suspend fun warn(circleId: String, message: String): Boolean = false
}
