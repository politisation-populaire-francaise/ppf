package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/** A dossier of "Voir" (independence of justice, ownership of the media...): an editorial dossier with an axis. */
data class VoirDossier(val article: Article, val axis: VoirAxis) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    companion object {
        fun from(article: Article): VoirDossier? {
            if (article.type != ArticleType.DOSSIER) return null
            val axis = VoirAxis.fromTag(article.event.value(VoirTags.AXIS)) ?: return null
            return VoirDossier(article, axis)
        }
    }
}

/** Anything "Voir" can open: what the search returns, what the user follows. */
sealed interface VoirItem {
    val address: ContentAddress
    /** The signed editorial content of the block (its version, its dates, its origin). */
    val article: Article
    val title: String
    val axis: VoirAxis

    data class OfIndicator(val indicator: Indicator) : VoirItem {
        override val address get() = indicator.address
        override val article get() = indicator.article
        override val title get() = indicator.title
        override val axis get() = indicator.axis
    }

    data class OfFact(val fact: Fact) : VoirItem {
        override val address get() = fact.address
        override val article get() = fact.article
        override val title get() = fact.title
        override val axis get() = fact.axis
    }

    data class OfPerson(val person: PublicPerson) : VoirItem {
        override val address get() = person.address
        override val article get() = person.article
        override val title get() = person.name
        override val axis get() = VoirAxis.POUVOIR
    }

    data class OfParty(val party: PoliticalParty) : VoirItem {
        override val address get() = party.address
        override val article get() = party.article
        override val title get() = party.name
        override val axis get() = VoirAxis.POUVOIR
    }

    data class OfText(val text: LegislativeText) : VoirItem {
        override val address get() = text.address
        override val article get() = text.article
        override val title get() = text.title
        override val axis get() = VoirAxis.POUVOIR
    }

    data class OfMovement(val movement: WorldMovement) : VoirItem {
        override val address get() = movement.address
        override val article get() = movement.article
        override val title get() = movement.title
        override val axis get() = VoirAxis.MOUVEMENT
    }

    data class OfDossier(val dossier: VoirDossier) : VoirItem {
        override val address get() = dossier.address
        override val article get() = dossier.article
        override val title get() = dossier.title
        override val axis get() = dossier.axis
    }
}

/** A correction, with the content it was made to, for the public log of corrections (VO-22). */
data class CorrectionEntry(val item: VoirItem, val correction: Correction)

/**
 * Everything "Voir" shows, assembled on the device from the trusted editorial contents (D-034).
 * Every block is read once; the links between them (a vote points to a person and a bill, a
 * reply to the fact it answers) are resolved here, so that a fact appears in each place it
 * belongs without being copied.
 */
class VoirCatalog(
    val indicators: List<Indicator>,
    val facts: List<Fact>,
    val persons: List<PublicPerson>,
    val texts: List<LegislativeText>,
    val movements: List<WorldMovement>,
    val dossiers: List<VoirDossier>,
    val replies: List<RightOfReply>,
    /** Political parties, by name (accents, case and the [EXEMPLE] marker ignored), never by size. */
    val parties: List<PoliticalParty> = emptyList(),
    /** « Transparence de PPF » (PP-01): the pages of the association itself, never among the blocks of the axes. */
    val transparency: TransparencyPages = TransparencyPages(emptyList()),
) {
    private val items: Map<ContentAddress, VoirItem> = buildMap {
        indicators.forEach { put(it.address, VoirItem.OfIndicator(it)) }
        facts.forEach { put(it.address, VoirItem.OfFact(it)) }
        persons.forEach { put(it.address, VoirItem.OfPerson(it)) }
        texts.forEach { put(it.address, VoirItem.OfText(it)) }
        movements.forEach { put(it.address, VoirItem.OfMovement(it)) }
        dossiers.forEach { put(it.address, VoirItem.OfDossier(it)) }
        parties.forEach { put(it.address, VoirItem.OfParty(it)) }
    }

    val isEmpty: Boolean get() = items.isEmpty()

    fun item(address: ContentAddress): VoirItem? = items[address]

    fun indicator(address: ContentAddress): Indicator? = (items[address] as? VoirItem.OfIndicator)?.indicator
    fun fact(address: ContentAddress): Fact? = (items[address] as? VoirItem.OfFact)?.fact
    fun person(address: ContentAddress): PublicPerson? = (items[address] as? VoirItem.OfPerson)?.person
    fun text(address: ContentAddress): LegislativeText? = (items[address] as? VoirItem.OfText)?.text
    fun movement(address: ContentAddress): WorldMovement? = (items[address] as? VoirItem.OfMovement)?.movement
    fun dossier(address: ContentAddress): VoirDossier? = (items[address] as? VoirItem.OfDossier)?.dossier
    fun party(address: ContentAddress): PoliticalParty? = (items[address] as? VoirItem.OfParty)?.party

    /** The party named on a person's sheet, when it has one in the catalog. */
    fun partyOf(person: PublicPerson): PoliticalParty? = person.partyAddress?.let(::party)

    /** The persons whose sheet names [party], by name (leaders included). */
    fun membersOf(party: ContentAddress): List<PublicPerson> = persons(party = party)

    /** The leaders of a party that have a sheet, in the order the party gives them. */
    fun leadersOf(party: PoliticalParty): List<PublicPerson> = party.leaders.mapNotNull(::person)

    /** The parties that have at least one person with a sheet (filter chips of the persons). */
    fun partiesInUse(): List<PoliticalParty> = parties.filter { p -> persons.any { it.partyAddress == p.address } }

    /** The key figures of an axis: ranked ones first, in their order, then by title. */
    fun keyFigures(axis: VoirAxis): List<Indicator> = indicators.filter { it.axis == axis }
        .sortedWith(compareBy<Indicator> { it.rank ?: Int.MAX_VALUE }.thenBy { FrenchText.fold(it.title) })

    /** The chronology of the movement (VO-06), newest first; [kinds] narrows it. */
    fun chronology(kinds: Set<FactKind> = FactKind.chronology.toSet()): List<Fact> =
        facts.filter { it.kind.inChronology && it.kind in kinds }.sortedByNewest()

    /**
     * The observatory of public freedoms (VO-26), newest first: every fact with a freedom category,
     * whatever its kind (a prefectoral ban is also an official decision of the chronology).
     */
    fun liberties(category: LibertyCategory? = null): List<Fact> =
        facts.filter { it.liberty != null && (category == null || it.liberty == category) }.sortedByNewest()

    /** The freedom categories that have at least one fact (filter chips). */
    fun libertyCategoriesInUse(): List<LibertyCategory> = LibertyCategory.entries.filter { c -> facts.any { it.liberty == c } }

    /** The latest facts about power (votes excepted, they are on each sheet): what changed lately. */
    fun latestAboutPower(limit: Int = 5): List<Fact> =
        facts.filter { it.axis == VoirAxis.POUVOIR && it.kind != FactKind.VOTE }.sortedByNewest().take(limit)

    /**
     * Every fact about a person or a party, newest first. For a party, only the facts that link the
     * party itself: never those of its members (no guilt by association).
     */
    fun factsAbout(address: ContentAddress, kind: FactKind? = null): List<Fact> =
        facts.filter { (address in it.persons || address in it.parties) && (kind == null || it.kind == kind) }.sortedByNewest()

    /** Every fact about a bill (votes, lobbies...), newest first. */
    fun factsOn(text: ContentAddress, kind: FactKind? = null): List<Fact> =
        facts.filter { text in it.texts && (kind == null || it.kind == kind) }.sortedByNewest()

    fun personsOf(fact: Fact): List<PublicPerson> = fact.persons.mapNotNull(::person)
    fun textsOf(fact: Fact): List<LegislativeText> = fact.texts.mapNotNull(::text)

    /** The rights of reply published under a content, oldest first. */
    fun repliesTo(address: ContentAddress): List<RightOfReply> = replies.filter { it.target == address }.sortedBy { it.publishedOn }

    /** Public persons, by name; [role] and [party] narrow the list. */
    fun persons(role: PublicRole? = null, party: ContentAddress? = null): List<PublicPerson> =
        persons.filter { (role == null || role in it.roles) && (party == null || it.partyAddress == party) }.sortedBy { FrenchText.fold(it.name.removePrefix(EXAMPLE_MARKER)) }

    /** Bills: those still under discussion first, the nearest vote first; then the closed ones, newest first. */
    fun bills(): List<LegislativeText> = texts.sortedWith(
        compareBy<LegislativeText> { it.stage.closed }
            .thenBy { it.nextVote?.date ?: FAR_AWAY }
            .thenByDescending { it.history.lastOrNull()?.date },
    )

    /** The roles that have at least one sheet, in their usual order (filter chips). */
    fun rolesInUse(): List<PublicRole> = PublicRole.entries.filter { role -> persons.any { role in it.roles } }

    /** The public log of corrections (VO-22): every correction of every block, newest first. */
    fun corrections(): List<CorrectionEntry> = buildList {
        indicators.forEach { i -> i.corrections.forEach { add(CorrectionEntry(VoirItem.OfIndicator(i), it)) } }
        facts.forEach { f -> f.corrections.forEach { add(CorrectionEntry(VoirItem.OfFact(f), it)) } }
        persons.forEach { p -> p.corrections.forEach { add(CorrectionEntry(VoirItem.OfPerson(p), it)) } }
        texts.forEach { t -> t.corrections.forEach { add(CorrectionEntry(VoirItem.OfText(t), it)) } }
        parties.forEach { p -> p.corrections.forEach { add(CorrectionEntry(VoirItem.OfParty(p), it)) } }
    }.sortedByDescending { it.correction.date }

    /**
     * Everything whose title, summary, place, territory or group contains every word of [query]
     * (accents and case ignored, word beginnings: "manif" finds "manifestants"). Persons, parties and
     * key figures first, then bills, then facts, newest first.
     */
    fun search(query: String, limit: Int = 50): List<VoirItem> {
        if (FrenchText.words(query).isEmpty()) return emptyList()
        fun matches(vararg parts: String?) = FrenchText.matchesAllWords(parts.filterNotNull().joinToString(" "), query)
        return buildList {
            persons(null).filter { matches(it.name, it.function, it.territory, it.group) }.forEach { add(VoirItem.OfPerson(it)) }
            parties.filter { matches(it.name, it.acronym, it.article.summary, it.europeanParty, it.europeanGroup) }.forEach { add(VoirItem.OfParty(it)) }
            indicators.sortedBy { it.rank ?: Int.MAX_VALUE }.filter { matches(it.title, it.definition, it.unit) }.forEach { add(VoirItem.OfIndicator(it)) }
            bills().filter { matches(it.title, it.article.summary) }.forEach { add(VoirItem.OfText(it)) }
            dossiers.filter { matches(it.title, it.article.summary) }.forEach { add(VoirItem.OfDossier(it)) }
            movements.filter { matches(it.title, it.article.summary, it.country) }.forEach { add(VoirItem.OfMovement(it)) }
            facts.sortedByNewest().filter { matches(it.title, it.article.summary, it.place, it.quote) }.forEach { add(VoirItem.OfFact(it)) }
        }.take(limit)
    }

    private fun List<Fact>.sortedByNewest(): List<Fact> = sortedWith(compareByDescending<Fact> { it.date }.thenBy { FrenchText.fold(it.title) })

    companion object {
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"
        private val FAR_AWAY = CivilDate(9999, 12, 31)

        val EMPTY: VoirCatalog = VoirCatalog(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        /** The editorial types read here, to ask the local copy for them. */
        val TYPES: List<ArticleType> = listOf(
            ArticleType.INDICATEUR, ArticleType.FAIT, ArticleType.PERSONNE, ArticleType.TEXTE,
            ArticleType.MOUVEMENT, ArticleType.DOSSIER, ArticleType.REPONSE, ArticleType.PARTI, ArticleType.TRANSPARENCE,
        )

        /**
         * Builds the catalog from articles already checked (signature, trusted author). Malformed
         * blocks are left out; for a given address only the most recent version counts.
         */
        fun from(articles: List<Article>): VoirCatalog {
            val latest = articles.groupBy { it.address }.values.map { versions -> versions.maxBy { it.updatedAt } }
            return VoirCatalog(
                indicators = latest.mapNotNull(Indicator::from),
                facts = latest.mapNotNull(Fact::from),
                persons = latest.mapNotNull(PublicPerson::from),
                texts = latest.mapNotNull(LegislativeText::from),
                movements = latest.mapNotNull(WorldMovement::from).sortedByDescending { it.start },
                dossiers = latest.mapNotNull(VoirDossier::from).sortedBy { FrenchText.fold(it.title) },
                replies = latest.mapNotNull(RightOfReply::from),
                parties = latest.mapNotNull(PoliticalParty::from).sortedBy { FrenchText.fold(it.name.removePrefix(EXAMPLE_MARKER)) },
                transparency = TransparencyPages(latest.mapNotNull(TransparencyEntry::from)),
            )
        }
    }
}
