plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Gleiche Berechnung wie in app/build.gradle.kts, rein zur Konsistenz (kein automatisches
// Mitinstallieren über Gradle — siehe Kommentar bei den Signing-Configs unten).
val gitCommitCount: Int = providers
    .exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
    .get()

android {
    namespace = "com.helga.android.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.helga.android.wear"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }

    // Derselbe eingecheckte Debug-Keystore wie in app/build.gradle.kts — kein Data-Layer-
    // Erfordernis, aber Voraussetzung für eine spätere gemeinsame Play-Store-Veröffentlichung
    // unter einem Eintrag (dort MUSS die Signatur übereinstimmen).
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material)

    // Data Layer (DataClient/MessageClient/NodeClient) — Brücke zur Handy-App, siehe
    // WearSyncPublisher/WearMessageListenerService im app-Modul für die Gegenseite.
    implementation(libs.google.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
