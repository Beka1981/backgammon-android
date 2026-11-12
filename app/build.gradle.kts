import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Client-public configuration only. Invalid/privileged keys are never embedded.
val localAuthProperties = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
fun authValue(name: String): String =
    providers.environmentVariable(name).orNull ?: localAuthProperties.getProperty(name, "")
val candidateUrl = authValue("SUPABASE_URL").trim()
val candidateKey = authValue("SUPABASE_PUBLISHABLE_KEY").trim()
val authConfigured = runCatching {
    val uri = URI(candidateUrl)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.port in listOf(-1, 443) &&
        uri.path in listOf("", "/") &&
        candidateKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]{12,490}"))
}.getOrDefault(false)
fun authLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "ge.gogichaishvili.backgammon"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ge.gogichaishvili.backgammon"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("boolean", "SUPABASE_CONFIGURED", authConfigured.toString())
        buildConfigField("String", "SUPABASE_URL", authLiteral(if (authConfigured) candidateUrl.trimEnd('/') else ""))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", authLiteral(if (authConfigured) candidateKey else ""))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Opt-in non-debuggable measurement target; production variants retain their configuration.
    testBuildType = if (providers.gradleProperty("profileTests").orNull == "true") "profile" else "debug"
    sourceSets.getByName("androidTest").kotlin.apply {
        if (providers.gradleProperty("profileTests").orNull == "true") directories.add("src/profileTest/java")
    }
    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildTypes.create("profile") {
        initWith(buildTypes.getByName("release"))
        isDebuggable = false
        applicationIdSuffix = ".profile"
        testProguardFiles("profile-test.pro")
        proguardFiles("profile-target.pro")
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Both supported languages must be available for the offline in-app switcher.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.graphics.path)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    add("profileImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}