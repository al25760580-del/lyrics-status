package com.lyricsstatus.app.data.model

import kotlinx.serialization.Serializable

/**
 * Supported AI Translation Providers
 */
@Serializable
enum class AiProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val description: String,
    val docsUrl: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        description = "Ultra-fast response times and generous free API tier.",
        docsUrl = "https://aistudio.google.com/app/apikey"
    ),
    CHATGPT(
        displayName = "OpenAI (ChatGPT)",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        description = "Industry standard for natural lyrics translations and rhythmic phrasing.",
        docsUrl = "https://platform.openai.com/api-keys"
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        description = "Superior poetic nuance, emotional tone, and metaphorical accuracy.",
        docsUrl = "https://console.anthropic.com/settings/keys"
    ),
    GROK(
        displayName = "xAI Grok",
        defaultEndpoint = "https://api.x.ai/v1/chat/completions",
        description = "Fast, witty, modern lyrical understanding and slang adaptation.",
        docsUrl = "https://console.x.ai/"
    ),
    CUSTOM(
        displayName = "Custom Endpoint",
        defaultEndpoint = "http://localhost:11434/v1/chat/completions",
        description = "Connect any self-hosted LLM (Ollama, vLLM, LMStudio, LocalAI) or custom API proxy.",
        docsUrl = ""
    )
}

/**
 * Predefined model specification with metadata
 */
@Serializable
data class AiModelOption(
    val id: String,
    val name: String,
    val description: String,
    val isRecommended: Boolean = false
)

object PredefinedAiModels {
    val GEMINI_MODELS = listOf(
        AiModelOption(
            id = "gemini-3.5-flash-lite",
            name = "Gemini 3.5 Flash Lite",
            description = "Default high-speed model optimized for real-time translation.",
            isRecommended = true
        ),
        AiModelOption(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            description = "High-speed flagship model with great accuracy.",
            isRecommended = true
        ),
        AiModelOption(
            id = "gemini-2.5-flash-lite",
            name = "Gemini 2.5 Flash Lite",
            description = "Ultra-low latency lightweight model for continuous background sync."
        ),
        AiModelOption(
            id = "gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            description = "Deep poetic reasoning and complex metaphor translation."
        ),
        AiModelOption(
            id = "gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            description = "Next-gen multi-modal speed-optimized engine."
        ),
        AiModelOption(
            id = "gemini-1.5-flash",
            name = "Gemini 1.5 Flash",
            description = "Reliable low-cost baseline model."
        )
    )

    val CHATGPT_MODELS = listOf(
        AiModelOption(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            description = "Fast, lightweight, and cost-effective for full songs.",
            isRecommended = true
        ),
        AiModelOption(
            id = "gpt-4o",
            name = "GPT-4o",
            description = "Flagship multi-modal model with rhythm and rhyme preservation.",
            isRecommended = true
        ),
        AiModelOption(
            id = "gpt-4.5-preview",
            name = "GPT-4.5 Preview",
            description = "Most capable frontier reasoning and creative writing model."
        ),
        AiModelOption(
            id = "o3-mini",
            name = "o3 Mini",
            description = "High-efficiency reasoning model with deep nuance."
        )
    )

    val CLAUDE_MODELS = listOf(
        AiModelOption(
            id = "claude-3-7-sonnet-20250219",
            name = "Claude 3.7 Sonnet",
            description = "Hybrid reasoning & artistic model with state-of-the-art lyric translation.",
            isRecommended = true
        ),
        AiModelOption(
            id = "claude-3-5-haiku-20241022",
            name = "Claude 3.5 Haiku",
            description = "Blazing fast translation with excellent tone consistency.",
            isRecommended = true
        ),
        AiModelOption(
            id = "claude-3-5-sonnet-20241022",
            name = "Claude 3.5 Sonnet",
            description = "Top tier poetic nuance, ideal for artful lyrics."
        )
    )

    val GROK_MODELS = listOf(
        AiModelOption(
            id = "grok-3",
            name = "Grok 3",
            description = "Latest frontier flagship from xAI with extreme intelligence.",
            isRecommended = true
        ),
        AiModelOption(
            id = "grok-2-latest",
            name = "Grok 2 Latest",
            description = "Fast and witty lyrical understanding and slang adaptation.",
            isRecommended = true
        ),
        AiModelOption(
            id = "grok-beta",
            name = "Grok Beta",
            description = "Classic Grok model."
        )
    )

    fun getModelsForProvider(provider: AiProvider): List<AiModelOption> {
        return when (provider) {
            AiProvider.GEMINI -> GEMINI_MODELS
            AiProvider.CHATGPT -> CHATGPT_MODELS
            AiProvider.CLAUDE -> CLAUDE_MODELS
            AiProvider.GROK -> GROK_MODELS
            AiProvider.CUSTOM -> emptyList()
        }
    }

    fun getDefaultModel(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> "gemini-3.5-flash-lite"
            AiProvider.CHATGPT -> "gpt-4o-mini"
            AiProvider.CLAUDE -> "claude-3-7-sonnet-20250219"
            AiProvider.GROK -> "grok-3"
            AiProvider.CUSTOM -> "llama3.2"
        }
    }
}
