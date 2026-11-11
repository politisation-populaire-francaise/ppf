package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.ppf_logo
import org.politisationpopulairefrancaise.ui.resources.symbol_description
import org.politisationpopulairefrancaise.ui.theme.LightPalette
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tag of the logo of PPF, wherever it is shown. */
object SymbolTags {
    const val SYMBOL = "ppf-symbol"
}

/*
 * The official logo of PPF (decision of the owner, 11 November 2026, D-187: « Le logo officiel est
 * désormais celui-ci »): a Marianne in a Phrygian cap raising the French flag among a crowd of citizens
 * and musicians, on a disc. Original in `design/identite/logo-officiel/marianne-logo.png`; the
 * application draws `ppf_logo.png` (256 px, cropped to the drawing), enough for its 28 to 48 dp places.
 * Its indigo is the brand colour of the light theme: on a dark page it sits on a light disc.
 */

/** The image of the logo, for the hosts (the window of the computer). */
@Composable
fun ppfLogoPainter(): Painter = painterResource(Res.drawable.ppf_logo)

/**
 * The logo of PPF at [size]. Read by screen readers as « Logo de Politisation Populaire Française… »,
 * unless [decorative] (when the name is written beside it). Never smaller than [MIN_SYMBOL_SIZE].
 */
@Composable
fun PpfSymbol(size: Dp, modifier: Modifier = Modifier, decorative: Boolean = false) {
    val description = stringResource(Res.string.symbol_description)
    val dark = PpfTheme.palette.surface1.luminance() < 0.5f
    val side = maxOf(size, MIN_SYMBOL_SIZE)
    Box(
        modifier = modifier
            .size(side)
            .then(if (dark) Modifier.background(LightPalette.surface2, CircleShape) else Modifier)
            .then(if (decorative) Modifier else Modifier.semantics { contentDescription = description; role = Role.Image })
            .testTag(SymbolTags.SYMBOL),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = ppfLogoPainter(),
            contentDescription = null,
            modifier = Modifier.size(side).then(if (dark) Modifier.padding(side * 0.08f) else Modifier),
        )
    }
}

/** The smallest size of the logo: below, its details are lost. */
val MIN_SYMBOL_SIZE: Dp = 24.dp
