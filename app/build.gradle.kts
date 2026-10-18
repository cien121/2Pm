plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.chan.ciancore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chan.ciancore"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.6.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Only arm64-v8a is shipped (matches the device fleet)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug builds are signed with the debug keystore by default.
            // CI uses the same debug keystore for all builds so the APK
            // can be installed directly without re-signing.
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
    // (ndk abiFilters live inside defaultConfig)
}

dependencies {
    // Xray-core AAR (official build from 2dust/AndroidLibXrayLite).
    // CI downloads it into app/libs/ before building; see .github/workflows/build.yml
    implementation(files("libs/libv2ray.aar"))
    // sing-box AAR intentionally NOT packaged in v0.2.0: libbox.aar and
    // libv2ray.aar both ship jni/arm64-v8a/libgojni.so under the same
    // name (mergeDebugNativeLibs fails), and two Go runtimes in one
    // process need a dedicated design pass. See SingBoxCore.kt.
    // Re-enable with the sing-box phase:
    // implementation(files("libs/libbox.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.7.0")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Room (node storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
