package sallim.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SallimApplicationTests : AbstractMySqlIntegrationTest() {

    @Test
    fun contextLoads() {
    }
}
