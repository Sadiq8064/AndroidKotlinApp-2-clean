buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath(libs.google.services.gradle)
        classpath(libs.firebase.crashlytics.gradle)
        classpath(libs.androidx.benchmark.baseline.profile.gradle.plugin)
    }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose.compiler) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.ksp) apply false
}