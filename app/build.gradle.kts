plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.munawar.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.munawar.ai"
        minSdk = 29
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"
    }

    // Optional release signing: only used when the KEYSTORE_* environment variables exist (GitHub secrets).
    // Without them the release APK is signed with the debug key so it is always installable.
    val hasKeystore = !System.getenv("KEYSTORE_FILE").isNullOrBlank()
    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(System.getenv("KEYSTORE_FILE"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
