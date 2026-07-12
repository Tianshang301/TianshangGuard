import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.tianshang.guard"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile", "release.jks"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "guard")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    defaultConfig {
        applicationId = "com.tianshang.guard"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.5.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
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

    implementation(libs.sqlcipher)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.koin.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.onnxruntime)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testUnifiedDebugUnitTest")

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/databinding/*",
        "**/BR.class",
        "**/di/*",
        "**/data/local/database/*",
        "**/data/remote/*"
    )
    val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/unifiedDebug")) {
        exclude(fileFilter)
    }
    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/unifiedDebug")) {
        exclude(fileFilter)
    }
    val execDataTree = fileTree(layout.buildDirectory) {
        include("jacoco/testUnifiedDebugUnitTest.exec")
        include("outputs/unit_test_code_coverage/unifiedDebugUnitTest/**/*.ec")
    }
    sourceDirectories.setFrom(files("src/main/java", "src/unified/java"))
    classDirectories.setFrom(kotlinClasses, javaClasses)
    executionData.setFrom(execDataTree)

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}
