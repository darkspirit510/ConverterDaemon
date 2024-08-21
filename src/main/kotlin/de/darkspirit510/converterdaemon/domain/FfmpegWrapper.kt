package de.darkspirit510.converterdaemon.domain

interface FfmpegWrapper {
    fun read(name: String): List<String>
}
