package ge.gogichaishvili.backgammon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    // Do not hide IO errors behind defaults: presentation offers an explicit retry.
    override val settings = store.data.map(SettingsCodec::decode).distinctUntilChanged()
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            SettingsCodec.encode(transform(SettingsCodec.decode(preferences)), preferences)
        }
    }
}

class LocalThemeRepository(private val settings: SettingsRepository) : ThemeRepository {
    override val themes = ThemeRegistry.all
    override val selectedTheme = settings.settings.map { ThemeRegistry.resolve(it.themeId) }
        .distinctUntilChanged()
    override suspend fun select(id: String) {
        require(themes.any { it.id == id }) { "Unknown board theme" }
        settings.update { it.copy(themeId = id) }
    }
}
