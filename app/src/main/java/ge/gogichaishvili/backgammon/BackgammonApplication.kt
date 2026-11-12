package ge.gogichaishvili.backgammon

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.dataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import ge.gogichaishvili.backgammon.data.*
import ge.gogichaishvili.backgammon.domain.match.MatchRepository
import ge.gogichaishvili.backgammon.data.DataStoreSettingsRepository
import ge.gogichaishvili.backgammon.data.LocalThemeRepository
import ge.gogichaishvili.backgammon.domain.settings.SettingsRepository
import ge.gogichaishvili.backgammon.domain.theme.ThemeRepository

private val Context.settingsDataStore by preferencesDataStore(name = "user_preferences_v1")
private val Context.matchDataStore by dataStore(
    fileName = "active_match.bin", serializer = MatchSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { StoredMatch.Recovered })

class AppContainer(context: Context) {
    private val authScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val accounts by lazy {
        ge.gogichaishvili.backgammon.data.auth.SupabaseClientProvider.services(
            ge.gogichaishvili.backgammon.data.auth.EncryptedAuthStorage(context), authScope)
    }
    val auth get() = accounts.auth
    val profiles get() = accounts.profiles
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)
    val matches: MatchRepository = DataStoreMatchRepository(context.matchDataStore)
    val themes: ThemeRepository = LocalThemeRepository(settings)
}

class BackgammonApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
