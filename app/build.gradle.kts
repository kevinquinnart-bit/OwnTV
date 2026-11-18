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
        // Kevin's own Google keys for the Drive sign-in, handed to the build; a build without them has no Drive.
        val driveId = (System.getenv("KUETV_DRIVE_CLIENT_ID") ?: "").filter { it.isLetterOrDigit() || it in "-_." }
        val driveSecret = (System.getenv("KUETV_DRIVE_CLIENT_SECRET") ?: "").filter { it.isLetterOrDigit() || it in "-_." }
        buildConfigField("String", "DRIVE_CLIENT_ID", "\"$driveId\"")
        buildConfigField("String", "DRIVE_CLIENT_SECRET", "\"$driveSecret\"")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        jniLibs {
            // The VPN engine runs OpenVPN as a program of its own, which the device can only start when unpacked.
            useLegacyPackaging = true
            // Parts of the engine KueTV doesn't use: the OpenVPN 3 engine and a speed test.
            excludes += setOf("**/libovpn3.so", "**/libosslspeedtest.so")
        }
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
    // The torrent engine, and its machinery for the two kinds of chip in Fire TVs, Android TVs and phones.
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    // The VPN engine: OpenVPN for Android (Arne Schwabe, GPL v2), built as a library by the "VPN engine" job
    // on the vpn-engine branch, from its 0.7.69 source. It needs these two at the versions it was built with.
    implementation(files("libs/openvpn-engine.aar"))
    implementation("androidx.core:core:1.19.0")
    implementation("androidx.annotation:annotation:1.10.0")
    testImplementation("junit:junit:4.13.2")
    // The checks that press remote keys on the real screens, on a pretend TV, without a device.
    testImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
