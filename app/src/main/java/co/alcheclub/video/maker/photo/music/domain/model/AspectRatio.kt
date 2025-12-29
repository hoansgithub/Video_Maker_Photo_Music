package co.alcheclub.video.maker.photo.music.domain.model

/**
 * AspectRatio - Video aspect ratios for export
 */
enum class AspectRatio(val width: Int, val height: Int, val displayName: String) {
    RATIO_16_9(1920, 1080, "16:9 Landscape"),
    RATIO_9_16(1080, 1920, "9:16 Portrait"),
    RATIO_1_1(1080, 1080, "1:1 Square"),
    RATIO_4_3(1440, 1080, "4:3 Standard");

    val ratio: Float get() = width.toFloat() / height.toFloat()

    companion object {
        fun fromString(value: String): AspectRatio {
            return entries.find { it.name == value } ?: RATIO_16_9
        }
    }
}
