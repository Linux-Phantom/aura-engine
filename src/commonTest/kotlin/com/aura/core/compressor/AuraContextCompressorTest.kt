package com.aura.core.compressor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraContextCompressorTest {
    @Test
    fun `removes filler words repeated punctuation and extra spaces`() {
        val result = AuraContextCompressor().compress(
            "  Пожалуйста, подскажи!!!   как   сжать   контекст???  "
        )

        assertEquals("как сжать контекст?", result.compressedText)
        assertEquals(65, result.savedRatioPercent)
        assertTrue(result.savedRatioPercent > 0)
    }

    @Test
    fun `reduces repeated punctuation to a single symbol and removes extra spaces`() {
        val result = AuraContextCompressor().compress("Привет!!!   как   дела???")

        assertEquals("Привет! как дела?", result.compressedText)
        assertTrue(result.savedRatioPercent > 0)
    }
}