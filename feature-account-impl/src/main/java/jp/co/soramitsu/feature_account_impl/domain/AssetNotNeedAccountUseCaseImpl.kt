package jp.co.soramitsu.feature_account_impl.domain

import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AssetNotNeedAccountUseCaseImpl(
    private val assetDao: AssetDao
) : AssetNotNeedAccountUseCase {

    override suspend fun markNotNeed(chainId: ChainId, metaId: Long, symbol: String) {
        updateAssetNotNeed(metaId, chainId, symbol)
    }

    private suspend fun updateAssetNotNeed(
        metaId: Long,
        chainId: ChainId,
        symbol: String
    ) {
        val cached = assetDao.getAsset(metaId, emptyAccountIdValue, chainId, symbol)?.asset
        if (cached == null) {
            val initial = AssetLocal.createEmpty(emptyAccountIdValue, symbol, chainId, metaId)
            val newAsset = initial.copy(markedNotNeed = true)
            assetDao.insertAsset(newAsset)
        } else {
            val updatedAsset = cached.copy(markedNotNeed = true)
            assetDao.updateAsset(updatedAsset)
        }
    }

    override fun getAssetsMarkedNotNeedFlow(metaId: Long): Flow<List<AssetKey>> {
        return assetDao.observeAssets(metaId).map {
            it.filter { it.asset.markedNotNeed }.map {
                AssetKey(
                    metaId = metaId,
                    chainId = it.asset.chainId,
                    accountId = it.asset.accountId,
                    it.token.symbol
                )
            }
        }
    }
}