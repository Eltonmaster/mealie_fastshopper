plugins {
    // AGP 9 compiles Kotlin itself, so org.jetbrains.kotlin.android is not applied here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing comes from environment variables so no secrets live in the repo.
// CI writes the keystore from a repository secret; locally you can export the same variables.
val signingStoreFile = providers.environmentVariable("SIGNING_STORE_FILE").orNull
val hasReleaseSigning = !signingStoreFile.isNullOrBlank()

// The version lives in version.properties (1.2.3 -> versionName 1.2.3, versionCode 10203).
// A release from a pushed tag overrides it with -PappVersionName / -PappVersionCode.
val appVersionName = providers.gradleProperty("appVersionName").orNull
    ?: providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get()
        .lineSequence().map { it.trim() }.first { it.startsWith("version=") }.removePrefix("version=").trim()
val appVersionCode = providers.gradleProperty("appVersionCode").orNull?.toInt()
    ?: Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersionName)!!.destructured
        .let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }

android {
    namespace = "io.github.eltonmaster.fastshopper"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.eltonmaster.fastshopper"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
