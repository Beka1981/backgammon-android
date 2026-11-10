package ge.gogichaishvili.backgammon.domain.theme

import kotlinx.coroutines.flow.Flow

/** ARGB values, independent of Android and rendering APIs. */
data class BoardPalette(
    val frame: Long, val frameLight: Long, val field: Long,
    val pointLight: Long, val pointDark: Long, val accent: Long,
    val ivory: Long, val dark: Long, val shadow: Long = 0x99000000,
)
data class BoardTheme(val id: String, val palette: BoardPalette)

object ThemeRegistry {
    val classic = BoardTheme("classic", BoardPalette(
        0xFF291711, 0xFF67432C, 0xFFB68B60, 0xFFE9D4AE, 0xFF743546,
        0xFFD9BA7B, 0xFFF4E8D3, 0xFF33241E))
    val royal = BoardTheme("royal", BoardPalette(
        0xFF0B0E13, 0xFF30343D, 0xFF262A31, 0xFFBFA16A, 0xFF11151C,
        0xFFE5C576, 0xFFF4E9CF, 0xFF151922))
    val ocean = BoardTheme("ocean", BoardPalette(
        0xFF072637, 0xFF18526B, 0xFF42788B, 0xFFDBEEF0, 0xFF16475F,
        0xFFC7DDE6, 0xFFF4FAFB, 0xFF103C56))
    val all: List<BoardTheme> = listOf(classic, royal, ocean)
    fun resolve(id: String): BoardTheme = all.firstOrNull { it.id == id } ?: classic
}

interface ThemeRepository {
    val themes: List<BoardTheme>
    val selectedTheme: Flow<BoardTheme>
    suspend fun select(id: String)
}
