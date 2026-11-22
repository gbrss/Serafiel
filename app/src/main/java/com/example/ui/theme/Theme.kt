package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val SerafielDarkColorScheme = darkColorScheme(
    primary = NightAmber,
    onPrimary = OnNightAmber,
    primaryContainer = NightAmberContainer,
    onPrimaryContainer = OnNightAmberContainer,
    secondary = NightYellow,
    onSecondary = OnNightYellow,
    secondaryContainer = NightYellowContainer,
    onSecondaryContainer = OnNightYellowContainer,
    tertiary = NightGold,
    onTertiary = OnNightGold,
    tertiaryContainer = NightGoldContainer,
    onTertiaryContainer = OnNightGoldContainer,
    background = NightEspressoBackground,
    onBackground = NightIvoryInk,
    surface = NightEspressoSurface,
    onSurface = NightIvoryInk,
    surfaceVariant = NightEspressoContainer,
    onSurfaceVariant = NightMutedInk,
    surfaceContainer = NightEspressoContainer,
    surfaceContainerHigh = NightEspressoContainerHigh,
    outline = NightOutline,
    outlineVariant = NightEspressoContainerHigh,
)

private val SerafielLightColorScheme = lightColorScheme(
    primary = SunlitAmber,
    onPrimary = OnSunlitAmber,
    primaryContainer = SunlitAmberContainer,
    onPrimaryContainer = OnSunlitAmberContainer,
    secondary = SunYellow,
    onSecondary = OnSunYellow,
    secondaryContainer = SunYellowContainer,
    onSecondaryContainer = OnSunYellowContainer,
    tertiary = GoldenBeacon,
    onTertiary = OnGoldenBeacon,
    tertiaryContainer = GoldenBeaconContainer,
    onTertiaryContainer = OnGoldenBeaconContainer,
    background = WarmIvoryBackground,
    onBackground = WarmEspressoInk,
    surface = WarmSandSurface,
    onSurface = WarmEspressoInk,
    surfaceVariant = WarmSandContainer,
    onSurfaceVariant = WarmMutedInk,
    surfaceContainer = WarmSandContainer,
    surfaceContainerHigh = WarmSandContainerHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = SafetyRed,
    errorContainer = SafetyRedContainer,
    onErrorContainer = OnSafetyRedContainer,
)

val SerafielShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SerafielTheme(
    darkTheme: Boolean = ThemeSettings.appTheme.collectAsState().value.let { t ->
        if (t != ThemeSettings.AppTheme.DEFAULT) t.isDark
        else when (ThemeSettings.mode.collectAsState().value) {
            ThemeSettings.Mode.LIGHT -> false
            ThemeSettings.Mode.DARK -> true
            ThemeSettings.Mode.SYSTEM -> isSystemInDarkTheme()
        }
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val accent by ThemeSettings.accent.collectAsState()
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SerafielDarkColorScheme
        else -> SerafielLightColorScheme
    }
    val appTheme by ThemeSettings.appTheme.collectAsState()
    val themedScheme = if (appTheme == ThemeSettings.AppTheme.DEFAULT) baseScheme else baseScheme.copy(
        background = Color(appTheme.bg),
        surface = Color(appTheme.surface),
        surfaceVariant = Color(appTheme.container),
        surfaceContainerLowest = Color(appTheme.bg),
        surfaceContainerLow = Color(appTheme.surface),
        surfaceContainer = Color(appTheme.container),
        surfaceContainerHigh = Color(appTheme.containerHigh),
        surfaceContainerHighest = Color(appTheme.containerHigh),
        onBackground = Color(appTheme.ink),
        onSurface = Color(appTheme.ink),
        onSurfaceVariant = Color(appTheme.muted),
        outline = Color(appTheme.outline),
        outlineVariant = Color(appTheme.containerHigh),
    )
    val colorScheme = if (accent == ThemeSettings.Accent.AMBER) themedScheme else if (darkTheme) {
        themedScheme.copy(
            primary = Color(accent.darkPrimary),
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = Color(accent.darkContainer),
            onPrimaryContainer = Color(accent.darkOnContainer),
        )
    } else {
        themedScheme.copy(
            primary = Color(accent.lightPrimary),
            onPrimary = Color.White,
            primaryContainer = Color(accent.lightContainer),
            onPrimaryContainer = Color(accent.lightOnContainer),
        )
    }

    // Iconos de las barras del sistema: oscuros sobre fondo claro y viceversa.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            var ctx = view.context
            while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
            val window = (ctx as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SerafielTypography,
        shapes = SerafielShapes,
        content = content,
    )
}
