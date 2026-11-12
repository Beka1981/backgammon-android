import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

gradle.projectsEvaluated {
    // Preserve a task-completion marker when the IDE RPC times out before a long device run.
    rootProject.project(":app").tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
        doFirst {
            val marker = rootProject.file("verification/task06/connected-task.txt")
            marker.parentFile.mkdirs()
            marker.writeText("RUNNING " + java.time.Instant.now() + "\n")
        }
        doLast {
            rootProject.file("verification/task06/connected-task.txt")
                .writeText("PASS connectedDebugAndroidTest " + java.time.Instant.now() + "\n")
        }
    }
    rootProject.project(":app").tasks.register("inspectReadinessDevices") {
        doLast {
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val adb = File(sdk, "platform-tools/adb").absolutePath
            val output = rootProject.file("verification/task06").apply { mkdirs() }
            fun run(vararg args: String): String {
                val process = ProcessBuilder(listOf(adb) + args).redirectErrorStream(true).start()
                check(process.waitFor(45, TimeUnit.SECONDS)) { "ADB inspection timed out" }
                val text = process.inputStream.bufferedReader().readText()
                check(process.exitValue() == 0) { text }
                return text
            }
            val devices = run("devices", "-l")
            val report = StringBuilder(devices)
            devices.lineSequence().map { it.trim().split(Regex("\\s+")) }
                .filter { it.getOrNull(1) == "device" }.forEach { columns ->
                val serial = columns[0]
                report.append("\nDevice: ").append(serial).append("\n")
                listOf("ro.product.cpu.abilist", "ro.kernel.qemu", "ro.product.model").forEach { key ->
                    report.append(key).append("=").append(run("-s", serial, "shell", "getprop", key))
                }
                report.append("pages=").append(run("-s", serial, "shell", "getconf", "PAGE_SIZE"))
                report.append("enabled accessibility services=").append(run("-s", serial, "shell",
                    "settings", "get", "secure", "enabled_accessibility_services"))
            }
            File(output, "devices.txt").writeText(report.toString())
            logger.lifecycle(report.toString())
        }
    }
    rootProject.project(":app").tasks.register("measureReadinessDrawing") {
        doLast {
            require(rootProject.findProperty("profileTests") == "true")
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val serial = rootProject.findProperty("readinessSerial")?.toString() ?: "emulator-5554"
            val adb = File(sdk, "platform-tools/adb").absolutePath
            val output = rootProject.file("app/build/reports/task06-drawing").apply { mkdirs() }
            val pkg = "ge.gogichaishvili.backgammon.profile"
            fun run(name: String, vararg args: String): String {
                val log = File(output, name + ".log")
                val process = ProcessBuilder(listOf(adb, "-s", serial) + args)
                    .redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(240, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("Drawing measurement timed out: " + name)
                }
                val text = log.readText()
                check(process.exitValue() == 0) { text }
                return text
            }
            run("stop", "shell", "am", "force-stop", pkg)
            run("compile", "shell", "cmd", "package", "compile", "-m", "speed", "-f", pkg)
            val result = run("instrumentation", "shell", "am", "instrument", "-w", "-r", "-e", "class",
                "ge.gogichaishvili.backgammon.ProfileDrawingMeasurementTest#pairedDrawingAllocationMeasurement",
                "-e", "additionalTestOutputDir", "/sdcard/Android/media/" + pkg + "/task06",
                pkg + ".test/androidx.test.runner.AndroidJUnitRunner")
            check(result.contains("OK (1 test)") && !result.contains("FAILURES!!!")) { result }
            val destination = rootProject.file("verification/task06/drawing-profile.json")
            destination.parentFile.mkdirs()
            run("pull", "pull", "/sdcard/Android/media/" + pkg + "/task06/task06-drawing/drawing.json",
                destination.absolutePath)
            logger.lifecycle(result)
            logger.lifecycle(destination.readText())
        }
    }
    rootProject.project(":app").tasks.register("summarizeReadinessTests") {
        doLast {
            val directory = rootProject.file("verification/task06").apply { mkdirs() }
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            fun summary(folder: String): String {
                val files = rootProject.file(folder).walkTopDown()
                    .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }.toList()
                var total = 0; var failures = 0; var errors = 0; var skipped = 0
                val suites = StringBuilder()
                files.forEach { file ->
                    val suite = factory.newDocumentBuilder().parse(file).documentElement
                    fun count(key: String) = suite.getAttribute(key).toIntOrNull() ?: 0
                    total += count("tests"); failures += count("failures")
                    errors += count("errors"); skipped += count("skipped")
                    suites.append(file.name).append(" tests=").append(count("tests"))
                        .append(" failures=").append(count("failures")).append(" errors=").append(count("errors"))
                        .append(" skipped=").append(count("skipped")).append("\n")
                }
                return "tests=" + total + " failures=" + failures + " errors=" + errors + " skipped=" + skipped + "\n" + suites
            }
            val jvm = summary("app/build/test-results/testDebugUnitTest")
            val device = summary("app/build/outputs/androidTest-results/connected/debug")
            File(directory, "jvm-results.txt").writeText(jvm)
            File(directory, "instrumentation-results.txt").writeText(device)
            logger.lifecycle(jvm)
            logger.lifecycle(device)
        }
    }
}
