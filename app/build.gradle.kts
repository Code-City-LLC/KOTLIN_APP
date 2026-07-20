import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // FCM enabled: google-services.json (project airdrop-app-b9423, the live
    // Firebase project every AIRDROP client uses) is committed at app/. Covers
    // com.ga.airdrop.app + the .staging flavor. Kotlin push is no longer inert.
    alias(libs.plugins.google.services)
}

val playUploadPropertiesFile = rootProject.file("keystore.properties")
val playUploadProperties = Properties().apply {
    if (playUploadPropertiesFile.isFile) {
        playUploadPropertiesFile.inputStream().use(::load)
    }
}
val playUploadPropertyNames = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
)
val playUploadStoreFile = playUploadProperties.getProperty("storeFile")
    ?.takeIf(String::isNotBlank)
    ?.let(rootProject::file)
val playUploadSigningConfigured =
    playUploadPropertiesFile.isFile &&
        playUploadPropertyNames.all { !playUploadProperties.getProperty(it).isNullOrBlank() } &&
        playUploadStoreFile?.isFile == true
val knownPlayProductionVersionCodeFloor = 21
val maximumPlayVersionCode = 2_100_000_000
val requestedPlayVersionCode = providers.gradleProperty("playVersionCode")
    .orElse(providers.environmentVariable("PLAY_VERSION_CODE"))
    .orNull
    ?.trim()
    ?.toIntOrNull()
val playReleaseConfigured =
    playUploadSigningConfigured &&
        requestedPlayVersionCode != null &&
        requestedPlayVersionCode > knownPlayProductionVersionCodeFloor &&
        requestedPlayVersionCode <= maximumPlayVersionCode

android {
    namespace = "com.ga.airdrop"
    compileSdk = 35

    defaultConfig {
        // Matches the existing Play Store / Firebase Android identity used by
        // the previous React Native app (AD-REACT_NATIVE_APP-OLD).
        applicationId = "com.ga.airdrop.app"
        minSdk = 26
        targetSdk = 35
        // Local builds keep the current repository code. prodRelease gets an
        // owner-verified Play code through androidComponents below.
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
            // Swift Staging.xcconfig LEGACY_BASE_URL — legacy PHP endpoints
            // (Documents server-generated form downloads).
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://pre-staging.airdropja.com/airdrop/inc\"")
            buildConfigField("String", "ENV_NAME", "\"Staging\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://app.airdropja.com/api/v1\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://airdropja.com\"")
            // Swift Production.xcconfig LEGACY_BASE_URL.
            buildConfigField("String", "LEGACY_BASE_URL", "\"https://airdropja.com/airdrop/inc\"")
            buildConfigField("String", "ENV_NAME", "\"Production\"")
        }
    }

    signingConfigs {
        create("playUpload") {
            if (playUploadSigningConfigured) {
                storeFile = requireNotNull(playUploadStoreFile)
                storePassword = playUploadProperties.getProperty("storePassword")
                keyAlias = playUploadProperties.getProperty("keyAlias")
                keyPassword = playUploadProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Staging release remains debug-signed for local sideload testing.
            // androidComponents overrides only prodRelease with playUpload;
            // an absent/incomplete authorized key fails signing validation.
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

androidComponents {
    beforeVariants(selector().withName("prodRelease")) { variantBuilder ->
        // Do not create production release tasks until both the authorized
        // upload key and an owner-verified Play version code are present.
        variantBuilder.enable = playReleaseConfigured
    }
    onVariants(selector().withName("prodRelease")) { variant ->
        variant.signingConfig.setConfig(android.signingConfigs.getByName("playUpload"))
        variant.outputs.forEach { output ->
            output.versionCode.set(requireNotNull(requestedPlayVersionCode))
        }
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
    // Keyless — powers Delivery Method "Use Current Location" (spec §6).
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

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
    // ViewModel request-order/zero-call proofs (tier change flow, gate #22836-4).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
