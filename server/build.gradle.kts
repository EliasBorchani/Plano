plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("app.plano.server.MainKt")
}

dependencies {
    // Shared with the clients: wire format, models and rules (validation, enums).
    implementation(projects.contract)
    implementation(projects.domain.auth.api)
    implementation(projects.domain.events.api)
    implementation(projects.domain.polls.api)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentneg)
    implementation(libs.ktor.server.statuspages)
    implementation(libs.ktor.server.calllogging)
    implementation(libs.ktor.serialization.json)

    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.contentneg)
}

// Local runs and tests use the Postgres of compose.yaml; settings come from the root .env.
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }?.readLines().orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }

tasks.test {
    environment(dotEnv)
}

tasks.named<JavaExec>("run") {
    environment(dotEnv)
}
