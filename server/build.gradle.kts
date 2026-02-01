plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "de.aarondietz.lehrerlog"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
application {
    mainClass.set("de.aarondietz.lehrerlog.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(platform(libs.ktor.bom))
    testImplementation(platform(libs.ktor.bom))
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.serialization.json)
    implementation(libs.khealth)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.serialization.json)

    // Authentication
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.jbcrypt)

    // Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)

    // Flyway migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Database drivers
    implementation(libs.postgres)
    implementation(libs.h2)

    // Object storage (Garage/S3)
    implementation(libs.aws.sdk.s3)
    implementation(libs.aws.sdk.auth)
}
repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
        name = "jitpack"
    }
}

tasks.register<JavaExec>("flywayRepair") {
    group = "database"
    description = "Runs Flyway repair against the configured local database."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("de.aarondietz.lehrerlog.db.FlywayRepair")
}
