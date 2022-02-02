package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.PinRequired
import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface AccountRouter : SecureRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openCreatePincode()

    fun openMnemonicScreen(accountName: String)

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openWallets(accountChosenNavDirection: AccountChosenNavDirection)

    fun openNodes(chainId: ChainId)

    fun openLanguages()

    fun openAddAccount()

    fun openAccountDetails(metaAccountId: Long)

    fun openEditAccounts()

    fun backToMainScreen()

    fun openNodeDetails(payload: NodeDetailsPayload)

    fun openAddNode(chainId: ChainId)

    @PinRequired
    fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportSeed(metaId: Long, chainId: ChainId): DelayedNavigation

    @PinRequired
    fun openExportJsonPassword(metaId: Long, chainId: ChainId): DelayedNavigation

    fun openConfirmMnemonicOnExport(mnemonic: List<String>)

    fun openExportJsonConfirm(payload: ExportJsonConfirmPayload)

    fun returnToWallet()

    fun finishExportFlow()

    fun openChangePinCode()
}
