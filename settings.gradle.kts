/*
 * Configures plugin and dependency repositories and includes the Android application module.
 */

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:${requested.version}")
            }
        }
    }
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
        google()
        // Resolve Robolectric artifacts through the same mirror as its runtime SDK downloads.
        exclusiveContent {
            forRepository {
                maven {
                    name = "RobolectricMavenCentralMirror"
                    url = uri("https://maven-central.storage-download.googleapis.com/maven2")
                }
            }
            filter { includeGroup("org.robolectric") }
        }
        mavenCentral()
    }
}

rootProject.name = "stderr-campsbridge"
include(":app")
