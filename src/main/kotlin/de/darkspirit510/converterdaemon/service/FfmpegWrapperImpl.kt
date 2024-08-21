package de.darkspirit510.converterdaemon.service

import de.darkspirit510.converterdaemon.domain.FfmpegWrapper
import org.springframework.stereotype.Service

@Service
class FfmpegWrapperImpl: FfmpegWrapper {

    override fun read(name: String): List<String> = with(
        ProcessBuilder("ffmpeg", "-i", name)
            .redirectErrorStream(true)
            .start()
    ) {
        waitFor()
        inputStream.bufferedReader().readLines()
    }
}
