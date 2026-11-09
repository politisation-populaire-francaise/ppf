package org.politisationpopulairefrancaise.ui.agir.collectives

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.collectives.Collective
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveQuery
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.territoires.Territoires
import org.politisationpopulairefrancaise.core.territoires.Territory
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode
import org.politisationpopulairefrancaise.core.territoires.TerritoryType
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.UrneRemote
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.groups.GroupDraft
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.territoires.territoiresFor

/** The collectif a composer of "Agir" publishes in the name of (AG-49, AG-50): its room id and its name, shown on the form. */
data class CollectiveLink(val id: String, val name: String)

/** Where the user is inside the collectifs of "Agir". */
sealed interface CollectivesView {
    data object Directory : CollectivesView

    data class Page(val id: String) : CollectivesView

    /** The creation wizard (S-129), or its explanation for an account not validated in person. */
    data object Create : CollectivesView

    /** The salon of the collectif: its room, as in "Messages". */
    data class Room(val id: String) : CollectivesView

    /** "Proposer un événement du collectif": the composer of the agenda, with the collectif's tag. */
    data class ProposeEvent(val id: String) : CollectivesView

    /** "Lancer un appel du collectif": the composer of the calls, with the collectif's tag. */
    data class LaunchCall(val id: String) : CollectivesView
}

/** What the device knows of the account's level, for the creation of a collectif (D-112). */
sealed interface FounderCheck {
    data object NoAccount : FounderCheck

    data object Checking : FounderCheck

    /** No server, no answer, or an account not registered to vote: treated as not validated. */
    data object Unknown : FounderCheck

    data class Known(val level: VoteLevel) : FounderCheck

    /** Validated in person (or certified elected official): the server will accept the creation. */
    val canFound: Boolean get() = (this as? Known)?.level.let { it == VoteLevel.VERIFIE || it == VoteLevel.ELU }
}

/** A territory proposed by the referential for the wizard: the label written in `ppf-territory` and its code. */
data class TerritoryChoice(val label: String, val code: String, val scope: CollectiveScope?)

/** Why a step of the wizard cannot be left yet. */
enum class WizardProblem {
    NAME_MISSING,
    NAME_TOO_LONG,
    TERRITORY_MISSING,
    TERRITORY_TOO_LONG,
    SCOPE_MISSING,
    ABOUT_TOO_LONG,
    TYPE_MISSING,
    TOO_MANY_THEMES,
    THEME_TOO_LONG,
    INTERNAL_CHARTER_TOO_LONG,
    CHARTER_NOT_ACCEPTED,
}

/**
 * What the creation wizard holds (S-129), kept while the user visits another view. Private and
 * secret collectifs, and joining on request or invitation, are not offered in step 1 (D-087).
 */
data class CollectiveDraft(
    val name: String = "",
    val territory: String = "",
    /** A code of the referential, set only when the user picked a proposed territory, never guessed from the text. */
    val territoryCode: String? = null,
    val scope: CollectiveScope? = null,
    val about: String = "",
    val type: CollectiveType? = null,
    /** The themes as typed, separated by commas. */
    val themes: String = "",
    val language: String = CollectiveFormat.DEFAULT_LANGUAGE,
    val internalCharter: String = "",
    /** Unticked until the user ticks it: the charter is never accepted on their behalf. */
    val charterAccepted: Boolean = false,
) {
    /** The themes as they will be published: normalized, without repetition. */
    val themeList: List<String> get() = themes.split(',', ';').map(CollectiveFormat::normalizeTheme).filter { it.isNotEmpty() }.distinct()

    /** The id the room of the collectif would get, empty while the name holds no letter or digit. */
    val proposedId: String get() = Group.idFor(name, territory)

    /** What blocks the step [step] (1 to 4), in the order of the form. */
    fun problems(step: Int): List<WizardProblem> = buildList {
        when (step) {
            1 -> {
                if (proposedId.isEmpty()) add(WizardProblem.NAME_MISSING)
                if (name.trim().length > Group.MAX_NAME_LENGTH) add(WizardProblem.NAME_TOO_LONG)
                if (territory.isBlank()) add(WizardProblem.TERRITORY_MISSING)
                if (territory.trim().length > Group.MAX_TERRITORY_LENGTH) add(WizardProblem.TERRITORY_TOO_LONG)
                if (scope == null) add(WizardProblem.SCOPE_MISSING)
            }
            2 -> {
                if (about.trim().length > Group.MAX_ABOUT_LENGTH) add(WizardProblem.ABOUT_TOO_LONG)
                if (type == null) add(WizardProblem.TYPE_MISSING)
                if (themeList.size > CollectiveFormat.MAX_THEMES) add(WizardProblem.TOO_MANY_THEMES)
                if (themeList.any { it.length > CollectiveFormat.MAX_THEME_LENGTH }) add(WizardProblem.THEME_TOO_LONG)
            }
            3 -> if (internalCharter.trim().length > CollectiveFormat.MAX_INTERNAL_CHARTER_LENGTH) add(WizardProblem.INTERNAL_CHARTER_TOO_LONG)
            else -> if (!charterAccepted) add(WizardProblem.CHARTER_NOT_ACCEPTED)
        }
    }

    /** Every step can be left: the description can be built. */
    val complete: Boolean get() = (1..4).all { problems(it).isEmpty() }

    /** The collectif tags of this draft ([CollectiveFormat.tags]), or null while a choice is missing or a value is out of bounds. */
    fun collectiveTags(): List<List<String>>? {
        val scope = scope ?: return null
        val type = type ?: return null
        return runCatching {
            CollectiveFormat.tags(scope, type, themeList, language, territoryCode, internalCharter.trim().ifEmpty { null })
        }.getOrNull()
    }

    /** The draft of the room of Communauté that carries this collectif: its creation path does the rest (duplicates, held, offline). */
    fun toGroupDraft(): GroupDraft? {
        val tags = collectiveTags() ?: return null
        return GroupDraft(name = name, territory = territory, about = about, charterAccepted = charterAccepted, extraTags = tags)
    }
}

/**
 * The collectifs of "Agir" (M24, step 1, D-087) as the screens need them. A collectif is a room of
 * Communauté whose description carries the marker: the list, the joining and the creation go through
 * the rooms' [groups] (the shell's, shared with "Messages"), so that a collectif and its salon are
 * the same thing everywhere.
 *
 * - The directory (AG-47) filters on the device only ([query], [home]); nothing of it is sent.
 * - Joining a collectif is joining its room on the device: nothing is published, the server knows no member.
 * - The page (AG-48) shows the public roles, computed on the device from the public journal of the room (D-131).
 * - Creating one needs an account validated in person ([founderCheck]); the server checks it again.
 */
class CollectivesLibrary(
    val groups: GroupsLibrary,
    private val store: LocalEventStore?,
    private val remote: RemoteEvents?,
    private val urne: UrneRemote?,
    private val scope: CoroutineScope,
    /** The user's home for « près de chez moi », nearest first ([CollectiveQuery.home]), read on the device only. */
    private val homeSource: suspend () -> List<String> = { emptyList() },
    /** The territories proposed by the referential for a typed text, on the device only. */
    private val territorySource: suspend (String) -> List<TerritoryChoice> = { emptyList() },
) {
    private var started = false

    var view: CollectivesView by mutableStateOf(CollectivesView.Directory)
        private set

    var query: CollectiveQuery by mutableStateOf(CollectiveQuery())

    /** Territory codes of the user's home, nearest first; empty while unknown (the filter is then hidden). */
    var home: List<String> by mutableStateOf(emptyList())
        private set

    /** The roles of the collectif whose page is open, computed from the public journal of its room. */
    var moderation: RoomModeration? by mutableStateOf(null)
        private set

    /** The collectif whose roles [moderation] holds. */
    private var moderationOf: String? = null

    /** The creation the server holds for the team's review (D-132), when the last sending of the wizard was held. */
    var held: Group? by mutableStateOf(null)
        private set

    var founderCheck: FounderCheck by mutableStateOf(FounderCheck.Unknown)
        private set

    var draft: CollectiveDraft by mutableStateOf(CollectiveDraft())
        private set

    /** The step of the wizard, 1 to 4. */
    var step: Int by mutableStateOf(1)
        private set

    /** The wizard sent the description at least once: the errors and the held state of the rooms' library now concern it. */
    var submitted: Boolean by mutableStateOf(false)
        private set

    var territoryChoices: List<TerritoryChoice> by mutableStateOf(emptyList())
        private set

    /** Every collectif known to the device: the rooms carrying the marker. */
    val collectives: List<Collective> get() = groups.groups.mapNotNull(Collective::from)

    /** The listed collectifs matching [query], the nearest first when the home is known. */
    val shown: List<Collective> get() = query.apply(collectives, home)

    /** The themes offered as filters. */
    val themes: List<String> get() = CollectiveQuery.themesOf(collectives)

    val hasRemote: Boolean get() = remote != null

    fun find(id: String): Collective? = collectives.firstOrNull { it.id == id }

    /** Loads the rooms and the home the first time the directory is shown; later calls do nothing. */
    fun start() {
        groups.ensureStarted()
        if (started) return
        started = true
        scope.launch {
            home = try {
                homeSource()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun search(text: String) {
        query = query.copy(text = text)
    }

    /** Chooses a theme filter, or removes it when it is chosen again. */
    fun toggleTheme(theme: String) {
        query = query.copy(theme = if (query.theme == theme) null else theme)
    }

    fun toggleType(type: CollectiveType) {
        query = query.copy(type = if (query.type == type) null else type)
    }

    fun toggleScope(scope: CollectiveScope) {
        query = query.copy(scope = if (query.scope == scope) null else scope)
    }

    fun toggleNearMe() {
        query = query.copy(nearMe = !query.nearMe)
    }

    fun clearFilters() {
        query = CollectiveQuery(text = query.text)
    }

    fun isMember(collective: Collective): Boolean = groups.isMember(collective.id)

    /** Joins the room of the collectif on this device. Nothing is published. */
    fun join(collective: Collective) = groups.join(collective.id)

    /** Leaves the room of the collectif on this device. Nothing is published. */
    fun leave(collective: Collective) = groups.leave(collective.id)

    /** Opens the page of [collective] and reads its public roles. */
    fun open(collective: Collective) {
        view = CollectivesView.Page(collective.id)
        if (moderationOf != collective.id) {
            moderation = null
            moderationOf = null
        }
        scope.launch { loadRoles(collective) }
    }

    /** Opens the salon of [collective]: its room, with its messages. */
    fun openRoom(collective: Collective) {
        groups.open(groups.groups.firstOrNull { it.id == collective.id } ?: collective.group)
        view = CollectivesView.Room(collective.id)
    }

    fun proposeEvent(collective: Collective) {
        view = CollectivesView.ProposeEvent(collective.id)
    }

    fun launchCall(collective: Collective) {
        view = CollectivesView.LaunchCall(collective.id)
    }

    /** One step back: from a composer or the salon to the page, from the page or the wizard to the directory. */
    fun back() {
        view = when (val current = view) {
            CollectivesView.Directory -> CollectivesView.Directory
            is CollectivesView.Page -> CollectivesView.Directory
            CollectivesView.Create -> CollectivesView.Directory
            is CollectivesView.Room -> {
                groups.close()
                if (find(current.id) != null) CollectivesView.Page(current.id) else CollectivesView.Directory
            }
            is CollectivesView.ProposeEvent -> CollectivesView.Page(current.id)
            is CollectivesView.LaunchCall -> CollectivesView.Page(current.id)
        }
    }

    fun toDirectory() {
        view = CollectivesView.Directory
    }

    /** Whether [pubkeyHex] may publish in the name of [collective]: its founder, an administrator or a moderator of its room. */
    fun canPublishFor(collective: Collective, pubkeyHex: String?): Boolean {
        if (pubkeyHex == null) return false
        if (collective.isFounder(pubkeyHex)) return true
        val role = rolesOf(collective)?.roleOf(pubkeyHex)
        return role == RoomRole.ADMIN || role == RoomRole.MODERATOR
    }

    /** The roles of [collective] when they were read for its page; null otherwise (its founder alone is then known). */
    fun rolesOf(collective: Collective): RoomModeration? = moderation?.takeIf { moderationOf == collective.id }

    /** The coming events of [collective] among [mobilizations] (published ones, as the agenda holds them), soonest first. */
    fun eventsOf(collective: Collective, mobilizations: List<Mobilization>, now: Long): List<Mobilization> =
        mobilizations.filter { CollectiveFormat.collectiveOf(it.event) == collective.id && !it.isPast(now) }.sortedBy { it.start }

    /** The open calls of [collective] among [calls], newest first. */
    fun callsOf(collective: Collective, calls: List<Call>): List<Call> =
        calls.filter { CollectiveFormat.collectiveOf(it.event) == collective.id && it.isOpen }.sortedByDescending { it.updatedAt }

    /**
     * Opens the wizard. [signer] is the account (null without one); [known] the status the voting of
     * the shell last received, if any; otherwise the server is asked once (`GET` of the ballot boxes'
     * status, signed by the account): only the account's own level comes back.
     */
    fun beginCreation(signer: EventSigner?, known: VoterStatus?) {
        if (view != CollectivesView.Create) {
            draft = CollectiveDraft()
            step = 1
            submitted = false
            held = null
            territoryChoices = emptyList()
            groups.dismissCreationError()
        }
        view = CollectivesView.Create
        checkLevel(signer, known)
    }

    /** Leaves the wizard; what was typed is forgotten. */
    fun cancelCreation() {
        draft = CollectiveDraft()
        step = 1
        submitted = false
        held = null
        territoryChoices = emptyList()
        groups.dismissCreationError()
        view = CollectivesView.Directory
    }

    fun checkLevel(signer: EventSigner?, known: VoterStatus?) {
        if (signer == null) {
            founderCheck = FounderCheck.NoAccount
            return
        }
        known?.level?.let(VoteLevel::of)?.let {
            founderCheck = FounderCheck.Known(it)
            return
        }
        val urne = urne ?: run {
            founderCheck = FounderCheck.Unknown
            return
        }
        founderCheck = FounderCheck.Checking
        scope.launch {
            founderCheck = try {
                when (val answer = urne.status(signer)) {
                    is UrneAnswer.Ok -> VoteLevel.of(answer.value.level)?.let { FounderCheck.Known(it) } ?: FounderCheck.Unknown
                    is UrneAnswer.Failure -> FounderCheck.Unknown
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FounderCheck.Unknown
            }
        }
    }

    /** Edits the draft; a code of the referential is dropped as soon as the territory is typed again. */
    fun updateDraft(next: CollectiveDraft) {
        val code = if (next.territory != draft.territory && next.territoryCode == draft.territoryCode) null else next.territoryCode
        draft = next.copy(territoryCode = code)
        if (submitted) groups.dismissCreationError()
    }

    /** Asks the referential, on the device, for the territories matching what is typed. */
    fun suggestTerritories(text: String) {
        val clean = text.trim()
        if (clean.length < MIN_TERRITORY_SEARCH) {
            territoryChoices = emptyList()
            return
        }
        scope.launch {
            val found = try {
                territorySource(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (draft.territory.trim() == clean && draft.territoryCode == null) territoryChoices = found
        }
    }

    /** Takes a territory of the referential: its label, its code and, when none was chosen, the scope it suggests. */
    fun pickTerritory(choice: TerritoryChoice) {
        draft = draft.copy(territory = choice.label, territoryCode = choice.code, scope = draft.scope ?: choice.scope)
        territoryChoices = emptyList()
    }

    fun clearTerritoryCode() {
        draft = draft.copy(territoryCode = null)
    }

    fun next() {
        if (draft.problems(step).isEmpty() && step < LAST_STEP) step++
    }

    fun previous() {
        if (step > 1) step--
    }

    /**
     * Publishes the collectif through the creation path of the rooms ([GroupsLibrary.create]): the
     * numbered ids, the duplicates (`group_exists`, `group_similar`), the creation held for the team,
     * the offline case are handled there, and shown by the views of the rooms' form. On success the
     * founder joins it on this device and its page opens.
     */
    suspend fun create(signer: EventSigner): Collective? {
        if (!founderCheck.canFound || !draft.complete) return null
        val roomDraft = draft.toGroupDraft() ?: return null
        submitted = true
        held = null
        val heldBefore = groups.heldCreation
        groups.updateDraft(roomDraft)
        val created = groups.create(signer)
        if (created == null) {
            held = groups.heldCreation?.takeIf { it != heldBefore }
            return null
        }
        // The rooms' library opened the room (as after a room's creation): the collectif's page is shown instead.
        groups.close()
        val collective = groups.groups.firstOrNull { it.id == created.id }?.let(Collective::from) ?: Collective.from(created)
        draft = CollectiveDraft()
        step = 1
        submitted = false
        if (collective != null) open(collective) else toDirectory()
        return collective
    }

    /**
     * The views of the rooms' form shown by the wizard opened [room]: created after confirming despite
     * rooms of close name, or an existing room joined instead. Its page when it is a collectif, its salon otherwise.
     */
    fun roomOpenedFromWizard(room: Group) {
        if (view != CollectivesView.Create || !submitted) return
        draft = CollectiveDraft()
        step = 1
        submitted = false
        held = null
        val collective = Collective.from(room)
        if (collective != null) {
            groups.close()
            open(collective)
        } else {
            view = CollectivesView.Room(room.id)
        }
    }

    private suspend fun loadRoles(collective: Collective) {
        val store = store ?: return
        val remote = remote
        if (remote != null) {
            val journal = remote.fetchByTags(
                listOf(TagFilter(RoomModeration.TAG_GROUP, collective.id)),
                RemoteFilter(kinds = RoomModeration.KINDS, limit = GroupsLibrary.JOURNAL_LIMIT),
            )
            if (journal is FetchResult.Events) {
                withContext(Dispatchers.Default) {
                    journal.events.forEach { event ->
                        if (event.kind in RoomModeration.KINDS && event.tagValues(RoomModeration.TAG_GROUP) == listOf(collective.id) && event.verify()) store.store(event)
                    }
                }
            }
        }
        val state = withContext(Dispatchers.Default) {
            RoomModeration.compute(collective.group, store.byTag(RoomModeration.KINDS, RoomModeration.TAG_GROUP, collective.id, limit = GroupsLibrary.JOURNAL_LIMIT))
        }
        if ((view as? CollectivesView.Page)?.id == collective.id) {
            moderation = state
            moderationOf = collective.id
        }
    }

    companion object {
        const val LAST_STEP: Int = 4

        /** Fewer letters than this are not looked up in the referential. */
        const val MIN_TERRITORY_SEARCH: Int = 2

        /** Territories proposed under the field, at most. */
        const val MAX_TERRITORY_CHOICES: Int = 5

        /** How the wizard writes a territory of the referential: « Nom (dd) » for a commune or a department, the name alone otherwise. */
        fun choiceOf(territory: Territory): TerritoryChoice {
            val label = when (territory.code.type) {
                TerritoryType.COMMUNE, TerritoryType.ARRONDISSEMENT, TerritoryType.EPCI ->
                    territory.departement?.let { "${territory.name} (${it.code})" } ?: territory.name
                TerritoryType.DEPARTEMENT -> "${territory.name} (${territory.code.code})"
                else -> territory.name
            }
            val scope = when (territory.code.type) {
                TerritoryType.FRANCE -> CollectiveScope.NATIONAL
                TerritoryType.REGION -> CollectiveScope.REGIONAL
                TerritoryType.DEPARTEMENT, TerritoryType.COLLECTIVITE, TerritoryType.OUTRE_MER -> CollectiveScope.DEPARTEMENTAL
                TerritoryType.EPCI, TerritoryType.COMMUNE -> CollectiveScope.LOCAL
                TerritoryType.ARRONDISSEMENT -> CollectiveScope.QUARTIER
            }
            return TerritoryChoice(label, territory.code.tag, scope)
        }

        /** The home of the user: the commune chosen in « Mes élus » (on the device), then the territories that contain it. */
        suspend fun homeOf(dependencies: AppDependencies, territoires: Territoires): List<String> {
            val database = dependencies.database ?: return emptyList()
            val chosen = withContext(Dispatchers.Default) { ElusStore(database).commune() } ?: return emptyList()
            val commune = TerritoryCode.commune(chosen.communeCode) ?: return emptyList()
            val parents = territoires.find(commune)?.parents ?: listOfNotNull(TerritoryCode.departement(chosen.departmentCode))
            return CollectiveQuery.home(commune, parents)
        }
    }
}

/** The collectifs of "Agir" for as long as the space is shown, on the rooms of the shell ([groups]). */
@Composable
fun rememberCollectivesLibrary(dependencies: AppDependencies, groups: GroupsLibrary): CollectivesLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies, groups) {
        val territoires = territoiresFor(dependencies)
        CollectivesLibrary(
            groups = groups,
            store = dependencies.contentStore,
            remote = dependencies.remote,
            urne = dependencies.urneRemote,
            scope = scope,
            homeSource = { CollectivesLibrary.homeOf(dependencies, territoires) },
            territorySource = { text ->
                territoires.search(text, limit = CollectivesLibrary.MAX_TERRITORY_CHOICES).map(CollectivesLibrary::choiceOf)
            },
        )
    }
}
