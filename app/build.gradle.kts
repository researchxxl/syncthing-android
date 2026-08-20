import java.util.Properties

plugins {
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

dependencies {
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.aboutlibraries.core)
    implementation(libs.accompanist.permissions)
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    // Preview tooling is debug-only, and so are the @Preview functions that use it (see
    // app/src/debug/.../recentchanges/RecentChangesPreviews.kt). The split matters because release
    // builds do not run R8 (isMinifyEnabled = false), so preview functions and their sample data
    // placed in `main` would ship to users.
    //
    // ui-tooling is the part that must stay out of release — it carries the renderer/inspector.
    // ui-tooling-preview is only annotations, and is on the release classpath regardless: it arrives
    // transitively via aboutlibraries-compose. Declaring it debug-only here just keeps the intent
    // explicit. Move it to `implementation` if @Preview is ever needed from main source.
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    implementation(libs.dagger)
    implementation(libs.documentfile)
    implementation(libs.fragment.ktx)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.jbcrypt)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.lingala.zip4j)
    implementation(libs.localbroadcastmanager)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.stream)
    implementation(libs.volley)
    implementation(libs.zhanghai.compose.preference)
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)
    ksp(libs.dagger.compiler)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    namespace = "com.nutomic.syncthingandroid"
    ndkVersion = libs.versions.ndk.version.get()

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/libSyncthingNative.mk")
        }
    }

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "com.github.catfriend1.syncthingfork"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                localFile.inputStream().use { localProps.load(it) }
            }
            
            fun propOrEnv(key: String): String? =
                localProps.getProperty(key) ?: System.getenv(key)
            
            storeFile = propOrEnv("SYNCTHING_RELEASE_STORE_FILE")?.let(::file)
            storePassword = propOrEnv("SIGNING_PASSWORD")
            keyAlias = propOrEnv("SYNCTHING_RELEASE_KEY_ALIAS")
            keyPassword = storePassword
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            signingConfig = null
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    splits {
        abi {
            // Only enable splits for release builds
            isEnable = project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            // Otherwise libsyncthing.so doesn't appear where it should in installs
            // based on app bundles, and thus nothing works.
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}

abstract class ValidateAppVersionCode : DefaultTask() {

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun validate() {
        val name = versionName.get()
        val code = versionCode.get()

        val parts = name.split(".")
        if (parts.size != 4) {
            throw GradleException("Invalid versionName format: '$name'. Expected format 'major.minor.patch.wrapper'.")
        }

        val calculatedCode = parts[0].toInt() * 1_000_000 +
                             parts[1].toInt() * 10_000 +
                             parts[2].toInt() * 100 +
                             parts[3].toInt()

        if (calculatedCode != code) {
            throw GradleException("Version mismatch: Calculated versionCode ($calculatedCode) does not match declared versionCode ($code). Please review 'gradle/libs.versions.toml'.")
        }
    }
}

tasks.register<ValidateAppVersionCode>("validateAppVersionCode") {
    versionName.set(libs.versions.version.name)
    versionCode.set(libs.versions.version.code.map { it.toInt() })
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        dependsOn("validateAppVersionCode")
    }

    val isCopilot = System.getenv("IS_COPILOT")?.toBoolean() ?: false
    if (!isCopilot) {
        tasks
            .matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
            .configureEach {
                dependsOn(":syncthing:buildNative")
            }
    }
}
