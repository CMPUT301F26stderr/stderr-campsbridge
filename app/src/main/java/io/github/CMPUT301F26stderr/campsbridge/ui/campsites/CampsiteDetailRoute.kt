package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId

/** Connects the current saved summary to its own graph-scoped read owner.
 * @param model Management graph owner, never borrowed from another context.
 * @param onAvailability Opens the independent saved inventory editor.
 * @param onResume Opens the registered setup parent for an unpublished draft.
 * @param onBack Returns to the caller's retained primary destination.
 * @param section Actual registered read-only child.
 * @param onOpenSection Opens a child within this saved-reader graph.
 * @param onOpenPolicy Opens the selected exact packaged policy.
 * @param onPreview Opens the separate owner preview graph.
 * @param onCreateLottery Opens new single-site lottery setup for a published owned listing.
 * @param onViewLotteries Opens the real owner list scoped to this listing's allocations.
 */
@Composable
fun CampsiteDetailRoute(
    model: CampsiteDetailViewModel,
    onResume: (() -> Unit)? = null,
    onAvailability: (() -> Unit)? = null,
    section: CampsiteDetailSection = CampsiteDetailSection.OVERVIEW,
    onOpenSection: (CampsiteDetailSection) -> Unit = {},
    onOpenPolicy: (PolicyId) -> Unit = {},
    onPreview: (() -> Unit)? = null,
    onCreateLottery: (() -> Unit)? = null,
    onViewLotteries: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val photo by model.photoState.collectAsStateWithLifecycle()
    if (model.context != CampsiteDetailContext.MANAGE) {
        LaunchedEffect(model, section) { model.selectSection(section) }
        val owner by model.ownerState.collectAsStateWithLifecycle()
        val rating by model.ratingState.collectAsStateWithLifecycle()
        val month by model.month.collectAsStateWithLifecycle()
        val inventory by model.inventory.collectAsStateWithLifecycle()
        when (section) {
            CampsiteDetailSection.OVERVIEW -> {
                CampsiteDetailScreen(
                    state,
                    photo,
                    onBack,
                    model::retry,
                    model::retryPhoto,
                    rating = rating,
                    context = model.context,
                    onRetryRatings = model::retryRatings,
                    onOpenSection = onOpenSection,
                )
            }

            CampsiteDetailSection.ABOUT -> {
                CampsiteAboutScreen(
                    state,
                    owner,
                    onBack,
                    model::retry,
                    model::retryOwner,
                )
            }

            CampsiteDetailSection.MAP -> {
                CampsiteMapScreen(state, onBack, model::retry)
            }

            CampsiteDetailSection.AVAILABILITY -> {
                CampsiteAvailabilityScreen(
                    state,
                    month,
                    inventory,
                    onBack,
                    model::retry,
                    model::retryInventory,
                    model::changeMonth,
                )
            }

            CampsiteDetailSection.RULES -> {
                CampsiteRulesScreen(
                    state,
                    onBack,
                    model::retry,
                    onOpenPolicy,
                )
            }
        }
        return
    }
    CampsiteManagementScreen(
        state,
        photo,
        onBack,
        model::retry,
        model::retryPhoto,
        onResume = onResume,
        onAvailability = onAvailability,
        onPreview = onPreview,
        onCreateLottery = onCreateLottery,
        onViewLotteries = onViewLotteries,
    )
}
