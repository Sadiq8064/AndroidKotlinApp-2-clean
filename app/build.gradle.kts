import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  id("atomtasks.android.hilt")
  id("kotlin-parcelize")
}

android {
    namespace = "com.example.androidkotlinapp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.androidkotlinapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Read from keystore.properties so passwords are not written into the build file.
            // A missing file is not fatal: the debug build still works, only the release one
            // needs the key.
            val props = Properties()
            val file = rootProject.file("keystore.properties")
            if (file.exists()) {
                FileInputStream(file).use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Left off deliberately. Compose plus reflection-shy Kotlin here is not the problem
            // shrinking solves, and a stripped release that misbehaves is far harder to debug
            // than an APK a few megabytes larger.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Falls back to the debug key only if the keystore is absent, so a release built
            // without it still installs rather than failing outright.
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")

  // CameraX, for the live selfie the alarm's face check needs.
  implementation("androidx.camera:camera-core:1.3.4")
  implementation("androidx.camera:camera-camera2:1.3.4")
  implementation("androidx.camera:camera-lifecycle:1.3.4")
  implementation("androidx.camera:camera-view:1.3.4")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Minitask integrated modules & libraries
  implementation(project(":core:ui"))
  implementation(project(":core:designsystem"))
  implementation(project(":core:notifications"))
  implementation(project(":core:logging"))
  implementation(project(":data"))
  implementation(project(":feature:agenda"))
  implementation(project(":feature:settings"))
  implementation(project(":common:tasks"))
  implementation(project(":feature:postpone-task"))
  implementation(project(":feature:detail"))
  implementation(project(":feature:onboarding"))
  
  implementation(libs.compose.destinations.core)
  implementation(libs.compose.destinations.bottomsheet)
  ksp(libs.compose.destinations.ksp)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.work)
  implementation(libs.compose.adaptative.navigation)
  coreLibraryDesugaring(libs.android.desugarjdk)
}
