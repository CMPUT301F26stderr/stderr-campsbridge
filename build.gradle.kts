// Keep AGP's built-in Kotlin compiler aligned with the Compose compiler plugin.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.google.oss.licenses) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
