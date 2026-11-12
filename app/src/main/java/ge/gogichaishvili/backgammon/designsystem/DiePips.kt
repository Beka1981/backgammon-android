package ge.gogichaishvili.backgammon.designsystem

import androidx.compose.ui.geometry.Offset

/** Immutable pip coordinates shared by board and animated control dice. */
internal val diePips: List<List<Offset>> = listOf(
    emptyList(),
    listOf(Offset(26f, 26f)),
    listOf(Offset(13f, 13f), Offset(39f, 39f)),
    listOf(Offset(13f, 13f), Offset(39f, 39f), Offset(26f, 26f)),
    listOf(Offset(13f, 13f), Offset(39f, 39f), Offset(39f, 13f), Offset(13f, 39f)),
    listOf(Offset(13f, 13f), Offset(39f, 39f), Offset(39f, 13f), Offset(13f, 39f), Offset(26f, 26f)),
    listOf(Offset(13f, 13f), Offset(39f, 39f), Offset(39f, 13f), Offset(13f, 39f), Offset(13f, 26f), Offset(39f, 26f)),
)
