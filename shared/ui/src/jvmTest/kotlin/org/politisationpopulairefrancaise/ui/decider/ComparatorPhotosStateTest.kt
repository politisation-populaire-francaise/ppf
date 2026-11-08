package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.decider.DeciderTags
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DE-16, Q3: the official photos are asked together, checked by their fingerprint, and shown all or not at all. */
class ComparatorPhotosStateTest {
    private val keys = IdentityKeys.generate()
    private val alexPhoto = byteArrayOf(1, 2, 3)
    private val camillePhoto = byteArrayOf(4, 5, 6)

    private fun sheet(): ComparatorSheet {
        val tags = listOf(
            listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection", ""),
            listOf(DeciderTags.THEME, "sante"),
            listOf(VoirTags.LINK, "30023:${keys.publicKey.hex}:alex", "", DeciderTags.ROLE_CANDIDATE),
            listOf(VoirTags.LINK, "30023:${keys.publicKey.hex}:camille", "", DeciderTags.ROLE_CANDIDATE),
            listOf(DeciderTags.PHOTO, "alex", MediaDigest.sha256Hex(alexPhoto), "Licence ouverte 2.0", "[EXEMPLE] Auteur", "https://exemple.invalid/a"),
            listOf(DeciderTags.PHOTO, "camille", MediaDigest.sha256Hex(camillePhoto), "Licence ouverte 2.0", "[EXEMPLE] Auteur", "https://exemple.invalid/c"),
        )
        val unsigned = Article.build(keys.publicKey, "comparateur", "[EXEMPLE] Comparateur", null, emptyList(), ArticleType.COMPARATEUR, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = tags)
        return ComparatorSheet.from(Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!)!!
    }

    private class Photos(val files: Map<String, ByteArray>) : RemoteEvents {
        val asked = mutableListOf<String>()
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        override suspend fun fetchComparatorPhoto(sha256: String): ByteArray? {
            synchronized(asked) { asked += sha256 }
            return files[sha256]
        }
    }

    private val codec = object : ImageCodec {
        override fun decode(bytes: ByteArray, maxSide: Int): ArgbImage = ArgbImage(1, 1, intArrayOf(bytes.first().toInt()))
        override fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray? = null
    }

    @Test
    fun everyPhotoIsAskedAndCheckedThenAllAreShown() = runBlocking {
        val sheet = sheet()
        val remote = Photos(mapOf(MediaDigest.sha256Hex(alexPhoto) to alexPhoto, MediaDigest.sha256Hex(camillePhoto) to camillePhoto))
        val state = ComparatorPhotosState(remote, codec) { ImageBitmap(1, 1) }
        state.load(sheet)
        assertEquals(setOf("alex", "camille"), assertNotNull(state.photos(sheet)).keys)
        assertEquals(2, remote.asked.size, "the photos of every candidate, together")
    }

    @Test
    fun oneMissingOrAlteredPhotoAndNoneIsShown() = runBlocking {
        val sheet = sheet()
        val missing = ComparatorPhotosState(Photos(mapOf(MediaDigest.sha256Hex(alexPhoto) to alexPhoto)), codec) { ImageBitmap(1, 1) }
        missing.load(sheet)
        assertNull(missing.photos(sheet))
        val altered = ComparatorPhotosState(
            Photos(mapOf(MediaDigest.sha256Hex(alexPhoto) to alexPhoto, MediaDigest.sha256Hex(camillePhoto) to byteArrayOf(7, 7, 7))),
            codec,
        ) { ImageBitmap(1, 1) }
        altered.load(sheet)
        assertNull(altered.photos(sheet), "bytes that do not match the signed fingerprint")
    }
}
