# Persistence contract

This contract defines the Firestore documents, Storage objects, and coordinated repository operations for the [application requirements](../doc/00_requirements.md). Android domain models and document codecs follow these definitions. Broader architecture guidance belongs in the team Wiki; integration instructions and pending work are listed in [Firebase setup](README.md).

## Conventions

- Firestore holds documents; Cloud Storage holds uploaded image bytes.
- IDs come from document paths. Relationships use string IDs. `?` means nullable; other fields are required. Readers may treat missing nullable fields as null.
- `Timestamp` is a Firestore timestamp; `Date` is a `YYYY-MM-DD` string mapped to Kotlin `LocalDate`; `GeoPoint` is the Firestore coordinate type. SDK types stay inside persistence implementations.
- `Int` means an integer stored in Firestore, represented with Kotlin `Long` at the serialization boundary. Money uses integer cents; currency is CAD application-wide. `Double` is floating point.
- Enum values are stable uppercase strings. Lists described as sets contain no duplicates.
- Check-in is inclusive; check-out is exclusive. Classify stays in the recorded campsite timezone.
- One win permits one reservation at one campsite for consecutive nights. Winners share offered inventory first-come. Different lotteries may offer the same nights. No replacement draws; cancelled reservations still consume their win.
- Payments and full refunds are simulated. Campsite ownership is immutable. One image attachment per account/campsite initially.
- Campsite and lottery draft IDs are allocated by the caller before creation and retained across retries. Creation returns the current stored record when the same owner already owns that ID, preserving saved edits and lifecycle metadata; it never overwrites another owner's record. Later edits use the update operation.

Reusable nested records (notation only, not collections):

```text
Removal = { at: Timestamp, actorId: AccountId, reason: String? }
Cancellation = { at: Timestamp, actorId: AccountId, reason: String? }
```

## Accounts

```text
accounts/{accountId}
  name: String
  email: String?
  phone: String?
  roles: List<ENTRANT | OWNER | ADMIN>                 # Set semantics
  preferences: { tags: List<String>, amenities: List<String> }
  settings: {
    notificationsEnabled: Boolean,
    colorMode: STANDARD | COLOR_BLIND,
    fontScale: Double,
    controlScale: Double
  }
  createdAt: Timestamp
  removal: Removal?

accounts/{accountId}/devices/{installationId}
  fcmToken: String
  updatedAt: Timestamp

accounts/{accountId}/inbox/{notificationId}
  readAt: Timestamp?
```

Defaults: entrant role, empty preferences, notifications enabled, standard colors, scales `1.0`, no removal. Roles may overlap; selected UI role is temporary application state. Device identity supplies `accountId`; the exact identity mechanism remains an integration decision. Push tokens do not establish identity. Notifications disabled means suppress alerts while retaining inbox/log history.

## Campsites and calendar

```text
campsites/{campsiteId}
  ownerId: AccountId
  name: String
  description: String
  location: GeoPoint
  locationLabel: String
  timeZone: String
  maxGuests: Int
  amenities: List<String>
  tags: List<String>
  nightlyPriceCents: Int
  rules: String
  createdAt: Timestamp
  publishedAt: Timestamp?
  removal: Removal?

campsites/{campsiteId}/calendar/{date}
  isOffered: Boolean
  reservationId: ReservationId?
```

Lifecycle is derived: removal -> removed; otherwise published timestamp -> published; otherwise draft. Amenities describe facilities; tags describe characteristics. Location label is owner-written text. Standard versioned full-refund policy text ships with the app, rather than being repeated on each campsite.

An absent calendar document means not offered. Physical availability is `isOffered && reservationId == null`; entrant eligibility additionally depends on the lottery and winning entry. Calendar reservation links are an intentional occupancy index updated with reservation writes. No lottery ID is stored on calendar nights.

## Lotteries, offers, and entries

```text
lotteries/{lotteryId}
  ownerId: AccountId
  title: String
  description: String
  registrationOpensAt: Timestamp
  registrationClosesAt: Timestamp
  bookingOpensAt: Timestamp
  bookingClosesAt: Timestamp
  winnerCount: Int
  waitlistCapacity: Int?
  waitingCount: Int
  createdAt: Timestamp
  publishedAt: Timestamp?
  drawnAt: Timestamp?
  cancellation: Cancellation?

lotteries/{lotteryId}/offers/{campsiteId}
  campsiteId: CampsiteId                             # Collection-group query field; equals path ID
  nights: List<Date>                                 # Unique, sorted

lotteries/{lotteryId}/entries/{accountId}
  accountId: AccountId                               # Collection-group query field
  state: WAITING | WITHDRAWN | LOST | OFFERED | DECLINED
  joinedAt: Timestamp
  stateChangedAt: Timestamp
```

- `winnerCount` is the maximum winners; select fewer if fewer entrants are eligible. `waitlistCapacity = null` is unlimited. `waitingCount` counts `WAITING` entries and is maintained by the lottery repository, initially zero.
- Require `registrationOpensAt < registrationClosesAt <= bookingOpensAt < bookingClosesAt`.
- `campsiteId` on each offer is an immutable query copy of its path ID, validated by codecs and writes. Campsite lookup joins those memberships with the owner-scoped published lottery query; no global lottery scan or copied publication state is needed.
- Offers alone own lottery-to-campsite/night membership; no separate campsite-ID list. Every offered campsite belongs to the lottery owner. Publishing validates offered calendar nights. Retain allocations after the lottery ends.
- Freeze allocations, windows, winner count, and capacity once registration begins. Descriptive edits remain allowed. Cancellation is explicit.
- Entries transition `WAITING <-> WITHDRAWN` during registration, `WAITING -> LOST | OFFERED` at draw, and unused `OFFERED -> DECLINED` during booking. Rejoining refreshes `joinedAt`.
- A reservation's existence consumes an offer, even after cancellation. Expiry and booked display state are derived; neither is an entry enum value. Cancellation overrides unused opportunities.
- Derive lifecycle labels from publication, windows, draw completion, and cancellation. Entry states own draw results; do not duplicate winner IDs on the lottery.
- Automatic draws while the app is running and manual fallback use the same operation. Background draws with every app closed are not required. The resumable draw uses a persisted random seed and frozen membership. Individual outcomes may appear during processing, but `drawnAt` remains null, booking stays disabled, and result delivery waits until every outcome is applied. Callers should present outcomes only once draw completion is visible.

## Reservations and simulated payments

```text
reservations/{reservationId}
  lotteryId: LotteryId
  entrantId: AccountId
  campsiteId: CampsiteId
  checkInDate: Date
  checkOutDate: Date
  guestCount: Int
  bookedCampsite: {
    name: String,
    locationLabel: String,
    location: GeoPoint,
    timeZone: String
  }
  acceptedTerms: {
    rules: String,
    cancellationPolicyVersion: String,
    acceptedAt: Timestamp
  }
  createdAt: Timestamp
  cancellation: {
    at: Timestamp,
    actorId: AccountId,
    initiatedAs: ENTRANT | OWNER | ADMIN,
    reason: String?
  }?
  payment: {
    amountCents: Int,
    paidAt: Timestamp,
    refundedAt: Timestamp?
  }
```

`reservationId = reservationKey(lotteryId, entrantId)`: use one collision-free, Firestore-safe encoding helper. The fields remain queryable relationships. Check document existence during booking; cancellation does not remove it. Every occupied night must belong to the lottery offer and be free in the calendar; guest count must fit the campsite.

The campsite snapshot and accepted terms are historical facts, independent of later listing edits. Retain all referenced policy versions in application resources. Owner identity follows immutable campsite ownership. Night count, active/past labels, and cancellation status are derived.

The nested payment record owns the charged total and is a required part of each reservation; it has no separate ID or collection. A single reservation observation includes cancellation and refund facts together. Initially charge current nightly price times nights; reject/reconfirm a stale displayed quote. `refundedAt` records a full refund; no separate amount or status is needed. Booking creates the payment; cancellation records the refund.

## Images

```text
images/{attachmentId}
  blobId: String
  uploadedAt: Timestamp

Cloud Storage object: images/{blobId}/original
```

Attachment IDs are `account_{accountId}` or `campsite_{campsiteId}`; the prefix identifies the target type and the remaining suffix its ID. The attachment identifies a stable image slot; `blobId` identifies a particular upload. Do not repeat target fields, parent-side image IDs, Storage paths, or download URLs. Content type belongs in Storage metadata.

Replacement: upload a new blob, switch the attachment record, then delete the old blob. Handle failed publication/cleanup in the image repository; Storage and Firestore do not share a transaction. Generate/cache download URLs for display.

## Reviews, reply threads, and reactions

```text
campsites/{campsiteId}/reviews/{authorId}
  rating: Int                                        # 1..5
  body: String?
  createdAt: Timestamp
  updatedAt: Timestamp

campsites/{campsiteId}/reviews/{authorId}/replies/{replyId}
  authorId: AccountId
  parentReplyId: ReplyId?
  body: String
  createdAt: Timestamp

campsites/{campsiteId}/reviews/{authorId}/reactions/{reactorId}
  type: LIKE | HELPFUL
```

One editable review per account/campsite; null text supports rating-only feedback. A reply parent is either the review (null) or an existing reply in that same review; parent links are immutable. One changeable reaction per account/review. Derive averages and counts. Reply reactions and review deletion are outside the initial feature set.

## Notifications and messages

```text
notifications/{notificationId}
  senderId: AccountId?                               # Null for system events
  type: LOTTERY_WON | LOTTERY_LOST | RESERVATION_CONFIRMED
        | RESERVATION_CANCELLED | OWNER_ANNOUNCEMENT | NEW_MESSAGE
  subject: { type: LOTTERY | RESERVATION | MESSAGE, id: String }
  announcement: { title: String, body: String }?
  createdAt: Timestamp

messages/{messageId}
  reservationId: ReservationId
  senderId: AccountId
  body: String
  createdAt: Timestamp
  readAt: Timestamp?
```

Notification content is shared once; account inbox documents own per-recipient read state. Owner announcements capture all entrants who joined and have not withdrawn, including entrants with draw outcomes; this implements "have joined" in US 02.10.01. Capture recipients when sending, not from later membership. Announcement content is present only for owner announcements; standard event text uses application templates. Events and messages are immutable after publication. Stable system-event IDs prevent duplicates on retry.

Subject types: lottery results/announcements -> lottery; reservation confirmations/cancellations -> reservation; new-message alerts -> message. Alerts reference messages rather than copying bodies. Owner sent history queries sender; admin logs browse events and receipts.

A reservation is the conversation context. Participants are its entrant and the campsite owner; the recipient is the other participant. `readAt` is the recipient's read time. Both participants can send messages; no separate conversation entity is needed.

## Coordinated operations and removal

| Operation | Consistent changes |
| --- | --- |
| Join/leave | Entry and waiting count; enforce capacity and registration window |
| Draw | Entry outcomes, waiting count, draw completion; no second draw |
| Book | Validate opportunity/quote, create unique reservation and payment, claim nights |
| Cancel/reject | Record cancellation/refund, release nights; retain consumed win |
| Notify | Publish shared event and intended receipts without duplicates on retry |
| Replace image | Upload, switch attachment, clean up obsolete blob |

Booking/cancellation invariants belong in Firestore transactions. Keep external side effects out of retryable transaction callbacks. Notification fan-out and large removal workflows must finish or resume after interruption; never assume unlimited atomic batch capacity.

Removal hides accounts/listings, clears personal profile fields and images, and retains identity/campsite records for references. Cancel affected active lotteries and future/current reservations with refunds. Removing a campsite cancels active lotteries containing it. Retain completed stays, payments, messages, and notification history. Resolve occupied nights through reservation cancellation before withdrawing them. A removed account keeps its document shape: empty name/roles/preferences, null email/phone, default settings with alerts disabled, original creation time, and removal metadata. Device tokens and profile images are removed by resumable cleanup.

## Internal coordination records

These are implementation-owned records, not additional application entities:

```text
lotteries/{lotteryId}._revision: Int                    # Absent means zero
_draws/{lotteryId}: { seed: String }
lotteries/{lotteryId}/_notificationDeliveries/{notificationId}: { recipientIds: List<AccountId> }
reservations/{reservationId}/_notificationDeliveries/{notificationId}: { recipientIds: List<AccountId> }
_storageCleanup/{blobId}: { queuedAt: Timestamp, attachmentId: String }
_imageUploads/{blobId}: {
  attachmentId: String, expiresAt: Timestamp,
  abandoned: Boolean, uploadFinished: Boolean
}
_removals/{attachmentId}: Removal
```

The lottery revision guards a queried offer set against concurrent edits during publication. A draw first creates its seed and conflicts with in-flight membership changes, then assigns deterministic outcomes in per-entry transactions. Each assignment decrements the waiting count and queues its result event; completion requires zero waiting entries. Retrying uses the same seed and never resets completed entry states. Cancellation checks the draw marker transactionally: if a draw already started, finish it before cancelling. Historical completed-draw notifications remain deliverable after cancellation.

Notification jobs live under their lottery (results/announcements) or reservation (confirmations/cancellations/message alerts). The parent path owns scope; event type and lottery completion gate result delivery without a copied gate field. Scoped listeners discover every job, including generated announcement/message IDs. Only explicit administrative maintenance uses a collection-group scan.

Notification jobs own a captured recipient set until receipts are created; retries preserve existing read timestamps. Event creation and its job are atomic with the originating write. Delivery jobs are deleted only after fan-out completes. Image replacement/removal queues obsolete blob cleanup with the attachment change. Upload intent is persisted before bytes are sent, with a 15-minute expiry and a 10-minute transfer timeout. Publication reads/deletes that intent in its transaction; cleanup claims expired intents as abandoned so publication cannot race deletion. If an interrupted uploader never acknowledged completion and no object exists, retain its abandoned intent to catch a late upload. An upload followed by ambiguous publication failure reports `ImagePublicationException` with the blob ID for reconciliation; never delete a possibly published blob blindly. Removal jobs are deleted only after dependent cleanup completes.

Calendar edits commit each requested night independently and are safe to retry; callers must allow partial progress if an occupied night or transport failure interrupts the operation. Native Firestore document/transaction size limits still apply; no unbounded-batch guarantee is implied.

## Tooling and derived features

```text
_migrations/{migrationId}
  completedAt: Timestamp
```

Numbered migration scripts are safe to rerun and record completion only after success. They run through tooling, never automatically on phone startup. Prefer updated fixtures/reseeding for disposable development data; no per-document schema version initially.

Do not persist QR graphics/payloads, CSV exports, average ratings, reaction counts, match scores, carousel ordering, temporary filters/device location, selected UI role, derived lifecycle labels, or image caches. Reservation maps use booking snapshots; nearby maps use campsite coordinates; match scores use filters; carousels use preferences; accessibility uses account settings. Guidelines and versioned standard policy text ship with the app.
