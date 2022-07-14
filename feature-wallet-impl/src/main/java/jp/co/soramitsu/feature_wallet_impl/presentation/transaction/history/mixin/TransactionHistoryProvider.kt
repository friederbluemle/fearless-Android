package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.daysFromMillis
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.hash
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToOperationModel
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationToParcel
import jp.co.soramitsu.feature_wallet_impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.HistoryFiltersProvider
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionStateMachine.Action
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.mixin.TransactionStateMachine.State
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICON_SIZE_DP = 32

class TransactionHistoryProvider(
    private val walletInteractor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    private val historyFiltersProvider: HistoryFiltersProvider,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val chainId: ChainId,
    private val assetId: String,
    private val localCursorStorage: TransferCursorStorage,
) : TransactionHistoryMixin, CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val domainState = MutableStateFlow<State>(
        State.EmptyProgress(filters = historyFiltersProvider.currentFilters())
    )

    override val state = domainState.map(::mapOperationHistoryStateToUi)
        .inBackground()
        .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

    init {
        historyFiltersProvider.filtersFlow()
            .distinctUntilChanged()
            .onEach {
                performTransition(Action.FiltersChanged(it))
            }
            .launchIn(this)
    }

    override fun scrolled(currentIndex: Int) {
        launch {
            performTransition(Action.Scrolled(currentIndex))
        }
    }

    override fun transactionClicked(transactionModel: OperationModel) {
        launch {
            val operations = (domainState.first() as? State.WithData)?.data ?: return@launch

            val clickedOperation = operations.firstOrNull { it.id == transactionModel.id } ?: return@launch

            withContext(Dispatchers.Main) {
                when (val operation = mapOperationToParcel(clickedOperation, resourceManager)) {
                    is OperationParcelizeModel.Transfer -> {
                        router.openTransferDetail(operation, AssetPayload(chainId, assetId))
                    }

                    is OperationParcelizeModel.Extrinsic -> {
                        router.openExtrinsicDetail(ExtrinsicDetailsPayload(operation, chainId))
                    }

                    is OperationParcelizeModel.Reward -> {
                        router.openRewardDetail(RewardDetailsPayload(operation, chainId))
                    }
                }
            }
        }
    }

    private suspend fun performTransition(action: Action) = withContext(Dispatchers.Default) {
        val newState = TransactionStateMachine.transition(action, domainState.value) { sideEffect ->
            when (sideEffect) {
                is TransactionStateMachine.SideEffect.ErrorEvent -> {
                    // ignore errors here, they are bypassed to client of mixin
                }
                is TransactionStateMachine.SideEffect.LoadPage -> loadNewPage(sideEffect)
            }
        }

        domainState.value = newState
    }

    private fun loadNewPage(sideEffect: TransactionStateMachine.SideEffect.LoadPage) {
        launch {
            walletInteractor.getOperations(chainId, assetId, sideEffect.curPageNumber, sideEffect.filters)
                .onFailure {
                    performTransition(Action.PageError(error = it))
                }.onSuccess {
                    localCursorStorage.removeOperations(it.items.mapNotNull { i -> i.type.hash })
                    performTransition(Action.NewPage(newPage = it, loadedWith = sideEffect.filters))
                }
        }
    }

    private suspend fun mapOperationHistoryStateToUi(state: State): TransactionHistoryUi.State {
        return when (state) {
            is State.Empty -> TransactionHistoryUi.State.Empty(state.message)
            is State.EmptyProgress -> TransactionHistoryUi.State.EmptyProgress
            is State.Data -> TransactionHistoryUi.State.Data(transformData(state.data))
            is State.FullData -> TransactionHistoryUi.State.Data(transformData(state.data))
            is State.NewPageProgress -> TransactionHistoryUi.State.Data(transformData(state.data))
        }
    }

    private suspend fun transformData(data: List<Operation>): List<Any> {
        val chain = walletInteractor.getChain(chainId)
        val localOperations = walletInteractor.getSelectedMetaAccount().accountId(chain)?.let {
            val address = chain.addressOf(it)
            localCursorStorage.getOperations(chain.name, address)
        }.orEmpty()
        val accountIdentifier = addressDisplayUseCase.createIdentifier()
        val localData = localOperations + data

        val operations = localData.map {
            mapOperationToOperationModel(it, accountIdentifier, resourceManager, iconGenerator)
        }

        return regroup(operations)
    }

    private fun regroup(operations: List<OperationModel>): List<Any> {

        return operations.groupBy { it.time.daysFromMillis() }
            .map { (daysSinceEpoch, transactions) ->
                val header = DayHeader(daysSinceEpoch)

                listOf(header) + transactions
            }.flatten()
    }
}
