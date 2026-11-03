plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.gymassistant.m0"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.gymassistant.m0.debug"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.2-m1-drive-probe"
        val probeAccount = providers.gradleProperty("syntheticDriveAccount").orElse("").get()
        require(probeAccount.isEmpty() || probeAccount.matches(Regex("[A-Za-z0-9._+@-]+")))
        buildConfigField("String", "SYNTHETIC_DRIVE_ACCOUNT", "\"$probeAccount\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets.getByName("main").apply {
        kotlin.srcDir("../../packages/engine/src/commonMain/kotlin")
        assets.srcDirs("../../examples/templates", "../../examples/exercises")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("examples")).withPropertyName("historyFixtures")
    systemProperty("gym.root", rootProject.projectDir.absolutePath)
    systemProperty("gym.history.outputs", layout.buildDirectory.dir("outputs/history-projection").get().asFile.absolutePath)
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    testImplementation(kotlin("test-junit"))
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.charleskorn.kaml:kaml:0.73.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
