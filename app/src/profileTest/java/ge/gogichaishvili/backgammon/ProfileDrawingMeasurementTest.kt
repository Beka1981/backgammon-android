package ge.gogichaishvili.backgammon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProfileDrawingMeasurementTest {
    @Test fun pairedDrawingAllocationMeasurement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".profile"))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val probe = context.classLoader.loadClass("ge.gogichaishvili.backgammon.DrawingProbe")
        val json = probe.getMethod("measure", Context::class.java).invoke(null, context) as String
        val output = JSONObject(json)
        assertFalse(output.getBoolean("debuggable"))
        assertEquals(6, output.getJSONArray("samples").length())
        val parent = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it, "task06-drawing") } ?: File(context.getExternalFilesDir(null), "task06-drawing")
        check(parent.isDirectory || parent.mkdirs())
        java.io.FileOutputStream(File(parent, "drawing.json")).use {
            it.write(json.toByteArray(Charsets.UTF_8))
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("task06_drawing", json) })
    }
}
