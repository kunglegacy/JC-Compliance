package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentPalette
import com.example.data.model.ThemePreference

@Immutable
data class CompliancePalette(
    val isDark: Boolean,
    val bgBase: Color,
    val bgEnd: Color,
    val glow1: Color,
    val glow2: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val shadow: Color,
    val trackFill: Color,
    val pillFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val tangerine: Color,
    val tangerineEnd: Color,
    val emerald: Color,
    val rose: Color
)

val DarkObsidian = CompliancePalette(
    isDark = true,
    bgBase = Color(0xFF090B0E),
    bgEnd = Color(0xFF12151C),
    glow1 = Color(0xFFFF5A1F).copy(alpha = 0.14f),
    glow2 = Color(0xFF3B82F6).copy(alpha = 0.10f),
    glassTop = Color(0xFF262B36).copy(alpha = 0.72f),
    glassBottom = Color(0xFF141820).copy(alpha = 0.58f),
    borderTop = Color.White.copy(alpha = 0.26f),
    borderBottom = Color.White.copy(alpha = 0.04f),
    shadow = Color.Black.copy(alpha = 0.55f),
    trackFill = Color.White.copy(alpha = 0.08f),
    pillFill = Color.White.copy(alpha = 0.18f),
    textPrimary = Color.White,
    textSecondary = Color(0xFF9CA3AF),
    tangerine = Color(0xFFFF5A1F),
    tangerineEnd = Color(0xFFE04312),
    emerald = Color(0xFF22C55E),
    rose = Color(0xFFF43F5E)
)

val LightAlabaster = CompliancePalette(
    isDark = false,
    bgBase = Color(0xFFF3F5F9),
    bgEnd = Color(0xFFE6EAF2),
    glow1 = Color(0xFFFF5A1F).copy(alpha = 0.10f),
    glow2 = Color(0xFF0EA5E9).copy(alpha = 0.09f),
    glassTop = Color.White.copy(alpha = 0.85f),
    glassBottom = Color(0xFFF8FAFC).copy(alpha = 0.64f),
    borderTop = Color.White.copy(alpha = 0.95f),
    borderBottom = Color(0xFF94A3B8).copy(alpha = 0.28f),
    shadow = Color(0xFF0F172A).copy(alpha = 0.10f),
    trackFill = Color(0xFF0F172A).copy(alpha = 0.06f),
    pillFill = Color.White.copy(alpha = 0.88f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    tangerine = Color(0xFFF04E16),
    tangerineEnd = Color(0xFFD93E0A),
    emerald = Color(0xFF16A34A),
    rose = Color(0xFFE11D48)
)

val LocalCompliancePalette = staticCompositionLocalOf { DarkObsidian }
val LocalComplianceColors = LocalCompliancePalette

@Immutable
data class ExtendedColors(
    val isDark: Boolean,
    val emerald: Color,
    val emeraldContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val rose: Color,
    val roseContainer: Color,
    val indigo: Color,
    val indigoContainer: Color,
    val cardBorder: Color,
    val subtleSurface: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassRimStart: Color,
    val glassRimEnd: Color,
    val ambientGlow: Color,
    val solarGold: Color,
    val solarEmber: Color,
    val heroInk: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        isDark = true,
        emerald = Emerald500,
        emeraldContainer = EmeraldSoftDark,
        amber = Amber500,
        amberContainer = AmberSoftDark,
        rose = Rose500,
        roseContainer = RoseSoftDark,
        indigo = Indigo500,
        indigoContainer = IndigoSoftDark,
        cardBorder = DarkOutline,
        subtleSurface = DarkSurfaceVariant,
        glassTop = DarkObsidian.glassTop,
        glassBottom = DarkObsidian.glassBottom,
        glassRimStart = DarkObsidian.borderTop,
        glassRimEnd = DarkObsidian.borderBottom,
        ambientGlow = DarkObsidian.glow1,
        solarGold = SolarGold,
        solarEmber = TangerineDark,
        heroInk = Color(0xFF0E1116)
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun JCBestTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    accentPalette: AccentPalette = AccentPalette.SOLAR,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val (primary, onPrimary, primaryContainer, onPrimaryContainer, glowTint) = when (accentPalette) {
        AccentPalette.SOLAR -> if (isDark) {
            listOf(TangerineDark, Color.White, SolarContainerDark, Color(0xFFFFB08E), TangerineDark)
        } else {
            listOf(TangerineLight, Color.White, SolarContainerLight, Color(0xFF7C2D12), TangerineLight)
        }
        AccentPalette.SAPPHIRE -> if (isDark) {
            listOf(SapphirePrimaryDark, Color(0xFF0A0C10), SapphireContainerDark, Color(0xFFDBEAFE), SapphirePrimaryDark)
        } else {
            listOf(SapphirePrimaryLight, Color.White, SapphireContainerLight, Color(0xFF1E3A8A), SapphirePrimaryLight)
        }
        AccentPalette.ROSE -> if (isDark) {
            listOf(RoseAccentDark, Color(0xFF0A0C10), RoseContainerDark, Color(0xFFFFE4E6), RoseAccentDark)
        } else {
            listOf(RoseAccentLight, Color.White, RoseContainerLight, Color(0xFF881337), RoseAccentLight)
        }
        AccentPalette.AMETHYST -> if (isDark) {
            listOf(AmethystAccentDark, Color(0xFF0A0C10), AmethystContainerDark, Color(0xFFEDE9FE), AmethystAccentDark)
        } else {
            listOf(AmethystAccentLight, Color.White, AmethystContainerLight, Color(0xFF4C1D95), AmethystAccentLight)
        }
        AccentPalette.TITANIUM -> if (isDark) {
            listOf(TitaniumAccentDark, Color(0xFF0A0C10), TitaniumContainerDark, Color(0xFFF1F5F9), TitaniumAccentDark)
        } else {
            listOf(TitaniumAccentLight, Color.White, TitaniumContainerLight, Color(0xFF0F172A), Color(0xFF64748B))
        }
    }.let { Tuple5(it[0], it[1], it[2], it[3], it[4]) }

    val basePalette = if (isDark) DarkObsidian else LightAlabaster
    val activePalette = if (accentPalette == AccentPalette.SOLAR) {
        basePalette
    } else {
        basePalette.copy(
            tangerine = primary,
            tangerineEnd = primary.copy(alpha = 0.85f),
            glow1 = glowTint.copy(alpha = if (isDark) 0.15f else 0.11f)
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = Indigo500,
            onSecondary = Color.White,
            secondaryContainer = IndigoSoftDark,
            onSecondaryContainer = Color(0xFFE0E7FF),
            tertiary = Emerald500,
            onTertiary = Color(0xFF0A0C10),
            background = activePalette.bgBase,
            onBackground = activePalette.textPrimary,
            surface = DarkSurface,
            onSurface = activePalette.textPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = activePalette.textSecondary,
            surfaceContainerLowest = DarkBackground,
            surfaceContainerLow = DarkBackgroundDeep,
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = DarkSurfaceGlassTop,
            surfaceContainerHighest = Color(0xFF2B3140),
            inverseSurface = Color(0xFF222733),
            inverseOnSurface = Color.White,
            outline = DarkOutline,
            outlineVariant = Color(0x20FFFFFF),
            error = Rose500,
            onError = Color.White,
            errorContainer = RoseSoftDark,
            onErrorContainer = Color(0xFFFFE4E6)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = Indigo600,
            onSecondary = Color.White,
            secondaryContainer = IndigoSoftLight,
            onSecondaryContainer = Color(0xFF312E81),
            tertiary = Emerald600,
            background = activePalette.bgBase,
            onBackground = activePalette.textPrimary,
            surface = LightSurface,
            onSurface = activePalette.textPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = activePalette.textSecondary,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = LightBackground,
            surfaceContainer = LightSurface,
            surfaceContainerHigh = LightBackgroundDeep,
            surfaceContainerHighest = Color(0xFFDDE3EC),
            inverseSurface = Color(0xFF0F172A),
            inverseOnSurface = Color.White,
            outline = LightOutline,
            outlineVariant = Color(0xFFCBD5E1),
            error = Rose600,
            onError = Color.White,
            errorContainer = RoseSoftLight,
            onErrorContainer = Color(0xFF881337)
        )
    }

    val extended = ExtendedColors(
        isDark = isDark,
        emerald = activePalette.emerald,
        emeraldContainer = if (isDark) EmeraldSoftDark else EmeraldSoftLight,
        amber = if (isDark) Amber500 else Amber600,
        amberContainer = if (isDark) AmberSoftDark else AmberSoftLight,
        rose = activePalette.rose,
        roseContainer = if (isDark) RoseSoftDark else RoseSoftLight,
        indigo = if (isDark) Indigo500 else Indigo600,
        indigoContainer = if (isDark) IndigoSoftDark else IndigoSoftLight,
        cardBorder = activePalette.borderTop,
        subtleSurface = activePalette.trackFill,
        glassTop = activePalette.glassTop,
        glassBottom = activePalette.glassBottom,
        glassRimStart = activePalette.borderTop,
        glassRimEnd = activePalette.borderBottom,
        ambientGlow = activePalette.glow1,
        solarGold = SolarGold,
        solarEmber = activePalette.tangerine,
        heroInk = Color(0xFF0E1116)
    )

    CompositionLocalProvider(
        LocalCompliancePalette provides activePalette,
        LocalExtendedColors provides extended,
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                content()
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

object ComplianceTheme {
    val extended: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current

    val palette: CompliancePalette
        @Composable
        get() = LocalCompliancePalette.current
}
