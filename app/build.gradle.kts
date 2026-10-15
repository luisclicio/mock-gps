plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mapStyleUrl = providers.gradleProperty("mapStyleUrl")
    .orElse("https://tiles.openfreemap.org/styles/liberty")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orElse("1").get().toInt()
val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("1.0").get()
val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningConfigured) {
    "Release signing requires RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
}

android {
    namespace = "dev.local.mockgps"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.local.mockgps"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
