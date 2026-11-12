package ge.gogichaishvili.backgammon.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.designsystem.Space
import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.domain.settings.AppLanguage
import ge.gogichaishvili.backgammon.domain.theme.ThemeRegistry
import ge.gogichaishvili.backgammon.presentation.LocalizedContent
import ge.gogichaishvili.backgammon.presentation.tr
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme

@Composable
fun AuthScreen(ui: AuthUiState, dispatch: (AuthAction) -> Unit, onLocalPlay: () -> Unit) {
    var email by remember(ui.page, ui.formVersion) { mutableStateOf("") }
    var password by remember(ui.page, ui.formVersion) { mutableStateOf("") }
    var confirmation by remember(ui.page, ui.formVersion) { mutableStateOf("") }
    var visible by remember(ui.page, ui.formVersion) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { password = ""; confirmation = ""; visible = false }
    val ready = (ui.session == AuthState.SignedOut || ui.session is AuthState.SignedIn) && !ui.busy
    fun submit() {
        keyboard?.hide()
        focus.clearFocus()
        when (ui.page) {
            AuthPage.LOGIN -> dispatch(AuthAction.Login(email, password))
            AuthPage.REGISTER -> dispatch(AuthAction.Register(email, password, confirmation))
            AuthPage.FORGOT -> dispatch(AuthAction.RequestReset(email))
            AuthPage.RESET_PASSWORD -> dispatch(AuthAction.NewPassword(password, confirmation))
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.large).testTag("auth_screen"),
            verticalArrangement = Arrangement.spacedBy(Space.medium)) {
            Text(tr(R.string.auth_welcome_label), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(tr(when (ui.page) {
                AuthPage.WELCOME -> R.string.auth_welcome
                AuthPage.LOGIN -> R.string.auth_login
                AuthPage.REGISTER -> R.string.auth_register
                AuthPage.FORGOT -> R.string.auth_forgot_title
                AuthPage.RESET_PASSWORD -> R.string.auth_new_password
                AuthPage.CONFIRM, AuthPage.RESET_SENT -> R.string.auth_confirm_title
                AuthPage.ACCOUNT -> R.string.auth_signed_in
            }), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            if (ui.session == AuthState.Loading) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(tr(R.string.auth_loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else if (ui.session == AuthState.Unconfigured) {
                Text(tr(R.string.auth_unconfigured))
            } else {
                if (ui.session is AuthState.RefreshFailed) {
                    Text(tr(R.string.auth_refresh_failed))
                    OutlinedButton(onClick = { dispatch(AuthAction.Refresh) }, enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch)) { Text(tr(R.string.auth_refresh)) }
                    OutlinedButton(onClick = { dispatch(AuthAction.Logout) }, enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch)) { Text(tr(R.string.auth_logout)) }
                }
                when (ui.page) {
                    AuthPage.WELCOME -> {
                        Text(tr(R.string.auth_intro), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { dispatch(AuthAction.Page(AuthPage.LOGIN)) }, enabled = ready,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("auth_open_login")) {
                            Text(tr(R.string.auth_login))
                        }
                        OutlinedButton(onClick = { dispatch(AuthAction.Page(AuthPage.REGISTER)) }, enabled = ready,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("auth_open_register")) {
                            Text(tr(R.string.auth_register))
                        }
                    }
                    AuthPage.ACCOUNT -> {
                        val user = (ui.session as? AuthState.SignedIn)?.user
                        if (user != null) {
                            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                                Column(Modifier.fillMaxWidth().padding(Space.large),
                                    verticalArrangement = Arrangement.spacedBy(Space.small)) {
                                    Text(user.email ?: "", style = MaterialTheme.typography.titleMedium)
                                    Text(tr(R.string.auth_user_id), style = MaterialTheme.typography.labelSmall)
                                    Text(user.id, style = MaterialTheme.typography.bodySmall)
                                    Text(tr(R.string.auth_future), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(onClick = { dispatch(AuthAction.Logout) }, enabled = ready,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("auth_logout")) {
                                Text(tr(R.string.auth_logout))
                            }
                        }
                    }
                    AuthPage.CONFIRM, AuthPage.RESET_SENT -> {
                        Text(tr(if (ui.page == AuthPage.CONFIRM) R.string.auth_confirm_sent else R.string.auth_reset_sent))
                        if (ui.page == AuthPage.CONFIRM)
                            OutlinedButton(onClick = { dispatch(AuthAction.Refresh) }, enabled = ready,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch)) { Text(tr(R.string.auth_refresh)) }
                        TextButton(onClick = { dispatch(AuthAction.Page(AuthPage.LOGIN)) }, enabled = ready,
                            modifier = Modifier.heightIn(min = Space.minTouch).testTag("auth_back_login")) {
                            Text(tr(R.string.auth_back_login))
                        }
                    }
                    else -> {
                        val reset = ui.page == AuthPage.RESET_PASSWORD
                        val confirm = reset || ui.page == AuthPage.REGISTER
                        if (reset) Text(tr(R.string.auth_recovery_intro))
                        if (!reset) OutlinedTextField(value = email, onValueChange = { email = it },
                            label = { Text(tr(R.string.auth_email)) }, enabled = ready, singleLine = true,
                            isError = ui.issue == AuthIssue.INVALID_EMAIL,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,
                                autoCorrectEnabled = false, imeAction = if (ui.page == AuthPage.FORGOT) ImeAction.Done else ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().testTag("auth_email"))
                        if (ui.page != AuthPage.FORGOT) {
                            OutlinedTextField(value = password, onValueChange = { password = it },
                                label = { Text(tr(R.string.auth_password)) }, enabled = ready, singleLine = true,
                                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val description = tr(if (visible) R.string.auth_hide_password else R.string.auth_show_password)
                                    TextButton(onClick = { visible = !visible }, enabled = ready,
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("auth_password_visibility").semantics { contentDescription = description }) {
                                        Text(tr(if (visible) R.string.auth_hide else R.string.auth_show))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false, imeAction = if (confirm) ImeAction.Next else ImeAction.Done),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth().testTag("auth_password"))
                            if (confirm) {
                                Text(tr(R.string.auth_password_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it },
                                    label = { Text(tr(R.string.auth_confirm_password)) }, enabled = ready, singleLine = true,
                                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false,
                                        imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
                                    modifier = Modifier.fillMaxWidth().testTag("auth_confirmation"))
                            }
                        }
                        Button(onClick = { submit() }, enabled = ready,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("auth_submit")) {
                            if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(tr(if (ui.busy) R.string.auth_working else when (ui.page) {
                                AuthPage.LOGIN -> R.string.auth_login
                                AuthPage.REGISTER -> R.string.auth_register
                                AuthPage.FORGOT -> R.string.auth_reset_request
                                else -> R.string.auth_update_password
                            }))
                        }
                        when (ui.page) {
                            AuthPage.LOGIN -> {
                                TextButton(onClick = { dispatch(AuthAction.Page(AuthPage.FORGOT)) }, enabled = ready,
                                    modifier = Modifier.heightIn(min = Space.minTouch).testTag("auth_forgot")) { Text(tr(R.string.auth_forgot)) }
                                TextButton(onClick = { dispatch(AuthAction.Page(AuthPage.REGISTER)) }, enabled = ready,
                                    modifier = Modifier.heightIn(min = Space.minTouch).testTag("auth_to_register")) { Text(tr(R.string.auth_no_account)) }
                            }
                            AuthPage.REGISTER, AuthPage.FORGOT ->
                                TextButton(onClick = { dispatch(AuthAction.Page(AuthPage.LOGIN)) }, enabled = ready,
                                    modifier = Modifier.heightIn(min = Space.minTouch).testTag("auth_back_login")) { Text(tr(R.string.auth_have_account)) }
                            AuthPage.RESET_PASSWORD ->
                                OutlinedButton(onClick = { dispatch(AuthAction.Logout) }, enabled = ready,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch)) { Text(tr(R.string.auth_logout)) }
                            else -> Unit
                        }
                    }
                }
            }
            ui.issue?.let {
                Text(tr(issueResource(it)), Modifier.testTag("auth_error").semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            ui.notice?.let { notice ->
                // Confirmation pages already contain their complete generic message.
                if (notice !in setOf(AuthNotice.CONFIRM_SENT, AuthNotice.RESET_SENT))
                    Text(tr(when (notice) {
                        AuthNotice.PASSWORD_CHANGED -> R.string.auth_changed
                        AuthNotice.LOGIN_AFTER_CONFIRM -> R.string.auth_login_after_confirm
                        AuthNotice.LOCAL_LOGOUT -> R.string.auth_logout_local
                        else -> R.string.auth_confirm_sent
                    }), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            HorizontalDivider()
            OutlinedButton(onClick = { keyboard?.hide(); focus.clearFocus(); onLocalPlay() },
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.minTouch).testTag("auth_local_play")) {
                Text(tr(R.string.auth_local))
            }
        }
    }
}
private fun issueResource(issue: AuthIssue) = when (issue) {
    AuthIssue.INVALID_EMAIL -> R.string.auth_invalid_email
    AuthIssue.WEAK_PASSWORD -> R.string.auth_weak_password
    AuthIssue.PASSWORD_MISMATCH -> R.string.auth_mismatch
    AuthIssue.INVALID_CREDENTIALS -> R.string.auth_invalid_credentials
    AuthIssue.EMAIL_UNCONFIRMED -> R.string.auth_email_unconfirmed
    AuthIssue.NETWORK -> R.string.auth_network
    AuthIssue.RATE_LIMITED -> R.string.auth_rate
    AuthIssue.INVALID_LINK -> R.string.auth_invalid_link
    AuthIssue.STORAGE -> R.string.auth_storage
    AuthIssue.UNCONFIGURED -> R.string.auth_unconfigured
    AuthIssue.UNKNOWN -> R.string.auth_unknown
}

@Preview(name = "Login • portrait", widthDp = 393, heightDp = 852)
@Preview(name = "Login • landscape", widthDp = 800, heightDp = 360)
@Composable private fun LoginPreview() {
    LocalizedContent(AppLanguage.ENGLISH) { BackgammonTheme(ThemeRegistry.classic) {
        AuthScreen(AuthUiState(AuthState.SignedOut, AuthPage.LOGIN), {}, {})
    } }
}
@Preview(name = "Registration • Georgian tablet", widthDp = 800, heightDp = 1100, fontScale = 2f)
@Composable private fun RegistrationPreview() {
    LocalizedContent(AppLanguage.GEORGIAN) { BackgammonTheme(ThemeRegistry.ocean) {
        AuthScreen(AuthUiState(AuthState.SignedOut, AuthPage.REGISTER), {}, {})
    } }
}
