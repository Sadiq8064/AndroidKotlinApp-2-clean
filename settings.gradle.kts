enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("minitask_temp/build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "AndroidKotlinApp"
include(":app")


// Minitask multi-module imports
val minitaskModules = listOf(
    ":core",
    ":core:designsystem",
    ":core:testing",
    ":core:ui",
    ":common",
    ":common:tasks",
    ":data",
    ":feature",
    ":feature:settings",
    ":feature:agenda",
    ":core:analytics",
    ":feature:postpone-task",
    ":core:notifications",
    ":core:logging",
    ":core:review",
    ":core:preferences",
    ":core:jobs",
    ":core:ui:tasks",
    ":feature:detail",
    ":feature:onboarding",
    ":core:locale"
)

minitaskModules.forEach { modulePath ->
    include(modulePath)
    val relPath = modulePath.replace(":", "/")
    project(modulePath).projectDir = file("minitask_temp/$relPath")
}
