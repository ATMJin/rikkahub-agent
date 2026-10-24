pluginManagement {
    includeBuild("build-logic")

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
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// The product is the phone-only agent, not the upstream all-purpose chat/workspace app.
// Preserve upstream sources for attribution/history, but never configure or package them.
rootProject.name = "LittleYiMobileAgent"
include(":app", ":ai", ":local-llm", ":common")
project(":app").projectDir = file("mobile-app")
