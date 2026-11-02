package com.aura.core.classifier

/** Result produced by [AuraClassifier]. */
data class AnalysisResult(
    /** Broad topic detected in the request. */
    val topic: RequestTopic,
    /** Estimated computational or contextual complexity. */
    val complexity: RequestComplexity
)
