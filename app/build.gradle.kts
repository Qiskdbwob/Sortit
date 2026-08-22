import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

// Helper: baca local.properties kalau ada
fun readLocalProps(): Properties? {
    val f = rootProject.file("local.properties")
    return if (f.exists()) Properties().apply { load(FileInputStream(f)) } else null
}
val localProps = readLocalProps()

// CI: env vars dari GitHub Actions secrets
val envStoreFile = System.getenv("SORTIT_STORE_FILE")
val envStorePassword = System.getenv("SORTIT_STORE_PASSWORD")
val envKeyAlias = System.getenv("SORTIT_KEY_ALIAS")
val envKeyPassword = System.getenv("SORTIT_KEY_PASSWORD")

val hasEnvSigning = !envStoreFile.isNullOrBlank()
    && !envStorePassword.isNullOrBlank()
    && !envKeyAlias.isNullOrBlank()
    && !envKeyPassword.isNullOrBlank()
    && file(envStoreFile!!).exists()

val hasLocalSigning = localProps != null
    && localProps.getProperty("RELEASE_STORE_FILE", "").isNotEmpty()

android {
    namespace = "com.sortit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sortit"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasEnvSigning) {
            create("release") {
                storeFile = file(envStoreFile!!)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        } else if (hasLocalSigning) {
            create("release") {
                storeFile = file(localProps!!.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
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
            signingConfig = when {
                hasEnvSigning || hasLocalSigning -> signingConfigs.getByName("release")
                else -> signingConfigs.getByName("debug")
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
    implementation("androidx.compose.foundation:foundation")
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
    // AndroidX Test
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Compose UI testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.mockito:mockito-core:5.12.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation("org.mockito:mockito-android:5.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
