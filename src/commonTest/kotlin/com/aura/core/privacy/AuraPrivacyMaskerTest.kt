package com.aura.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraPrivacyMaskerTest {
    private val masker = AuraPrivacyMasker()

    @Test
    fun `masks email phone and sensitive number`() {
        val result = masker.mask(
            "Email user@example.com, phone +7 (999) 123-45-67, card 1234567890123456"
        )

        assertEquals(
            "Email [EMAIL_1], phone [PHONE_1], card [SENSITIVE_1]",
            result.maskedText
        )
        assertEquals(
            mapOf(
                "[EMAIL_1]" to "user@example.com",
                "[PHONE_1]" to "+7 (999) 123-45-67",
                "[SENSITIVE_1]" to "1234567890123456"
            ),
            result.tokenMap
        )
        assertTrue(result.maskedText.contains("[EMAIL_1]"))
        assertTrue(result.maskedText.contains("[PHONE_1]"))
        assertTrue(result.maskedText.contains("[SENSITIVE_1]"))
    }

    @Test
    fun `assigns separate tokens to repeated matches`() {
        val result = masker.mask("a@b.com and c@d.com; 1234567890123456 and 6543210987654321")

        assertEquals(
            "[EMAIL_1] and [EMAIL_2]; [SENSITIVE_1] and [SENSITIVE_2]",
            result.maskedText
        )
        assertEquals("a@b.com", result.tokenMap["[EMAIL_1]"])
        assertEquals("6543210987654321", result.tokenMap["[SENSITIVE_2]"])
    }

    @Test
    fun `unmasks tokens in model response`() {
        val result = masker.mask("Contact user@example.com")

        assertEquals(
            "Please contact user@example.com.",
            masker.unmask("Please contact [EMAIL_1].", result.tokenMap)
        )
    }
}