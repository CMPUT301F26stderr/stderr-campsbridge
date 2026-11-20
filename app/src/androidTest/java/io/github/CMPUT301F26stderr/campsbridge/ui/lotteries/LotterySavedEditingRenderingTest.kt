/*
 * Captures saved lottery editors at normal and enlarged text and control scales.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryBasicsStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWindowStep
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.steps.LotteryWinnersStep
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Checks reachable fields, feedback and footers in the production saved-editor screens. */
class LotterySavedEditingRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val title = "May weekends beside the river and the mountain trail"
    private val a =
        previewCampsite().let {
            it.copy(id = "A", details = it.details.copy(name = "Riverside family campsite beside the mountain trail"))
        }
    private val b =
        a.copy(
            id = "B",
            details =
                a.details.copy(
                    name = "Coastal family campsite overlooking the sheltered bay",
                    timeZone = "America/Vancouver",
                ),
        )
    private val day = LocalDate.of(2027, 5, 14)

    /** Captures the declared valid, invalid, frozen and conflict layouts at both supported scales. */
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun savedEditorContentAndFootersRemainReachable() {
        val scale = mutableStateOf(1f)
        val page = mutableStateOf("details")
        val variant = mutableStateOf("normal")
        val section = mutableStateOf<LotteryEditSection?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.value)) {
                Box(Modifier.width(360.dp).height(844.dp).testTag("saved-editor-preview")) {
                    key(scale.value, page.value, variant.value, section.value) {
                        CampsbridgeTheme(AccountSettings(controlScale = scale.value.toDouble())) {
                            if (page.value == "details") {
                                val current = detailsState(variant.value)
                                val activeSection = section.value
                                if (activeSection == null) {
                                    LotteryDetailsEditScreen(current, {}, {}, {}, { section.value = it })
                                } else {
                                    LotteryDetailsFieldsScreen(activeSection, current, { section.value = null }, {}) {
                                        when (activeSection) {
                                            LotteryEditSection.BASICS -> {
                                                LotteryBasicsStep(current.draft, current.errors.titleRequired, {}, {})
                                            }

                                            LotteryEditSection.WINNERS -> {
                                                LotteryWinnersStep(
                                                    current.draft.maximumWinnersText,
                                                    current.draft.waitlistCapacityText,
                                                    current.errors.winnerCount,
                                                    current.errors.capacity,
                                                    {},
                                                    {},
                                                )
                                            }

                                            LotteryEditSection.REGISTRATION, LotteryEditSection.BOOKING -> {
                                                val registration = activeSection == LotteryEditSection.REGISTRATION
                                                LotteryWindowStep(
                                                    current.draft.schedule,
                                                    current.errors.schedule,
                                                    if (registration) {
                                                        LotterySchedulePoint.REGISTRATION_OPEN
                                                    } else {
                                                        LotterySchedulePoint.BOOKING_OPEN
                                                    },
                                                    if (registration) {
                                                        LotterySchedulePoint.REGISTRATION_CLOSE
                                                    } else {
                                                        LotterySchedulePoint.BOOKING_CLOSE
                                                    },
                                                    YearMonth.of(2027, 5),
                                                    { _, _ -> },
                                                    { _, _ -> },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                LotteryAllocationsEditScreen(
                                    allocationsState(variant.value),
                                    false,
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                )
                            }
                        }
                    }
                }
            }
        }
        val directory =
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "lottery152")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (font in listOf(1f, 2f)) {
            for (editor in listOf("details", "allocations")) {
                for (mode in listOf("normal", "invalid", if (editor == "details") "frozen" else "locked", "conflict")) {
                    compose.runOnIdle {
                        scale.value = font
                        page.value = editor
                        variant.value = mode
                        section.value = null
                    }
                    val footer = compose.onNodeWithText(if (editor == "details") "Save details" else "Save campsites")
                    footer.assertIsDisplayed()
                    if (editor == "allocations" && (mode == "invalid" || mode == "locked")) footer.assertIsNotEnabled()
                    if (editor == "details") {
                        when (mode) {
                            "normal" -> {
                                compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                                compose.onNodeWithText("Title & description").performScrollTo().performClick()
                                compose.onNodeWithText("Lottery title").performScrollTo().assertIsDisplayed()
                                compose.onNodeWithText(title).assertIsDisplayed()
                                compose.runOnIdle { section.value = null }
                                compose.onNodeWithText("Winners & waitlist capacity").performScrollTo().performClick()
                                compose
                                    .onNodeWithText(
                                        "Registration limit (optional)",
                                    ).performScrollTo()
                                    .assertIsDisplayed()
                                compose.onNodeWithText(Long.MAX_VALUE.toString()).performScrollTo().assertIsDisplayed()
                            }

                            "invalid" -> {
                                compose.onNodeWithText("Winners & waitlist capacity").performScrollTo().performClick()
                                compose
                                    .onNodeWithText(
                                        "Registration limit (optional)",
                                    ).performScrollTo()
                                    .assertIsDisplayed()
                                compose
                                    .onNodeWithText(
                                        "Enter a positive whole number, or leave blank for unlimited registrations.",
                                    ).performScrollTo()
                                    .assertIsDisplayed()
                            }

                            "frozen" -> {
                                compose
                                    .onNodeWithText(
                                        "Registration has started. You can still edit the title and description.",
                                    ).performScrollTo()
                                    .assertIsDisplayed()
                            }

                            "conflict" -> {
                                compose.onNodeWithText("Review changes").performScrollTo().assertIsDisplayed()
                            }
                        }
                    } else {
                        when (mode) {
                            "normal" -> {
                                compose
                                    .onAllNodesWithText(
                                        a.details.name,
                                    ).onFirst()
                                    .performScrollTo()
                                    .assertIsDisplayed()
                                compose
                                    .onAllNodesWithText(
                                        b.details.name,
                                    ).onFirst()
                                    .performScrollTo()
                                    .assertIsDisplayed()
                            }

                            "invalid" -> {
                                compose
                                    .onNodeWithText(
                                        "This campsite is unavailable. Remove it from your selection to save.",
                                    ).performScrollTo()
                                    .assertIsDisplayed()
                            }

                            "locked" -> {
                                compose
                                    .onNodeWithText("These campsite allocations can no longer be changed.")
                                    .performScrollTo()
                                    .assertIsDisplayed()
                            }

                            "conflict" -> {
                                compose.onNodeWithText("Currently saved").performScrollTo().assertIsDisplayed()
                                compose.onNodeWithText("Your changes").performScrollTo().assertIsDisplayed()
                                compose.onNodeWithText("Review changes").performScrollTo().assertIsDisplayed()
                            }
                        }
                    }
                    val bitmap = compose.onNodeWithTag("saved-editor-preview").captureToImage().asAndroidBitmap()
                    File(directory, "$editor-$mode-${font.toInt()}x.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                }
            }
        }
    }

    /** Supplies existing preview content with the fixed raw-input and correction states.
     * @param variant Declared normal, invalid, frozen or conflict layout.
     */
    private fun detailsState(variant: String): LotteryDetailsEditUiState.Content {
        val preview = LotteryDetailsEditPreviewProvider().values.first() as LotteryDetailsEditUiState.Content
        val saved = savedLottery()
        return preview.copy(
            lottery = saved,
            draft =
                LotteryDraft(
                    id = saved.id,
                    title = title,
                    description = "Quiet riverside camping with room for families.\nUse the east gate after dusk.",
                    maximumWinnersText = "2",
                    waitlistCapacityText = if (variant == "invalid") "0" else Long.MAX_VALUE.toString(),
                    schedule =
                        LotterySchedulePoint.entries
                            .mapIndexed { index, point ->
                                point to LotteryLocalDateTime(LocalDate.of(2027, 5, 5 + index), LocalTime.of(9, 0))
                            }.toMap(),
                ),
            errors =
                LotterySetupErrors(
                    capacity =
                        if (variant ==
                            "invalid"
                        ) {
                            PositiveCountInput.Error.INVALID_WHOLE_NUMBER
                        } else {
                            null
                        },
                ),
            configurationEditable = variant != "frozen",
            isDirty = true,
            contextChanged = variant == "conflict",
        )
    }

    /** Supplies two applied owned allocations and their declared correction states.
     * @param variant Declared normal, invalid, locked or conflict layout.
     */
    private fun allocationsState(variant: String): LotteryAllocationsEditUiState.Content {
        val preview = LotteryAllocationsEditPreviewProvider().values.first() as LotteryAllocationsEditUiState.Content
        return preview.copy(
            lottery = savedLottery(),
            offers =
                listOf(
                    LotteryOffer(a.id, listOf(day, day.plusDays(1))),
                    LotteryOffer(b.id, listOf(day.plusDays(2), day.plusDays(3))),
                ),
            savedOffers = listOf(LotteryOffer(a.id, listOf(day))),
            campsites = listOf(a, if (variant == "invalid") b.copy(publishedAt = null) else b),
            isEditable = variant != "locked",
            isDirty = true,
            contextChanged = variant == "conflict",
            canSave = variant != "invalid" && variant != "locked",
        )
    }

    /** Builds minute-aligned saved facts for static configuration and conflict summaries. */
    private fun savedLottery(): Lottery =
        Lottery(
            id = "saved-preview",
            ownerId = a.ownerId,
            title = "Saved May camping",
            description = "Saved directions for the campsite entrance.",
            configuration =
                LotteryConfiguration(
                    Instant.parse("2027-05-05T15:00:00Z"),
                    Instant.parse("2027-05-06T15:00:00Z"),
                    Instant.parse("2027-05-07T15:00:00Z"),
                    Instant.parse("2027-05-08T15:00:00Z"),
                    2,
                    40,
                ),
            waitingCount = 0,
            createdAt = Instant.parse("2027-05-01T12:00:00Z"),
            publishedAt = Instant.parse("2027-05-01T13:00:00Z"),
        )
}
