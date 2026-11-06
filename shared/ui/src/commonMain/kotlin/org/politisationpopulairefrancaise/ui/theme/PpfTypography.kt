package org.politisationpopulairefrancaise.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.atkinson_hyperlegible_bold
import org.politisationpopulairefrancaise.ui.resources.atkinson_hyperlegible_bold_italic
import org.politisationpopulairefrancaise.ui.resources.atkinson_hyperlegible_italic
import org.politisationpopulairefrancaise.ui.resources.atkinson_hyperlegible_regular

/**
 * One typeface for everything, bundled with the application (no font is ever loaded from a third
 * party): Atkinson Hyperlegible, designed by the Braille Institute for low-vision readers and
 * released under the SIL Open Font License. Two weights only, regular and bold, as in the identity.
 * The scale is the one of the mock-ups: display 40/44, h1 28/34, h2 22/28, h3 18/24, body 16/24,
 * small 14/20, caption 12/16 (bold, slightly spaced).
 */
@Composable
fun ppfTypography(): Typography {
    val atkinson = FontFamily(
        Font(Res.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(Res.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        Font(Res.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val bold = FontWeight.Bold
    val regular = FontWeight.Normal
    return Typography(
        displayLarge = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 40.sp, lineHeight = 44.sp),
        displayMedium = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 40.sp, lineHeight = 44.sp),
        displaySmall = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineLarge = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = atkinson, fontWeight = regular, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = atkinson, fontWeight = regular, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = atkinson, fontWeight = regular, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.24.sp),
        labelSmall = TextStyle(fontFamily = atkinson, fontWeight = bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.24.sp),
    )
}
