package ge.gogichaishvili.backgammon.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.profile.*
import ge.gogichaishvili.backgammon.domain.settings.AppLanguage
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme

@Composable
fun ProfileScreen(ui: ProfileUiState, dispatch: (ProfileAction) -> Unit,
    onLogout: () -> Unit, onLocalPlay: () -> Unit, logoutEnabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun save() { keyboard?.hide(); focus.clearFocus(); dispatch(ProfileAction.Save) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.large).testTag("profile_screen"),
            verticalArrangement = Arrangement.spacedBy(Space.medium)) {
            Text(tr(R.string.profile_label), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text(tr(when(ui.page) {
                ProfilePage.SETUP -> R.string.profile_setup
                ProfilePage.EDIT -> R.string.profile_edit
                ProfilePage.AVATARS -> R.string.profile_choose_avatar
                else -> R.string.profile_title
            }), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            if (ui.page == ProfilePage.LOADING) {
                CircularProgressIndicator()
                Text(tr(R.string.profile_loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else if (ui.page == ProfilePage.ERROR) {
                Button(onClick = { dispatch(ProfileAction.Retry) }, modifier = Modifier.testTag("profile_retry")
                    .heightIn(min = Space.minTouch)) { Text(tr(R.string.retry)) }
            } else if (ui.page == ProfilePage.AVATARS) {
                PlayerAvatar(ui.avatarPreview, tr(avatarName(ui.avatarPreview)),
                    Modifier.size(144.dp).align(Alignment.CenterHorizontally).testTag("avatar_preview"))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 440.dp) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
                        BuiltInAvatars.ids.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                                row.forEach { id ->
                                    val label = tr(avatarName(id))
                                    Surface(shape = MaterialTheme.shapes.large,
                                        color = if (id == ui.avatarPreview) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.weight(1f).selectable(id == ui.avatarPreview, role = Role.RadioButton,
                                            onClick = { dispatch(ProfileAction.PreviewAvatar(id)) })
                                            .testTag("avatar_$id").heightIn(min = Space.minTouch)) {
                                        Column(Modifier.padding(Space.small), horizontalAlignment = Alignment.CenterHorizontally) {
                                            PlayerAvatar(id, label, Modifier.size(68.dp).clearAndSetSemantics { })
                                            Text(label, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { dispatch(ProfileAction.SaveAvatar) }, modifier = Modifier.fillMaxWidth()
                    .heightIn(min = Space.minTouch).testTag("avatar_save")) { Text(tr(R.string.profile_use_avatar)) }
                OutlinedButton(onClick = { dispatch(ProfileAction.Cancel) }, modifier = Modifier.fillMaxWidth()
                    .heightIn(min = Space.minTouch).testTag("profile_cancel")) { Text(tr(R.string.profile_cancel)) }
            } else {
                val editing = ui.page == ProfilePage.SETUP || ui.page == ProfilePage.EDIT
                val avatar = if (editing) ui.draft.avatarId else ui.profile?.avatarId ?: BuiltInAvatars.defaultId
                PlayerAvatar(avatar, tr(avatarName(avatar)),
                    Modifier.size(144.dp).align(Alignment.CenterHorizontally).testTag("profile_avatar"))
                if (editing) {
                    if (ui.page == ProfilePage.SETUP) Text(tr(R.string.profile_setup_hint))
                    OutlinedButton(onClick = { dispatch(ProfileAction.OpenAvatars) }, enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("profile_avatars")) {
                        Text(tr(R.string.profile_choose_avatar))
                    }
                    Text(tr(R.string.profile_custom_disabled), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(ui.draft.username, { dispatch(ProfileAction.Username(it)) },
                        label = { Text(tr(R.string.profile_username)) }, singleLine = true, enabled = !ui.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
                        modifier = Modifier.fillMaxWidth().testTag("profile_username"))
                    Text(tr(R.string.profile_username_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(ui.draft.displayName, { dispatch(ProfileAction.DisplayName(it)) },
                        label = { Text(tr(R.string.profile_display_name)) }, singleLine = true, enabled = !ui.busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth().testTag("profile_display_name"))
                    Button(onClick = { save() }, enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("profile_save")) {
                        Text(tr(if(ui.busy) R.string.profile_saving else R.string.profile_save))
                    }
                    if (ui.page == ProfilePage.EDIT) OutlinedButton(onClick = { dispatch(ProfileAction.Cancel) },
                        enabled = !ui.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch)
                            .testTag("profile_cancel")) { Text(tr(R.string.profile_cancel)) }
                } else ui.profile?.let { profile ->
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("profile_name"))
                    Text("@"+profile.username, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(Space.large), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                            Text(tr(R.string.profile_statistics), style = MaterialTheme.typography.titleMedium)
                            Text(tr(R.string.profile_played, profile.gamesPlayed))
                            Text(tr(R.string.profile_won, profile.gamesWon))
                            Text(tr(R.string.profile_lost, profile.gamesLost))
                            Text(tr(R.string.profile_percentage, profile.winPercentage))
                            Text(tr(R.string.profile_rating_future))
                        }
                    }
                    Button(onClick = { dispatch(ProfileAction.Edit) }, modifier = Modifier.fillMaxWidth()
                        .heightIn(min = Space.minTouch).testTag("profile_edit")) { Text(tr(R.string.profile_edit)) }
                }
            }
            ui.issue?.let { issue ->
                Text(tr(profileIssue(issue)), Modifier.testTag("profile_error").semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error)
                if (issue == ProfileIssue.VERSION_CONFLICT) TextButton(onClick = { dispatch(ProfileAction.Retry) },
                    modifier = Modifier.heightIn(min = Space.minTouch)) { Text(tr(R.string.profile_reload)) }
            }
            HorizontalDivider()
            OutlinedButton(onClick = onLocalPlay, modifier = Modifier.fillMaxWidth()
                .heightIn(min = Space.minTouch).testTag("profile_local_play")) { Text(tr(R.string.auth_local)) }
            TextButton(onClick = onLogout, enabled = logoutEnabled, modifier = Modifier.fillMaxWidth()
                .heightIn(min = Space.minTouch).testTag("profile_logout")) { Text(tr(R.string.auth_logout)) }
        }
    }
}
private fun avatarName(id: String): Int = when (BuiltInAvatars.safe(id)) {
    "knight" -> R.string.avatar_knight; "sun" -> R.string.avatar_sun; "moon" -> R.string.avatar_moon
    "mountain" -> R.string.avatar_mountain; "lotus" -> R.string.avatar_lotus
    "shield" -> R.string.avatar_shield; "star" -> R.string.avatar_star
    else -> R.string.avatar_crown
}
private fun profileIssue(issue: ProfileIssue): Int = when(issue) {
    ProfileIssue.USERNAME_INVALID -> R.string.profile_username_hint
    ProfileIssue.USERNAME_RESERVED -> R.string.profile_reserved
    ProfileIssue.USERNAME_UNAVAILABLE -> R.string.profile_unavailable_name
    ProfileIssue.DISPLAY_NAME_INVALID -> R.string.profile_name_hint
    ProfileIssue.AVATAR_INVALID -> R.string.profile_avatar_invalid
    ProfileIssue.VERSION_CONFLICT -> R.string.profile_conflict
    ProfileIssue.AUTH_REQUIRED -> R.string.profile_auth_required
    ProfileIssue.NETWORK -> R.string.profile_network
    ProfileIssue.RATE_LIMITED -> R.string.profile_rate_limit
    ProfileIssue.UNAVAILABLE -> R.string.profile_service_unavailable
    ProfileIssue.UNKNOWN -> R.string.profile_unknown
}
@Preview(name = "Profile portrait")
@Preview(name = "Profile landscape", widthDp = 840, heightDp = 400)
@Preview(name = "Profile tablet", widthDp = 840, heightDp = 1100)
@Composable private fun ProfilePreview() {
    LocalizedContent(AppLanguage.ENGLISH) { BackgammonTheme(ThemeRegistry.classic) {
        Surface { ProfileScreen(ProfileUiState(ProfilePage.SETUP), {}, {}, {}) }
    } }
}
@Preview(name = "Royal avatars")
@Composable private fun RoyalAvatarPreview() {
    LocalizedContent(AppLanguage.ENGLISH) { BackgammonTheme(ThemeRegistry.royal) {
        Surface { ProfileScreen(ProfileUiState(ProfilePage.AVATARS), {}, {}, {}) }
    } }
}
@Preview(name = "Ocean Georgian profile", locale = "ka")
@Composable private fun OceanProfilePreview() {
    LocalizedContent(AppLanguage.GEORGIAN) { BackgammonTheme(ThemeRegistry.ocean) {
        Surface { ProfileScreen(ProfileUiState(ProfilePage.SETUP), {}, {}, {}) }
    } }
}
