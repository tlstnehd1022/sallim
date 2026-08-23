plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    // chore/build.gradle.kts와 동일한 이유(로컬 Docker 엔진과 testcontainers 1.19.8 충돌) —
    // 이 모듈도 자체 datasource로 SallimApplicationTests를 띄우므로 동일 오버라이드가 필요하다.
    dependencies {
        dependencySet("org.testcontainers:${libs.versions.testcontainers.get()}") {
            entry("testcontainers")
            entry("junit-jupiter")
            entry("mysql")
            entry("jdbc")
            entry("database-commons")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":household"))
    implementation(project(":chore"))
    implementation(project(":calendar"))
    implementation(project(":ledger"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
