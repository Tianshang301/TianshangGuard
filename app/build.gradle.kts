plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tianshang.guard"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "TsGuard@2026Release"
            keyAlias = "guard"
            keyPassword = "TsGuard@2026Release"
        }
    }

    defaultConfig {
        applicationId = "com.tianshang.guard"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            lint {
                checkReleaseBuilds = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    flavorDimensions += "language"
    productFlavors {
        create("zh") {
            dimension = "language"
            applicationIdSuffix = ".zh"
            versionNameSuffix = "-zh"
        }
        create("en") {
            dimension = "language"
            applicationIdSuffix = ".en"
            versionNameSuffix = "-en"
        }
        create("unified") {
            dimension = "language"
            versionNameSuffix = "-unified"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.onnxruntime.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)

    implementation(libs.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.datastore.preferences)

    // implementation(libs.sqlcipher)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
