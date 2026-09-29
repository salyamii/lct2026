pluginManagement {
    resolutionStrategy {
        eachPlugin {
            // Both Android plugins ship in AGP; reuse it without a separate library marker.
            if (requested.id.id == "com.android.library") {
                requested.version?.let { useModule("com.android.tools.build:gradle:$it") }
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
        mavenCentral()
        maven("https://nexus-external.rustore.ru/repository/maven-rustore-exposed") {
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "LCT App"
include(":app")
include(":core:game")
include(":feature:onboarding")
include(":feature:debug")
include(":feature:parents")
