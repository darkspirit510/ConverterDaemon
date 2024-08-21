package de.darkspirit510.converterdaemon.domain

import java.io.File

data class Task(
    val sourceFile: File,
    val command: List<String>,
    val temporaryOutputFile: File,
    val outputFile: File,
    val frameCount: Int
)
