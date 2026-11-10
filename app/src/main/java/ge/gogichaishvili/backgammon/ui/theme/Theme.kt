package ge.gogichaishvili.backgammon.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ge.gogichaishvili.backgammon.designsystem.AppColors
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry

@Composable
fun BackgammonTheme(
    boardTheme: BoardTheme = ThemeRegistry.classic,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(boardTheme.palette.accent),
            onPrimary = AppColors.background,
            primaryContainer = AppColors.surfaceRaised,
            onPrimaryContainer = AppColors.ink,
            secondary = Color(boardTheme.palette.accent),
            background = AppColors.background,
            onBackground = AppColors.ink,
            surface = AppColors.surface,
            onSurface = AppColors.ink,
            surfaceVariant = AppColors.surfaceRaised,
            onSurfaceVariant = AppColors.muted,
            outline = AppColors.outline,
            error = AppColors.error,
        ),
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
