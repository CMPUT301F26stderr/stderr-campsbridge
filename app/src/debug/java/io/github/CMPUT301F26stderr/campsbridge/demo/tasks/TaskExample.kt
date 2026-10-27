package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.annotation.StringRes
import io.github.CMPUT301F26stderr.campsbridge.R

/** Stable handoff identities; scaffold readiness is separate from helper implementation.
 * @param id Planning ID shared with the Wiki.
 * @param title Localized task label.
 * @param baseline Baseline required before issuing this exercise.
 * @param scaffoldReady Whether this example has an implemented feature owner.
 * @param instructions Local steps and expected fixture behavior.
 */
enum class TaskExample(
    val id: String,
    @StringRes val title: Int,
    val baseline: String,
    val scaffoldReady: Boolean,
    @StringRes val instructions: Int,
) {
    DISPLAY_NAME("H01", R.string.task_name, "B1", true, R.string.task_name_steps),
    CONTACT("H02", R.string.task_contact, "B1", true, R.string.task_contact_steps),
    AVAILABILITY("H03", R.string.task_availability, "B2", true, R.string.task_availability_steps),
    CAMPSITE_ORDER("H04", R.string.task_site_order, "B2", true, R.string.task_site_order_steps),
    TEXT_MATCH("H05", R.string.task_search, "B3", true, R.string.task_search_steps),
    GUEST_MATCH("H06", R.string.task_guests, "B3", true, R.string.task_guests_steps),
    LOTTERY_WINDOWS("H07", R.string.task_windows, "B4", false, R.string.task_windows_steps),
    PARTICIPANTS("H08", R.string.task_participants, "B4", false, R.string.task_participants_steps),
    INBOX_ORDER("H09", R.string.task_inbox_order, "B5", false, R.string.task_inbox_steps),
    UNREAD_COUNT("H10", R.string.task_unread, "B5", false, R.string.task_unread_steps),
    BOOKING_NIGHTS("H11", R.string.task_booking, "B6", false, R.string.task_booking_steps),
    RESERVATION_CATEGORY("H12", R.string.task_category, "B7", false, R.string.task_category_steps),
    RESERVATION_ORDER("H13", R.string.task_stay_order, "B7", false, R.string.task_stay_order_steps),
    PROFILE_ROLE("H15", R.string.task_role, "B9", false, R.string.task_future),
    PROFILE_TEXT("H16", R.string.task_profile_text, "B9", false, R.string.task_future),
    EVENT_TYPE("H17", R.string.task_event_type, "B9", false, R.string.task_future),
    EVENT_TEXT("H18", R.string.task_event_text, "B9", false, R.string.task_future),
    EVENT_ORDER("H19", R.string.task_event_order, "B9", false, R.string.task_future),
    IMAGE_TEXT("H20", R.string.task_image_text, "B9", false, R.string.task_future),
    IMAGE_ORDER("H21", R.string.task_image_order, "B9", false, R.string.task_future),
    AUDIENCE_IDS("H22", R.string.task_audience_ids, "B9", false, R.string.task_future),
    RECIPIENT_FACTS("H23", R.string.task_recipient, "B9", false, R.string.task_future),
    AUDIENCE_ORDER("H24", R.string.task_audience_order, "B9", false, R.string.task_future),
}
