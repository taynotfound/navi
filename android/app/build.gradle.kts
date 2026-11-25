import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val naviApi = providers.gradleProperty("naviApi").get()
// The version lives in android/version.properties; pushing a change there makes CI publish a release.
val versionProps = Properties().also { p -> rootProject.file("version.properties").inputStream().use { p.load(it) } }
val ciVersionCode = versionProps.getProperty("versionCode").toInt()
val ciVersionName = versionProps.getProperty("versionName")
// release signing only when the keystore is provided (CI secrets or local env)
val keystore = System.getenv("NAVI_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
// share links open in the app: the website's trip page and old https://<api>/j/… links
val shareHost = providers.gradleProperty("shareHost").getOrElse("taynotfound.github.io")
val apiHost = URI(naviApi).host

android {
    namespace = "app.navi"
    compileSdk { version = release(37) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "app.navi"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "API_URL", "\"$naviApi\"")
        manifestPlaceholders["shareHost"] = shareHost
        manifestPlaceholders["apiHost"] = apiHost
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = keystore
            storePassword = System.getenv("NAVI_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("NAVI_KEY_ALIAS")
            keyPassword = System.getenv("NAVI_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["cleartext"] = "true" // dev server over plain http
        }
        release {
            manifestPlaceholders["cleartext"] = "false"
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // website screenshots (SiteScreenshots) only render with -PsiteShots
        unitTests.all { it.systemProperty("siteShots", providers.gradleProperty("siteShots").isPresent.toString()) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // F-Droid: no Google dependency metadata blob in the APK
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1") // per-app language on Android < 13
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
