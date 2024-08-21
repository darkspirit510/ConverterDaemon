package de.darkspirit510.converterdaemon.service

import de.darkspirit510.converterdaemon.domain.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import kotlin.concurrent.thread

@Service
class TaskService(
    private val commandCreator: CommandCreator
) {

    var logger: Logger = LoggerFactory.getLogger(TaskService::class.java)

    private val regexPattern = Pattern.compile("""frame=\s*(?<frame>\d+)""")

    var task: Task? = null
    private var frame: Int = 0

    fun hasTask() = task != null

    fun currentFrame() = frame
    fun frameCount() = task?.frameCount ?: -1

    @Async
    @Synchronized
    fun createTaskFor(file: File) {
        if (hasTask()) {
            return
        }

        logger.info("Analyzing file ${file.name} 🕵️‍♂️")

        task = commandCreator.createCommand(file)

        thread(start = true) {
            transcodeVideoFile()

            if (task!!.temporaryOutputFile.mediaInfo().isTruncated()) {
                handleTruncatedResult()
            } else {
                handleSuccessfulResult(file)
            }

            task = null
        }
    }

    private fun transcodeVideoFile() {
        with(
            ProcessBuilder(task!!.command)
                .redirectErrorStream(true)
                .start()
        ) {
            logger.info("Started conversion, ${task!!.frameCount} frames ahead 🤖")

            inputStream.bufferedReader().use { stream ->
                while (isAlive) {
                    val line = stream.readLine()

                    if (line?.startsWith("frame") == true) {
                        logger.debug(line)
                        frame = extractFrameFrom(line)
                    }
                }
            }
        }
    }

    private fun handleSuccessfulResult(file: File) {
        task!!.outputFile.parentFile.mkdirs()
        task!!.temporaryOutputFile.moveTo(task!!.outputFile)

        task!!.sourceFile.delete()

        logger.info("Successfully converted ${file.name}! 🥳")

        if (task!!.sourceFile.parentFile.listFiles()!!.isEmpty()) {
            task!!.sourceFile.parentFile.delete()
            logger.info("Removed empty directory ${file.parentFile.name}! 🧹")
        }
    }

    private fun handleTruncatedResult() {
        logger.warn("Temporary file is truncated, deleting it for reprocessing 🗑️")
        task!!.temporaryOutputFile.delete()
    }

    private fun extractFrameFrom(line: String) = with(regexPattern.matcher(line)) {
        find()
        group("frame").toInt()
    }
}

private fun File.moveTo(targetFile: File) {
    Files.move(this.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
}
