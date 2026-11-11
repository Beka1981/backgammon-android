import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

gradle.projectsEvaluated {
    rootProject.project(":app").tasks.register("verifyMatchProcessRestore") {
        doLast {
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val adb = File(sdk, "platform-tools/adb").absolutePath
            val output = rootProject.file("app/build/reports/task05/process-restore").apply { mkdirs() }
            val pkg = "ge.gogichaishvili.backgammon.profile"
            fun run(name: String, vararg args: String): String {
                val log = File(output, name + ".log")
                val process = ProcessBuilder(listOf(adb, "-s", "emulator-5554") + args)
                    .redirectErrorStream(true).redirectOutput(log).start()
                check(process.waitFor(180, TimeUnit.SECONDS)) { "Timed out: " + name }
                val text = log.readText()
                check(process.exitValue() == 0) { text }
                logger.lifecycle(text)
                return text
            }
            fun test(method: String) {
                val text = run(method, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                    "ge.gogichaishvili.backgammon.ProcessPersistenceTest#" + method,
                    pkg + ".test/androidx.test.runner.AndroidJUnitRunner")
                check(text.contains("OK (1 test)") && !text.contains("FAILURES!!!")) { text }
            }
            test("seedBeforeProcessDeath")
            run("force-stop", "shell", "am", "force-stop", pkg)
            test("restoreAfterProcessDeath")
        }
    }
}
