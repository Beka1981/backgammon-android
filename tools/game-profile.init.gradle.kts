import java.io.File
import java.net.URI
import java.util.Properties
import java.util.concurrent.TimeUnit

gradle.projectsEvaluated {
    val app = rootProject.project(":app")
    app.tasks.register("captureGameProfile") {
        doLast {
            val label = rootProject.findProperty("profileLabel")?.toString() ?: "baseline"
            require(label.matches(Regex("[a-zA-Z0-9_-]+")))
            val output = rootProject.file("app/build/reports/task05/" + label).apply { mkdirs() }
            val tools = rootProject.file("app/build/task05-tools").apply { mkdirs() }
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val adb = File(sdk, "platform-tools/adb").absolutePath
            val serial = rootProject.findProperty("profileSerial")?.toString() ?: "emulator-5554"
            val pkg = "ge.gogichaishvili.backgammon.profile"
            fun run(name: String, seconds: Long = 120, vararg args: String): String {
                val log = File(output, name + ".log")
                val process = ProcessBuilder(args.toList()).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("Timed out: " + name)
                }
                val text = log.readText()
                check(process.exitValue() == 0) { name + ": " + text }
                return text
            }
            val configuration = rootProject.file(rootProject.findProperty("profileConfig")?.toString()
                ?: "verification/task05/gameplay.pftxt")
            val durationMillis = Regex("duration_ms:\\s*(\\d+)").find(configuration.readText())
                ?.groupValues?.get(1)?.toLongOrNull() ?: error("Trace config needs a finite duration_ms")
            require(durationMillis > 0)
            val helper = File(tools, "record_android_trace")
            if (!helper.isFile) URI("https://raw.githubusercontent.com/google/perfetto/main/tools/record_android_trace")
                .toURL().openStream().use { input -> helper.outputStream().use { input.copyTo(it) } }
            run("stop-previous", 30, adb, "-s", serial, "shell", "am", "force-stop", pkg)
            run("compile", 120, adb, "-s", serial, "shell", "cmd", "package", "compile", "-m", "speed", "-f", pkg)
            val recorderLog = File(output, "recording.log")
            val recorder = ProcessBuilder("python3", helper.absolutePath, "-n", "-s", serial,
                "-c", configuration.absolutePath,
                "-o", File(output, "gameplay.perfetto-trace").absolutePath)
                .redirectErrorStream(true).redirectOutput(recorderLog).apply {
                    environment()["PATH"] = File(sdk, "platform-tools").absolutePath + File.pathSeparator + System.getenv("PATH")
                }.start()
            try {
                Thread.sleep(3000)
                val result = run("instrumentation", 240, adb, "-s", serial, "shell", "am", "instrument", "-w", "-r",
                    "-e", "class", "ge.gogichaishvili.backgammon.GamePerformanceTest",
                    "-e", "additionalTestOutputDir", "/sdcard/Android/media/" + pkg + "/task05-" + label,
                    pkg + ".test/androidx.test.runner.AndroidJUnitRunner")
                check(!result.contains("FAILURES!!!") && !result.contains("INSTRUMENTATION_FAILED") &&
                    result.contains("OK (1 test)")) { result }
                check(recorder.waitFor(durationMillis / 1000 + 120, TimeUnit.SECONDS)) { "Trace recording timed out" }
                check(recorder.exitValue() == 0) { recorderLog.readText() }
                run("pull", 60, adb, "-s", serial, "pull",
                    "/sdcard/Android/media/" + pkg + "/task05-" + label + "/task05-performance/performance.json",
                    File(output, "performance.json").absolutePath)
                logger.lifecycle(File(output, "performance.json").readText())
                logger.lifecycle("Trace and measurements: " + output)
            } finally { if (recorder.isAlive) recorder.destroyForcibly() }
        }
    }
    app.tasks.register("perfettoQuery") {
        doLast {
            val label = rootProject.findProperty("profileLabel")?.toString() ?: "baseline"
            require(label.matches(Regex("[a-zA-Z0-9_-]+")))
            val output = rootProject.file("app/build/reports/task05/" + label).apply { mkdirs() }
            val wrapper = File("/Users/Admin/.android-studio/skills/android-profiler/references/perfetto/bin/trace_processor")
            require(wrapper.isFile) { "Bundled Android Profiler trace_processor wrapper missing: " + wrapper }
            val action = rootProject.findProperty("perfettoAction")?.toString() ?: "query"
            val arguments = when (action) {
                "start" -> listOf("server", "unix", "--name", "bg_" + label, "--daemonize",
                    File(output, "gameplay.perfetto-trace").absolutePath)
                "stop" -> listOf("server", "kill", "bg_" + label)
                else -> listOf("query", "--remote", "bg_" + label,
                    rootProject.findProperty("perfettoQueryEncoded")?.toString()?.let {
                        java.net.URLDecoder.decode(it, "UTF-8")
                    } ?: requireNotNull(rootProject.findProperty("perfettoQuery")?.toString()))
            }
            val log = File(output, "query-" + (rootProject.findProperty("queryLabel")?.toString() ?: action) + ".txt")
            val errorLog = File(output, log.nameWithoutExtension + "-stderr.txt")
            val process = ProcessBuilder(listOf("python3", wrapper.absolutePath) + arguments)
                .redirectOutput(log).redirectError(errorLog).start()
            check(process.waitFor(180, TimeUnit.SECONDS)) { "Trace processor timed out" }
            check(process.exitValue() == 0) { log.readText() + "\n" + errorLog.readText() }
            logger.lifecycle(log.readText())
        }
    }
    app.tasks.register("runGameProfile") {
        doLast {
            val sdk = Properties().apply { rootProject.file("local.properties").inputStream().use { load(it) } }.getProperty("sdk.dir")
            val adb = File(sdk, "platform-tools/adb").absolutePath
            val output = rootProject.file("app/build/reports/task05/baseline").apply { mkdirs() }
            val log = File(output, "instrumentation.log")
            val process = ProcessBuilder(adb, "-s", "emulator-5554", "shell", "am", "instrument", "-w", "-r",
                "-e", "class", "ge.gogichaishvili.backgammon.GamePerformanceTest",
                "-e", "additionalTestOutputDir", "/sdcard/Android/media/ge.gogichaishvili.backgammon.profile/task05-baseline",
                "ge.gogichaishvili.backgammon.profile.test/androidx.test.runner.AndroidJUnitRunner")
                .redirectErrorStream(true).redirectOutput(log).start()
            check(process.waitFor(240, TimeUnit.SECONDS)) { "Profile test timed out" }
            val text = log.readText()
            logger.lifecycle(text)
            check(text.contains("OK (1 test)") && !text.contains("FAILURES!!!")) { "Profile test failed: " + log }
        }
    }
    app.tasks.register("profileDeviceLog") {
        doLast {
            val sdk = Properties().apply { rootProject.file("local.properties").inputStream().use { load(it) } }.getProperty("sdk.dir")
            val output = rootProject.file("app/build/reports/task05/profile-crash.txt")
            output.parentFile.mkdirs()
            val process = ProcessBuilder(File(sdk, "platform-tools/adb").absolutePath, "-s", "emulator-5554",
                "logcat", "-b", "crash", "-d", "-v", "threadtime")
                .redirectErrorStream(true).redirectOutput(output).start()
            check(process.waitFor(60, TimeUnit.SECONDS))
            logger.lifecycle(output.readText())
        }
    }
}
