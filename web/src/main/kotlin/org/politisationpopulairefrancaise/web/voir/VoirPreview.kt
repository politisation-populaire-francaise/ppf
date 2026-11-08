package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.DIV
import kotlinx.html.Entities
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.attributesMapOf
import kotlinx.html.org.w3c.dom.events.Event
import kotlinx.html.visit
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import java.util.IdentityHashMap

/**
 * A draft of "Voir" rendered exactly as its page on the site (same components, same words, same order),
 * for the preview of the back-office (circuit éditorial, step 6). The catalog is made of the published
 * contents of "Voir" [published] and of the draft, which replaces the published version of its address:
 * its links (persons, texts, parties) and what points at it show as they would once it is published.
 * A right of reply is shown under the content it answers, as on the site, or alone when that content is
 * not published. The headings move down [headingOffset] levels, under the headings of the back-office page.
 *
 * The draft is never signed: it is read here with an empty signature, only during the request, and nothing
 * of it is kept. Returns false when the application and the site would not show the draft at all (the
 * engine of the schemas reports why).
 */
fun FlowContent.voirBlockPreview(draft: UnsignedEvent, published: List<SignedEvent>, headingOffset: Int = 1): Boolean {
    val article = Article.from(SignedEvent(draft.idHex(), draft.pubkey, draft.createdAt, draft.kind, draft.tags, draft.content, NO_SIGNATURE)) ?: return false
    val others = published.mapNotNull(Article::from).filter { it.address != article.address }
    val catalog = VoirCatalog.from(others + article)
    val reply = catalog.replies.firstOrNull { it.article.address == article.address }
    val item = itemAt(catalog, reply?.target ?: article.address)
    if (item == null && reply == null) return false
    DIV(attributesMapOf("class", "voir-preview"), HeadingShift(consumer, headingOffset)).visit {
        if (item != null) voirItemBody(item, catalog) else repliesSection(listOfNotNull(reply), offerReply = false)
    }
    return true
}

private val NO_SIGNATURE: String = "0".repeat(128)

/** The content of [catalog] at [address], whatever its kind. */
private fun itemAt(catalog: VoirCatalog, address: ContentAddress): VoirItem? = sequence {
    catalog.indicators.forEach { yield(VoirItem.OfIndicator(it)) }
    catalog.facts.forEach { yield(VoirItem.OfFact(it)) }
    catalog.persons.forEach { yield(VoirItem.OfPerson(it)) }
    catalog.parties.forEach { yield(VoirItem.OfParty(it)) }
    catalog.texts.forEach { yield(VoirItem.OfText(it)) }
    catalog.movements.forEach { yield(VoirItem.OfMovement(it)) }
    catalog.dossiers.forEach { yield(VoirItem.OfDossier(it)) }
}.firstOrNull { it.address == address }

/**
 * Passes everything to [target] unchanged, except the headings h1 to h6, written [offset] levels lower
 * (h6 at most): a page of the site keeps its outline once placed under the headings of another page. The
 * same renamed tag is given for the start, the attributes and the end of a heading, as the stream of
 * kotlinx.html expects.
 */
private class HeadingShift(private val target: TagConsumer<*>, private val offset: Int) : TagConsumer<Unit> {
    private val renamed = IdentityHashMap<Tag, Tag>()

    private fun shifted(tag: Tag): Tag {
        val level = HEADING.matchEntire(tag.tagName)?.groupValues?.get(1)?.toInt() ?: return tag
        return renamed.getOrPut(tag) { Renamed(tag, "h${(level + offset).coerceIn(1, 6)}") }
    }

    override fun onTagStart(tag: Tag) = target.onTagStart(shifted(tag))

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) = target.onTagAttributeChange(shifted(tag), attribute, value)

    override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) = target.onTagEvent(shifted(tag), event, value)

    override fun onTagEnd(tag: Tag) {
        target.onTagEnd(shifted(tag))
        renamed.remove(tag)
    }

    override fun onTagContent(content: CharSequence) = target.onTagContent(content)

    override fun onTagContentEntity(entity: Entities) = target.onTagContentEntity(entity)

    override fun onTagContentUnsafe(block: Unsafe.() -> Unit) = target.onTagContentUnsafe(block)

    override fun onTagComment(content: CharSequence) = target.onTagComment(content)

    override fun finalize() = Unit

    private class Renamed(original: Tag, override val tagName: String) : Tag by original

    private companion object {
        val HEADING = Regex("h([1-6])")
    }
}
