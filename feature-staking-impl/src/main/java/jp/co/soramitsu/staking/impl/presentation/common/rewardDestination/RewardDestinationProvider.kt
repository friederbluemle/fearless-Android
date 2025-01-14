package jp.co.soramitsu.staking.impl.presentation.common.rewardDestination

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.DAYS_IN_YEAR
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.presentation.mappers.RewardSuffix
import jp.co.soramitsu.staking.impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.math.BigDecimal

class RewardDestinationProvider(
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val appLinksProvider: AppLinksProvider,
    private val sharedState: StakingSharedState,
    private val accountDisplayUseCase: AddressDisplayUseCase
) : RewardDestinationMixin.Presentation {

    override val rewardReturnsLiveData = MutableLiveData<RewardDestinationEstimations>()
    override val showDestinationChooserEvent = MutableLiveData<Event<DynamicListBottomSheet.Payload<AddressModel>>>()

    override val rewardDestinationModelFlow = MutableSharedFlow<RewardDestinationModel>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
        tryEmit(RewardDestinationModel.Restake)
    }

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val initialRewardDestination = MutableSharedFlow<RewardDestinationModel>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val rewardDestinationChangedFlow = initialRewardDestination.combine(rewardDestinationModelFlow) { initial, current ->
        initial != current
    }.onStart { emit(false) }

    override fun payoutClicked(scope: CoroutineScope) {
        scope.launch {
            interactor.getSelectedAccountProjection()?.let { currentAccount ->
                rewardDestinationModelFlow.emit(RewardDestinationModel.Payout(generateDestinationModel(currentAccount)))
            }
        }
    }

    override fun payoutTargetClicked(scope: CoroutineScope) {
        scope.launch {
            val selectedDestination = rewardDestinationModelFlow.first() as? RewardDestinationModel.Payout ?: return@launch
            val accountsInNetwork = accountsInCurrentNetwork()

            showDestinationChooserEvent.value = Event(DynamicListBottomSheet.Payload(accountsInNetwork, selectedDestination.destination))
        }
    }

    override fun payoutDestinationChanged(newDestination: AddressModel, scope: CoroutineScope) {
        scope.launch { rewardDestinationModelFlow.emit(RewardDestinationModel.Payout(newDestination)) }
    }

    override fun learnMoreClicked(scope: CoroutineScope) {
        scope.launch {
            val link = when (sharedState.assetWithChain.first().asset.staking) {
                Chain.Asset.StakingType.PARACHAIN -> appLinksProvider.moonbeamStakingLearnMore
                Chain.Asset.StakingType.RELAYCHAIN -> appLinksProvider.payoutsLearnMore
                Chain.Asset.StakingType.UNSUPPORTED -> ""
            }
            openBrowserEvent.value = Event(link)
        }
    }

    override fun restakeClicked(scope: CoroutineScope) {
        scope.launch {
            rewardDestinationModelFlow.emit(RewardDestinationModel.Restake)
        }
    }

    override suspend fun loadActiveRewardDestination(stashState: StakingState.Stash) {
        val rewardDestination = stakingScenarioInteractor.getRewardDestination(stashState)
        val rewardDestinationModel = mapRewardDestinationToRewardDestinationModel(rewardDestination)

        initialRewardDestination.emit(rewardDestinationModel)
        rewardDestinationModelFlow.emit(rewardDestinationModel)
    }

    override suspend fun updateReturns(rewardCalculator: RewardCalculator, asset: Asset, amount: BigDecimal) {
        val chainId = sharedState.chainId()
        val restakeReturns = rewardCalculator.calculateReturns(amount, DAYS_IN_YEAR, true, chainId)

        val payoutReturns = rewardCalculator.calculateReturns(amount, DAYS_IN_YEAR, false, chainId)

        val restakeEstimations = mapPeriodReturnsToRewardEstimation(restakeReturns, asset.token, resourceManager, RewardSuffix.APY)
        val payoutEstimations = mapPeriodReturnsToRewardEstimation(payoutReturns, asset.token, resourceManager, RewardSuffix.APR)

        rewardReturnsLiveData.value = RewardDestinationEstimations(restakeEstimations, payoutEstimations)
    }

    private suspend fun mapRewardDestinationToRewardDestinationModel(rewardDestination: RewardDestination): RewardDestinationModel {
        return when (rewardDestination) {
            RewardDestination.Restake -> RewardDestinationModel.Restake
            is RewardDestination.Payout -> {
                val chain = sharedState.chain()
                val addressModel = generateDestinationModel(chain.addressOf(rewardDestination.targetAccountId))

                RewardDestinationModel.Payout(addressModel)
            }
        }
    }

    private suspend fun accountsInCurrentNetwork(): List<AddressModel> {
        return interactor.getAccountProjectionsInSelectedChains()
            .map { generateDestinationModel(it) }
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, AddressIconGenerator.SIZE_MEDIUM, account.name)
    }

    private suspend fun generateDestinationModel(address: String): AddressModel {
        return addressIconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_MEDIUM, accountDisplayUseCase(address))
    }
}
