# Intent tests by user story

Each user story maps to the Compose interaction tests that drive it through the real screens. Paths are relative to this folder. In Android Studio, open a class and use the run gutter icon beside it. From a terminal, run one class with an emulator or device connected:

```
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.CMPUT301F26stderr.campsbridge.ui.discovery.DiscoveryFlowTest
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

`FinalConnectedDemoTest` and `ConnectedOfflineDemoTest` also walk the main entrant, owner and admin journey end to end.

## Entrant: discovery and lotteries

| Story | | Intent tests |
| --- | --- | --- |
| US 01.01.01 | Search campsites | [DiscoveryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/discovery/DiscoveryFlowTest.kt), [MaterialInteractionTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/components/MaterialInteractionTest.kt) |
| US 01.01.02 | Filter by location, dates and guests | [DiscoveryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/discovery/DiscoveryFlowTest.kt) |
| US 01.01.03 | See campsites that match the filters | [DiscoveryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/discovery/DiscoveryFlowTest.kt) |
| US 01.01.04 | See summary facts on each result | [DiscoveryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/discovery/DiscoveryFlowTest.kt) |
| US 01.01.05 | See campsite details | [CampsiteDetailFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteDetailFlowTest.kt), [DiscoveryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/discovery/DiscoveryFlowTest.kt) |
| US 01.01.06 | Join an active lottery from campsite details | [CampsiteLotteryEntryFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteLotteryEntryFlowTest.kt) |
| US 01.02.01 | Join a lottery waiting list | [LotteryMembershipFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryMembershipFlowTest.kt) |
| US 01.02.02 | Leave a lottery waiting list | [LotteryMembershipFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryMembershipFlowTest.kt) |
| US 01.03.01 | Get a win notification | [InboxNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/InboxNavigationTest.kt), [LotteryEntrantFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryEntrantFlowTest.kt), [WinningOpportunityFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/WinningOpportunityFlowTest.kt) |
| US 01.03.02 | Get a loss notification | [InboxNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/InboxNavigationTest.kt) |
| US 01.12.01 | Open campsite details by scanning its QR code | [CampsiteQrFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteQrFlowTest.kt), [QrScannerActivityTest](java/io/github/CMPUT301F26stderr/campsbridge/platform/qr/QrScannerActivityTest.kt) |

## Entrant: booking and reservations

| Story | | Intent tests |
| --- | --- | --- |
| US 01.04.01 | See the dates a win can reserve | [BookingFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingFlowTest.kt), [BookingScreenTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingScreenTest.kt) |
| US 01.04.02 | Choose a campsite after a multi-campsite win | [WinningOpportunityFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/WinningOpportunityFlowTest.kt) |
| US 01.04.03 | Decline a win | [WinningOpportunityFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/WinningOpportunityFlowTest.kt) |
| US 01.02.06 | Reserve the chosen dates | [BookingFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingFlowTest.kt) |
| US 01.05.01 | Get a reservation confirmation | [BookingFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingFlowTest.kt), [InboxNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/InboxNavigationTest.kt) |
| US 01.05.02 | Get the reservation by notification | [InboxNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/InboxNavigationTest.kt) |
| US 01.06.01 | List active reservations | [ReservationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationListFlowTest.kt) |
| US 01.06.02 | List past reservations | [ReservationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationListFlowTest.kt), [ReservationMapScreenTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationMapScreenTest.kt) |
| US 01.07.01 | Cancel a reservation | [ReservationCancellationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationCancellationFlowTest.kt) |
| US 01.07.02 | Get a cancellation confirmation by notification | [InboxNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/InboxNavigationTest.kt), [ReservationCancellationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationCancellationFlowTest.kt) |
| US 01.14.01 | Message the owner about a reservation | [ConversationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/messaging/ConversationFlowTest.kt), [ConversationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/messaging/ConversationListFlowTest.kt) |

## Entrant: profile, settings and feedback

| Story | | Intent tests |
| --- | --- | --- |
| US 01.07.03 | Update name, email and phone | [ProfileEditingTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/ProfileEditingTest.kt) |
| US 01.08.01 | Delete the profile | [AccountRemovalFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/AccountRemovalFlowTest.kt) |
| US 01.08.02 | Add, replace or remove a profile image | [ProfileImageTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/ProfileImageTest.kt), [PhotoPickerIntentTest](java/io/github/CMPUT301F26stderr/campsbridge/platform/images/PhotoPickerIntentTest.kt) |
| US 01.09.01 | Be identified by the device | [AppNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/navigation/AppNavigationTest.kt), [OnboardingFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/onboarding/OnboardingFlowTest.kt) |
| US 01.10.01 | Opt out of notifications | [NotificationSettingsTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/NotificationSettingsTest.kt) |
| US 01.11.01 | Read the app policy and guidelines | [PolicyReaderTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/policies/PolicyReaderTest.kt), [OnboardingScreenTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/onboarding/OnboardingScreenTest.kt) |
| US 01.13.01 | Rate a campsite | [CampsiteReviewsFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reviews/CampsiteReviewsFlowTest.kt), [RatingInputTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reviews/RatingInputTest.kt) |
| US 01.13.02 | Post a review | [CampsiteReviewsFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reviews/CampsiteReviewsFlowTest.kt) |

## Owner: campsites

| Story | | Intent tests |
| --- | --- | --- |
| US 02.01.01 | Register a campsite | [CampsiteSetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteSetupFlowTest.kt), [CampsiteDraftSaveFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteDraftSaveFlowTest.kt) |
| US 02.01.02 | Provide details, map location and image | [CampsiteSetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteSetupFlowTest.kt), [LocationPickerTest](java/io/github/CMPUT301F26stderr/campsbridge/platform/maps/LocationPickerTest.kt), [PhotoPickerIntentTest](java/io/github/CMPUT301F26stderr/campsbridge/platform/images/PhotoPickerIntentTest.kt) |
| US 02.01.03 | Update campsite details | [CampsiteEditFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteEditFlowTest.kt) |
| US 02.01.04 | View campsite details | [CampsiteDetailFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteDetailFlowTest.kt) |
| US 02.02.01 | Generate the campsite QR code | [CampsiteQrFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteQrFlowTest.kt), [WeChatQrCompatibilityTest](java/io/github/CMPUT301F26stderr/campsbridge/platform/qr/WeChatQrCompatibilityTest.kt) |
| US 02.03.01 | List my campsites | [CampsiteNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/CampsiteNavigationTest.kt) |
| US 02.06.01 | See booked dates | [AvailabilityEditorTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/campsites/AvailabilityEditorTest.kt), [CampsiteCalendarTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/components/calendar/CampsiteCalendarTest.kt) |

## Owner: lotteries

| Story | | Intent tests |
| --- | --- | --- |
| US 02.04.01 | List a campsite in a lottery | [LotterySetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotterySetupFlowTest.kt) |
| US 02.04.02 | List several campsites in one lottery | [LotterySetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotterySetupFlowTest.kt), [LotteryAllocationRenderingTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryAllocationRenderingTest.kt) |
| US 02.02.02 | Share a lottery with other owners | [CollaboratingOwnersFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/CollaboratingOwnersFlowTest.kt), [FinalConnectedDemoTest](java/io/github/CMPUT301F26stderr/campsbridge/demo/FinalConnectedDemoTest.kt) |
| US 02.04.03 | Set the registration period | [LotterySetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotterySetupFlowTest.kt) |
| US 02.04.04 | Set the booking period | [LotterySetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotterySetupFlowTest.kt) |
| US 02.04.05 | Set the number of winners | [LotterySetupFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotterySetupFlowTest.kt), [LotteryCapacityRenderingTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryCapacityRenderingTest.kt) |
| US 02.04.06 | Update a lottery | [LotteryDetailsEditFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryDetailsEditFlowTest.kt), [LotteryAllocationsEditFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryAllocationsEditFlowTest.kt) |
| US 02.04.07 | Set an optional waiting-list capacity | [LotteryCapacityRenderingTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryCapacityRenderingTest.kt) |
| US 02.05.01 | Draw winners when registration closes | [LotteryDrawFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/LotteryDrawFlowTest.kt) |
| US 02.09.01 | See the waiting-list entrants | [ParticipantListTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/ParticipantListTest.kt) |
| US 02.10.01 | Send waiting-list entrants a notification | [AnnouncementComposeFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/lotteries/AnnouncementComposeFlowTest.kt) |
| US 02.10.02 | See the notifications I sent | [SentNotificationsFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/SentNotificationsFlowTest.kt), [NotificationAudienceFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/NotificationAudienceFlowTest.kt) |

## Owner: reservations

| Story | | Intent tests |
| --- | --- | --- |
| US 02.07.01 | List active reservations | [ReservationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationListFlowTest.kt) |
| US 02.07.02 | List past reservations | [ReservationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationListFlowTest.kt) |
| US 02.07.03 | List cancelled reservations | [ReservationListFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationListFlowTest.kt) |
| US 02.08.01 | Reject a reservation | [ReservationCancellationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reservations/ReservationCancellationFlowTest.kt) |
| US 02.11.01 | Require entrants to accept the rules | [BookingFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingFlowTest.kt), [BookingScreenTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/booking/BookingScreenTest.kt) |
| US 02.12.01 | Message the entrant who reserved | [ConversationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/messaging/ConversationFlowTest.kt), [SentNotificationsFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/inbox/SentNotificationsFlowTest.kt) |

## Admin

| Story | | Intent tests |
| --- | --- | --- |
| US 03.01.01 | Remove campsites | [AdminRemovalFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminRemovalFlowTest.kt) |
| US 03.02.01 | Remove profiles | [AdminRemovalFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminRemovalFlowTest.kt) |
| US 03.03.01 | Remove images | [AdminRemovalFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminRemovalFlowTest.kt) |
| US 03.04.01 | Browse campsites | [AdminBrowseTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminBrowseTest.kt), [AdminInspectionFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminInspectionFlowTest.kt) |
| US 03.05.01 | Browse profiles | [AdminProfileScreenTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminProfileScreenTest.kt), [AdminProfileNavigationTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminProfileNavigationTest.kt) |
| US 03.06.01 | Browse uploaded images | [AdminBrowseTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminBrowseTest.kt) |
| US 03.07.01 | Remove owners who break the policy | [AdminRemovalFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/AdminRemovalFlowTest.kt) |
| US 03.08.01 | Review notification logs | [NotificationLogTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/NotificationLogTest.kt) |
| US 03.09.01 | Remove ratings | [ReviewModerationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reviews/ReviewModerationFlowTest.kt) |
| US 03.10.01 | Remove reviews | [ReviewModerationFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/reviews/ReviewModerationFlowTest.kt) |
| US 03.11.01 | Use owner and entrant workspaces as an admin | [CombinedWorkspaceFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/CombinedWorkspaceFlowTest.kt) |
| US 03.12.01 | Review message logs | [MessageLogFlowTest](java/io/github/CMPUT301F26stderr/campsbridge/ui/administration/MessageLogFlowTest.kt) |
