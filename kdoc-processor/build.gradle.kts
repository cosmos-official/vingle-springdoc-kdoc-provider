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
} 
