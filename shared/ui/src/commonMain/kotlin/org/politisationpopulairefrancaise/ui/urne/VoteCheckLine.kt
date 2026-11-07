package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.urne.UrneSelfTest
import org.politisationpopulairefrancaise.core.urne.UrneSelfTestResult
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.urne_check_failed
import org.politisationpopulairefrancaise.ui.resources.urne_check_passed
import org.politisationpopulairefrancaise.ui.resources.urne_check_running
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tag of the line that shows the result of the voting self-test. */
const val VOTE_CHECK_TAG: String = "vote-check"

/**
 * The result of the voting self-test on this device (D-110, condition b), in one line under the
 * version in "Mon espace": what a tester reads to tell whether anonymous voting can work on the
 * phone. Nothing is sent; voting itself will refuse to open when the self-test failed.
 */
@Composable
fun VoteCheckLine(modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<UrneSelfTestResult?>(null) }
    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.Default) { UrneSelfTest.onThisDevice() }
    }
    val current = result
    val text = when {
        current == null -> stringResource(Res.string.urne_check_running)
        current.passed -> stringResource(Res.string.urne_check_passed)
        else -> stringResource(Res.string.urne_check_failed, current.failedCheck.orEmpty())
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (current != null && !current.passed) PpfTheme.palette.warning else PpfTheme.palette.inkSubtle,
        modifier = modifier.testTag(VOTE_CHECK_TAG),
    )
}
