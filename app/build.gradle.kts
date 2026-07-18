plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

// Load keystore config dari local.properties (tidak di-commit)
val localProps = project.rootProject.file("local.properties")
if (localProps.exists()) {
    val props = java.util.Properties().apply { load(localProps.inputStream()) }
    project.extra["RELEASE_STORE_FILE"] = props.getProperty("RELEASE_STORE_FILE", "")
    project.extra["RELEASE_STORE_PASSWORD"] = props.getProperty("RELEASE_STORE_PASSWORD", "")
    project.extra["RELEASE_KEY_ALIAS"] = props.getProperty("RELEASE_KEY_ALIAS", "")
    project.extra["RELEASE_KEY_PASSWORD"] = props.getProperty("RELEASE_KEY_PASSWORD", "")
}

android {
    namespace = "com.sortit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sortit"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFile = project.extra["RELEASE_STORE_FILE"] as? String ?: ""
            if (storeFile.isNotEmpty()) {
                storeFile(file(storeFile))
                storePassword(project.extra["RELEASE_STORE_PASSWORD"] as? String ?: "")
                keyAlias(project.extra["RELEASE_KEY_ALIAS"] as? String ?: "")
                keyPassword(project.extra["RELEASE_KEY_PASSWORD"] as? String ?: "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseStoreFile = project.extra["RELEASE_STORE_FILE"] as? String ?: ""
            signingConfig = if (releaseStoreFile.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                // Fallback ke debug signing kalau tidak ada keystore release
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val lifecycle = "2.8.4"
    val room = "2.6.1"
    val nav = "2.7.7"
    val composeBom = "2024.09.00"

    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")
    implementation("androidx.navigation:navigation-compose:$nav")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
