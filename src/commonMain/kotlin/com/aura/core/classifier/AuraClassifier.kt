package com.aura.core.classifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Performs lightweight local topic and complexity classification for routing. */
class AuraClassifier {
    private val codeKeywords = setOf(
        "kotlin","code","class","fun","bug","algorithm","fix","async","sqlite",
        "java","python","javascript","ts","typescript","compile","build","stacktrace",
        "exception","error","debug","repository","git","pull request","merge"
    )

    private val mathKeywords = setOf(
        "calculate","solve","integral","equation","sum","product","derivative","limit","matrix","algebra","geometry","theorem"
    )

    /**
     * Classifies a prompt using code, math, and length heuristics.
     *
     * @param prompt prompt to classify.
     * @return detected topic and estimated complexity.
     */
    suspend fun classify(prompt: String): AnalysisResult = withContext(Dispatchers.Default) {
        val lower = prompt.lowercase()

        // Check for code-related keywords using whole-word/phrase matching to avoid false positives.
        for (kw in codeKeywords) {
            if (containsKeyword(lower, kw)) {
                return@withContext AnalysisResult(RequestTopic.CODE, RequestComplexity.HEAVY)
            }
        }

        // Check for math-related keywords
        for (kw in mathKeywords) {
            if (containsKeyword(lower, kw)) {
                return@withContext AnalysisResult(RequestTopic.MATH_LOGIC, RequestComplexity.HEAVY)
            }
        }

        // Check length
        if (prompt.length > 300) {
            return@withContext AnalysisResult(RequestTopic.GENERAL_CHAT, RequestComplexity.HEAVY)
        }

        // Default
        AnalysisResult(RequestTopic.GENERAL_CHAT, RequestComplexity.LIGHT)
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(keyword)}(?![\\p{L}\\p{N}])")
        return pattern.containsMatchIn(text)
    }
}
