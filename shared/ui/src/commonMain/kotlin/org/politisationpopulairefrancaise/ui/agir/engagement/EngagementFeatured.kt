package org.politisationpopulairefrancaise.ui.agir.engagement

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.engagement.Engagement
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.engager_featured_days
import org.politisationpopulairefrancaise.ui.resources.engager_featured_next
import org.politisationpopulairefrancaise.ui.resources.engager_featured_open
import org.politisationpopulairefrancaise.ui.resources.engager_featured_title
import org.politisationpopulairefrancaise.ui.resources.engager_featured_today
import org.politisationpopulairefrancaise.ui.resources.engager_milestone_inscription
import org.politisationpopulairefrancaise.ui.resources.engager_milestone_tour1
import org.politisationpopulairefrancaise.ui.resources.engager_milestone_tour2
import org.politisationpopulairefrancaise.ui.resources.engager_rubric_text
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the highlight of « S'engager en politique » on the home of « Agir ». */
object EngagementFeaturedTags {
    const val CARD = "engager-featured"
    const val NEXT = "engager-featured-next"
    const val OPEN = "engager-featured-open"
}

/**
 * « S'engager en politique » put forward on the home of « Agir » (D-213): the next dated milestone of the
 * calendar (registering first) and how many days are left, then the way to the steps. Once every milestone
 * is past, only the invitation stays.
 */
@Composable
fun EngagementFeatured(today: CivilDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val next = Engagement.nextMilestone(today)
    PpfCard(modifier.fillMaxWidth().testTag(EngagementFeaturedTags.CARD), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Vote, contentDescription = null, tint = palette.brand, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.engager_featured_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(8.dp))
        if (next == null) {
            Text(stringResource(Res.string.engager_rubric_text), style = MaterialTheme.typography.bodyMedium)
        } else {
            val what = when (next) {
                Engagement.REGISTRATION_DEADLINE -> Res.string.engager_milestone_inscription
                Engagement.PRESIDENTIAL_FIRST_ROUND -> Res.string.engager_milestone_tour1
                else -> Res.string.engager_milestone_tour2
            }
            val days = Engagement.daysUntil(next, today)
            val left = if (days == 0L) stringResource(Res.string.engager_featured_today) else stringResource(Res.string.engager_featured_days, days.toInt())
            Text(
                stringResource(Res.string.engager_featured_next, stringResource(what), longDate(next)) + " " + left,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EngagementFeaturedTags.NEXT),
            )
        }
        Spacer(Modifier.height(12.dp))
        PpfPrimaryButton(onClick = onOpen, text = stringResource(Res.string.engager_featured_open), modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementFeaturedTags.OPEN))
    }
}
