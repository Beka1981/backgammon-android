import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Independent SDK packaging verification, with exact output retained under build/reports. */
gradle.projectsEvaluated {
    rootProject.project(":app").tasks.register("verifyApkPackaging") {
        dependsOn(":app:assembleDebug", ":app:assembleRelease")
        doLast {
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val output = rootProject.file("app/build/reports/task05/packaging").apply { mkdirs() }
            val tool = File(sdk, "build-tools/36.0.0/zipalign").absolutePath
            listOf("debug/app-debug.apk", "release/app-release-unsigned.apk").forEach { relative ->
                val apk = rootProject.file("app/build/outputs/apk/" + relative)
                val log = File(output, apk.name + ".log")
                val process = ProcessBuilder(tool, "-c", "-P", "16", "-v", "4", apk.absolutePath)
                    .redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("SDK zipalign timed out: " + apk)
                }
                val result = log.readText()
                check(process.exitValue() == 0 && result.contains("Verification successful")) { result }
                logger.lifecycle(apk.name + ": Verification successful (exit 0)")
            }
            File(output, "summary.txt").writeText(
                "Debug and unsigned release APK: SDK zipalign -c -P 16 -v 4 passed, exit 0.\n")
        }
    }
}
