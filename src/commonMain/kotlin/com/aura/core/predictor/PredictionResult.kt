package com.aura.core.predictor

/** Result of local intent prediction. */
data class PredictionResult(
    /** Original, unnormalized user input. */
    val originalText: String,
    /** Suggested completion for the recognized intent. */
    val predictedCompletion: String,
    /** Confidence assigned by the predictor. */
    val confidence: PredictionConfidence,
    /** Whether the input is ready for preflight execution. */
    val isPreflightReady: Boolean
)
