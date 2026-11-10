package ge.gogichaishvili.backgammon.domain.settings

import kotlinx.coroutines.flow.Flow

enum class CheckerStyle { SCULPTED, FLAT }
enum class DiceStyle { IVORY, ACCENT }
enum class AnimationSpeed(val multiplier: Float) { RELAXED(1.5f), NORMAL(1f), QUICK(0.65f) }
enum class AppLanguage(val tag: String) { ENGLISH("en"), GEORGIAN("ka") }

data class AppSettings(
    val themeId: String = "classic",
    val checkerStyle: CheckerStyle = CheckerStyle.SCULPTED,
    val diceStyle: DiceStyle = DiceStyle.IVORY,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val soundEffects: Boolean = true,
    val music: Boolean = false,
    val haptics: Boolean = true,
    val reducedMotion: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    /** Transform the latest persisted value atomically, avoiding stale UI snapshots. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
