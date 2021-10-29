package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

class MoonbeamContributeViewState(
    private val interactor: MoonbeamContributeInteractor,
    val customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    coroutineScope: CoroutineScope,
) : CustomContributeViewState {

    val title = customContributePayload.parachainMetadata.run {
        "$name ($token)"
    }

    init {
        interactor.nextStep(customContributePayload)
    }

    val privacyAcceptedFlow = MutableStateFlow(customContributePayload.isPrivacyAccepted ?: false)

    suspend fun getSystemRemarkFee(): BigInteger {
        return interactor.getSystemRemarkFee(customContributePayload.parachainMetadata.flow!!.data.apiKey)
    }

    suspend fun doSystemRemark(): Boolean {
        return interactor.doSystemRemark()
    }

    suspend fun termsText(): String =
        interactor.getTerms()

    override val applyActionState = when (customContributePayload.step) {
        0 -> privacyAcceptedFlow.map { privacyAccepted ->
            when {
                privacyAccepted -> ApplyActionState.Available
                else -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
            }
        }
        else -> flow {
            ApplyActionState.Available
        }
    }

    override suspend fun generatePayload(): Result<BonusPayload> = runCatching {
        val payload = createBonusPayload()
        payload
    }

    private fun createBonusPayload(): ReferralCodePayload {
        return MoonbeamBonusPayload("", customContributePayload.parachainMetadata.rewardRate)
    }
}
