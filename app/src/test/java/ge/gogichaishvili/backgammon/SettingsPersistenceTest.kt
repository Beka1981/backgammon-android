package ge.gogichaishvili.backgammon

import androidx.datastore.preferences.core.*
import ge.gogichaishvili.backgammon.data.*
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun emptyAndFutureValuesDecodeSafely() {
        assertEquals(AppSettings(), SettingsCodec.decode(emptyPreferences()))
        val future = mutablePreferencesOf(
            stringPreferencesKey("board_theme") to "missing",
            stringPreferencesKey("language") to "KLINGON",
            stringPreferencesKey("checker_style") to "FUTURE",
            stringPreferencesKey("dice_style") to "FUTURE",
            stringPreferencesKey("animation_speed") to "FUTURE")
        assertEquals(AppSettings(), SettingsCodec.decode(future))
    }

    @Test fun allPreferencesSurviveClosingAndReopeningTheStore() = runTest {
        val file = File(temporary.root, "settings.preferences_pb")
        val expected = AppSettings(
            themeId = "ocean", checkerStyle = CheckerStyle.FLAT, diceStyle = DiceStyle.ACCENT,
            animationSpeed = AnimationSpeed.QUICK, soundEffects = false, music = true,
            haptics = false, reducedMotion = true, language = AppLanguage.GEORGIAN)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
            val repo = DataStoreSettingsRepository(store)
            repo.update { expected }
            assertEquals(expected, repo.settings.first())
        } finally { firstScope.coroutineContext[Job]!!.cancelAndJoin() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
            assertEquals(expected, DataStoreSettingsRepository(store).settings.first())
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun concurrentWritesPreserveUnrelatedValuesAndThemeRepositorySharesStore() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope,
                produceFile = { File(temporary.root, "concurrent.preferences_pb") })
            val repo = DataStoreSettingsRepository(store)
            val themes = LocalThemeRepository(repo)
            listOf(
                async { themes.select("royal") },
                async { repo.update { it.copy(music = true) } },
                async { repo.update { it.copy(language = AppLanguage.GEORGIAN) } },
            ).awaitAll()
            val settings = repo.settings.first()
            assertEquals("royal", settings.themeId)
            assertTrue(settings.music)
            assertEquals(AppLanguage.GEORGIAN, settings.language)
            assertEquals(ThemeRegistry.royal, themes.selectedTheme.first())
            assertEquals(3, themes.themes.size)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun unknownKeysArePreservedDuringEdits() {
        val key = stringPreferencesKey("future_setting")
        val preferences = mutablePreferencesOf(key to "retained")
        SettingsCodec.encode(AppSettings(), preferences)
        assertEquals("retained", preferences[key])
    }
}
