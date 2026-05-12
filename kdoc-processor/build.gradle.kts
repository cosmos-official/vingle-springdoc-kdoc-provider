plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    `maven-publish`
}

dependencies {
    implementation(libs.com.google.devtools.ksp.symbol.processing.api)

    // JSON for writing documentation data
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Reference to runtime module for data models
    implementation(project(":kdoc-runtime"))

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":kdoc-runtime")))
    testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.dev.zacsweers.kctfork.ksp)

    testRuntimeOnly(libs.org.junit.jupiter.junit.jupiter.engine)
    testRuntimeOnly(libs.org.junit.platform.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}