package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["sallim"])
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
