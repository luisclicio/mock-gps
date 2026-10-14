plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mapStyleUrl = providers.gradleProperty("mapStyleUrl")
    .orElse("https://tiles.openfreemap.org/styles/liberty")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "dev.local.mockgps"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.local.mockgps"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    testImplementation("junit:junit:4.13.2")
}
