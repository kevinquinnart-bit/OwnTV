plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version comes from the build (VERSION_NAME / VERSION_CODE); a build without them is 0.0.0.
val appVersionName = System.getenv("VERSION_NAME") ?: "0.0.0"
val appVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "art.kquinn.kuesounds"
    compileSdk = 37

    defaultConfig {
        applicationId = "art.kquinn.kuesounds"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // One fixed key, kept with the project, so every build installs over the one before it.
        // Google's sign-in knows the app by this key, so it must never change.
        create("fixed") {
            storeFile = file("signing/kuesounds.jks")
            storePassword = "kuetv-fixed"
            keyAlias = "kuetv"
            keyPassword = "kuetv-fixed"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // Google's own sign-in on the phone, which can read the whole music Drive.
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    // The music player, and the part that keeps playing with the screen off and shows on the lock screen.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.0")
    implementation("androidx.core:core:1.19.0")
    testImplementation("junit:junit:4.13.2")
}

// A failed check prints its own message in the build log, saying what was wrong.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
