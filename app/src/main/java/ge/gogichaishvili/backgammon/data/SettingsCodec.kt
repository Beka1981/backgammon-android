package ge.gogichaishvili.backgammon.data

import androidx.datastore.preferences.core.*
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry

/** Stable keys and enum names form the v1 on-disk schema. Unknown values use defaults. */
internal object SettingsCodec {
    private val theme = stringPreferencesKey("board_theme")
    private val checker = stringPreferencesKey("checker_style")
    private val dice = stringPreferencesKey("dice_style")
    private val speed = stringPreferencesKey("animation_speed")
    private val sound = booleanPreferencesKey("sound_effects")
    private val music = booleanPreferencesKey("music")
    private val haptics = booleanPreferencesKey("haptics")
    private val reduced = booleanPreferencesKey("reduced_motion")
    private val language = stringPreferencesKey("language")

    private inline fun <reified T : Enum<T>> value(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    fun decode(p: Preferences): AppSettings = AppSettings(
        themeId = ThemeRegistry.resolve(p[theme].orEmpty()).id,
        checkerStyle = value(p[checker], CheckerStyle.SCULPTED),
        diceStyle = value(p[dice], DiceStyle.IVORY),
        animationSpeed = value(p[speed], AnimationSpeed.NORMAL),
        soundEffects = p[sound] ?: true,
        music = p[music] ?: false,
        haptics = p[haptics] ?: true,
        reducedMotion = p[reduced] ?: false,
        language = value(p[language], AppLanguage.ENGLISH),
    )

    fun encode(s: AppSettings, p: MutablePreferences) {
        p[theme] = ThemeRegistry.resolve(s.themeId).id
        p[checker] = s.checkerStyle.name
        p[dice] = s.diceStyle.name
        p[speed] = s.animationSpeed.name
        p[sound] = s.soundEffects
        p[music] = s.music
        p[haptics] = s.haptics
        p[reduced] = s.reducedMotion
        p[language] = s.language.name
    }
}
