package de.darkspirit510.converterdaemon.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File


@Service
class DirectoryPoller(
    @Value("\${directory.source}") private val sourceDirectory: String,
    private val taskService: TaskService
) {

    @Scheduled(cron = "*/5 * * * * ?")
    fun callScheduled() {
        if (taskService.hasTask()) {
            return
        }

        createTask()
    }

    private fun createTask() {
        val directories = File(sourceDirectory)
            .listFiles()
            ?.filter { it.isDirectory }
            ?: emptyList<File>()

        if (directories.isNotEmpty()) {
            taskService.createTaskFor(directories.first().listFiles()!!.first { it.isFile })
        }
    }
}
