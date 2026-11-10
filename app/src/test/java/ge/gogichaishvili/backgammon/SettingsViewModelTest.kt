package ge.gogichaishvili.backgammon

import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.data.LocalThemeRepository
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.presentation.SettingsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    private class FakeSettings : SettingsRepository {
        val values = MutableStateFlow(AppSettings())
        var failRead = false
        var failWrite = false
        override val settings = flow {
            if (failRead) throw IOException("Read failed")
            emitAll(values)
        }
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            if (failWrite) throw IOException("Write failed")
            values.update(transform)
        }
    }

    private fun model(repo: FakeSettings) = SettingsViewModel(repo, LocalThemeRepository(repo))
        .also { store.put("settings", it) }

    @Test fun loadsSavedStateAndSerializesRapidActions() = runTest {
        val repo = FakeSettings()
        repo.values.value = AppSettings(themeId = "ocean")
        val model = model(repo)
        advanceUntilIdle()
        assertTrue(model.state.value.loaded)
        assertEquals("ocean", model.state.value.theme.id)
        model.selectTheme("royal")
        model.update { it.copy(music = true) }
        model.update { it.copy(reducedMotion = true) }
        advanceUntilIdle()
        assertEquals("royal", model.state.value.theme.id)
        assertTrue(model.state.value.settings.music)
        assertTrue(model.state.value.settings.reducedMotion)
    }

    @Test fun writeFailureKeepsCommittedThemeAndNextActionCanSucceed() = runTest {
        val repo = FakeSettings()
        val model = model(repo)
        advanceUntilIdle()
        repo.failWrite = true
        model.selectTheme("royal")
        advanceUntilIdle()
        assertTrue(model.state.value.saveFailed)
        assertEquals("classic", model.state.value.theme.id)
        repo.failWrite = false
        model.selectTheme("royal")
        advanceUntilIdle()
        assertFalse(model.state.value.saveFailed)
        assertEquals("royal", model.state.value.theme.id)
    }

    @Test fun failedReadCanBeRetried() = runTest {
        val repo = FakeSettings().apply { failRead = true }
        val model = model(repo)
        advanceUntilIdle()
        assertTrue(model.state.value.readFailed)
        assertFalse(model.state.value.loaded)
        repo.failRead = false
        model.retryRead()
        advanceUntilIdle()
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.readFailed)
    }

    @Test fun unknownThemeActionDoesNotChangeSettings() = runTest {
        val repo = FakeSettings()
        val model = model(repo)
        model.selectTheme("missing")
        advanceUntilIdle()
        assertEquals(AppSettings(), model.state.value.settings)
    }
}
