plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.atmos.weather.next"
    compileSdk = 36
    defaultConfig {
        // Future official build MUST keep io.atmos.weather and the ORIGINAL release keystore.
        applicationId = "io.atmos.weather"
        minSdk = 31
        targetSdk = 36
        versionCode = 25010001
        versionName = "25.1.1-ios-pages-lab"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "NEXT_BUILD_KIND", "\"FOUNDATION_NOT_FOR_RELEASE\"")
    }
    buildTypes {
        getByName("debug") {
            // Never overwrite an installed thinn-khì app with an unsigned laboratory build.
            applicationIdSuffix = ".nextioslab"
            versionNameSuffix = "-lab"
            isDebuggable = true
        }
        getByName("release") {
            // Deliberately no signingConfig. Official OTA is blocked pending legacy signer,
            // package, version-code, migration, and real-device acceptance verification.
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
