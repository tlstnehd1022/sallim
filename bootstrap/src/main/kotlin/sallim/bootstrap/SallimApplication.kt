package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
