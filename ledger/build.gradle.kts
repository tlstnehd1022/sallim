import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
    // chore/calendar의 build.gradle.kts와 동일한 이유(로컬 Docker 엔진과 testcontainers 1.19.8 충돌) —
    // 이 모듈도 Testcontainers MySQL로 TransactionRepositoryAdapterTest를 띄우므로 동일 오버라이드가 필요하다.
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
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
