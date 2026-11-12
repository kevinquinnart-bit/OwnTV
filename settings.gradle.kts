pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // A libmpv built on this PC (OwnTV_libmpv's buildscripts/wsl_build.sh), only while
        // the build command carries -Powntv.libmpvLocalRepo=<folder> (never a file) — core's :player-core then asks
        // for version "local". Core's settings carry the same lines.
        providers.gradleProperty("owntv.libmpvLocalRepo").orNull?.let { dir ->
            maven {
                name = "LocalLibmpv"
                url = uri(file(dir))
                content { includeVersion("tv.own.owntv", "libmpv", "local") }
            }
        }
        google()
        mavenCentral()
        // Kevin's copy (KueTV): the mpv engine is kept in this repo (kuetv-maven), so a build reaches
        // nothing of the original project's.
        maven {
            name = "KueTV"
            url = uri(file("kuetv-maven"))
            content { includeGroup("tv.own.owntv") }
        }
    }
}

// Kevin's copy (KueTV): the shared code lives in this repo (kuetv-core) and is always built from
// there; gradle.properties sets owntv.corePath. Gradle substitutes it for the tv.own.owntv artifacts.
providers.gradleProperty("owntv.corePath").orNull?.takeIf { it.isNotBlank() }?.let { includeBuild(it) }

rootProject.name = "OwnTV"
include(":app")
// Baseline-profile generator (audit ST1). Test-only module: it ships nothing to users, it records
// the cold-start journey on a device and writes the profile :app packages.
include(":baselineprofile")
 