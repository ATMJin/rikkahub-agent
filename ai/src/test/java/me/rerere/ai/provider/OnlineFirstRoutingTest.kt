package me.rerere.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineFirstRoutingTest {
    private val online = Model(modelId = "online-model")
    private val e2b = Model(modelId = OnlineFirstRouting.OFFLINE_MODEL_FILE)
    private val remote = ProviderSetting.Codex(enabled = true, models = listOf(online))
    private val local = ProviderSetting.LiteRtLocal(enabled = true, models = listOf(e2b))
    private val providers = listOf(remote, local)

    @Test fun `network evidence distinguishes offline from captive portal and unknown`() {
        assertEquals(InternetState.OFFLINE, classifyInternetEvidence(false, null, null))
        assertEquals(InternetState.OFFLINE, classifyInternetEvidence(true, false, false))
        assertEquals(InternetState.ONLINE, classifyInternetEvidence(true, true, true))
        assertEquals(InternetState.UNVERIFIED, classifyInternetEvidence(true, true, false))
        assertEquals(InternetState.UNVERIFIED, classifyInternetEvidence(true, null, null))
        assertEquals(InternetState.UNVERIFIED, classifyInternetEvidence(true, true, null))
    }

    @Test fun `online preserves explicitly selected provider`() {
        assertSame(online, OnlineFirstRouting.selectModel(online, providers, InternetState.ONLINE))
    }

    @Test fun `offline uses configured E2B without mutating online choice`() {
        assertSame(e2b, OnlineFirstRouting.selectModel(online, providers, InternetState.OFFLINE))
        assertSame(online, OnlineFirstRouting.selectModel(online, providers, InternetState.ONLINE))
        assertEquals(listOf(online), remote.models)
    }

    @Test fun `offline can start without any online model configured`() {
        assertSame(e2b, OnlineFirstRouting.selectModel(null, listOf(local), InternetState.OFFLINE))
    }

    @Test fun `online refuses local model even when explicitly selected`() {
        assertThrows(ModelRoutingException::class.java) {
            OnlineFirstRouting.selectModel(e2b, providers, InternetState.ONLINE)
        }
    }

    @Test fun `online never silently picks another provider`() {
        assertThrows(ModelRoutingException::class.java) {
            OnlineFirstRouting.selectModel(null, providers, InternetState.ONLINE)
        }
        assertThrows(ModelRoutingException::class.java) {
            OnlineFirstRouting.selectModel(online, listOf(remote.copy(enabled = false), local), InternetState.ONLINE)
        }
    }

    @Test fun `unverified network never selects local or remote`() {
        for (selected in listOf(online, e2b, null)) {
            assertThrows(ModelRoutingException::class.java) {
                OnlineFirstRouting.selectModel(selected, providers, InternetState.UNVERIFIED)
            }
        }
    }

    @Test fun `offline refuses disabled or missing E2B`() {
        for (candidates in listOf(listOf(remote), listOf(remote, local.copy(enabled = false)))) {
            assertThrows(ModelRoutingException::class.java) {
                OnlineFirstRouting.selectModel(online, candidates, InternetState.OFFLINE)
            }
        }
    }

    @Test fun `offline does not use E4B or a local model with a remote overwrite`() {
        val e4b = Model(modelId = "gemma-4-E4B-it.litertlm")
        val overridden = e2b.copy(providerOverwrite = remote)
        for (candidate in listOf(e4b, overridden)) {
            assertThrows(ModelRoutingException::class.java) {
                OnlineFirstRouting.selectModel(online, listOf(local.copy(models = listOf(candidate))), InternetState.OFFLINE)
            }
        }
    }

    @Test fun `online cannot hide a local provider in a model overwrite`() {
        val overridden = online.copy(providerOverwrite = local)
        assertThrows(ModelRoutingException::class.java) {
            OnlineFirstRouting.selectModel(overridden, listOf(remote.copy(models = listOf(overridden))), InternetState.ONLINE)
        }
    }

    @Test fun `inference gate rejects remote offline and every on-device provider online`() {
        assertThrows(ModelRoutingException::class.java) {
            OnlineFirstRouting.requireTextInference(remote, online, InternetState.OFFLINE)
        }
        for (candidate in listOf(local, ProviderSetting.AICore(enabled = true), ProviderSetting.LlamaCppLocal(enabled = true))) {
            assertThrows(ModelRoutingException::class.java) {
                OnlineFirstRouting.requireTextInference(candidate, e2b, InternetState.ONLINE)
            }
        }
    }
}
