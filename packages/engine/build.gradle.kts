plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// Fixture edits must invalidate local test results even when Kotlin is unchanged.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("examples")).withPropertyName("conformanceFixtures")
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("com.charleskorn.kaml:kaml:0.73.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting { dependencies { implementation("it.krzeminski:snakeyaml-engine-kmp:3.1.1") } }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
