package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MuzeychaLightScheme = lightColorScheme(
    primary = GalleryBronze,
    onPrimary = IvorySurface,
    primaryContainer = WarmBronzeContainer,
    onPrimaryContainer = DeepBronze,
    secondary = ChampagneGold,
    onSecondary = EspressoInk,
    secondaryContainer = WarmStone,
    onSecondaryContainer = EspressoInk,
    tertiary = SageSuccess,
    onTertiary = IvorySurface,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = SageSuccess,
    background = CreamBackground,
    onBackground = EspressoInk,
    surface = IvorySurface,
    onSurface = EspressoInk,
    surfaceVariant = WarmStone,
    onSurfaceVariant = WarmTaupe,
    outline = SoftLinenBorder,
    outlineVariant = SoftLinenBorder,
    error = TerracottaError,
    onError = IvorySurface,
    errorContainer = ErrorContainer,
    onErrorContainer = TerracottaError
)

private val MuzeychaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MuzeychaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MuzeychaLightScheme,
        typography = Typography,
        shapes = MuzeychaShapes,
        content = content
    )
}
