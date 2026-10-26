package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Owns one short onboarding flow; account facts remain in the repository.
 * @param accountId Established session identity, reused for every retry.
 * @param accounts Profile persistence and capability confirmation.
 * @param preferences Account-local completion and workspace preferences.
 * @param savedState Navigation-owned step and unsaved input; never stores repository objects.
 * @param parseName Reserved pure contract, or null while that implementation is pending.
 */
class OnboardingViewModel(
    private val accountId: String,
    private val accounts: AccountRepository,
    private val preferences: LocalAppPreferences,
    private val savedState: SavedStateHandle,
    private val parseName: ((String) -> DisplayNameInput)? = null,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            OnboardingUiState(
                nameValidationPending =
                    parseName == null,
            ),
        )
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var account: Account? = null
    private var loaded = false

    init {
        retryLoad()
    }

    /** Restores confirmed facts and unsaved input; a failed read never becomes a new profile. */
    fun retryLoad() {
        if (operation?.isActive == true || loaded) return
        mutableState.value = mutableState.value.copy(isLoading = true, failed = false)
        operation =
            viewModelScope.launch {
                try {
                    val record = accounts.get(accountId)
                    val local = preferences.observe(accountId).first()
                    account = record
                    val intent =
                        savedState.get<String>("intent")?.let(Role::valueOf)
                            ?: local.workspace?.takeIf { it != Role.ADMIN }
                            ?: if (record != null) Role.ENTRANT else null
                    val branch =
                        if (intent ==
                            Role.OWNER
                        ) {
                            OnboardingStep.OWNER
                        } else {
                            OnboardingStep.INTERESTS
                        }
                    val restoredStep = savedState.get<String>("step")?.let(OnboardingStep::valueOf)
                    val step =
                        when {
                            restoredStep == null -> {
                                if (record ==
                                    null
                                ) {
                                    OnboardingStep.WELCOME
                                } else {
                                    branch
                                }
                            }

                            record == null &&
                                restoredStep in
                                listOf(
                                    OnboardingStep.INTERESTS,
                                    OnboardingStep.OWNER,
                                )
                            -> {
                                OnboardingStep.NAME
                            }

                            else -> {
                                restoredStep
                            }
                        }
                    val draft =
                        OnboardingDraft(
                            name = savedState["name"] ?: record?.name.orEmpty(),
                            intent = intent,
                            preferences =
                                Preferences(
                                    tags =
                                        savedState.get<ArrayList<String>>("tags")?.toSet()
                                            ?: record?.preferences?.tags.orEmpty(),
                                    amenities =
                                        savedState.get<ArrayList<String>>("amenities")?.toSet()
                                            ?: record?.preferences?.amenities.orEmpty(),
                                ),
                        )
                    loaded = true
                    mutableState.value =
                        OnboardingUiState(
                            step,
                            draft,
                            isLoading = false,
                            unavailable = record != null && !record.isActive,
                            nameValidationPending =
                                parseName == null,
                        )
                    retain()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(isLoading = false, failed = true)
                }
            }
    }

    /** Advances the visible step, awaiting required writes before leaving it. */
    fun next() {
        if (!canEdit()) return
        when (state.value.step) {
            OnboardingStep.WELCOME -> {
                moveTo(OnboardingStep.INTENT)
            }

            OnboardingStep.INTENT -> {
                if (state.value.draft.intent !=
                    null
                ) {
                    moveTo(OnboardingStep.NAME)
                }
            }

            OnboardingStep.NAME -> {
                submitName()
            }

            OnboardingStep.INTERESTS -> {
                finish(saveInterests = true)
            }

            OnboardingStep.OWNER -> {
                finish(saveInterests = false)
            }
        }
    }

    /** Returns to the preceding step without discarding fields or replaying writes. */
    fun back() {
        if (!canEdit()) return
        val previous =
            when (state.value.step) {
                OnboardingStep.WELCOME -> return
                OnboardingStep.INTENT -> OnboardingStep.WELCOME
                OnboardingStep.NAME -> OnboardingStep.INTENT
                OnboardingStep.INTERESTS, OnboardingStep.OWNER -> OnboardingStep.NAME
            }
        moveTo(previous)
    }

    /** Selects a public starting intent; this is not an account capability update.
     * @param role Entrant or owner; admin is not a public onboarding choice.
     */
    fun chooseIntent(role: Role) {
        if (!canEdit() || state.value.step != OnboardingStep.INTENT || role == Role.ADMIN) return
        updateDraft(state.value.draft.copy(intent = role))
    }

    /** Updates input without saving a profile.
     * @param value Exact field text, including unsubmitted surrounding whitespace.
     */
    fun editName(value: String) {
        if (!canEdit() || state.value.step != OnboardingStep.NAME) return
        updateDraft(state.value.draft.copy(name = value))
    }

    /** Accepts explicit selections without implementing a generic preference-toggle policy.
     * @param value Caller-assembled interests.
     */
    fun editPreferences(value: Preferences) {
        if (!canEdit() || state.value.step != OnboardingStep.INTERESTS) return
        updateDraft(state.value.draft.copy(preferences = value))
    }

    /** Finishes without writing the optional unsubmitted interest selection. */
    fun skipInterests() {
        if (canEdit() && state.value.step == OnboardingStep.INTERESTS) finish(saveInterests = false)
    }

    /** Saves name input under the existing identity, retaining contact details on later edits. */
    private fun submitName() {
        val validate = parseName ?: return
        val draft = state.value.draft
        val name =
            when (val result = validate(draft.name)) {
                DisplayNameInput.Required -> {
                    mutableState.value = state.value.copy(nameRequired = true)
                    return
                }

                is DisplayNameInput.Valid -> {
                    result.name
                }
            }
        perform {
            preferences.setSetupProgress(accountId, SetupProgress.IN_PROGRESS)
            preferences.setWorkspace(accountId, checkNotNull(draft.intent))
            val record = account ?: accounts.create(accountId, ProfileUpdate(name, null, null))
            account = record
            if (record.name != name) {
                accounts.updateProfile(accountId, ProfileUpdate(name, record.email, record.phone))
                account = record.copy(name = name)
            }
            moveTo(
                if (draft.intent ==
                    Role.OWNER
                ) {
                    OnboardingStep.OWNER
                } else {
                    OnboardingStep.INTERESTS
                },
            )
        }
    }

    /** Completes required writes before publishing the local completion marker.
     * @param saveInterests Whether to apply the optional draft selection.
     */
    private fun finish(saveInterests: Boolean) {
        val draft = state.value.draft
        val role = checkNotNull(draft.intent)
        perform {
            preferences.setSetupProgress(accountId, SetupProgress.IN_PROGRESS)
            if (role == Role.OWNER) {
                accounts.enableOwner(accountId)
                account =
                    accounts.observe(accountId).first {
                        it == null || !it.isActive ||
                            Role.OWNER in it.roles
                    }
                check(account?.isActive == true && Role.OWNER in account!!.roles)
            } else if (saveInterests) {
                accounts.updatePreferences(accountId, draft.preferences)
            }
            preferences.setWorkspace(accountId, role)
            preferences.setSetupProgress(accountId, SetupProgress.COMPLETED)
            mutableState.value = state.value.copy(completed = true)
        }
    }

    /** Serializes actions and retains input on recoverable errors; cancellation propagates.
     * @param action Required profile/local operations for one explicit user action.
     */
    private fun perform(action: suspend () -> Unit) {
        mutableState.value =
            state.value.copy(isSubmitting = true, failed = false, nameRequired = false)
        operation =
            viewModelScope.launch {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = state.value.copy(failed = true)
                } finally {
                    mutableState.value = state.value.copy(isSubmitting = false)
                }
            }
    }

    /** Allows only loaded, active and idle input owners to change the flow. */
    private fun canEdit(): Boolean =
        loaded && !state.value.isSubmitting && !state.value.completed && !state.value.unavailable

    /** Moves within the existing flow, retaining the same draft.
     * @param step Next visible short task.
     */
    private fun moveTo(step: OnboardingStep) {
        mutableState.value = state.value.copy(step = step, failed = false, nameRequired = false)
        retain()
    }

    /** Replaces local input and clears stale field/save errors.
     * @param draft New unsubmitted input.
     */
    private fun updateDraft(draft: OnboardingDraft) {
        mutableState.value = state.value.copy(draft = draft, nameRequired = false, failed = false)
        retain()
    }

    /** Saves small values supported by Android saved state, never a copy of a persisted record. */
    private fun retain() {
        val current = state.value
        savedState["step"] = current.step.name
        savedState["name"] = current.draft.name
        savedState["intent"] = current.draft.intent?.name
        savedState["tags"] = ArrayList(current.draft.preferences.tags)
        savedState["amenities"] = ArrayList(current.draft.preferences.amenities)
    }
}
