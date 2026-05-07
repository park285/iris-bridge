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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven { url = uri("https://api.xposed.info/") }
            }
            filter {
                includeGroup("de.robv.android.xposed")
            }
        }
    }
}

rootProject.name = "Iris"
include(":app")
include(":bridge")
include(":imagebridge-protocol")
