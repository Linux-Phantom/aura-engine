package com.aura.core

import com.aura.core.compressor.AuraContextCompressor
import com.aura.core.privacy.AuraPrivacyMasker
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceBenchmarkTest {
    @Test
    fun `measures compressor and masker for 100 typical prompts`() {
        val compressor = AuraContextCompressor()
        val masker = AuraPrivacyMasker()
        val promptTemplates = listOf(
            "Hi!!!",
            "Please summarize this request and remove unnecessary details!!!",
            "  Could you   explain   why   this   prompt   has   extra spaces???  ",
            "Please contact user{index}@example.test about the meeting.",
            "Call +1 (202) 555-01{phoneSuffix} after reviewing the update!!!",
            "Keep card 411111111111{cardSuffix} private, please!!!",
            "  Пожалуйста, подскажи!!!   как   исправить   эту   ошибку???  ",
            "Could you review user{index}@example.test and call +44 20 7946 0958, please!!!",
            "Long context {index}: " + "Please analyze the repeated details!!!   ".repeat(8),
            "Long privacy context {index}: user{index}@example.test, +1 (202) 555-01{phoneSuffix}, " +
                "card 411111111111{cardSuffix}. Please summarize!!!   Avoid repeated punctuation???"
        )
        val prompts = List(100) { index ->
            promptTemplates[index % promptTemplates.size]
                .replace("{index}", index.toString())
                .replace("{phoneSuffix}", (index % 100).toString().padStart(2, '0'))
                .replace("{cardSuffix}", (index % 10_000).toString().padStart(4, '0'))
        }
        assertEquals(100, prompts.size)

        var processedCharacters = 0
        val elapsedMillis = measureTimeMillis {
            prompts.forEach { prompt ->
                val compressed = compressor.compress(prompt)
                val maskingResult = masker.mask(compressed.compressedText)
                val restoredText = masker.unmask(maskingResult.maskedText, maskingResult.tokenMap)
                processedCharacters += compressed.compressedText.length + maskingResult.maskedText.length +
                    restoredText.length
            }
        }

        assertTrue(processedCharacters > 0)
        println("AuraContextCompressor + AuraPrivacyMasker: $elapsedMillis ms for ${prompts.size} prompts")
    }
}