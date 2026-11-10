package ge.gogichaishvili.backgammon.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.*
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme

class BoardSkinPreviews : PreviewParameterProvider<BoardTheme> {
    override val values = ThemeRegistry.all.asSequence()
}
@Preview(name = "Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Preview(name = "Landscape", widthDp = 852, heightDp = 393, showBackground = true)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun BoardPreview(@PreviewParameter(BoardSkinPreviews::class) theme: BoardTheme) {
    LocalizedContent(AppLanguage.ENGLISH) {
        BackgammonTheme(theme) {
            Surface { BoardScreen(SettingsUiState(AppSettings(themeId = theme.id), loaded = true), {}) }
        }
    }
}
@Preview(name = "Settings Georgian", widthDp = 393, heightDp = 852, locale = "ka")
@Preview(name = "Settings large text", widthDp = 393, heightDp = 852, fontScale = 1.6f)
@Composable
private fun SettingsPreview() {
    LocalizedContent(AppLanguage.GEORGIAN) {
        BackgammonTheme {
            Surface { SettingsScreen(AppSettings(), ThemeRegistry.all, {}, {}) }
        }
    }
}
