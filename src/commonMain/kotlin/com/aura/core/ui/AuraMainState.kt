package com.aura.core.ui

import com.aura.core.predictor.PredictionResult
import com.aura.core.compressor.CompressionResult

data class AuraMainState(
    val inputText: String = "",
    val isOnline: Boolean = true,
    val predictionResult: PredictionResult? = null,
    val predictionSuggestions: List<String> = emptyList(),
    val optimizationPrompt: String = "",
    val compressionResult: CompressionResult? = null,
    val maskedOptimizationText: String = "",
    val isOptimizing: Boolean = false,
    val chatMessages: List<Pair<String, String>> = emptyList(),
    val isPreflightActive: Boolean = false,
    val isSending: Boolean = false
)
