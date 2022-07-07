package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.flow.flowOf

class ParachainStakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow() = flowOf(
        listOf(
            StoryGroup.Staking(
                titleRes = R.string.parachain_staking_story_collator_title,
                iconSymbol = "\uD83D\uDC8E",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_collator_title,
                        R.string.parachain_staking_story_collator_page_1
                    ),
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_collator_title,
                        R.string.parachain_staking_story_collator_page_2
                    )
                )
            ),
            StoryGroup.Staking(
                titleRes = R.string.parachain_staking_story_delegator_title,
                iconSymbol = "⛏",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_delegator_title,
                        R.string.parachain_staking_story_delegator_page_1
                    ),
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_delegator_title,
                        R.string.parachain_staking_story_delegator_page_2
                    )
                )
            ),
            StoryGroup.Staking(
                titleRes = R.string.parachain_staking_story_rewards_title,
                iconSymbol = "\uD83C\uDF81",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_rewards_title,
                        R.string.parachain_staking_story_rewards_page_1
                    ),
                    StoryGroup.Story.Staking(
                        R.string.parachain_staking_story_rewards_title,
                        R.string.parachain_staking_story_rewards_page_2
                    )
                )
            )
        )
    )
}
