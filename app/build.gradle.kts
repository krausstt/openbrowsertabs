import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.krausstt.openbrowsertabs"
    compileSdk = 36

    // CI passes -PbuildNumber=<run number> so every build is a valid update
    val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "io.github.krausstt.openbrowsertabs"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // consistent release signing in CI: keystore comes from GitHub secrets,
    // decoded to a temp file whose path is exported as CI_KEYSTORE_PATH
    val ciKeystorePath: String? = System.getenv("CI_KEYSTORE_PATH")
    if (ciKeystorePath != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CI_KEY_ALIAS") ?: "openbrowsertabs"
                keyPassword = System.getenv("CI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ciKeystorePath != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
