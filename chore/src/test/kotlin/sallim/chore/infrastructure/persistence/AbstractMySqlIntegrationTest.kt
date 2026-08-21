package sallim.chore.infrastructure.persistence

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

class KMySqlContainer(imageName: String) : MySQLContainer<KMySqlContainer>(DockerImageName.parse(imageName))

abstract class AbstractMySqlIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: KMySqlContainer = KMySqlContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
