package com.pockettunes.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Brand palette: near-black navy + cyan blue, sampled from the Noirson reference design. */
object Brand {
    val Black = Color(0xFF050B10)      // app background
    val Ink = Color(0xFF081018)        // surfaces
    val Panel = Color(0xFF0C1722)      // cards, mini player
    val PanelHigh = Color(0xFF112131)  // fields, chips
    val Blue = Color(0xFF22B8F0)       // primary accent
    val BlueDeep = Color(0xFF0A3550)   // gradients, containers
    val Sky = Color(0xFF2AD0F1)        // secondary accent
    val Pale = Color(0xFFAFD0E5)       // big play button
    val Text = Color(0xFFE6F2F8)
    val TextDim = Color(0xFF7F98A8)
    val Line = Color(0xFF16303F)       // thin card borders
}

private val AppColors = darkColorScheme(
    primary = Brand.Blue,
    onPrimary = Color(0xFF03141C),
    primaryContainer = Brand.BlueDeep,
    onPrimaryContainer = Brand.Text,
    secondary = Brand.Sky,
    onSecondary = Color(0xFF03141C),
    secondaryContainer = Brand.PanelHigh,
    onSecondaryContainer = Brand.Text,
    background = Brand.Black,
    onBackground = Brand.Text,
    surface = Brand.Ink,
    onSurface = Brand.Text,
    surfaceVariant = Brand.Panel,
    onSurfaceVariant = Brand.TextDim,
    surfaceContainer = Brand.Ink,
    surfaceContainerHigh = Brand.PanelHigh,
    outline = Brand.Line,
    outlineVariant = Brand.Line,
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}
