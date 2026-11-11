package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.core.agir.harassment.CircleSupport
import org.politisationpopulairefrancaise.core.agir.harassment.Evidence
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceDraft
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceFormat
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceImageRefusal
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceImageResult
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceImages
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceLocker
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceProblem
import org.politisationpopulairefrancaise.core.agir.harassment.HarassmentReports
import org.politisationpopulairefrancaise.core.agir.harassment.KeepResult
import org.politisationpopulairefrancaise.core.agir.harassment.NoCircleSupport
import org.politisationpopulairefrancaise.core.agir.harassment.Offence
import org.politisationpopulairefrancaise.core.agir.harassment.PreparedEvidenceImage
import org.politisationpopulairefrancaise.core.agir.harassment.ReportInput
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.media.platformImageCodec
import org.politisationpopulairefrancaise.ui.media.toImageBitmap

/** What went wrong last, said in the kit. */
enum class KitProblem { KEEP_FAILED, OPEN_FAILED, DELETE_FAILED, IMAGE_UNSUPPORTED, IMAGE_TOO_LARGE, IMAGE_MALFORMED, IMAGE_FAILED }

/** What went right last. */
enum class KitNotice { KEPT, DELETED }

/** The three templates of the kit. */
enum class ReportKind { PLATFORM, PHAROS, COMPLAINT }

/** The piece of evidence open in the kit, with its picture decrypted in memory once ready. */
data class ViewedEvidence(val evidence: Evidence, val bitmap: ImageBitmap?, val loading: Boolean)

/**
 * The kit against harassment and doxxing (AG-59) for the whole run of the shell: the evidence of
 * the platform's vault ([vault], null where there is none: computer, iOS), the form of a new piece
 * of evidence, and the templates. Everything typed (the form, the name and the email address of the
 * templates) stays in memory; decrypted pictures too, only while the kit is shown and PPF unlocked
 * ([dropDecrypted]). Nothing here talks to a server, a platform or an authority.
 *
 * The quick erase part `agir-harcelement` ([eraseFromDevice]) is registered by the shell.
 */
class HarassmentKitState(
    vault: ProtectedVault?,
    private val scope: CoroutineScope,
    private val codec: ImageCodec? = platformImageCodec(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** « Prévenir mon cercle » (M28): nothing until the circles exist. */
    val circle: CircleSupport = NoCircleSupport,
) {
    private val locker: EvidenceLocker? = vault?.let { EvidenceLocker(it, clock) }

    /** False where this platform keeps no evidence: the screen says it is on Android; the templates stay. */
    val available: Boolean get() = locker != null

    var evidence: List<Evidence> by mutableStateOf(emptyList())
        private set

    /** Records that could not be read (never shown). */
    var unreadable: Int by mutableStateOf(0)
        private set

    var loading: Boolean by mutableStateOf(false)
        private set

    /** True while the form of a new piece of evidence is open. */
    var adding: Boolean by mutableStateOf(false)
        private set

    var draft: EvidenceDraft by mutableStateOf(EvidenceDraft())
        private set

    /** The picture of the form, cleaned, in memory only. */
    var draftImage: PreparedEvidenceImage? by mutableStateOf(null)
        private set

    var draftThumbnail: ImageBitmap? by mutableStateOf(null)
        private set

    /** What the last attempt to keep found wrong. */
    var draftProblems: Set<EvidenceProblem> by mutableStateOf(emptySet())
        private set

    /** True while a piece of evidence is being encrypted. */
    var saving: Boolean by mutableStateOf(false)
        private set

    var viewing: ViewedEvidence? by mutableStateOf(null)
        private set

    var problem: KitProblem? by mutableStateOf(null)
        private set

    var notice: KitNotice? by mutableStateOf(null)
        private set

    // The templates: in memory only, never written.
    var report: ReportKind by mutableStateOf(ReportKind.PLATFORM)
    var explanation: String by mutableStateOf("")
    var offences: Set<Offence> by mutableStateOf(emptySet())
    var name: String by mutableStateOf("")
    var email: String by mutableStateOf("")
    var goodFaith: Boolean by mutableStateOf(false)
    var author: String by mutableStateOf("")
    var city: String by mutableStateOf("")

    /** The evidence the person left out of the templates (all of it is in by default). */
    var excluded: Set<String> by mutableStateOf(emptySet())
        private set

    private var generation = 0
    private var loadJob: Job? = null

    /** Lists the evidence kept on this device. */
    fun load() {
        val locker = locker ?: return
        loadJob?.cancel()
        loading = true
        val token = generation
        loadJob = scope.launch {
            try {
                // The thumbnail of a picture still in the form comes back after a lock.
                val pending = draftImage
                if (pending != null && draftThumbnail == null) draftThumbnail = withContext(io) { thumbnailOf(pending.bytes) }
                val shelf = withContext(io) { locker.list() }
                if (token != generation) return@launch
                evidence = shelf.items
                unreadable = shelf.unreadable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) problem = KitProblem.OPEN_FAILED
            } finally {
                if (token == generation) loading = false
            }
        }
    }

    /** Opens the form, with the present moment already typed (the person corrects it). */
    fun startAdding() {
        if (!available) return
        if (draft.seenDate.isBlank() && draft.seenTime.isBlank()) {
            val (date, time) = EvidenceFormat.typedDateAndTime(clock())
            draft = draft.copy(seenDate = date, seenTime = time)
        }
        problem = null
        notice = null
        adding = true
    }

    fun updateDraft(change: (EvidenceDraft) -> EvidenceDraft) {
        draft = change(draft)
    }

    /** Leaves the form; what was typed is forgotten. */
    fun cancelAdding() {
        adding = false
        clearDraft()
    }

    /** A picture chosen or taken: cleaned on the device ([EvidenceImages]); the original bytes are wiped. */
    fun attachImage(original: ByteArray, source: String) {
        problem = null
        scope.launch {
            val result = try {
                withContext(io) { EvidenceImages.prepare(original, source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                original.fill(0)
            }
            when (result) {
                is EvidenceImageResult.Ready -> {
                    draftImage?.bytes?.fill(0)
                    draftImage = result.image
                    draftThumbnail = withContext(io) { thumbnailOf(result.image.bytes) }
                }
                is EvidenceImageResult.Refused -> problem = when (result.reason) {
                    EvidenceImageRefusal.TOO_LARGE -> KitProblem.IMAGE_TOO_LARGE
                    EvidenceImageRefusal.UNSUPPORTED -> KitProblem.IMAGE_UNSUPPORTED
                    EvidenceImageRefusal.MALFORMED, EvidenceImageRefusal.NOT_CLEAN -> KitProblem.IMAGE_MALFORMED
                }
                null -> problem = KitProblem.IMAGE_FAILED
            }
        }
    }

    /** The picture could not even be read (the platform said so). */
    fun imageFailed(problem: KitProblem) {
        this.problem = problem
    }

    fun removeImage() {
        draftImage?.bytes?.fill(0)
        draftImage = null
        draftThumbnail = null
    }

    /** Keeps the form encrypted; the problems are said next to the fields otherwise. */
    fun keep() {
        val locker = locker ?: return
        val current = draft
        val image = draftImage
        val problems = current.problems(hasImage = image != null, now = clock())
        draftProblems = problems
        if (problems.isNotEmpty()) return
        saving = true
        problem = null
        notice = null
        // The locker wipes what it is given: a copy, so that the form keeps its picture if keeping fails.
        val handed = image?.let { PreparedEvidenceImage(it.bytes.copyOf(), it.format, it.originalSha256, it.source) }
        scope.launch {
            try {
                when (val result = withContext(io) { locker.keep(current, handed) }) {
                    is KeepResult.Kept -> {
                        evidence = listOf(result.evidence) + evidence.filter { it.id != result.evidence.id }
                        adding = false
                        clearDraft()
                        notice = KitNotice.KEPT
                    }
                    is KeepResult.Refused -> draftProblems = result.problems
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = KitProblem.KEEP_FAILED
            } finally {
                handed?.bytes?.fill(0)
                saving = false
            }
        }
    }

    /** Opens the evidence [id], its picture decrypted in memory. */
    fun view(id: String) {
        val locker = locker ?: return
        val item = evidence.firstOrNull { it.id == id } ?: return
        problem = null
        notice = null
        viewing = ViewedEvidence(item, null, loading = item.imageEntry != null)
        if (item.imageEntry == null) return
        val token = generation
        scope.launch {
            val bitmap = try {
                withContext(io) {
                    val bytes = locker.image(item)
                    try {
                        bytes?.let { thumbnailOf(it, VIEW_SIDE) }
                    } finally {
                        bytes?.fill(0)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (token != generation || viewing?.evidence?.id != id) return@launch
            if (bitmap == null) problem = KitProblem.OPEN_FAILED
            viewing = ViewedEvidence(item, bitmap, loading = false)
        }
    }

    fun closeView() {
        viewing = null
        problem = null
    }

    /** Deletes the evidence [id] (after the confirmation of the screen). */
    fun delete(id: String) {
        val locker = locker ?: return
        scope.launch {
            try {
                withContext(io) { locker.delete(id) }
                evidence = evidence.filter { it.id != id }
                excluded = excluded - id
                if (viewing?.evidence?.id == id) viewing = null
                problem = null
                notice = KitNotice.DELETED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = KitProblem.DELETE_FAILED
            }
        }
    }

    fun toggleOffence(offence: Offence) {
        offences = if (offence in offences) offences - offence else offences + offence
    }

    /** Puts the evidence [id] in the templates, or leaves it out. */
    fun include(id: String, included: Boolean) {
        excluded = if (included) excluded - id else excluded + id
    }

    /** What the templates are filled with now. */
    fun input(): ReportInput = ReportInput(
        evidence = evidence.filter { it.id !in excluded },
        explanation = explanation,
        offences = offences,
        name = name,
        email = email,
        goodFaith = goodFaith,
        author = author,
        city = city,
    )

    /** The text of the template [kind], as it will be copied. */
    fun text(kind: ReportKind = report): String {
        val input = input()
        val today = clock()
        return when (kind) {
            ReportKind.PLATFORM -> HarassmentReports.dsaNotice(input, today)
            ReportKind.PHAROS -> HarassmentReports.pharos(input)
            ReportKind.COMPLAINT -> HarassmentReports.complaint(input, today)
        }
    }

    /** The decrypted pictures leave memory: the kit is no longer shown, or PPF locked itself. */
    fun dropDecrypted() {
        generation++
        loadJob?.cancel()
        loadJob = null
        loading = false
        viewing = null
        draftThumbnail = null
    }

    /**
     * The part `agir-harcelement` of the quick erase (S-44): the vault destroys its own master key,
     * then its files; the form and the templates forget what was typed. Waits for the end, throws
     * when something is left, may be called again.
     */
    suspend fun eraseFromDevice() {
        dropDecrypted()
        evidence = emptyList()
        unreadable = 0
        adding = false
        clearDraft()
        explanation = ""
        offences = emptySet()
        name = ""
        email = ""
        goodFaith = false
        author = ""
        city = ""
        excluded = emptySet()
        problem = null
        notice = null
        val locker = locker ?: return
        withContext(io) { locker.eraseAll() }
    }

    private fun clearDraft() {
        draft = EvidenceDraft()
        draftProblems = emptySet()
        removeImage()
    }

    private fun thumbnailOf(bytes: ByteArray, side: Int = THUMBNAIL_SIDE): ImageBitmap? =
        runCatching { PhotoPipeline.preview(bytes, codec, maxSide = side)?.toImageBitmap() }.getOrNull()

    companion object {
        const val THUMBNAIL_SIDE: Int = 320
        const val VIEW_SIDE: Int = 1600
    }
}

/** Lets tests and previews replace the vault of the evidence; null uses the platform's. */
val LocalEvidenceVault = staticCompositionLocalOf<ProtectedVault?> { null }

/** The kit for as long as its caller stays on screen (the shell keeps it for the whole run). */
@Composable
fun rememberHarassmentKitState(): HarassmentKitState {
    val provided = LocalEvidenceVault.current
    val platform = rememberPlatformEvidenceVault()
    val vault = provided ?: platform
    val scope = rememberCoroutineScope()
    return remember(vault) { HarassmentKitState(vault, scope) }
}
