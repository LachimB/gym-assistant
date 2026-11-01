pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        ivy {
            name = "NodeDist"
            url = uri("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

rootProject.name = "gym-assistant"
include(":engine")
project(":engine").projectDir = file("packages/engine")
include(":android")
project(":android").projectDir = file("apps/android")
include(":cloud-adapters")
project(":cloud-adapters").projectDir = file("packages/cloud-adapters")
