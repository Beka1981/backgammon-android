package ge.gogichaishvili.backgammon

import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Regression guard: no dialog dismissal, compatibility flags or persistent suppression. */
class PageSizeCompatibilityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            Log.i("BackgammonTest", "Kernel page size=" + Os.sysconf(OsConstants._SC_PAGESIZE))
            base.evaluate()
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            assertFalse("Native alignment warning must be fixed, never suppressed",
                root?.packageName?.toString() == "android" &&
                    root.findAccessibilityNodeInfosByText("Android App Compatibility").isNotEmpty())
        }
    }
}
