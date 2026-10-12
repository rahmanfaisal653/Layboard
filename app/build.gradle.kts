plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from the environment so the keystore never lives in the repo.
// Without it (local builds, pull requests) the release build falls back to the debug key.
val releaseKeystore: String? = System.getenv("LAYBOARD_KEYSTORE_FILE")

android {
    namespace = "io.github.rahmanfaisal653.layboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.rahmanfaisal653.layboard"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LAYBOARD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LAYBOARD_KEY_ALIAS")
                keyPassword = System.getenv("LAYBOARD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(
                if (releaseKeystore != null) "release" else "debug",
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

    lint {
        checkReleaseBuilds = false
    }

    // Keep the APK free of build metadata nobody needs.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/*.version",
            "/kotlin/**",
            "/kotlin-tooling-metadata.json",
            "DebugProbesKt.bin",
        )
    }
}

dependencies {
    // Deliberately no runtime dependencies: only the Android framework and the Kotlin stdlib.
    testImplementation("junit:junit:4.13.2")
}
