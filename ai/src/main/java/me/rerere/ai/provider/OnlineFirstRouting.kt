package me.rerere.ai.provider

/** Network evidence, independent of the result of any particular provider request. */
enum class InternetState { ONLINE, OFFLINE, UNVERIFIED }

internal fun classifyInternetEvidence(
    hasActiveNetwork: Boolean,
    hasInternetCapability: Boolean?,
    validated: Boolean?,
): InternetState = when {
    !hasActiveNetwork -> InternetState.OFFLINE
    hasInternetCapability == false -> InternetState.OFFLINE
    hasInternetCapability == true && validated == true -> InternetState.ONLINE
    else -> InternetState.UNVERIFIED
}

class ModelRoutingException(message: String) : IllegalStateException(message)

/** Mobile-agent policy: provider errors must never be interpreted as evidence of being offline. */
object OnlineFirstRouting {
    const val OFFLINE_MODEL_FILE = "gemma-4-E2B-it.litertlm"

    fun isOnDevice(setting: ProviderSetting): Boolean = when (setting) {
        is ProviderSetting.AICore,
        is ProviderSetting.LiteRtLocal,
        is ProviderSetting.LlamaCppLocal -> true
        else -> false
    }

    fun selectModel(
        selected: Model?,
        providers: List<ProviderSetting>,
        internet: InternetState,
    ): Model {
        if (internet == InternetState.UNVERIFIED) {
            throw ModelRoutingException("網路狀態尚未確認，請完成網路連線後重試；不會自行啟用本地模型。")
        }
        if (internet == InternetState.OFFLINE) {
            return providers.filterIsInstance<ProviderSetting.LiteRtLocal>()
                .filter { it.enabled }
                .flatMap { it.models }
                .firstOrNull {
                    it.modelId == OFFLINE_MODEL_FILE && it.providerOverwrite == null
                }
                ?: throw ModelRoutingException("目前沒有網路，且離線 Gemma 4 E2B 尚未設定。請先下載模型並啟用 Local · LiteRT。")
        }
        val model = selected
            ?: throw ModelRoutingException("請先在設定中選擇線上模型並完成登入。")
        val owner = providers.firstOrNull { p -> p.models.any { it.id == model.id } }
            ?: throw ModelRoutingException("所選模型沒有對應的 Provider，請重新設定。")
        if (!owner.enabled) {
            throw ModelRoutingException("所選 Provider 已停用，請先啟用或選擇另一個線上模型。")
        }
        requireTextInference(model.providerOverwrite ?: owner, model, internet)
        return model
    }

    /** Called immediately before inference, including retries and auxiliary text requests. */
    fun requireTextInference(setting: ProviderSetting, model: Model, internet: InternetState) {
        if (!setting.enabled) throw ModelRoutingException("所選 Provider 已停用。")
        when (internet) {
            InternetState.UNVERIFIED -> throw ModelRoutingException("網路狀態尚未確認，請稍後重試。")
            InternetState.ONLINE -> if (isOnDevice(setting)) {
                throw ModelRoutingException("目前有網路，請選擇線上模型；本地模型只在斷網時啟用。")
            }
            InternetState.OFFLINE -> if (
                setting !is ProviderSetting.LiteRtLocal || model.modelId != OFFLINE_MODEL_FILE
            ) {
                throw ModelRoutingException("目前沒有網路，只允許使用手機上的 Gemma 4 E2B；不會呼叫線上 Provider。")
            }
        }
    }
}
