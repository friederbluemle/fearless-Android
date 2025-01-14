package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItem
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState

data class SelectValidatorsScreenViewState(
    val toolbarTitle: String,
    val isCustom: Boolean,
    val searchQuery: String = "",
    val listState: MultiSelectListItemViewState<String>
)

data class MultiSelectListItemViewState<ItemIdType>(
    val items: List<SelectableListItemState<ItemIdType>>,
    val selectedItems: List<SelectableListItemState<ItemIdType>>
)

interface SelectValidatorsScreenInterface {
    fun onNavigationClick()
    fun onSelected(item: SelectableListItemState<String>)
    fun onInfoClick(item: SelectableListItemState<String>)
    fun onChooseClick()
    fun onOptionsClick()
    fun onSearchQueryInput(query: String)
}

@Composable
fun SelectValidatorsScreen(
    state: SelectValidatorsScreenViewState,
    callbacks: SelectValidatorsScreenInterface
) {
    BottomSheetScreen {
        val toolbarOptions = if (state.isCustom) {
            listOf(
                MenuIconItem(
                    R.drawable.ic_dots_horizontal_24,
                    onClick = callbacks::onOptionsClick
                )
            )
        } else {
            emptyList()
        }
        Toolbar(
            state = ToolbarViewState(
                title = state.toolbarTitle,
                navigationIcon = R.drawable.ic_arrow_back_24dp,
                menuItems = toolbarOptions
            ),
            onNavigationClick = callbacks::onNavigationClick
        )
        MarginVertical(margin = 8.dp)
        if (state.isCustom) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                CorneredInput(state = state.searchQuery, onInput = callbacks::onSearchQueryInput)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            val selectedIds = state.listState.selectedItems.map { it.id }
            val items = state.listState.items.map { it.copy(isSelected = it.id in selectedIds) }
            items(items = items) { pool ->
                SelectableListItem(
                    state = pool,
                    onSelected = callbacks::onSelected,
                    onInfoClick = { callbacks.onInfoClick(pool) }
                )
            }
        }
        AccentButton(
            text = stringResource(id = R.string.pool_staking_choosepool_button_title),
            onClick = callbacks::onChooseClick,
            enabled = state.listState.selectedItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        )
        MarginVertical(margin = 16.dp)
    }
}

@Preview
@Composable
private fun SelectValidatorsScreenPreview() {
    val stakedText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("${stringResource(R.string.pool_staking_choosepool_staked_title)} ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append("20k KSM")
        }
    }
    val subtitle = stringResource(R.string.pool_staking_choosepool_members_count_title, 15)

    val items = listOf(
        SelectableListItemState(
            id = "1",
            title = "Polkadot js plus",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = true
        ),
        SelectableListItemState(
            id = "2",
            title = "POOL NUMBER ONE",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = false,
            additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
        )
    )
    val state = MultiSelectListItemViewState(
        items = items,
        selectedItems = listOf(items.first())
    )
    val callbacks = object : SelectValidatorsScreenInterface {
        override fun onNavigationClick() = Unit
        override fun onSelected(item: SelectableListItemState<String>) = Unit
        override fun onInfoClick(item: SelectableListItemState<String>) = Unit
        override fun onChooseClick() = Unit
        override fun onOptionsClick() = Unit
        override fun onSearchQueryInput(query: String) = Unit
    }

    FearlessTheme {
        Column {
            SelectValidatorsScreen(
                state = SelectValidatorsScreenViewState(
                    "Select suggested",
                    true,
                    listState = state
                ),
                callbacks
            )
        }
    }
}
