plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release credentials are supplied only through the local environment.
// Never place keystore files or passwords in the repository.
val releaseStoreFile = providers.environmentVariable("JAPANESE_WRITING_TRAINER_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("JAPANESE_WRITING_TRAINER_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("JAPANESE_WRITING_TRAINER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("JAPANESE_WRITING_TRAINER_KEY_PASSWORD").orNull

val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigning) {
    error(
        "Release signing is partially configured. Set all four " +
            "JAPANESE_WRITING_TRAINER_* signing environment variables."
    )
}

android {
    namespace = "com.example.japanesewritingtrainer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.japanesewritingtrainer"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // Pin Material 3 to a stable release so NavigationBarItem is available consistently.
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}