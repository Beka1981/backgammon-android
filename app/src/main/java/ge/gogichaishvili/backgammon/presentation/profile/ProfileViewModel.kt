package ge.gogichaishvili.backgammon.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.profile.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ProfilePage { LOADING, SETUP, MY_PROFILE, EDIT, AVATARS, ERROR }
data class ProfileUiState(
    val page: ProfilePage = ProfilePage.LOADING,
    val profile: PublicProfile? = null,
    val draft: ProfileDraft = ProfileDraft(),
    val avatarPreview: String = BuiltInAvatars.defaultId,
    val busy: Boolean = false,
    val issue: ProfileIssue? = null,
    val openRequested: Boolean = false,
    internal val ownerId: String? = null, // UI-only binding; never part of PublicProfile or server DTOs.
)
sealed interface ProfileAction {
    data class Username(val value: String) : ProfileAction
    data class DisplayName(val value: String) : ProfileAction
    data object Edit : ProfileAction
    data object Save : ProfileAction
    data object Cancel : ProfileAction
    data object Retry : ProfileAction
    data object OpenAvatars : ProfileAction
    data class PreviewAvatar(val id: String) : ProfileAction
    data object SaveAvatar : ProfileAction
    data object NavigationHandled : ProfileAction
}

/** Auth epochs fence canceled/late requests. Only committed server profiles become display state. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val repository: ProfileRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state = mutable.asStateFlow()
    private var owner: String? = null
    private var epoch = 0L
    private var work: Job? = null
    private var avatarReturn = ProfilePage.SETUP
    init {
        viewModelScope.launch {
            auth.state.map { (it as? AuthState.SignedIn)?.takeUnless { session -> session.recoveryRequired }?.user?.id }
                .distinctUntilChanged().collect { next ->
                    epoch++; work?.cancel(); owner = next
                    mutable.value = ProfileUiState(ownerId = next)
                    if (next == null) {
                        // Keep a process-restored draft until the initial Auth load resolves.
                        if (auth.state.value != AuthState.Loading) clearDraft()
                    } else {
                        if (saved.get<String>("profile.owner") != next) clearDraft()
                        load()
                    }
                }
        }
    }
    fun dispatch(action: ProfileAction) {
        if (action == ProfileAction.NavigationHandled) {
            mutable.update { it.copy(openRequested = false) }; return
        }
        if (owner == null || mutable.value.busy) return
        val ui = mutable.value
        when (action) {
            ProfileAction.Retry -> {
                if (ui.issue == ProfileIssue.VERSION_CONFLICT) clearDraft()
                load()
            }
            ProfileAction.Edit -> if (ui.profile != null) {
                clearDraft()
                change(ui.copy(page = ProfilePage.EDIT, draft = ui.profile.draft(), issue = null))
            }
            is ProfileAction.Username -> if (ui.page in editable) change(ui.copy(draft = ui.draft.copy(username = action.value.take(100)), issue = null))
            is ProfileAction.DisplayName -> if (ui.page in editable) change(ui.copy(draft = ui.draft.copy(displayName = action.value.take(200)), issue = null))
            ProfileAction.OpenAvatars -> if (ui.page in editable) {
                avatarReturn = ui.page
                mutable.value = ui.copy(page = ProfilePage.AVATARS, avatarPreview = ui.draft.avatarId, issue = null)
            }
            is ProfileAction.PreviewAvatar -> if (ui.page == ProfilePage.AVATARS && action.id in BuiltInAvatars.ids)
                mutable.value = ui.copy(avatarPreview = action.id)
            ProfileAction.SaveAvatar -> if (ui.page == ProfilePage.AVATARS)
                change(ui.copy(page = avatarReturn, draft = ui.draft.copy(avatarId = ui.avatarPreview)))
            ProfileAction.Cancel -> when (ui.page) {
                ProfilePage.AVATARS -> mutable.value = ui.copy(page = avatarReturn, avatarPreview = ui.draft.avatarId)
                ProfilePage.EDIT -> { clearDraft(); mutable.value = ui.copy(page = ProfilePage.MY_PROFILE, draft = ui.profile!!.draft(), issue = null) }
                else -> Unit // Setup can always be left through Local Play, without inventing a profile.
            }
            ProfileAction.Save -> if (ui.page in editable) save()
            else -> Unit
        }
    }
    private fun load() {
        val generation = epoch
        mutable.update { it.copy(page = ProfilePage.LOADING, busy = true, issue = null) }
        work = viewModelScope.launch {
            val result = safe { repository.loadMine() }
            if (generation != epoch) return@launch
            when (result) {
                is ProfileResult.Found -> {
                    val restored = restoreDraft()
                    mutable.value = ProfileUiState(
                        page = if (restored == null) ProfilePage.MY_PROFILE else ProfilePage.EDIT,
                        profile = result.profile, draft = restored ?: result.profile.draft(), ownerId = owner)
                }
                ProfileResult.Missing -> mutable.value = ProfileUiState(page = ProfilePage.SETUP,
                    draft = restoreDraft() ?: ProfileDraft(), openRequested = true, ownerId = owner)
                is ProfileResult.Failure -> mutable.value = ProfileUiState(page = ProfilePage.ERROR, issue = result.issue, ownerId = owner)
            }
        }
    }
    private fun save() {
        val ui = mutable.value
        ProfileValidation.validate(ui.draft)?.let { mutable.value = ui.copy(issue = it); return }
        val generation = epoch
        val normalized = ProfileValidation.normalized(ui.draft)
        val version = saved.get<Long>("profile.expected") ?: ui.profile?.version
        val command = saved.get<String>("profile.command") ?: UUID.randomUUID().toString().also {
            saved["profile.command"] = it; if (version != null) saved["profile.expected"] = version
        }
        mutable.value = ui.copy(busy = true, issue = null)
        work = viewModelScope.launch {
            val result = safe {
                if (ui.profile == null) repository.create(normalized)
                else repository.update(normalized, version!!, command)
            }
            if (generation != epoch) return@launch
            when (result) {
                is ProfileResult.Found -> {
                    clearDraft()
                    mutable.value = ProfileUiState(page = ProfilePage.MY_PROFILE, profile = result.profile, draft = result.profile.draft(), ownerId = owner)
                }
                is ProfileResult.Failure -> {
                    // Conflict requires an explicit reload; never silently overwrite newer remote data.
                    mutable.value = ui.copy(busy = false, issue = result.issue)
                }
                ProfileResult.Missing -> mutable.value = ui.copy(busy = false, issue = ProfileIssue.UNKNOWN)
            }
        }
    }
    private suspend fun safe(block: suspend () -> ProfileResult): ProfileResult = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { ProfileResult.Failure(ProfileIssue.NETWORK) }
    private fun change(ui: ProfileUiState) {
        if (ui.draft != mutable.value.draft) saved.remove<String>("profile.command")
        if (ui.profile != null && saved.get<Long>("profile.expected") == null)
            saved["profile.expected"] = ui.profile.version
        saved["profile.owner"] = owner
        saved["profile.username"] = ui.draft.username
        saved["profile.name"] = ui.draft.displayName
        saved["profile.avatar"] = ui.draft.avatarId
        mutable.value = ui
    }
    private fun restoreDraft(): ProfileDraft? {
        if (saved.get<String>("profile.owner") != owner) return null
        return ProfileDraft(saved["profile.username"] ?: "", saved["profile.name"] ?: "",
            BuiltInAvatars.safe(saved["profile.avatar"] ?: BuiltInAvatars.defaultId))
    }
    private fun clearDraft() {
        listOf("owner", "username", "name", "avatar", "command", "expected").forEach { saved.remove<Any>("profile.$it") }
    }
    private fun PublicProfile.draft() = ProfileDraft(username, displayName, avatarId)
    private companion object { val editable = setOf(ProfilePage.SETUP, ProfilePage.EDIT) }
}
