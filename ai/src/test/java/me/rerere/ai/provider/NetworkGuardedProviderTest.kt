package me.rerere.ai.provider

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkGuardedProviderTest {
    private class RecordingProvider : Provider<ProviderSetting> {
        var calls = 0
        var failure: IOException? = null
        override suspend fun listModels(providerSetting: ProviderSetting) = emptyList<Model>()
        override suspend fun generateText(
            providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams,
        ): TextGenerationResult {
            calls++
            failure?.let { throw it }
            return TextGenerationResult("test", params.model.modelId, UIMessage(role = MessageRole.ASSISTANT, parts = emptyList()))
        }
        override suspend fun streamText(
            providerSetting: ProviderSetting, messages: List<UIMessage>, params: TextGenerationParams,
        ): Flow<StreamChunk> {
            calls++
            failure?.let { throw it }
            return emptyFlow()
        }
        override suspend fun generateImage(
            providerSetting: ProviderSetting, params: ImageGenerationParams,
        ): Flow<ImageGenerationItem> = error("Not used by these tests")
    }

    private val model = Model(modelId = OnlineFirstRouting.OFFLINE_MODEL_FILE)
    private val params = TextGenerationParams(model = model)
    private val local = ProviderSetting.LiteRtLocal(enabled = true)
    private val remote = ProviderSetting.Codex(enabled = true)

    @Test fun `online local request never reaches the runtime`() {
        val delegate = RecordingProvider()
        val guarded = NetworkGuardedProvider(delegate) { InternetState.ONLINE }
        assertThrows(ModelRoutingException::class.java) {
            runBlocking { guarded.generateText(local, emptyList(), params) }
        }
        assertThrows(ModelRoutingException::class.java) {
            runBlocking { guarded.streamText(local, emptyList(), params).toList() }
        }
        assertEquals(0, delegate.calls)
    }

    @Test fun `stream checks connectivity when collected and again on retry`() = runBlocking {
        val delegate = RecordingProvider()
        var network = InternetState.OFFLINE
        val guarded = NetworkGuardedProvider(delegate) { network }
        val stream = guarded.streamText(local, emptyList(), params)
        stream.toList()
        assertEquals(1, delegate.calls)
        network = InternetState.ONLINE
        assertThrows(ModelRoutingException::class.java) { runBlocking { stream.toList() } }
        assertEquals(1, delegate.calls)
    }

    @Test fun `offline blocks remote before creating a stream`() {
        val delegate = RecordingProvider()
        val guarded = NetworkGuardedProvider(delegate) { InternetState.OFFLINE }
        assertThrows(ModelRoutingException::class.java) {
            runBlocking { guarded.streamText(remote, emptyList(), params).toList() }
        }
        assertEquals(0, delegate.calls)
    }

    @Test fun `provider 401 403 429 5xx and timeout errors propagate without fallback`() {
        for (reason in listOf("HTTP 401", "HTTP 403", "HTTP 429", "HTTP 500", "timeout")) {
            val delegate = RecordingProvider()
            val original = IOException(reason)
            delegate.failure = original
            val guarded = NetworkGuardedProvider(delegate) { InternetState.ONLINE }
            assertSame(original, assertThrows(IOException::class.java) {
                runBlocking { guarded.generateText(remote, emptyList(), params) }
            })
            assertEquals(1, delegate.calls)
        }
    }

    @Test fun `discovery stays available while local inference is blocked`() = runBlocking {
        val delegate = RecordingProvider()
        val guarded = NetworkGuardedProvider(delegate) { InternetState.ONLINE }
        assertEquals(emptyList<Model>(), guarded.listModels(local))
        assertEquals(0, delegate.calls)
    }
}
