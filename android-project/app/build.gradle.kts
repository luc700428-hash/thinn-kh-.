plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.atmos.weather"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.atmos.weather"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.gradleProperty("THINN_VERSION_CODE").orNull?.toIntOrNull() ?: 25000000
        versionName = providers.gradleProperty("THINN_VERSION_NAME").orNull ?: "25.0.0"
        // Git commit identifier injected by the CI, so installed builds can be audited.
        val sourceCommit = providers.gradleProperty("THINN_BUILD_SHA").orNull ?: "local"
        require(Regex("(?:[a-fA-F0-9]{40}|local)").matches(sourceCommit)) {
            "THINN_BUILD_SHA must be a 40-character commit id or 'local'"
        }
        buildConfigField("String", "THINN_BUILD_SHA", "\"$sourceCommit\"")
        vectorDrawables.useSupportLibrary = true

        // BuildConfig values belong to defaultConfig (ApplicationDefaultConfig),
        // not to the top-level android extension. These are PUBLIC project IDs,
        // never Firebase service-account credentials or private signing keys.
        listOf(
            "THINN_FIREBASE_APP_ID", "THINN_FIREBASE_API_KEY",
            "THINN_FIREBASE_SENDER_ID", "THINN_FIREBASE_PROJECT_ID",
            "THINN_PUSH_FUNCTIONS_REGION"
        ).forEach { key ->
            val value = providers.gradleProperty(key).orNull.orEmpty()
            val escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            buildConfigField("String", key, "\"$escaped\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("com.google.android.filament:filament-utils-android:1.75.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
