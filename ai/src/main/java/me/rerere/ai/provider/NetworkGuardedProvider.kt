package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage

/** Leave model discovery/downloads alone; guard the operation that actually runs a text LLM. */
internal class NetworkGuardedProvider<T : ProviderSetting>(
    private val delegate: Provider<T>,
    private val internetState: () -> InternetState,
) : Provider<T> by delegate {
    override suspend fun generateText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult {
        OnlineFirstRouting.requireTextInference(providerSetting, params.model, internetState())
        return delegate.generateText(providerSetting, messages, params)
    }

    override suspend fun streamText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk> = flow {
        // Check on collection, not when the Flow is created: retries must re-read network state.
        OnlineFirstRouting.requireTextInference(providerSetting, params.model, internetState())
        emitAll(delegate.streamText(providerSetting, messages, params))
    }
}
