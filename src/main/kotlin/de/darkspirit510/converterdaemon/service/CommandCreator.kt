package de.darkspirit510.converterdaemon.service

import de.darkspirit510.converterdaemon.domain.FfmpegWrapper
import de.darkspirit510.converterdaemon.domain.Task
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.UUID.randomUUID
import java.util.regex.Pattern

@Service
class CommandCreator(
    @Value("\${directory.source}") private val sourceDirectory: String,
    @Value("\${directory.destination}") private val destinationDirectory: String,
    private val ffmpegWrapper: FfmpegWrapper
) {

    private val knownChannelTypes = setOf("Video", "Audio", "Subtitle", "Attachment")
    private val defaultLanguages = listOf("deu", "ger", "eng")
    private val escapeCharacters = listOf(" ", "`", "(", ")", "!", "?")

    fun createCommand(source: File): Task {
        val mediaInfoStreams = source.mediaInfo()

        val streams = ffmpegWrapper
            .read(source.absolutePath)
            .asSequence()
            .map { it.trim() }
            .filter { it.contains("Stream") }
            .filter { !it.startsWith("Guessed") }
            .map { Stream.from(it) }
            .groupBy { it.type }

        if (streams["Video"]!!.size > 1) {
            throw IllegalArgumentException("Multiple video streams found")
        }

        if (streams.keys.any { !knownChannelTypes.contains(it) }) {
            throw IllegalArgumentException("Unknown stream type found")
        }

        val temporaryOutputFile = File(destinationDirectory, "${randomUUID()}.mkv")
        val outputFile =
            File(
                "$destinationDirectory${File.separator}${source.parent.substringAfter(sourceDirectory)}",
                outputName(source.name)
            )

        val command = listOf(
            "ffmpeg",
            "-n",
            "-i",
            source.absolutePath,
            "-map",
            "0:v",
            "-c:v",
            videoFormat(streams)
        ) +
                audioMappings(streams) +
                subtitleMappings(streams) +
                attachmentMapping(streams) +
                listOf(
                    "-crf",
                    "17",
                    "-preset",
                    "medium",
                    "-max_muxing_queue_size",
                    "9999",
                    temporaryOutputFile.absolutePath
                )

        return Task(
            sourceFile = source,
            command = command,
            temporaryOutputFile = temporaryOutputFile,
            outputFile = outputFile,
            frameCount = mediaInfoStreams.media.tracks.maxOf { it.frameCount }
        )
    }

    private fun attachmentMapping(streams: Map<String, List<Stream>>) =
        if (streams.keys.contains("Attachment")) {
            listOf("-map", "0:t", "-c:t", "copy")
        } else {
            emptyList()
        }

    private fun escape(filename: String): String {
        var escapedFilename = filename

        escapeCharacters.forEach {
            escapedFilename = escapedFilename.replace(it, "\\$it")
        }

        return escapedFilename
    }

    private fun videoFormat(streams: Map<String, List<Stream>>): String =
        if (streams["Video"]!!.single().codec.startsWith("hevc")) {
            "copy"
        } else {
            "libx265"
        }

    private fun audioMappings(streams: Map<String, List<Stream>>): List<String> = defaultLanguages
        .flatMap { lang ->
            audioMappingsFor(streams["Audio"]?.mapIndexedNotNull { idx, it ->
                if (it.lang == lang) {
                    Pair(idx, it)
                } else {
                    null
                }
            } ?: emptyList()
            )
        }
        .flatMapIndexed { idx, mapping -> listOf("-map", "0:a:${mapping.index}", "-c:a:$idx", mapping.action) }

    private fun audioMappingsFor(sourceMappings: List<Pair<Int, Stream>>): List<Mapping> {
        val audioMappings = mutableListOf<Mapping>()

        sourceMappings.forEach {
            audioMappings.add(Mapping(it.first, it.second.codec, "copy"))
        }

        if (audioMappings.any { !it.codec.startsWith("ac3") }
            && audioMappings.none { it.codec.startsWith("ac3") && !it.codec.endsWith("stereo, fltp, 192 kb/s") }) {
            val lastNonAC3Index = audioMappings
                .indexOf(audioMappings.last { !it.codec.startsWith("ac3") })
            audioMappings.add(lastNonAC3Index + 1, audioMappings.first().copy(action = "ac3"))
        }

        return audioMappings
    }

    private fun subtitleMappings(streams: Map<String, List<Stream>>): List<String> {
        val subtitleCommands = mutableListOf<String>()

        defaultLanguages.forEach { lang ->
            streams["Subtitle"]?.forEachIndexed { idx, it ->
                if (it.lang == lang) {
                    subtitleCommands.addAll(listOf("-map", "0:s:$idx", "-c:s:${subtitleCommands.size}", "copy"))
                }
            }
        }

        return subtitleCommands
    }

    private fun outputName(filename: String) = "${filename.substringBeforeLast(".")}.mkv"
}

data class Stream(
    val index: Int,
    val lang: String,
    val type: String,
    val codec: String
) {
    companion object {
        private val patternWithLang =
            Pattern.compile("""Stream #0:(?<index>\d+)\((?<lang>\w+)\): (?<type>\w+): (?<codec>.*)""")
        private val patternWithoutLang = Pattern.compile("""Stream #0:(?<index>\d+): (?<type>\w+): (?<codec>.*)""")

        fun from(raw: String): Stream {
            with(
                patternWithLang
                    .matcher(raw)
                    .apply {
                        if (!matches()) {
                            with(
                                patternWithoutLang
                                    .matcher(raw)
                                    .apply {
                                        if (!matches() || !setOf("Video", "Attachment").contains(group("type"))) {
                                            throw IllegalArgumentException("Missing language for stream")
                                        }
                                    }) {
                                return Stream(
                                    index = group("index").toInt(),
                                    lang = "???",
                                    type = group("type"),
                                    codec = group("codec")
                                )
                            }
                        }
                    }
            ) {
                return Stream(
                    index = group("index").toInt(),
                    lang = group("lang"),
                    type = group("type"),
                    codec = group("codec")
                )
            }
        }
    }
}

data class Mapping(
    val index: Int,
    val codec: String,
    val action: String
)
