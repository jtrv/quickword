plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "io.github.jtrv.quickword"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jtrv.quickword"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "db" // DictionaryRepository uses openFd on the bundled DB
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric needs assets/resources
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-currency is a renovate-bot concern; as gate errors these
        // redden the build whenever upstream releases, checking nothing of ours.
        disable += listOf("AndroidGradlePluginVersion", "NewerVersionAvailable", "GradleDependency")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
    jvmToolchain(21)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// The Roborazzi shot rig lives in *Shots test classes: excluded from the
// normal gate (kotlin-verify-loop: shots never redden `check`), enabled by
// `-Pshots` which mise's `shots` task passes.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.graphicsMode", "NATIVE")
    if (providers.gradleProperty("shots").isPresent) {
        systemProperty("roborazzi.test.record", "true")
    } else {
        filter.excludeTestsMatching("*Shots")
        filter.isFailOnNoMatchingTests = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
