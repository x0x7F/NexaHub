package me.rerere.ai.provider

import android.content.Context
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import okhttp3.OkHttpClient

/**
 * Provider manager, responsible for registering and looking up Provider instances
 */
class ProviderManager(client: OkHttpClient, context: Context) {
    // Registered Provider instances
    private val providers = mutableMapOf<String, Provider<*>>()

    init {
        // Register the default Providers
        registerProvider("openai", OpenAIProvider(client, context))
        registerProvider("google", GoogleProvider(client, context))
        registerProvider("claude", ClaudeProvider(client, context))
    }

    /**
     * Register a Provider instance
     *
     * @param name Provider name
     * @param provider Provider instance
     */
    fun registerProvider(name: String, provider: Provider<*>) {
        providers[name] = provider
    }

    /**
     * Get a Provider instance
     *
     * @param name Provider name
     * @return the Provider instance, or null if it does not exist
     */
    fun getProvider(name: String): Provider<*> {
        return providers[name] ?: throw IllegalArgumentException("Provider not found: $name")
    }

    /**
     * Get the Provider instance for a ProviderSetting
     *
     * @param setting Provider setting
     * @return the Provider instance, or null if it does not exist
     */
    fun <T : ProviderSetting> getProviderByType(setting: T): Provider<T> {
        @Suppress("UNCHECKED_CAST")
        return when (setting) {
            is ProviderSetting.OpenAI -> getProvider("openai")
            is ProviderSetting.Google -> getProvider("google")
            is ProviderSetting.Claude -> getProvider("claude")
        } as Provider<T>
    }
}
