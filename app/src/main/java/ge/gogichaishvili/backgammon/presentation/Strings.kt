package ge.gogichaishvili.backgammon.presentation

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.domain.settings.AppLanguage
import java.util.Locale

private val LocalAppResources = staticCompositionLocalOf<Resources> { error("Missing app resources") }

/** App-local language; no Activity restarts or global configuration mutation. */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, language) {
        val localized = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        context.createConfigurationContext(localized).resources
    }
    CompositionLocalProvider(LocalAppResources provides resources, content = content)
}
@Composable
fun tr(@StringRes id: Int, vararg args: Any): String = LocalAppResources.current.getString(id, *args)

@Composable
fun trQuantity(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
    LocalAppResources.current.getQuantityString(id, quantity, *args)

@StringRes
fun themeName(id: String): Int = when (id) {
    "royal" -> R.string.royal_name
    "ocean" -> R.string.ocean_name
    else -> R.string.classic_name
}
@StringRes
fun themeDetail(id: String): Int = when (id) {
    "royal" -> R.string.royal_detail
    "ocean" -> R.string.ocean_detail
    else -> R.string.classic_detail
}
