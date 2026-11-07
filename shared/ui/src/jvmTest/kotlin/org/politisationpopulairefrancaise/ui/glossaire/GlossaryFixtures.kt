package org.politisationpopulairefrancaise.ui.glossaire

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryFormat
import org.politisationpopulairefrancaise.core.comprendre.GlossarySource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Entries of the glossary for the tests, signed by a key of their own (the real entries are written
 * in content/glossaire by another block and bundled in glossaire.json). Every one is an [EXEMPLE].
 */
object GlossaryFixtures {
    val editor: IdentityKeys = IdentityKeys.generate()
    val trust: EditorialTrust = EditorialTrust(setOf(editor.publicKey.hex))

    private val sources = listOf(
        GlossarySource(
            file = "motion-de-censure.md",
            slug = "motion-de-censure",
            title = "[EXEMPLE] Motion de censure",
            summary = "Vote par lequel les députés peuvent renverser le gouvernement.",
            variants = listOf("motion de censure", "motions de censure"),
            seeAlso = listOf("49-3"),
            related = emptyList(),
            auto = null,
            body = "Une motion de censure doit être signée par un dixième des députés. Elle n'est adoptée qu'à la majorité absolue des membres de l'Assemblée. Elle sert aussi de réponse au 49.3.\n\n## Sources\n\n- [Constitution, article 49](https://www.legifrance.gouv.fr/)\n",
        ),
        GlossarySource(
            file = "49-3.md",
            slug = "49-3",
            title = "[EXEMPLE] 49.3",
            summary = "Article de la Constitution qui permet au gouvernement de faire adopter un texte sans vote, sauf si une motion de censure est votée.",
            variants = listOf("49.3", "49-3", "article 49.3", "article 49 alinéa 3"),
            seeAlso = listOf("motion-de-censure"),
            related = emptyList(),
            auto = null,
            body = "Le gouvernement engage sa responsabilité sur un texte. Le texte est adopté sans vote, sauf si une motion de censure est votée dans les 48 heures.\n\n## Sources\n\n- [Constitution, article 49](https://www.legifrance.gouv.fr/)\n",
        ),
        GlossarySource(
            file = "senat.md",
            slug = "senat",
            title = "[EXEMPLE] Sénat",
            summary = "La seconde chambre du Parlement, élue par les élus locaux.",
            variants = listOf("Sénat"),
            seeAlso = emptyList(),
            related = emptyList(),
            auto = null,
            body = "Le Sénat vote la loi avec l'Assemblée nationale.\n\n## Sources\n\n- [Sénat](https://www.senat.fr/)\n",
        ),
        GlossarySource(
            file = "gouvernement.md",
            slug = "gouvernement",
            title = "[EXEMPLE] Gouvernement",
            summary = "Le Premier ministre et les ministres, qui dirigent la politique de la nation.",
            variants = listOf("gouvernement"),
            seeAlso = emptyList(),
            related = emptyList(),
            auto = "non",
            body = "Le gouvernement est responsable devant l'Assemblée nationale.\n\n## Sources\n\n- [Constitution, article 20](https://www.legifrance.gouv.fr/)\n",
        ),
    )

    val events: List<SignedEvent> by lazy {
        val signer = LocalEventSigner(editor)
        sources.map { source ->
            val unsigned = Article.build(
                author = editor.publicKey,
                slug = source.slug,
                title = source.title,
                summary = source.summary,
                topics = listOf("institutions"),
                type = ArticleType.GLOSSAIRE,
                origin = ContentOrigin.AI_ASSISTED,
                publishedAt = 1_794_052_800,
                updatedAt = 1_794_052_800,
                body = source.body,
                extraTags = GlossaryFormat.tags(source, editor.publicKey.hex),
            )
            runBlocking { signer.sign(unsigned) }
        }
    }

    val entries: List<GlossaryEntry> by lazy { events.mapNotNull(Article::from).mapNotNull(GlossaryEntry::from) }

    const val GUIDE_SLUG: String = "fiche-glossaire-test"

    /** A practical guide of "Comprendre" whose body holds the terms of the glossary. */
    val guide: SignedEvent by lazy {
        val unsigned = Article.build(
            author = editor.publicKey,
            slug = GUIDE_SLUG,
            title = "[EXEMPLE] Fiche de test du glossaire",
            summary = "Une fiche dont les mots sont expliqués.",
            topics = listOf("institutions"),
            type = ArticleType.FICHE_PRATIQUE,
            origin = ContentOrigin.AI_ASSISTED,
            publishedAt = 1_794_052_800,
            updatedAt = 1_794_052_800,
            body = "::: en-bref\n- Le Sénat ne vote pas la motion de censure.\n:::\n\n$PARAGRAPH\n\n## Sources\n\n- Constitution\n",
        )
        runBlocking { LocalEventSigner(editor).sign(unsigned) }
    }

    /** A glossary held in memory, as the shell would hold it once the entries are read. */
    fun state(): GlossaryState = GlossaryState(store = null, trust = trust, scope = CoroutineScope(Dispatchers.Default), initial = entries)

    /** A paragraph of a content: the 49.3 and the motion de censure (twice: linked once), no « Sénat ». */
    const val PARAGRAPH: String =
        "Le gouvernement peut engager sa responsabilité devant l’Assemblée avec le 49.3. Les députés répondent " +
            "alors par une motion de censure. Si la motion de censure est adoptée, le gouvernement doit démissionner."
}
