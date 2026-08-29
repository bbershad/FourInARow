import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing, if a key has been set up. Deliberately NOT in git: the password
// would be permanent in history. Without this file the project still builds debug,
// which is all that is needed to sideload and play.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// versionCode is what Android compares to decide a build is an UPDATE. It has to go up
// every release or the new APK will not install over the old one. Kept in its own file
// so tools\rebuild.ps1 can bump it without editing this script.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) FileInputStream(versionPropsFile).use { load(it) }
}

android {
    namespace = "com.bershad.fourinarow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bershad.fourinarow"
        // 26 so it installs on anything still in service, and it is also the floor for
        // adaptive launcher icons, which is the only icon format shipped here.
        minSdk = 26
        targetSdk = 35
        versionCode = (versionProps.getProperty("versionCode") ?: "1").toInt()
        versionName = versionProps.getProperty("versionName") ?: "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // The whole game engine and AI are plain Kotlin with no Android types, so they are
    // fully covered by fast JVM unit tests - no emulator needed.
    testImplementation(libs.junit)
}
