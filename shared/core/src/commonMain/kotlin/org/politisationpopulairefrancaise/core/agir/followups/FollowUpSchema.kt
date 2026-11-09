package org.politisationpopulairefrancaise.core.agir.followups

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.BodySpec
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.CrossRule
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialApprovals
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.Occurs
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.SchemaCheck
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.SourceKind

/**
 * The schema of a follow-up (AG-62, `ppf-type` = `suite`) for the editorial engine (D-092), so that the content
 * signer (and, later, the back-office) refuses an unknown field, a follow-up without a primary source, a source
 * without its address, an unknown kind or status, and a follow-up that names no mobilization of the agenda.
 *
 * The front matter keys and their tags:
 * - `mobilization` (one or more): `["a", "31923:<pubkey>:<d>", "", "mobilisation"]`, the full address of the
 *   mobilization of the agenda, whoever published it;
 * - `suite-kind` (`ppf-suite-kind`, [FollowUpKind]), `date` (`ppf-date`, AAAA-MM-JJ), `fact-status`
 *   (`ppf-fact-status`, the vocabulary of the facts of « Voir », [FactStatus]);
 * - `source` (`ppf-source`, at least one): « type | nom | adresse https | date », the convention of « Voir »,
 *   the address being required here;
 * - `person`, `demand`, `call`, `chronology` (optional, repeated): the full address `30023:<pubkey>:<d>` of a
 *   public person of « Voir », a demand, a call or a fact of the chronology of « Voir », with the roles of
 *   [FollowUpLinkKind]; an elected official's answer names its person (right of reply, M17);
 * - `correction` (`ppf-correction`): « date | résumé public | nature », the public log of corrections.
 *
 * The links carry full addresses (FieldSpec.addressRole) because their targets are not contents of this space
 * under its key: a mobilization or a demand is signed by its author, a block of « Voir » by the key of « Voir ».
 */
object FollowUpSchema {
    const val SOURCE_DIRECTORY: String = "suites"
    const val BUNDLE: String = "suites.json"

    const val KEY_MOBILIZATION: String = "mobilization"
    const val KEY_KIND: String = "suite-kind"
    const val KEY_DATE: String = "date"
    const val KEY_STATUS: String = "fact-status"
    const val KEY_SOURCE: String = "source"
    const val KEY_PERSON: String = "person"
    const val KEY_DEMAND: String = "demand"
    const val KEY_CALL: String = "call"
    const val KEY_CHRONOLOGY: String = "chronology"
    const val KEY_CORRECTION: String = "correction"

    /** The keys of the links to contents (kind 30023), with their role and the type their target must have when known. */
    val CONTENT_LINKS: List<Triple<String, FollowUpLinkKind, ArticleType>> = listOf(
        Triple(KEY_PERSON, FollowUpLinkKind.PERSON, ArticleType.PERSONNE),
        Triple(KEY_DEMAND, FollowUpLinkKind.DEMAND, ArticleType.REVENDICATION),
        Triple(KEY_CALL, FollowUpLinkKind.CALL, ArticleType.APPEL),
        Triple(KEY_CHRONOLOGY, FollowUpLinkKind.CHRONOLOGY, ArticleType.FAIT),
    )

    /** The longest address written in a link (31923 or 30023, a 64-character key, an identifier of 128 at most). */
    const val ADDRESS_MAX: Int = 260

    /** The text of a follow-up: plain text, short (the summary says the essential). */
    const val BODY_MAX: Int = 4_000

    /** The rule codes of the schema (French texts in EditorialMessages). */
    object Rules {
        const val UNREADABLE: String = "agir.rule.suite_unreadable"
        const val LINE_IGNORED: String = "agir.rule.suite_line_ignored"
        const val REPLY_NEEDS_PERSON: String = "agir.rule.suite_reply_needs_person"
        const val MOBILIZATION_ADDRESS: String = "agir.rule.suite_mobilization_address"
        const val CONTENT_ADDRESS: String = "agir.rule.suite_content_address"
        const val LINK_WRONG_TYPE: String = "agir.rule.suite_link_wrong_type"
        const val DUPLICATE: String = "agir.rule.suite_duplicate"

        val all: List<String> = listOf(UNREADABLE, LINE_IGNORED, REPLY_NEEDS_PERSON, MOBILIZATION_ADDRESS, CONTENT_ADDRESS, LINK_WRONG_TYPE, DUPLICATE)
    }

    /** An identifier of a content of kind 30023 in a link (the demands and calls of the application, the slugs of « Voir »). */
    private val CONTENT_IDENTIFIER = Regex("^[a-z0-9][a-z0-9._~-]{0,127}$")

    private fun link(key: String, role: String, occurs: Occurs) = FieldSpec(
        key, "agir.field.suite_$key", "agir.help.suite_$key", occurs = occurs,
        value = ValueSpec.Single(FieldType.Text(ADDRESS_MAX)),
        tag = FieldSpec.ADDRESS_TAG,
        addressRole = role,
    )

    /** « declaration | Lettre publique (exemple) | https://… | 2026-11-06 »: kind, name, address (required), date. */
    private val source = FieldSpec(
        KEY_SOURCE, "voir.field.source", "agir.help.suite_source", occurs = Occurs.MANY(min = 1),
        value = ValueSpec.Positional(
            listOf(
                Position("kind", "voir.source.kind", FieldType.Choice(SourceKind.entries.map { it.tagValue }, "voir.source_kind.")),
                Position("name", "voir.source.name", FieldType.Text(300)),
                Position("url", "voir.source.url", FieldType.Url),
                Position("date", "voir.source.date", FieldType.Date(), required = false),
            ),
        ),
    )

    /** « 2026-11-10 | résumé public | correction »: the public log of corrections (D-095). */
    private val correction = FieldSpec(
        KEY_CORRECTION, "voir.field.correction", "voir.help.correction", occurs = Occurs.MANY(),
        value = ValueSpec.Positional(
            listOf(
                Position("date", "voir.correction.date", FieldType.Date()),
                Position("reason", "voir.correction.reason", FieldType.Text(1000)),
                Position("nature", "editorial.correction.nature", FieldType.Choice(listOf("correction", "mise-a-jour"), "editorial.correction_nature."), required = false),
            ),
        ),
    )

    private fun parseAddress(value: String): ContentAddress? = runCatching { ContentAddress.parse(value.trim()) }.getOrNull()

    private fun occurrences(values: List<String>) = values.withIndex().filter { it.value.isNotBlank() }

    /**
     * Each link is a full address of the right kind; a mobilization has the identifier of the agenda; a content
     * known to the check (published, or of the same batch, under the key of the space) has the expected type.
     */
    private val addresses = SchemaCheck { draft, _, context ->
        buildList {
            for ((i, raw) in occurrences(draft.values(KEY_MOBILIZATION))) {
                val address = parseAddress(raw)
                if (address == null || address.kind != Mobilization.KIND || !Mobilization.isValidIdentifier(address.identifier)) {
                    add(Problem(Rules.MOBILIZATION_ADDRESS, KEY_MOBILIZATION, i, args = listOf(raw.trim())))
                }
            }
            for ((key, _, expected) in CONTENT_LINKS) {
                for ((i, raw) in occurrences(draft.values(key))) {
                    val address = parseAddress(raw)
                    if (address == null || address.kind != Article.KIND || !CONTENT_IDENTIFIER.matches(address.identifier)) {
                        add(Problem(Rules.CONTENT_ADDRESS, key, i, args = listOf(raw.trim())))
                        continue
                    }
                    val type = knownType(context, address)
                    if (type != null && type != expected) add(Problem(Rules.LINK_WRONG_TYPE, key, i, args = listOf(raw.trim(), expected.tagValue, type.tagValue)))
                }
            }
        }
    }

    private fun knownType(context: CheckContext, address: ContentAddress): ArticleType? = runCatching { context.typeOf(address) }.getOrNull()

    /**
     * The application reads the follow-up ([FollowUp.parse]) and drops none of its lines: for each repeated field,
     * the number of tags written is the number of values the reader keeps.
     */
    private val readable = SchemaCheck { draft, event, _ ->
        val signed = SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))
        val followUp = FollowUp.parse(signed) ?: return@SchemaCheck listOf(Problem(Rules.UNREADABLE))
        val kept = buildMap {
            put(KEY_MOBILIZATION, followUp.mobilizations.size)
            put(KEY_SOURCE, followUp.sources.size)
            put(KEY_CORRECTION, followUp.corrections.size)
            for ((key, kind, _) in CONTENT_LINKS) put(key, followUp.linksOf(kind).size)
        }
        kept.mapNotNull { (key, count) ->
            val written = draft.values(key).count { it.isNotBlank() }
            if (count < written) Problem(Rules.LINE_IGNORED, key, args = listOf((written - count).toString())) else null
        }
    }

    val SUITE: ContentSchema = ContentSchema(
        type = ArticleType.SUITE,
        space = EditorialSpace.AGIR,
        labelKey = "editorial.type.suite",
        sourceDirectory = SOURCE_DIRECTORY,
        bundle = BUNDLE,
        fields = listOf(
            link(KEY_MOBILIZATION, FollowUpFormat.ROLE_MOBILIZATION, Occurs.MANY(min = 1, max = FollowUpFormat.MAX_LINKS)),
            FieldSpec(KEY_KIND, "agir.field.suite_kind", "agir.help.suite_kind", value = ValueSpec.Single(FieldType.Choice(FollowUpKind.entries.map { it.tagValue }, "agir.suite_kind."))),
            FieldSpec(KEY_DATE, "agir.field.suite_date", "agir.help.suite_date", value = ValueSpec.Single(FieldType.Date())),
            FieldSpec(KEY_STATUS, "voir.field.fact_status", "voir.help.fact_status", value = ValueSpec.Single(FieldType.Choice(FactStatus.entries.map { it.tagValue }, "voir.fact_status."))),
            source,
            link(KEY_PERSON, FollowUpLinkKind.PERSON.role, Occurs.MANY(max = FollowUpFormat.MAX_LINKS)),
            link(KEY_DEMAND, FollowUpLinkKind.DEMAND.role, Occurs.MANY(max = FollowUpFormat.MAX_LINKS)),
            link(KEY_CALL, FollowUpLinkKind.CALL.role, Occurs.MANY(max = FollowUpFormat.MAX_LINKS)),
            link(KEY_CHRONOLOGY, FollowUpLinkKind.CHRONOLOGY.role, Occurs.MANY(max = FollowUpFormat.MAX_LINKS)),
            correction,
        ),
        // Plain text, like the descriptions of the agenda and the calls: no required section, the sources are tags.
        body = BodySpec(requiredSections = emptyList(), maxLength = BODY_MAX),
        rules = listOf(
            // An answer of an elected official names its person: the right of reply of « Voir » applies (M17).
            CrossRule.RequiredWhen(KEY_PERSON, whenField = KEY_KIND, values = setOf(FollowUpKind.ELECTED_REPLY.tagValue), messageKey = Rules.REPLY_NEEDS_PERSON),
            CrossRule.UniqueBy(KEY_MOBILIZATION, emptyList(), Rules.DUPLICATE),
            CrossRule.UniqueBy(KEY_PERSON, emptyList(), Rules.DUPLICATE),
            CrossRule.UniqueBy(KEY_DEMAND, emptyList(), Rules.DUPLICATE),
            CrossRule.UniqueBy(KEY_CALL, emptyList(), Rules.DUPLICATE),
            CrossRule.UniqueBy(KEY_CHRONOLOGY, emptyList(), Rules.DUPLICATE),
        ),
        // The reader is asked only once every address is well-formed (a wrong address would also make it unreadable).
        checks = listOf(SchemaCheck { draft, event, context -> addresses.problems(draft, event, context).ifEmpty { readable.problems(draft, event, context) } }),
        // Fails closed until the team lead decides (docs/integration/agir-et-apres.md, questions): a follow-up often
        // names a public official, so two editors who wrote nothing approve it, as the contents of M17.
        approvals = EditorialApprovals.M17,
    )

    val all: List<ContentSchema> = listOf(SUITE)
}
