package jp.co.soramitsu.wallet.impl.data.network.model.request

class TransactionHistoryRequest(
    val address: String,
    val row: Int,
    val page: Int
)
