package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// @SpringBootApplication's scanBasePackages only extends @ComponentScan — it does not
// move where @EnableAutoConfiguration registers its "auto-configuration base package"
// (AutoConfigurationPackages), which JpaRepositoriesAutoConfiguration/entity scanning use and which
// otherwise defaults to this class's own package (sallim.bootstrap). chore's JPA repositories/entities
// live under sallim.chore.infrastructure.persistence, so without these two explicit annotations Spring
// Data JPA never finds them. Standard Spring Boot multi-module fix, not scope creep.
@SpringBootApplication(scanBasePackages = ["sallim"])
@EnableJpaRepositories(basePackages = ["sallim"])
@EntityScan(basePackages = ["sallim"])
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
