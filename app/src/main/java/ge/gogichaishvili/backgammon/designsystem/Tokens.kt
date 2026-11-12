package ge.gogichaishvili.backgammon.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Space {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 32.dp
    val page = 40.dp
    val maxContent = 1200.dp
    val minTouch = 48.dp
}
/** Keep consumed/unplayable faces readable; status text also identifies their state. */
object DiceEmphasis {
    const val consumed = 0.55f
    const val unplayable = 0.65f
}
object AppColors {
    val background = Color(0xFF10161C)
    val surface = Color(0xFF1B242D)
    val surfaceRaised = Color(0xFF25323D)
    val ink = Color(0xFFF2EDE2)
    val muted = Color(0xFFB9C3CC)
    val outline = Color(0xFF697984)
    val error = Color(0xFFFFB4AB)
}
