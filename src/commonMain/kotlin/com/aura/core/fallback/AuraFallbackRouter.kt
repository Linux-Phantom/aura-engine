package com.aura.core.fallback

import com.aura.core.privacy.AuraPrivacyMasker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of remote execution or local fallback execution. */
data class FallbackResponse(
    /** Model response text after local token restoration. */
    val response: String,
    /** Whether the local model handled the request. */
    val isLocalFallback: Boolean
)

/** Routes privacy-protected prompts to a remote model and a local fallback. */
class AuraFallbackRouter(
    /** Engine used when the network is unavailable or the remote call fails. */
    private val localEngine: LocalModelEngine
) {
    private val privacyMasker = AuraPrivacyMasker()

    /**
     * Executes a masked prompt remotely or falls back to [localEngine].
     *
     * @param prompt original user prompt.
     * @param isNetworkAvailable whether a remote call is currently possible.
     * @param remoteCall suspending callback receiving the masked prompt.
     * @return response and a flag identifying local fallback execution.
     */
    suspend fun executeWithFallback(
        prompt: String,
        isNetworkAvailable: Boolean,
        remoteCall: suspend (String) -> String
    ): FallbackResponse = withContext(Dispatchers.Default) {
        val maskingResult = privacyMasker.mask(prompt)

        if (!isNetworkAvailable) {
            return@withContext FallbackResponse(
                response = privacyMasker.unmask(
                    localEngine.generateLocalResponse(maskingResult.maskedText),
                    maskingResult.tokenMap
                ),
                isLocalFallback = true
            )
        }

        try {
            val remoteResponse = remoteCall(maskingResult.maskedText)
            FallbackResponse(
                response = privacyMasker.unmask(remoteResponse, maskingResult.tokenMap),
                isLocalFallback = false
            )
        } catch (error: Throwable) {
            println("[AuraFallbackRouter] remote call failed: ${error.message}")
            FallbackResponse(
                response = privacyMasker.unmask(
                    localEngine.generateLocalResponse(maskingResult.maskedText),
                    maskingResult.tokenMap
                ),
                isLocalFallback = true
            )
        }
    }
}
