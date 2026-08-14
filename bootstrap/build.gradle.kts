plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":household"))
    implementation(project(":chore"))
    implementation(project(":calendar"))
    implementation(project(":ledger"))
    implementation(libs.spring.boot.starter)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}
