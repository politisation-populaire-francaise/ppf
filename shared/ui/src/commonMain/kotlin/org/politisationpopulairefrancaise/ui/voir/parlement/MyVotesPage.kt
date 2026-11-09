package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfRemovableChip
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** Test tags of « Mes élus ont voté ». */
object MyVotesTags {
    const val ENTRY = "my-votes-entry"
    const val PAGE = "my-votes-page"
    const val NO_COMMUNE = "my-votes-no-commune"
    const val OFFICIALS = "my-votes-officials"
    const val NONE = "my-votes-none"
    const val WIFI_ONLY = "my-votes-wifi-only"
    const val EUROPEANS_NONE = "my-votes-europeans-none"
    const val EUROPEANS_ALL = "my-votes-europeans-all"
    const val EUROPEANS_COUNT = "my-votes-europeans-count"
    const val WORD_FIELD = "my-votes-word-field"
    const val WORD_ADD = "my-votes-word-add"
    const val WORD_PROBLEM = "my-votes-word-problem"
    fun ballot(id: String) = "my-votes-ballot-$id"
    fun word(word: String) = "my-votes-word-$word"
    fun european(id: String) = "my-votes-european-$id"
    fun list(index: Int) = "my-votes-list-$index"
}

/** The card on the list of the Parliament that opens « Mes élus ont voté ». */
@Composable
internal fun MyVotesEntry(state: ParliamentState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(MyVotesTags.ENTRY), onClick = { state.show(ParliamentPage.MyVotes) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Vote, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.my_votes_entry_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.my_votes_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * « Mes élus ont voté » (VO-34, D-102), a page of the parliamentary screen: my parliamentarians, the
 * latest ballots where they are listed with their position in words (neutral colours, delegation and
 * correction as published), the alerts (off by default, « seulement en Wi-Fi » on by default), the
 * members of the European Parliament followed (none by default, with the shortcuts « toute la liste »
 * and « tous ») and the followed words. Everything is computed and kept on the device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MyVotesPage(state: ParliamentState, snapshot: ParliamentSnapshot, padding: Dp) {
    val myVotes = state.myVotes
    val palette = PpfTheme.palette
    LaunchedEffect(myVotes) { myVotes.load() }
    val catalog = snapshot.catalog
    val mine = myVotes.parliamentarians(catalog)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(MyVotesTags.PAGE)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.my_votes_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(Res.string.my_votes_privacy), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)

            if (myVotes.loaded && myVotes.mine == null) {
                InfoBox(Lucide.MapPin, Modifier.testTag(MyVotesTags.NO_COMMUNE)) {
                    Text(stringResource(Res.string.my_votes_no_commune), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (mine != null && (mine.deputies.isNotEmpty() || mine.senators.isNotEmpty() || mine.europeans.isNotEmpty())) {
                Column(Modifier.testTag(MyVotesTags.OFFICIALS), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.my_votes_officials), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (mine.sharedCommune) Text(stringResource(Res.string.my_votes_shared_commune), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    (mine.deputies + mine.senators).forEach { official ->
                        val member = official.member
                        if (member != null) {
                            Text("${member.name.withoutExample()} · ${stringResource(member.chamber.role)}", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(stringResource(Res.string.my_votes_unmatched, official.official.name.withoutExample()), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                        }
                    }
                    mine.europeans.forEach { member ->
                        Text("${member.name.withoutExample()} · ${stringResource(member.chamber.role)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(stringResource(Res.string.my_votes_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val recent = remember(catalog, mine) { myVotes.recentVotes(catalog) }
            if (recent.isEmpty()) {
                Text(stringResource(Res.string.my_votes_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(MyVotesTags.NONE))
            }
            recent.forEach { alert ->
                val ballot = alert.entry.ballot
                PpfCard(modifier = Modifier.fillMaxWidth().testTag(MyVotesTags.ballot(ballot.id)), onClick = { state.show(ParliamentPage.OfBallot(ballot.id)) }) {
                    VoirTitle(ballot.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(stringResource(alert.entry.chamber.label), frenchDate(ballot.date)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                    )
                    alert.votes.forEach { mineVote ->
                        Text(
                            stringResource(Res.string.my_votes_member_vote, mineVote.member.name.withoutExample(), stringResource(mineVote.vote.position.label)),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        VoteDetail(mineVote.vote, showPosition = false)
                    }
                }
            }

            AlertsSection(myVotes)
            EuropeansSection(myVotes, snapshot)
            WordsSection(myVotes)
        }
    }
}

@Composable
private fun AlertsSection(myVotes: MyVotesState) {
    val palette = PpfTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.my_votes_alerts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.my_votes_alerts_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = myVotes.wifiOnly, role = Role.Switch, onValueChange = { myVotes.chooseWifiOnly(it) })
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag(MyVotesTags.WIFI_ONLY),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.my_votes_wifi_only), style = MaterialTheme.typography.bodyLarge, color = palette.ink)
                Text(stringResource(Res.string.my_votes_wifi_only_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = myVotes.wifiOnly,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onBrand,
                    checkedTrackColor = palette.brand,
                    uncheckedThumbColor = palette.inkMuted,
                    uncheckedTrackColor = palette.surface1,
                    uncheckedBorderColor = palette.borderStrong,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EuropeansSection(myVotes: MyVotesState, snapshot: ParliamentSnapshot) {
    val palette = PpfTheme.palette
    val catalog = snapshot.catalog
    val lists = remember(catalog) { MyVotesState.europeansByList(catalog) }
    val all = lists.flatMap { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.my_votes_europeans), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.my_votes_europeans_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (!snapshot.isHeld(ParliamentFormat.MEMBERS_PACKAGE) || all.isEmpty()) {
            Text(stringResource(Res.string.my_votes_europeans_download), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@Column
        }
        val chosen = myVotes.europeans
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(stringResource(Res.string.my_votes_europeans_none), selected = chosen.isEmpty(), onClick = { myVotes.chooseEuropeans(emptySet()) }, modifier = Modifier.testTag(MyVotesTags.EUROPEANS_NONE))
            lists.forEachIndexed { index, (party, members) ->
                val ids = members.map { it.id }.toSet()
                val name = party?.withoutExample() ?: stringResource(Res.string.my_votes_europeans_other_list)
                PpfSelectableChip(
                    stringResource(Res.string.my_votes_europeans_list, name, members.size),
                    selected = chosen.isNotEmpty() && chosen == ids,
                    onClick = { myVotes.chooseEuropeans(chosen + ids) },
                    modifier = Modifier.testTag(MyVotesTags.list(index)),
                )
            }
            PpfSelectableChip(
                stringResource(Res.string.my_votes_europeans_all, all.size),
                selected = chosen.isNotEmpty() && chosen.containsAll(all.map { it.id }),
                onClick = { myVotes.chooseEuropeans(all.map { it.id }.toSet()) },
                modifier = Modifier.testTag(MyVotesTags.EUROPEANS_ALL),
            )
        }
        // A sentence on the number of alerts to expect, from the ballots on the device only.
        val ballots = remember(catalog) { catalog.ballots().count { it.chamber == Chamber.PE } }
        Text(
            if (ballots > 0) stringResource(Res.string.my_votes_europeans_expected, ballots) else stringResource(Res.string.my_votes_europeans_expected_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )
        Text(
            stringResource(Res.string.my_votes_europeans_count, chosen.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag(MyVotesTags.EUROPEANS_COUNT).semantics { liveRegion = LiveRegionMode.Polite },
        )
        all.forEach { member ->
            val followed = member.id in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = followed, role = Role.Checkbox, onValueChange = { myVotes.toggleEuropean(member.id) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag(MyVotesTags.european(member.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (followed) Lucide.CircleCheck else Lucide.Plus, contentDescription = null, tint = if (followed) palette.brand else palette.inkSubtle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name.withoutExample(), style = MaterialTheme.typography.bodyMedium)
                    member.nationalParty?.let { Text(it.withoutExample(), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsSection(myVotes: MyVotesState) {
    val palette = PpfTheme.palette
    var draft by remember { mutableStateOf("") }
    val add = { if (myVotes.addWord(draft)) draft = "" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.my_votes_words), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.my_votes_words_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PpfTextField(
                value = draft,
                onValueChange = { draft = it.take(120) },
                placeholder = stringResource(Res.string.my_votes_word_field),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f).testTag(MyVotesTags.WORD_FIELD),
            )
            Spacer(Modifier.width(8.dp))
            PpfSecondaryButton(onClick = { add() }, text = stringResource(Res.string.my_votes_word_add), icon = Lucide.Plus, modifier = Modifier.testTag(MyVotesTags.WORD_ADD))
        }
        myVotes.wordProblem?.let { problem ->
            val text = when (problem) {
                WordProblem.EMPTY -> Res.string.my_votes_word_empty
                WordProblem.ALREADY -> Res.string.my_votes_word_already
                WordProblem.TOO_MANY -> Res.string.my_votes_word_too_many
            }
            Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = palette.accent, modifier = Modifier.testTag(MyVotesTags.WORD_PROBLEM).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (myVotes.words.isEmpty()) {
            Text(stringResource(Res.string.my_votes_words_none), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                myVotes.words.forEach { word ->
                    PpfRemovableChip(word, onRemove = { myVotes.removeWord(word) }, removeDescription = stringResource(Res.string.my_votes_word_remove, word), modifier = Modifier.testTag(MyVotesTags.word(word)))
                }
            }
        }
    }
}
