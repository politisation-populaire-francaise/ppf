package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.FlowContent

/**
 * The Markdown body of a content of "Comprendre" rendered exactly as the site renders it (same renderer,
 * same escaping, same boxes and blocks), for the preview of a draft in the back-office (circuit
 * éditorial, step 3). Without the glossary links: a draft is not yet part of a page of the site. The
 * headings move down one level, under the title of the content.
 */
fun FlowContent.comprendreBodyPreview(markdown: String) {
    ComprendreRenderer().markdown(this, markdown, headingOffset = 1, withGlossary = false)
}
