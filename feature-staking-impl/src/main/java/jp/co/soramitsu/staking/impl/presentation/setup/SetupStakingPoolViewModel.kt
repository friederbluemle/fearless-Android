package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AccountInfoViewState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.common.validation.InsufficientBalanceException
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.setup.compose.SetupStakingScreenViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SetupStakingPoolViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingInteractor: StakingInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel() {

    val chain: Chain
    val asset: Asset

    init {
        val setupState = requireNotNull(stakingPoolSharedStateProvider.mainState.get())
        chain = requireNotNull(setupState.chain)
        asset = requireNotNull(setupState.asset)
    }

    private val toolbarViewState = ToolbarViewState(resourceManager.getString(R.string.pool_staking_join_title), R.drawable.ic_arrow_back_24dp)

    private val accountInfoViewStateFlow: Flow<AccountInfoViewState> = flow {
        val meta = stakingInteractor.getCurrentMetaAccount()
        val address = meta.address(chain) ?: ""
        val icon = iconGenerator.createAddressIcon(
            chain.isEthereumBased,
            address,
            AddressIconGenerator.SIZE_BIG
        )
        val state = AccountInfoViewState(
            accountName = meta.name,
            address = address,
            image = icon,
            caption = resourceManager.getString(R.string.pool_staking_join_account_title)
        )
        emit(state)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAccountInfoState)

    private val enteredAmountFlow = MutableStateFlow("10")

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { enteredAmount ->
        val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration.symbol)
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = enteredAmount
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val inPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = stakingPoolInteractor.estimateJoinFee(inPlanks)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val amountInPlanks = asset.token.planksFromAmount(amount)
        ButtonViewState(
            resourceManager.getString(R.string.pool_staking_join_button_title),
            amountInPlanks != BigInteger.ZERO
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val viewState = combine(
        accountInfoViewStateFlow,
        amountInputViewState,
        feeInfoViewStateFlow,
        buttonStateFlow
    ) { accountInfoViewState, amountInputViewState, feeViewState, buttonState ->
        SetupStakingScreenViewState(
            toolbarViewState,
            accountInfoViewState,
            amountInputViewState,
            feeViewState,
            buttonState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    fun onNavigationClick() {
        router.back()
    }

    fun onAmountEntered(amount: String) {
        enteredAmountFlow.value = amount.replace(',', '.')
    }

    fun onNextClick() {
        val setupFlow = requireNotNull(stakingPoolSharedStateProvider.setupState.get())
        val amount = enteredAmountFlow.value.toBigDecimalOrNull().orZero()

        isValid(amount).fold({
            stakingPoolSharedStateProvider.setupState.set(setupFlow.copy(amount = amount))
            router.openSelectPool()
        }, {
            showError(it)
        })
    }

    private fun isValid(amount: BigDecimal): Result<Any> {
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val transferableInPlanks = asset.token.planksFromAmount(asset.transferable)

        return when {
            amountInPlanks >= transferableInPlanks -> Result.failure(InsufficientBalanceException(resourceManager))
            else -> Result.success(Unit)
        }
    }

    private val defaultState
        get() = SetupStakingScreenViewState(
            toolbarViewState,
            defaultAccountInfoState,
            defaultAmountInputState,
            FeeInfoViewState.default,
            defaultButtonState
        )

    private val defaultAmountInputState
        get() = AmountInputViewState(
            tokenName = "...",
            tokenImage = "",
            totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
            fiatAmount = "",
            tokenAmount = "10"
        )

    private val defaultAccountInfoState
        get() = AccountInfoViewState(
            accountName = "...",
            address = "",
            image = R.drawable.ic_wallet,
            caption = resourceManager.getString(R.string.pool_staking_join_account_title)
        )

    private val defaultButtonState
        get() = ButtonViewState(
            resourceManager.getString(R.string.pool_staking_join_button_title),
            true
        )
}
