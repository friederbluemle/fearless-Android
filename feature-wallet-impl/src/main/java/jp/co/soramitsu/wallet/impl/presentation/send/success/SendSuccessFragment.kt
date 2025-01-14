package jp.co.soramitsu.wallet.impl.presentation.send.success

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class SendSuccessFragment : BaseComposeBottomSheetDialogFragment<SendSuccessViewModel>() {

    companion object {
        const val KEY_OPERATION_HASH = "KEY_OPERATION_HASH"
        const val KEY_CHAIN_ID = "KEY_CHAIN_ID"

        fun getBundle(operationHash: String?, chainId: ChainId) = bundleOf(
            KEY_OPERATION_HASH to operationHash,
            KEY_CHAIN_ID to chainId
        )
    }

    override val viewModel: SendSuccessViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        SendSuccessContent(
            state = state,
            callback = viewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.showHashActions.observeEvent {
            showExternalTransactionActions()
        }
    }

    private fun showExternalTransactionActions() = viewModel.operationHash?.let { hash ->
        showExternalActionsSheet(
            copyLabelRes = R.string.transaction_details_copy_hash,
            value = hash,
            explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EXTRINSIC, hash),
            externalViewCallback = viewModel::openUrl
        )
    }

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        explorers: Map<Chain.Explorer.Type, String>,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                chainId = viewModel.chainId,
                explorers = explorers
            )
        )

        ExternalActionsSheet(
            context = requireContext(),
            payload = payload,
            onCopy = viewModel::copyStringClicked,
            onViewExternal = externalViewCallback
        )
            .show()
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
