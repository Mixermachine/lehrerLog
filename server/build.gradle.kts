plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ktor)
    application
    jacoco
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
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.id)
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
    implementation(libs.hikari)

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

// JaCoCo configuration for test coverage
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // Generate report after tests run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // Tests must run before generating report
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacoco.xml"))
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    // Exclude generated code
                    "**/de/aarondietz/lehrerlog/ServerConfig*",
                    // Exclude data classes (DTOs)
                    "**/de/aarondietz/lehrerlog/data/**",
                    // Exclude database table definitions
                    "**/de/aarondietz/lehrerlog/db/tables/**",
                    // Exclude main application entry point
                    "**/de/aarondietz/lehrerlog/ApplicationKt*"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal() // 60% minimum coverage
            }
        }
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal() // 50% minimum per class
            }
            excludes = listOf(
                "de.aarondietz.lehrerlog.ServerConfig*",
                "de.aarondietz.lehrerlog.data.*",
                "de.aarondietz.lehrerlog.db.tables.*",
                "de.aarondietz.lehrerlog.ApplicationKt"
            )
        }
    }
}

// Register coverage check task
tasks.register("checkCoverage") {
    group = "verification"
    description = "Run tests and verify code coverage meets thresholds"
    dependsOn(tasks.test, tasks.jacocoTestCoverageVerification)
}
