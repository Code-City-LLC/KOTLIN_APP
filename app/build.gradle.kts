plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Enable once google-services.json for com.ga.airdrop.app is provided
    // (Firebase console → project settings → Android app). Until then FCM
    // token registration no-ops at runtime (guarded by FirebaseApp check).
    // alias(libs.plugins.google.services)
}

android {
    namespace = "com.ga.airdrop"
    compileSdk = 35

    defaultConfig {
        // Matches the existing Play Store / Firebase Android identity used by
        // the previous React Native app (AD-REACT_NATIVE_APP-OLD).
        applicationId = "com.ga.airdrop.app"
        minSdk = 26
        targetSdk = 35
        // Previous RN release shipped versionCode 7 / versionName 7.0.
        versionCode = 8
        versionName = "8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            buildConfigField("String", "API_BASE_URL", "\"https://pre-staging.airdropja.com/api/v1\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://pre-staging.airdropja.com\"")
            buildConfigField("String", "ENV_NAME", "\"Staging\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://app.airdropja.com/api/v1\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://airdropja.com\"")
            buildConfigField("String", "ENV_NAME", "\"Production\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign the release with the auto-managed debug keystore so
            // `assembleRelease` produces an INSTALLABLE apk for on-device
            // sideload testing (an unsigned apk is rejected by the package
            // installer). The debug keystore is a well-known, non-secret
            // shared key — nothing sensitive is committed.
            //
            // FOR PLAY STORE UPLOAD: replace this with a dedicated upload
            // keystore. Put its credentials in a gitignored keystore.properties
            // (storeFile/storePassword/keyAlias/keyPassword) and point a real
            // signingConfig at them — never commit the keystore or passwords.
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.zxing.core)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
