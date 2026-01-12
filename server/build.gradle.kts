plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "de.aarondietz.lehrerlog"
version = "1.0.0"
application {
    mainClass.set("de.aarondietz.lehrerlog.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation("io.insert-koin:koin-ktor:3.5.6")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.6")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
    implementation("dev.hayden:khealth:3.0.2")
    implementation("io.ktor:ktor-server-compression:3.3.1")
    implementation("io.ktor:ktor-server-swagger:3.3.1")
    implementation("io.ktor:ktor-server-websockets:3.3.1")
    implementation("io.ktor:ktor-server-core:3.3.1")
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation("io.ktor:ktor-client-content-negotiation:3.3.1")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")

    // Authentication
    implementation("io.ktor:ktor-server-auth:3.3.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.3.1")
    implementation("org.mindrot:jbcrypt:0.4")

    // Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation("org.jetbrains.exposed:exposed-java-time:0.58.0")

    // Flyway migrations
    implementation(libs.flyway.core)
    implementation("org.flywaydb:flyway-database-postgresql:11.17.0")

    // Database drivers
    implementation(libs.postgres)
    implementation(libs.h2)
}
repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
        name = "jitpack"
    }
}
