package ge.gogichaishvili.backgammon.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    themes: List<BoardTheme>,
    onTheme: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("settings_grid"),
            contentPadding = PaddingValues(Space.medium),
            horizontalArrangement = Arrangement.spacedBy(Space.medium),
            verticalArrangement = Arrangement.spacedBy(Space.medium),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text(tr(R.string.choose_board), style = MaterialTheme.typography.headlineMedium)
                    Text(tr(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(themes, key = { it.id }) { theme ->
                ThemeCard(theme, theme.id == settings.themeId, settings) { onTheme(theme.id) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(tr(R.string.appearance), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Space.medium))
            }
            item {
                ChoiceSetting(R.string.checker_style, CheckerStyle.entries, settings.checkerStyle,
                    { if (it == CheckerStyle.SCULPTED) R.string.sculpted else R.string.flat }) { value ->
                    onUpdate { it.copy(checkerStyle = value) }
                }
            }
            item {
                ChoiceSetting(R.string.dice_style, DiceStyle.entries, settings.diceStyle,
                    { if (it == DiceStyle.IVORY) R.string.ivory else R.string.accent }) { value ->
                    onUpdate { it.copy(diceStyle = value) }
                }
            }
            item {
                ChoiceSetting(R.string.animation_speed, AnimationSpeed.entries, settings.animationSpeed,
                    { when (it) {
                        AnimationSpeed.RELAXED -> R.string.relaxed
                        AnimationSpeed.NORMAL -> R.string.normal
                        AnimationSpeed.QUICK -> R.string.quick
                    } }) { value -> onUpdate { it.copy(animationSpeed = value) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(tr(R.string.experience), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Space.medium))
            }
            item { ToggleSetting(R.string.reduced_motion, settings.reducedMotion) { value -> onUpdate { it.copy(reducedMotion = value) } } }
            item { ToggleSetting(R.string.sound, settings.soundEffects) { value -> onUpdate { it.copy(soundEffects = value) } } }
            item { ToggleSetting(R.string.music, settings.music) { value -> onUpdate { it.copy(music = value) } } }
            item { ToggleSetting(R.string.haptics, settings.haptics) { value -> onUpdate { it.copy(haptics = value) } } }
            item {
                ChoiceSetting(R.string.language, AppLanguage.entries, settings.language,
                    { if (it == AppLanguage.ENGLISH) R.string.english else R.string.georgian }) { value ->
                    onUpdate { it.copy(language = value) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(tr(R.string.future_audio), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.medium))
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: BoardTheme, selected: Boolean, settings: AppSettings, onSelect: () -> Unit) {
    val accessibleName = tr(themeName(theme.id))
    Surface(
        modifier = Modifier.testTag("theme_" + theme.id).selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = accessibleName },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            BoardCanvas(theme, Modifier.fillMaxWidth(), settings.checkerStyle, settings.diceStyle,
                description = tr(R.string.board_preview))
            Text(tr(themeName(theme.id)), style = MaterialTheme.typography.titleMedium)
            Text(tr(themeDetail(theme.id)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(Space.small))
                Text(if (selected) tr(R.string.theme_selected) else tr(R.string.preview_badge),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun <T> ChoiceSetting(
    @StringRes title: Int, values: List<T>, selected: T,
    label: (T) -> Int, onSelect: (T) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Space.medium).selectableGroup()) {
            Text(tr(title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Space.small))
            values.forEach { value ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Space.minTouch)
                        .selectable(value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(value == selected, onClick = null)
                    Spacer(Modifier.width(Space.small))
                    Text(tr(label(value)), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .padding(Space.medium).heightIn(min = Space.minTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.medium)) {
            Text(tr(title), Modifier.weight(1f))
            Switch(checked, onCheckedChange = null)
        }
    }
}
