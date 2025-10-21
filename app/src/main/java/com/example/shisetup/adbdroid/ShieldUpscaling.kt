package com.example.shisetup.adbdroid

enum class ShieldUpscaling(val content: String, val payload: List<String>) {
    BASIC("Basic", listOf("Basic", "")),
    ENHANCED("Enhanced", listOf("Enhanced", "")),
    AI_LOW("AI Low", listOf("AI-Enhanced", "Low")),
    AI_MEDIUM("AI Medium", listOf("AI-Enhanced", "Medium (default)")),
    AI_HIGH("AI High", listOf("AI-Enhanced", "High"));
}