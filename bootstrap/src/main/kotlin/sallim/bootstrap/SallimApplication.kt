package sallim.bootstrap

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

// ponytail: chore's JPA persistence beans (RoomRepositoryAdapter 등) and their
// DataSource/JPA auto-configuration are not wired up yet — no datasource decision
// belongs in this plan. @SpringBootApplication is expanded into its three
// constituent meta-annotations (replicating its default component-scan filters)
// solely so an extra excludeFilters entry can be added; @SpringBootApplication
// itself has no attribute for that. Revert to plain @SpringBootApplication once
// bootstrap actually wires a real datasource + the application/api layer.
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        JpaRepositoriesAutoConfiguration::class,
    ],
)
@ComponentScan(
    basePackages = ["sallim"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["sallim\\..*\\.infrastructure\\.persistence\\..*"]),
    ],
)
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
