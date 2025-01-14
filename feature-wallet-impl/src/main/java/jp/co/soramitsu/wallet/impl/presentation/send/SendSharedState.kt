package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SendSharedState {
    private val _chainIdFlow = MutableStateFlow<ChainId?>(null)
    private val _assetIdFlow = MutableStateFlow<String?>(null)

    val chainIdFlow: Flow<ChainId?> = _chainIdFlow
    val assetIdFlow: Flow<String?> = _assetIdFlow

    val chainId: ChainId?
        get() = _chainIdFlow.value
    val assetId: String?
        get() = _assetIdFlow.value

    fun update(chainId: ChainId, assetId: String) {
        _chainIdFlow.value = chainId
        _assetIdFlow.value = assetId
    }
}
