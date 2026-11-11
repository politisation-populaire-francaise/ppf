package org.politisationpopulairefrancaise.ui.agir.testimonies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.testimonies.MyTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.OutgoingTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.ServerTestimonyTransport
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyComposer
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyDraft
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyProblem
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyProgress
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRules
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySendResult
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStatuses
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStore
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyTransport
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.BlurZone
import org.politisationpopulairefrancaise.core.media.CaptureSource
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.MediaKind
import org.politisationpopulairefrancaise.core.media.MediaSniffer
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.NoTimestamping
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.PhotoResult
import org.politisationpopulairefrancaise.core.media.PreparedPhoto
import org.politisationpopulairefrancaise.core.media.Provenance
import org.politisationpopulairefrancaise.core.media.RemovedMetadata
import org.politisationpopulairefrancaise.core.media.StripResult
import org.politisationpopulairefrancaise.core.media.Timestamping
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.media.platformImageCodec
import org.politisationpopulairefrancaise.ui.media.toImageBitmap

/** A photo chosen for the testimony: the untouched original, its provenance signed at once, what will be removed, and its preview. */
class AttachedPhoto(
    val original: ByteArray,
    val provenance: SignedEvent,
    val kind: MediaKind,
    val removed: Set<RemovedMetadata>,
    /** Upright pixels to draw the zones on; null when the platform cannot decode this photo (no blur then). */
    val preview: ArgbImage?,
    val previewBitmap: ImageBitmap?,
)

/** Why a chosen file is not attached. */
enum class PhotoRefusal { VIDEO, AUDIO, HEIF, UNKNOWN, TOO_LARGE, MALFORMED, FAILED }

/** Where the sending of the testimony stands. */
sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    /** The server has it, waiting for review. */
    data class Sent(val id: String) : SendState
    /** Encrypted and kept on the device: it leaves when the network is back. */
    data class Queued(val id: String) : SendState
    data class Refused(val code: String) : SendState
    /** The photo with zones could not be blurred on this device: nothing was sent. */
    data object BlurUnavailable : SendState
}

/**
 * The state of "Témoigner" (AG-07) and of "Mes témoignages", kept for the whole run like the other
 * libraries of Agir; nothing is loaded before [start]. The draft lives in memory only (never written
 * in clear on the device); once encrypted, a testimony waits in [TestimonyStore] until the server has it.
 */
class TestimonyLibrary(
    private val store: TestimonyStore?,
    private val transport: TestimonyTransport?,
    private val scope: CoroutineScope,
    val recipients: TestimonyRecipients = TestimonyRecipients.EXAMPLE,
    private val codec: ImageCodec? = platformImageCodec(),
    private val timestamping: Timestamping = NoTimestamping,
    private val clock: () -> Long = ::currentEpochSeconds,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    var text by mutableStateOf("")
    var factDate by mutableStateOf("")
    var factTime by mutableStateOf("")
    var place by mutableStateOf("")
    var charterAccepted by mutableStateOf(false)

    /** False by default (principle 10); set only after the warning is confirmed. */
    var giveOriginal by mutableStateOf(false)

    /** The call for witnesses answered, with its title for the screen. */
    var call by mutableStateOf<Pair<String, String>?>(null)
        private set

    var photo by mutableStateOf<AttachedPhoto?>(null)
        private set
    var photoRefusal by mutableStateOf<PhotoRefusal?>(null)
        private set
    var preparingPhoto by mutableStateOf(false)
        private set
    var zones by mutableStateOf<List<BlurZone>>(emptyList())
        private set
    var sendState by mutableStateOf<SendState>(SendState.Idle)
        private set

    /** True once the user tried to send: errors are shown from then on. */
    var showErrors by mutableStateOf(false)
        private set

    var mine by mutableStateOf<List<MyTestimony>>(emptyList())
        private set

    private var started = false

    /** Loads "Mes témoignages", sends what waits and asks where the others stand; idempotent. */
    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun refresh() {
        scope.launch {
            flushUnsent()
            updateStatuses()
            reload()
        }
    }

    /** Opens the form as an answer to a call for witnesses (address `30023:<key>:<d>`). */
    fun answer(callAddress: String, callTitle: String) {
        if (call?.first != callAddress) call = callAddress to callTitle
        sendState = SendState.Idle
    }

    fun detachCall() {
        call = null
    }

    // --- Photo -----------------------------------------------------------------------------------

    /**
     * A photo of the protected camera (AG-58, D-089), taken at [takenAt]: attached like a chosen one,
     * its provenance saying "camera" and when it was taken. A new form if the last testimony left.
     */
    fun attachFromCamera(bytes: ByteArray, takenAt: Long) {
        if (sendState is SendState.Sent || sendState is SendState.Queued) sendState = SendState.Idle
        attach(bytes, CaptureSource.CAMERA, capturedAt = takenAt)
    }

    /**
     * A file the user chose: its provenance is signed at once, before anything is changed (AG-54);
     * [capturedAt] when the device knows when it was taken (the protected camera), else now.
     */
    fun attach(bytes: ByteArray, source: CaptureSource = CaptureSource.IMPORT, capturedAt: Long? = null) {
        photoRefusal = null
        val kind = MediaSniffer.kindOf(bytes)
        val refusal = when {
            bytes.size > PhotoPipeline.MAX_ORIGINAL_BYTES -> PhotoRefusal.TOO_LARGE
            kind == MediaKind.Video -> PhotoRefusal.VIDEO
            kind == MediaKind.Audio -> PhotoRefusal.AUDIO
            kind == MediaKind.Unknown -> PhotoRefusal.UNKNOWN
            kind == MediaKind.Heif && codec == null -> PhotoRefusal.HEIF
            else -> null
        }
        if (refusal != null) {
            photoRefusal = refusal
            return
        }
        preparingPhoto = true
        scope.launch {
            try {
                val provenance = if (capturedAt != null) {
                    Provenance.signWithOneTimeKey(bytes, mimeOf(kind), source, capturedAt)
                } else {
                    Provenance.signWithOneTimeKey(bytes, mimeOf(kind), source)
                }
                // Only the digest of the statement leaves (D-111); the stand-in stamps nothing yet.
                runCatching { timestamping.stamp(provenance.id.hexToByteArray()) }
                val prepared = withContext(io) {
                    val strip = if (kind is MediaKind.Image) MetadataStripper.strip(bytes) else null
                    if (strip is StripResult.Malformed) return@withContext null
                    val removed = (strip as? StripResult.Clean)?.image?.removed ?: setOf(RemovedMetadata.EXIF, RemovedMetadata.OTHER)
                    val preview = PhotoPipeline.preview(bytes, codec)
                    if (kind == MediaKind.Heif && preview == null) return@withContext PhotoRefusal.HEIF
                    AttachedPhoto(bytes, provenance, kind, removed, preview, preview?.let { runCatching { it.toImageBitmap() }.getOrNull() })
                }
                when (prepared) {
                    is AttachedPhoto -> {
                        photo = prepared
                        zones = emptyList()
                    }
                    is PhotoRefusal -> photoRefusal = prepared
                    else -> photoRefusal = PhotoRefusal.MALFORMED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoRefusal = PhotoRefusal.FAILED
            } finally {
                preparingPhoto = false
            }
        }
    }

    fun refusePhoto(refusal: PhotoRefusal) {
        photoRefusal = refusal
    }

    fun removePhoto() {
        photo = null
        zones = emptyList()
        giveOriginal = false
    }

    private fun aspect(): Float = photo?.preview?.let { it.width.toFloat() / it.height } ?: 1f

    /** A zone centred where the user touched the preview ([x], [y] in fractions of the picture). */
    fun addZoneAt(x: Float, y: Float) {
        if (photo?.preview == null) return
        zones = zones + BlurZone.around(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), DEFAULT_ZONE_SIDE, aspect())
    }

    /** The same without pointing: a zone in the middle, then moved with the buttons (keyboard, screen readers). */
    fun addZoneInCentre() = addZoneAt(0.5f, 0.5f)

    fun moveLastZone(dx: Float, dy: Float) {
        val last = zones.lastOrNull() ?: return
        zones = zones.dropLast(1) + last.moved(dx = dx, dy = dy)
    }

    fun resizeLastZone(scale: Float) {
        val last = zones.lastOrNull() ?: return
        zones = zones.dropLast(1) + last.moved(scale = scale)
    }

    fun removeLastZone() {
        zones = zones.dropLast(1)
    }

    // --- Sending ---------------------------------------------------------------------------------

    /** The draft as the rules see it: dates typed the French way (JJ/MM/AAAA, HH:MM or 18h05) become ISO. */
    fun draft(prepared: PreparedPhoto? = null): TestimonyDraft = TestimonyDraft(
        text = text,
        factDate = isoDate(factDate),
        factTime = isoTime(factTime),
        place = place.trim().takeIf { it.isNotEmpty() },
        callAddress = call?.first,
        photo = prepared,
        giveOriginal = giveOriginal && prepared != null,
        charterAccepted = charterAccepted,
    )

    fun problems(): List<TestimonyProblem> = TestimonyRules.check(draft(), today())

    private fun today(): String = ParisTime.moment(clock()).date.let { d -> "${d.year.toString().padStart(4, '0')}-${two(d.month)}-${two(d.day)}" }

    /** Encrypts and sends the testimony; [account] is required (the seal of the envelope). */
    fun send(account: MessagingSigner) {
        showErrors = true
        if (problems().isNotEmpty() || sendState == SendState.Sending || preparingPhoto) return
        sendState = SendState.Sending
        scope.launch {
            try {
                val attached = photo
                val prepared = if (attached != null) {
                    when (val result = withContext(io) { PhotoPipeline.prepare(attached.original, attached.provenance, codec, zones) }) {
                        is PhotoResult.Ready -> result.photo
                        PhotoResult.BlurUnavailable -> {
                            sendState = SendState.BlurUnavailable
                            return@launch
                        }
                        else -> {
                            sendState = SendState.Refused("photo")
                            return@launch
                        }
                    }
                } else {
                    null
                }
                val built = withContext(io) { TestimonyComposer.build(draft(prepared), account, recipients, clock()) }
                val now = clock()
                withContext(io) { store?.keep(built, now) }
                sendState = when (val result = transport?.send(built.outgoing()) ?: TestimonySendResult.Unavailable()) {
                    is TestimonySendResult.Sent -> {
                        withContext(io) { store?.markSent(built.id, clock()) }
                        SendState.Sent(built.id)
                    }
                    is TestimonySendResult.Unavailable -> SendState.Queued(built.id)
                    is TestimonySendResult.Refused -> {
                        // Refused for good: nothing is kept that cannot leave.
                        withContext(io) { store?.forget(built.id) }
                        SendState.Refused(result.code)
                    }
                }
                if (sendState is SendState.Sent || sendState is SendState.Queued) clearDraft()
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendState = SendState.Refused("internal")
            }
        }
    }

    /** Back to an empty form after a testimony left (or waits, encrypted). */
    private fun clearDraft() {
        text = ""
        factDate = ""
        factTime = ""
        place = ""
        charterAccepted = false
        giveOriginal = false
        call = null
        photo = null
        zones = emptyList()
        showErrors = false
    }

    /** A new testimony after the confirmation. */
    fun newTestimony() {
        sendState = SendState.Idle
    }

    private suspend fun flushUnsent() {
        val store = store ?: return
        val transport = transport ?: return
        for (id in withContext(io) { store.unsent() }) {
            val outgoing: OutgoingTestimony = withContext(io) { store.outgoing(id) } ?: continue
            when (transport.send(outgoing)) {
                is TestimonySendResult.Sent -> withContext(io) { store.markSent(id, clock()) }
                is TestimonySendResult.Refused -> withContext(io) { store.forget(id) }
                is TestimonySendResult.Unavailable -> return
            }
        }
    }

    private suspend fun updateStatuses() {
        val store = store ?: return
        val transport = transport ?: return
        val ids = withContext(io) { store.all() }.filter { it.progress == TestimonyProgress.PENDING }.map { it.id }
        for (chunk in ids.chunked(TestimonyTransport.MAX_IDS)) {
            when (val result = transport.statuses(chunk)) {
                is TestimonyStatuses.Known -> withContext(io) { result.states.forEach { store.update(it, clock()) } }
                is TestimonyStatuses.Unavailable -> return
            }
        }
    }

    private suspend fun reload() {
        mine = withContext(io) { store?.all().orEmpty() }
    }

    /** Forgets the list on this device (the user's choice after a warning, or the erasure of the keys). */
    fun forgetAll() {
        clearDraft()
        mine = emptyList()
        scope.launch { withContext(io) { store?.clear() } }
    }

    companion object {
        /** Side of a new zone, as a fraction of the shorter side of the picture: about a face at arm's length. */
        const val DEFAULT_ZONE_SIDE: Float = 0.2f

        /** One step of the move buttons, as a fraction of the picture. */
        const val MOVE_STEP: Float = 0.05f

        private fun two(value: Int) = value.toString().padStart(2, '0')

        private fun mimeOf(kind: MediaKind): String = when (kind) {
            is MediaKind.Image -> kind.format.mimeType
            MediaKind.Heif -> "image/heic"
            else -> "application/octet-stream"
        }

        /** "05/11/2026" or "2026-11-05" to "2026-11-05"; anything else is kept as typed (and refused by the rules). */
        fun isoDate(typed: String): String? {
            val value = typed.trim()
            if (value.isEmpty()) return null
            val french = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").matchEntire(value)
            return if (french != null) {
                val (day, month, year) = french.destructured
                "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
            } else {
                value
            }
        }

        /** "18:05", "18h05", "18h" to "18:05", "18:00". */
        fun isoTime(typed: String): String? {
            val value = typed.trim().lowercase()
            if (value.isEmpty()) return null
            val match = Regex("""(\d{1,2})\s*[:h]\s*(\d{2})?""").matchEntire(value) ?: return value
            val (hours, minutes) = match.destructured
            return "${hours.padStart(2, '0')}:${minutes.ifEmpty { "00" }}"
        }
    }
}

/** The library of the testimonies, built on the device's database and the configured server. */
@Composable
fun rememberTestimonyLibrary(dependencies: AppDependencies, start: Boolean = true): TestimonyLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        TestimonyLibrary(
            store = dependencies.database?.let(::TestimonyStore),
            transport = (dependencies.remote as? ServerPublicationPort)?.let(ServerTestimonyTransport::of),
            scope = scope,
        ).also { if (start) it.start() }
    }
}
