package com.aura.core.compressor

/** Result of local prompt compression. */
data class CompressionResult(
    /** The normalized prompt text. */
    val compressedText: String,
    /** Percentage of characters removed from the original prompt. */
    val savedRatioPercent: Int
)

/** Reduces locally detectable prompt overhead before a request is sent. */
class AuraContextCompressor {
    private companion object {
        val compressionRegex = Regex(
            "(?iu)(?<![\\p{L}\\p{N}])(?:could\\s+you|would\\s+you|can\\s+you|please|пожалуйста|подскажи|помоги)(?![\\p{L}\\p{N}])(?:\\s*[,;:!?]+\\s*|\\s+|$)"
                + "|([!?.,;:])\\1+"
                + "|\\s+([!?.,;:])"
                + "|\\s+"
        )
    }

    /**
     * Compresses redundant prompt formatting and common filler words.
     *
     * @param prompt original prompt text.
     * @return compressed text and the percentage of characters saved.
     */
    fun compress(prompt: String): CompressionResult {
        if (prompt.isEmpty()) return CompressionResult("", 0)

        val compressedText = compressionRegex.replace(prompt) { match ->
            when {
                match.groupValues[1].isNotEmpty() -> match.groupValues[1].first().toString()
                match.groupValues[2].isNotEmpty() -> match.groupValues[2]
                else -> " "
            }
        }.trim()
        val savedRatioPercent = ((prompt.length - compressedText.length) * 100 / prompt.length)

        return CompressionResult(compressedText, savedRatioPercent)
    }
}