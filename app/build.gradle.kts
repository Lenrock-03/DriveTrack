plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.kornelriedl.drivetrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.kornelriedl.drivetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose / Material 3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room (lokale Speicherung der Fahrten)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // osmdroid für die Kartenansicht
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Standort-Tracking + automatische Fahrterkennung
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Foreground Service Support
    implementation("androidx.core:core-ktx:1.13.1")

    //Erweiterte Icon-Bibliothek
    implementation("androidx.compose.material:material-icons-extended")

    // Android Auto Integration
    implementation("androidx.car.app:app:1.4.0")
}
