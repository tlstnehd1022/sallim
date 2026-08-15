import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "sallim"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// Captured here (root script's own top-level scope) because type-safe version
// catalog accessors don't resolve inside subprojects {} closures — Gradle
// configures each subproject as a distinct Project without the "libs"
// extension registered, so `libs.foo` there falls back to a runtime lookup
// that fails. Resolving it here and capturing as closure variables works
// around that.
val kotestRunnerJunit5 = libs.kotest.runner.junit5
val kotestAssertionsCore = libs.kotest.assertions.core

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(kotestRunnerJunit5)
        "testImplementation"(kotestAssertionsCore)
    }
}
