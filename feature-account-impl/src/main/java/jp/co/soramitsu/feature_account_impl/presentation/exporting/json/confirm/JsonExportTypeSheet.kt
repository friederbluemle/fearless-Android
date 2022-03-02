package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.textItem

class JsonExportTypeSheet(
    context: Context,
    private val onExportByText: () -> Unit,
    private val onExportByFile: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.recovery_source_type)

        textItem(titleRes = R.string.json_export_type_text) {
            onExportByText()
        }

        textItem(titleRes = R.string.json_export_type_file) {
            onExportByFile()
        }
    }
}