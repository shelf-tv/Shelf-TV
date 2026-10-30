import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Official releases are signed with a key that never leaves the maintainer's machine.
// Point SHELF_SIGNING at its properties file (or keep it at the default path); without
// it, release builds fall back to the debug key so anyone can build from source.
val signingProps = file(System.getenv("SHELF_SIGNING") ?: "${System.getProperty("user.home")}/.android/shelf-release.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "com.hayatayubi.shelf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hayatayubi.shelf"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Compose is far smoother minified; the Chromecast's chip needs all the help it can get.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Installs the bundled Compose baseline profiles on sideloaded builds.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
