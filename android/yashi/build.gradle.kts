import com.android.build.api.variant.impl.VariantOutputImpl
import java.time.LocalDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Yashi is the companion browser/client for a Yakushi server. It has its own
// version line; CI can override it with -PyashiVersionName/-PyashiVersionCode.
val yashiVersionName: String = providers.gradleProperty("yashiVersionName").orElse("0.1.3").get()
val yashiVersionCode: Int = providers.gradleProperty("yashiVersionCode").orElse("103").get().toInt()

// Sideload APK file name, e.g. yashi-client-release-2026-11-27.apk.
val apkDate: String = providers.gradleProperty("apkDate")
    .orElse(LocalDate.now().toString())
    .get()

val signingKeystore = rootProject.file("keystore/tanoshi-signing.jks")
val useSharedKeystore = signingKeystore.exists()

android {
    namespace = "dev.spainclaro.yashi"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    signingConfigs {
        create("yashi") {
            if (useSharedKeystore) {
                storeFile = signingKeystore
                storePassword = "tanoshi-android"
                keyAlias = "tanoshi"
                keyPassword = "tanoshi-android"
            }
        }
    }

    defaultConfig {
        applicationId = "dev.spainclaro.yashi"
        minSdk = 26
        targetSdk = 35
        versionCode = yashiVersionCode
        versionName = yashiVersionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = if (useSharedKeystore) {
                signingConfigs.getByName("yashi")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (useSharedKeystore) {
                signingConfigs.getByName("yashi")
            } else {
                signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Name the APK after the build date instead of app-debug.apk. This is what CI
// uploads and publishes as the release asset.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as VariantOutputImpl).outputFileName.set(
                "yashi-client-release-$apkDate.apk",
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Camera QR scanning. ZXing embedded ships its own CaptureActivity and
    // handles camera preview/detection; we only launch its contract.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
