package de.darkspirit510.converterdaemon.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import kotlin.math.floor


fun File.mediaInfo(): MediaInfoResult = with(
    ProcessBuilder("mediainfo", "--Output=JSON", absolutePath)
        .redirectErrorStream(true)
        .start()
) {
    waitFor()

    jacksonObjectMapper().readValue(
        inputStream.bufferedReader().readText(),
        MediaInfoResult::class.java
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
class MediaInfoResult {

    fun isTruncated() = media.isTruncated()

    lateinit var media: MediaInfoMedia

    @JsonIgnoreProperties(ignoreUnknown = true)
    class MediaInfoMedia {

        fun isTruncated() = generalTrack().isTruncated() || videoTracks().any { it.isTruncated() }

        @JsonProperty(value = "track")
        lateinit var tracks: List<MediaInfoTrack>

        private fun generalTrack() = tracks.single { it.type == "General" }

        private fun videoTracks() = tracks.filter { it.type == "Video" }

        @JsonIgnoreProperties(ignoreUnknown = true)
        class MediaInfoTrack {

            fun isTruncated() = when (type) {
                "General" -> extra.isTruncated
                "Video" -> floor(frameCount / frameRate) != floor(duration)
                else -> throw RuntimeException("Can't tell if truncated")
            }

            @JsonAlias("@type")
            var type: String = ""

            @JsonAlias("Duration")
            var duration: Double = 0.0

            @JsonAlias("FrameCount")
            var frameCount: Int = 0

            @JsonAlias("FrameRate")
            var frameRate: Double = 0.0

            private var extra: MediaInfoExtra = MediaInfoExtra()

            @JsonIgnoreProperties(ignoreUnknown = true)
            class MediaInfoExtra {

                @JsonAlias("IsTruncated")
                var isTruncated: Boolean = false
            }
        }
    }
}
