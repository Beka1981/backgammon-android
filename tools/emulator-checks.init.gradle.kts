import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Extended SDK installer timeout for the isolated, non-debuggable measurement package.
 * Run interactive Compose tests with the standard connectedDebugAndroidTest driver.
 */
gradle.projectsEvaluated {
    rootProject.project(":app").tasks.register("installProfileMeasurement") {
        dependsOn(":app:assembleProfileAndroidTest")
        doLast {
            require(rootProject.findProperty("profileTests") == "true")
            val sdk = Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            val serial = rootProject.findProperty("verificationSerial")?.toString() ?: "emulator-5554"
            require(serial.startsWith("emulator-")) { "Deterministic measurement fixtures require an emulator" }
            val output = rootProject.file("app/build/reports/task05/profile-install").apply { mkdirs() }
            listOf("profile/app-profile.apk", "androidTest/profile/app-profile-androidTest.apk").forEachIndexed { n, path ->
                val log = File(output, "install-" + n + ".log")
                val process = ProcessBuilder(File(sdk, "platform-tools/adb").absolutePath, "-s", serial,
                    "install", "-r", "-t", rootProject.file("app/build/outputs/apk/" + path).absolutePath)
                    .redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(240, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("Measurement APK install timed out: " + path)
                }
                check(process.exitValue() == 0 && log.readText().contains("Success")) { log.readText() }
                logger.lifecycle(path + ": installed")
            }
        }
    }
}
