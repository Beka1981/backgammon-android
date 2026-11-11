package ge.gogichaishvili.backgammon.designsystem

import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Low-overhead platform markers. Inert unless app tracing is enabled by profiling tools. */
@Composable
@NonRestartableComposable
internal fun CompositionTrace(name: String) {
    SideEffect { Trace.beginSection(name); Trace.endSection() }
}

internal inline fun DrawScope.renderTrace(name: String, block: DrawScope.() -> Unit) {
    Trace.beginSection(name)
    try { block() } finally { Trace.endSection() }
}
