package com.aura.core.privacy

/** Result of replacing sensitive values with reversible local tokens. */
data class MaskingResult(
    /** Text containing privacy tokens instead of detected values. */
    val maskedText: String,
    /** Mapping from each generated token to its original value. */
    val tokenMap: Map<String, String>
)

/** Detects and masks common PII patterns before remote model invocation. */
class AuraPrivacyMasker {
    private companion object {
        val emailRegex = Regex(
            """(?<![\w.+-])[\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+(?![\w.-])"""
        )
        val sensitiveRegex = Regex("(?<!\\d)\\d{16}(?!\\d)")
        val phoneRegex = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)")
        val sensitiveDataRegex = Regex(
            "${emailRegex.pattern}|${sensitiveRegex.pattern}|${phoneRegex.pattern}"
        )
    }

    /**
     * Replaces emails, phone numbers, and 16-digit sensitive values with tokens.
     *
     * @param inputText text to inspect locally.
     * @return masked text and the token-to-original mapping.
     */
    fun mask(inputText: String): MaskingResult {
        val tokenMap = linkedMapOf<String, String>()
        val counters = mutableMapOf(
            TokenType.EMAIL to 0,
            TokenType.PHONE to 0,
            TokenType.SENSITIVE to 0
        )

        val maskedText = sensitiveDataRegex.replace(inputText) { match ->
            val value = match.value
            val type = when {
                emailRegex.matches(value) -> TokenType.EMAIL
                sensitiveRegex.matches(value) -> TokenType.SENSITIVE
                else -> TokenType.PHONE
            }
            val token = "[${type.name}_${counters.getValue(type) + 1}]"
            counters[type] = counters.getValue(type) + 1
            tokenMap[token] = value
            token
        }

        return MaskingResult(maskedText, tokenMap)
    }

    /**
     * Restores tokens in a model response using the local token map.
     *
     * @param responseText response containing privacy tokens.
     * @param tokenMap mapping produced by [mask].
     * @return response with known tokens replaced by original values.
     */
    fun unmask(responseText: String, tokenMap: Map<String, String>): String {
        return tokenMap.entries.fold(responseText) { text, (token, originalValue) ->
            text.replace(token, originalValue)
        }
    }

    private enum class TokenType {
        EMAIL,
        PHONE,
        SENSITIVE
    }
}