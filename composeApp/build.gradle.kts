import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqlDelight)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()

}

abstract class GenerateServerConfig : DefaultTask() {
    @get:Input
    abstract val serverUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageDir = outputDir.get().asFile.resolve("de/aarondietz/lehrerlog")
        packageDir.mkdirs()
        val configFile = packageDir.resolve("ServerConfig.kt")
        val escapedUrl = serverUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")
        configFile.writeText(
            """
            |package de.aarondietz.lehrerlog
            |
            |object ServerConfig {
            |    const val SERVER_URL = "$escapedUrl"
            |}
            """.trimMargin()
        )
    }
}

val serverUrlProvider = providers.gradleProperty("serverUrl").orElse("http://localhost:8080")
val serverConfigDir = layout.buildDirectory.dir("generated/source/serverConfig")

val generateServerConfig = tasks.register<GenerateServerConfig>("generateServerConfig") {
    serverUrl.set(serverUrlProvider)
    outputDir.set(serverConfigDir)
}

val appVersionNameProvider = providers.gradleProperty("appVersionName").orElse("0.0.1-dev.local")
val buildNumberProvider = providers.gradleProperty("buildNumber").orElse("1")

fun parseSemVer(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".")
    require(parts.size == 3) { "appVersionName must start with MAJOR.MINOR.PATCH, got: $version" }
    val major = parts[0].toIntOrNull()
        ?: error("Invalid major version in appVersionName: $version")
    val minor = parts[1].toIntOrNull()
        ?: error("Invalid minor version in appVersionName: $version")
    val patch = parts[2].toIntOrNull()
        ?: error("Invalid patch version in appVersionName: $version")
    return Triple(major, minor, patch)
}

fun computeVersionCode(major: Int, minor: Int, patch: Int, build: Int): Int {
    val code = major * 100_000_000 + minor * 1_000_000 + patch * 10_000 + build
    require(code in 1..2_100_000_000) { "versionCode out of range: $code" }
    return code
}

val appVersionName = appVersionNameProvider.get()
val baseVersion = appVersionName.substringBefore("-")
val buildNumber = buildNumberProvider.get().toIntOrNull()
    ?: error("Invalid buildNumber: ${buildNumberProvider.get()}")
val (versionMajor, versionMinor, versionPatch) = parseSemVer(baseVersion)
val versionCodeValue = computeVersionCode(versionMajor, versionMinor, versionPatch, buildNumber)

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(serverConfigDir)
        }
        val nonWasmMain by creating {
            dependsOn(commonMain)
        }
        val nativeMain by creating {
            dependsOn(nonWasmMain)
        }
        val iosMain by creating {
            dependsOn(nativeMain)
        }

        androidMain.get().dependsOn(nonWasmMain)
        jvmMain.get().dependsOn(nonWasmMain)

        iosArm64Main.get().dependsOn(iosMain)
        iosSimulatorArm64Main.get().dependsOn(iosMain)

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation(libs.navigation.compose)
            implementation(libs.material.icons.extended)
            implementation(libs.sqldelight.coroutines)

            // Ktor Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.json)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Logging
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.jvm)
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation("app.cash.sqldelight:web-worker-driver-wasm-js:2.2.1")
            implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
            implementation(npm("sql.js", "1.13.0"))
            implementation(devNpm("copy-webpack-plugin", "13.0.1"))
            implementation(libs.ktor.client.js)
        }
    }
}

sqldelight {
    databases {
        create("lehrerLog") { // IDE displays error but this is fine
            packageName = "de.aarondietz.lehrerlog"
            generateAsync.set(true)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateServerConfig)
}

android {
    namespace = "de.aarondietz.lehrerlog"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.aarondietz.lehrerlog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeValue
        versionName = appVersionName
    }
    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
        }
        create("qa") {
            dimension = "env"
            applicationIdSuffix = ".qa"
            resValue("string", "app_name", "LehrerLog QA")
        }
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "LehrerLog Dev")
        }
    }
    signingConfigs {
        create("releaseApk") {
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS_APK")
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        }
        create("releaseAab") {
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS_AAB")
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        create("releaseApk") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("releaseApk")
        }
        create("releaseAab") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("releaseAab")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "de.aarondietz.lehrerlog.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "de.aarondietz.lehrerlog"
            packageVersion = baseVersion
        }
    }
}
