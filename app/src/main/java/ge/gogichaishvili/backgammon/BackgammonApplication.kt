package ge.gogichaishvili.backgammon

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import ge.gogichaishvili.backgammon.data.DataStoreSettingsRepository
import ge.gogichaishvili.backgammon.data.LocalThemeRepository
import ge.gogichaishvili.backgammon.domain.settings.SettingsRepository
import ge.gogichaishvili.backgammon.domain.theme.ThemeRepository

private val Context.settingsDataStore by preferencesDataStore(name = "user_preferences_v1")

class AppContainer(context: Context) {
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val themes: ThemeRepository = LocalThemeRepository(settings)
}

class BackgammonApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
