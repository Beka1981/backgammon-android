import java.util.Properties
import java.io.File
import java.util.concurrent.TimeUnit

gradle.projectsEvaluated {
    rootProject.project(":app").tasks.register("dumpReadinessThreads") {
        doLast {
            val sdk = Properties().apply { rootProject.file("local.properties").inputStream().use { load(it) } }.getProperty("sdk.dir")
            val adb = File(sdk, "platform-tools/adb").absolutePath
            fun run(vararg args: String): String {
                val p = ProcessBuilder(listOf(adb, "-s", "emulator-5554") + args).redirectErrorStream(true).start()
                check(p.waitFor(30, TimeUnit.SECONDS)) { "Diagnostic ADB timeout" }
                val output = p.inputStream.bufferedReader().readText()
                check(p.exitValue() == 0) { output }
                return output
            }
            val pid = run("shell", "pidof", "ge.gogichaishvili.backgammon").trim()
            require(pid.matches(Regex("[0-9]+")))
            logger.lifecycle("Diagnostic target PID=" + pid)
            // Debuggable-app UID only; does not root adbd or change device security settings.
            logger.lifecycle(run("shell", "run-as", "ge.gogichaishvili.backgammon", "kill", "-3", pid))
        }
    }
}
