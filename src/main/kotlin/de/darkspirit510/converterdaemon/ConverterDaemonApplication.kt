package de.darkspirit510.converterdaemon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConverterDaemonApplication

fun main(args: Array<String>) {
	runApplication<ConverterDaemonApplication>(*args)
}
