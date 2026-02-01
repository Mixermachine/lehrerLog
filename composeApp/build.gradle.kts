import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
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

abstract class PrepareRobolectricAndroidAll : DefaultTask() {
    @get:Input
    abstract val repoUrl: Property<String>

    @get:Input
    abstract val groupId: Property<String>

    @get:Input
    abstract val artifact: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val repo = repoUrl.get().trimEnd('/')
        val groupPath = groupId.get().replace('.', '/')
        val artifactId = artifact.get()
        val artifactVersion = version.get()
        val artifactPath = "$groupPath/$artifactId/$artifactVersion"
        val baseName = "$artifactId-$artifactVersion"
        val files = listOf(
            "$baseName.jar",
            "$baseName.pom",
            "$baseName.jar.sha512",
            "$baseName.pom.sha512"
        )
        val targetDir = outputDir.get().asFile.resolve(artifactPath)
        targetDir.mkdirs()
        files.forEach { fileName ->
            val targetFile = targetDir.resolve(fileName)
            if (targetFile.exists()) {
                return@forEach
            }
            val downloadUrl = "$repo/$artifactPath/$fileName"
            var lastError: Exception? = null
            repeat(3) { attempt ->
                try {
                    URI(downloadUrl).toURL().openStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@forEach
                } catch (e: Exception) {
                    lastError = e
                    targetFile.delete()
                    if (attempt < 2) {
                        Thread.sleep(500L * (attempt + 1))
                    }
                }
            }
            throw lastError ?: IllegalStateException("Failed to download $downloadUrl")
        }
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

val robolectricAndroidAllVersion = "13-robolectric-9030017-i7"
val robolectricAndroidAllArtifact = "android-all-instrumented"
val robolectricAndroidAllGroup = "org.robolectric"
val robolectricAndroidAllRepo = providers.gradleProperty("robolectric.dependency.repo.url")
    .orElse("https://repo.maven.apache.org/maven2")
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")

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
val desktopPackageVersion = if (versionMajor < 1) {
    "1.$versionMinor.$versionPatch"
} else {
    baseVersion
}

val prepareRobolectricAndroidAll = tasks.register<PrepareRobolectricAndroidAll>("prepareRobolectricAndroidAll") {
    repoUrl.set(robolectricAndroidAllRepo)
    groupId.set(robolectricAndroidAllGroup)
    artifact.set(robolectricAndroidAllArtifact)
    version.set(robolectricAndroidAllVersion)
    outputDir.set(robolectricDepsDir)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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
        val commonTest by getting
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.compose.ui)
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.robolectric)
                implementation(libs.roborazzi)
                implementation(libs.roborazzi.compose)
                implementation(libs.roborazzi.junit.rule)
                implementation(libs.kotlin.testJunit)
            }
        }
        val nonWasmMain by creating {
            dependsOn(commonMain)
        }
        val wasmJsTest by getting {
            dependsOn(commonTest)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            dependencies { implementation(libs.compose.ui.test) }
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
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation(libs.navigation.compose)
            implementation(libs.material.icons.extended)
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
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(devNpm("copy-webpack-plugin", "13.0.1"))
            implementation(libs.ktor.client.js)
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    robolectricDepsDir.get().asFile.toURI().toString()
                )
                it.systemProperty("robolectric.dependency.repo.id", "local")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Roborazzi configuration for snapshot testing
// Note: Most Roborazzi configuration is done via test annotations or programmatically.
// The plugin automatically handles snapshot recording and verification via Gradle tasks:
// - recordRoborazzi{Variant} to update baseline snapshots
// - verifyRoborazzi{Variant} to compare against baselines (used in CI)
// - compareRoborazzi{Variant} to generate diff images
//
// Default comparison uses pixel-perfect matching. To allow tolerance:
// - Set @RoborazziOptions annotation on tests, or
// - Use captureRoboImage(file, roborazziOptions = RoborazziOptions(...))
// For threshold configuration, see: https://github.com/takahirom/roborazzi#configuration

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricAndroidAll)
    if (name.contains("Release", ignoreCase = true)) {
        filter {
            excludeTestsMatching("de.aarondietz.lehrerlog.ui.composables.RoborazziSmokeTest")
        }
    }
}

compose.desktop {
    application {
        mainClass = "de.aarondietz.lehrerlog.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "de.aarondietz.lehrerlog"
            packageVersion = desktopPackageVersion
        }
    }
}

// Kover configuration for Kotlin Multiplatform test coverage
kover {
    currentProject {
        // Include all source sets
        sources {
            excludeJava = false
        }
    }

    reports {
        // Configure report filters
        filters {
            excludes {
                // Exclude generated code
                classes("de.aarondietz.lehrerlog.ServerConfig*")

                // Exclude platform-specific entry points
                classes("de.aarondietz.lehrerlog.MainKt")
                classes("de.aarondietz.lehrerlog.App*")

                // Exclude data classes (DTOs) - same as server
                packages("de.aarondietz.lehrerlog.data")

                // Exclude generated resources
                packages("de.aarondietz.lehrerlog.generated")

                // Exclude Koin modules (dependency injection configuration)
                classes("*Module*Kt")

                // Exclude preview functions (Compose previews)
                annotatedBy("*Preview*")
            }
        }

        // Total coverage report for all test variants
        total {
            html {
                onCheck = false // Don't fail build on coverage threshold
                title = "LehrerLog Client Coverage Report"
            }
            xml {
                onCheck = false
            }
            verify {
                onCheck = true // Verify coverage on check task
                rule {
                    minBound(60) // 60% minimum coverage
                }
                rule("Class-level coverage") {
                    minBound(50)
                    // Apply to each class
                    groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.CLASS
                }
            }
        }

        // Android-specific coverage
        filters {
            excludes {
                // Exclude Android framework callbacks
                classes("*.BuildConfig")
                classes("*.*_Factory")
                classes("*.*_Impl")
            }
        }
    }
}

// Register coverage check task
tasks.register("checkCoverage") {
    group = "verification"
    description = "Run tests and verify code coverage meets thresholds"
    dependsOn("koverHtmlReport", "koverXmlReport", "koverVerify")
}
