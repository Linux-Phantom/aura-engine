package com.aura.core.ui

import com.aura.core.fallback.AuraFallbackRouter
import com.aura.core.fallback.MockLocalEngine
import com.aura.core.compressor.AuraContextCompressor
import com.aura.core.privacy.AuraPrivacyMasker
import com.aura.core.predictor.AuraPredictor
import com.aura.core.predictor.PredictionConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuraMainViewModel(
    private val predictor: AuraPredictor = AuraPredictor(),
    private val fallbackRouter: AuraFallbackRouter = AuraFallbackRouter(MockLocalEngine())
) {
    private val compressor = AuraContextCompressor()
    private val privacyMasker = AuraPrivacyMasker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AuraMainState())
    val state: StateFlow<AuraMainState> = _state

    fun onInputChanged(text: String) {
        _state.value = _state.value.copy(inputText = text)
        scope.launch {
            val prediction = predictor.predictIntent(text)
            val suggestions = predictor.predictNext(text)
            _state.value = _state.value.copy(
                predictionResult = prediction,
                predictionSuggestions = suggestions,
                isPreflightActive = prediction.isPreflightReady
            )
        }
    }

    fun onOptimizationPromptChanged(text: String) {
        _state.value = _state.value.copy(optimizationPrompt = text)
    }

    fun optimizePrompt() {
        val prompt = _state.value.optimizationPrompt
        if (prompt.isBlank()) return

        _state.value = _state.value.copy(isOptimizing = true)
        scope.launch {
            val compression = compressor.compress(prompt)
            val masking = privacyMasker.mask(compression.compressedText)
            _state.value = _state.value.copy(
                compressionResult = compression,
                maskedOptimizationText = masking.maskedText,
                isOptimizing = false
            )
        }
    }

    fun acceptSuggestion() {
        val suggestion = _state.value.predictionResult?.predictedCompletion.orEmpty()
        if (suggestion.isNotEmpty()) {
            _state.value = _state.value.copy(inputText = suggestion)
        }
    }

    fun toggleNetworkStatus() {
        _state.value = _state.value.copy(isOnline = !_state.value.isOnline)
    }

    fun onSend() {
        val prompt = _state.value.inputText.trim()
        if (prompt.isEmpty()) return

        _state.value = _state.value.copy(isSending = true)
        scope.launch {
            val result = fallbackRouter.executeWithFallback(prompt, _state.value.isOnline) { maskedPrompt ->
                delay(150)
                "AURA remote response for: $maskedPrompt"
            }

            val updatedMessages = _state.value.chatMessages + (
                "Пользователь" to prompt
            ) + (
                if (result.isLocalFallback) "AURA (Local)" to result.response else "AURA" to result.response
            )

            _state.value = _state.value.copy(
                inputText = "",
                predictionResult = null,
                isPreflightActive = false,
                chatMessages = updatedMessages,
                isSending = false
            )
        }
    }
}
