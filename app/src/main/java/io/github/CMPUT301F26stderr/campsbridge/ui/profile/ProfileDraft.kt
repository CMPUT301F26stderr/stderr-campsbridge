package io.github.CMPUT301F26stderr.campsbridge.ui.profile

/** Unsaved personal input, separate from the last confirmed account.
 * @param nameText Exact editable name, including surrounding whitespace.
 */
data class PersonalDetailsDraft(
    val nameText: String,
)

/** Unsaved optional contacts; parsers decide validity only on explicit submission.
 * @param emailText Exact editable email.
 * @param phoneText Exact editable phone.
 */
data class ContactDetailsDraft(
    val emailText: String,
    val phoneText: String,
)

/** Editor identity used to consume one confirmed completion in its active route. */
enum class ProfileEditor { PERSONAL, CONTACT }

/** Profile-field writes are independent from the shared photo controller's operation. */
enum class ProfileOperation { IDLE, SAVING_NAME, SAVING_CONTACTS, ENABLING_OWNER }
