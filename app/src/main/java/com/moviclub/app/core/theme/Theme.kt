package com.moviclub.app.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MoviClubColorScheme = lightColorScheme(
    primary = DeepGraphite,
    onPrimary = PureWhite,
    secondary = CompetitionOrange,
    onSecondary = DeepGraphite,
    background = OffWhite,
    onBackground = DeepGraphite,
    surface = PureWhite,
    onSurface = DeepGraphite,
    surfaceVariant = IceGray,
    onSurfaceVariant = Slate,
    outline = BorderGray,
    error = ActionRed
)

private val MoviClubTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun MoviClubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoviClubColorScheme,
        typography = MoviClubTypography,
        content = content
    )
}
