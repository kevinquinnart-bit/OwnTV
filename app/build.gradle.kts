plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version comes from the build (VERSION_NAME / VERSION_CODE); a build without them is 0.0.0.
val appVersionName = System.getenv("VERSION_NAME") ?: "0.0.0"
val appVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "art.kquinn.kuetv"
    compileSdk = 37

    defaultConfig {
        applicationId = "art.kquinn.kuetv"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // One fixed key, kept with the project, so every build installs over the one before it.
        create("fixed") {
            storeFile = file("signing/kuetv.jks")
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
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.0")
    testImplementation("junit:junit:4.13.2")
}
