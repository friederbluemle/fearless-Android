package jp.co.soramitsu.wallet.impl.presentation.transaction.history

import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi.State

fun TransferHistorySheet.showState(state: State) {
    when (state) {
        is State.Empty -> showPlaceholder(state.message)
        is State.EmptyProgress -> showProgress()
        is State.Data -> showTransactions(state.items)
    }
}
