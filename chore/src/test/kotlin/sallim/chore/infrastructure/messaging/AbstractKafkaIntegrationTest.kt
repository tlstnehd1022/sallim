package sallim.chore.infrastructure.messaging

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractKafkaIntegrationTest {
    companion object {
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            registry.add("spring.kafka.producer.value-serializer") { "org.springframework.kafka.support.serializer.JsonSerializer" }
            registry.add("spring.kafka.consumer.value-deserializer") { "org.springframework.kafka.support.serializer.JsonDeserializer" }
            registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages") { "sallim.chore.domain" }
        }
    }
}
