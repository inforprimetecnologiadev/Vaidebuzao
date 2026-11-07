import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Versão: fonte única em /version.properties (MAJOR.MINOR.PATCH)
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "1.0.0")
val appVersionCode: Int = appVersionName.substringBefore("-").split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 10000 + minor * 100 + patch
}

// Assinatura de release: lida de variáveis de ambiente (GitHub Secrets). Nunca commitar o keystore.
val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "br.com.inforprimetecnologia.vaidebuzao"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.inforprimetecnologia.vaidebuzao"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
        ndk {
            // Celulares Android reais (32 e 64 bits). Mantém o APK menor.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("String", "REPO_URL", "\"https://github.com/inforprimetecnologiadev/vaidebuzao\"")
        // Branch que recebe as linhas gravadas enviadas pelo app (botão "Enviar para o projeto")
        buildConfigField("String", "DATA_BRANCH", "\"main\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Vai de Buzão (teste)")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            resValue("string", "app_name", "Vai de Buzão")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.location)
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
