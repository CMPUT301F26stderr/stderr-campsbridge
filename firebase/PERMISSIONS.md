# Persistence permissions

Cloud policy for [SCHEMA.md](SCHEMA.md). Firebase Auth UID is the account ID. Roles are additive and come from `accounts/{uid}.roles`; a removed account cannot start ordinary operations. Client-supplied actor IDs are never proof of identity. Console/tooling assigns `ADMIN`; app clients may only add `OWNER` to their own existing roles.

| Operation | Entrant | Owner | Admin |
| --- | --- | --- | --- |
| Read published listings/lotteries and availability | Yes | Yes | Yes |
| Read drafts | No | Own campsite; participating lottery | All |
| Read profiles/reviews | Yes | Yes | Yes |
| Edit profile/settings/preferences; register devices | Own | Own | Own |
| Add owner role | Own account | Already enabled | Own account |
| Grant admin role | No | No | Console/tooling only |
| Remove own account | Yes | Yes | Yes |
| Create/manage campsite and calendar | Requires OWNER | Own | Requires OWNER and ownership |
| Create lottery; change participants/configuration/publication | Requires OWNER | Organizer | Requires OWNER and organizer identity |
| Add/change/remove lottery offers | Requires OWNER | Participating owner of that campsite | Requires OWNER and campsite ownership/participation |
| Join/leave a lottery; decline an offer | Own entry | Requires ENTRANT | Requires ENTRANT |
| Read entries | Own | Participating lottery | All |
| Draw after registration closes; cancel lottery | No | Participating lottery | Maintenance/moderation |
| Book | Own unused winning opportunity | Requires ENTRANT | Requires ENTRANT |
| Read reservations/payments | Own | Own campsite | All |
| Cancel/reject | Own reservation | Own campsite | Any for moderation |
| Upload/replace image | Own profile | Own profile/campsite | Own profile |
| Remove image/listing | Own profile image | Own profile/campsite | Any |
| Rate/review | Own review | Requires ENTRANT | Requires ENTRANT |
| Remove a rating or written review component | No | No | Yes; preserve other component and children |
| Reply/react | Own reply/reaction | Own reply/reaction | Own reply/reaction |
| Read notifications | Own inbox | Own inbox/sent events | All logs |
| Announce to lottery entrants | No | Participating lottery | Requires OWNER and participation |
| Read messages | Reservation participant | Reservation participant | All logs |
| Send/read-receipt messages | Participant | Participant | Participant |
| Remove another account/owner | No | No | Yes |

Profile reads expose the complete account document, including contact/settings fields. Device registrations and inboxes have separate rules. Published removed listing records remain readable for history; discovery excludes them. No unauthenticated reads are allowed.

Lottery participation is distinct from campsite ownership. The organizer controls membership and shared configuration. Each participant controls only their own campsite offers, including deletions. Shared offers use published sites; campsite drafts stay private to their owner/admin. A lottery organizer or another participant gains no authority over a booked site's reservation or conversation.

Author review updates change only stars or text plus `updatedAt`, preserving the other component and `createdAt`. Admin moderation may only clear its selected component. Both components may be null without deleting the parent or children. Empty feedback cannot receive new replies or non-null reaction writes; retained children remain readable and existing reactions can be removed by their authors.

Missing-message and missing-notification `get` requests must be distinguishable from denied reads. Permit authenticated absence checks needed for retained-ID creation and lookup, while keeping reads of existing records and collection queries scoped to their actual participants, senders, recipients or admins.

## Code and policy ownership

| Boundary | Owns | Does not own |
| --- | --- | --- |
| Domain models | Pure value constraints and derived values | Firebase requests or actor authorization |
| Kotlin operation owner | Current-state validation and coordinated transactions | Privileged server identity; these are ordinary client requests |
| Firestore rules | Request identity, permitted fields/transitions and paired-document checks below | The full Kotlin ranking algorithm or arbitrary multi-night orchestration |
| Storage rules | Image object reads, intent-bound creation and authorized deletion | Firestore attachment publication or cleanup scheduling |
| Maintenance | Discovery and retry of existing operation owners | A second implementation of booking, draw or delivery rules |

Rules and Kotlin have distinct jobs. Repositories give application callers meaningful operations; Firestore remains the enforcement boundary for direct requests. Refactoring an operation must preserve both its application behavior and its current cloud permission contract. There is one deployable rules file per service, without generated fragments.

| Operation | Kotlin owner | Rules boundary | Existing verification |
| --- | --- | --- | --- |
| Profile, owner activation and device registration | `FirestoreAccountRepository` | Account/device matches | Account and permission suites |
| Campsite details and availability | `FirestoreCampsiteRepository` | Campsite and calendar matches | Campsite and permission suites |
| Lottery participants/configuration, offers and publication | `LotteryWrites` | Organizer configuration, participant revision and owned-offer predicates | Lottery and offer/revision permission cases |
| Join, withdraw, rejoin and decline | `LotteryMembership` | `memberCountUpdate`, entry transition predicates | Lottery membership and permission cases |
| Draw progress and completion | `LotteryDraw` | `drawEligible`, `drawAuthority`, draw transition predicates | Draw and permission cases |
| Lottery cancellation | `LotteryCancellation` | `lotteryCancellation` | Lottery/moderation and permission cases |
| Book and cancel a stay | `BookingWrites`, calling authenticated `reserve`/`cancelReservation` functions | Client rules deny reservation and calendar occupancy mutations; functions own coordinated writes | Booking/cancellation and permission cases |
| Edit review components and add thread content | `FirestoreReviewRepository` | Author field changes, nonempty feedback and thread identity | Review and permission cases |
| Remove a review component | `FirestoreModerationRepository` | Admin-only clearing of stars/text | Review/moderation and permission cases |
| Record event and recipient job | `NotificationWrites`, called within the owning transaction | Event-family validators and scoped `createDelivery` | Notification and permission cases |
| Deliver inbox receipts | `NotificationDeliveries` | Inbox and aggregate delivery matches | Notification/session-work and permission cases |
| Image upload, attachment publication and cleanup | `FirebaseImageRepository` | `publishAttachment`, upload lifecycle predicates, Storage `createObject` | Image and permission cases |
| Authorize and mark removal | `FirestoreModerationRepository`, `RemovalWrites` | Account/campsite removal and `_removals` matches | Moderation and permission cases |
| Process recorded removals | `RemovalProcessor` | Explicit removal-actor predicates plus each affected operation's rules | Moderation and permission cases |

A removal request authorizes and records the first removal fact before cleanup starts. The processor reads those durable facts and delegates cancellation/attachment operations; it does not grant itself privileges or rewrite historical attribution. `RemovalWrites` contains shared mark/queue transaction writes, not authorization. Lottery cancellation shares the existing draw owner so an interrupted draw is finished before cancellation.

The original removal actor or an active admin can resume the same target's recorded cleanup. Self-removal may have cleared the actor's roles; use the saved removal rather than requiring ordinary owner capability again. A retained removed participant may cancel member lotteries and finish a persisted draw, but cannot create a new draw marker. That path also needs read/processing authority for entries, events, delivery jobs and receipts. Global draining remains separate administrative maintenance.

## Coordinated writes

| Data | Cloud-authorized writer |
| --- | --- |
| Waiting count | Entrant's matching entry transition; draw operator or scheduled worker; admin removal cleanup |
| Draw seed/outcomes/completion | Active lottery participant, admin or scheduled worker after the deadline; recorded cleanup may finish an existing draw; seed is immutable |
| Calendar occupancy | Authenticated callable booking/cancellation transaction |
| Payments | Authenticated callable booking/cancellation transaction; amount is immutable after creation |
| System notification/delivery job | Matching draw, reservation, or message operation |
| Inbox creation | Valid delivery job, processed by its lottery/reservation participants, recorded cleanup actor, admin or scheduled worker |
| Inbox read time | Recipient only; write once |
| Upload intent/attachment | Target owner; removal/obsolete cleanup is also available to admin |
| Removal jobs/cascades | Original self-removal actor or admin; immutable removal facts |

`SessionPendingWork` discovers only the active account's participating lotteries and entrant/owner reservations through `FirestoreWorkSource`. It invokes the same draw and delivery owners used by explicit actions. `AdminMaintenance.resume(adminId)` separately authorizes global removal, draw, notification and image recovery. Ordinary startup never performs global administrative cleanup. Query rules are not filters: private collection queries must constrain account/owner/participant fields or run as an authorized admin.

The scheduled `processPendingWork` function uses the server Admin SDK, which bypasses mobile rules. Its draw transactions enforce publication, cancellation, deadlines and the shared completion protocol directly. It processes captured delivery audiences and preserves receipt read state. The scheduler uses its service identity; it does not impersonate an owner or expose a mobile callable. Client permissions above are unchanged.

Rules enforce access, immutable identities, allowed transitions, and selected transaction relationships. Repositories still own the lottery's random ranking and complete multi-night orchestration: Rules cannot run the Kotlin ranking algorithm or iterate an arbitrary stay. Do not mistake access rules for a replacement booking/draw implementation. Emulator permission tests cover direct denied writes and representative coordinated operations.

Storage objects carry `attachmentId` metadata. Creation requires a matching, unexpired Firestore `_imageUploads/{blobId}` intent authorized for that attachment. Objects cannot be overwritten; replacement uses a new blob. Rules can therefore resolve ownership without duplicating roles or putting an actor in the image document. Obsolete cleanup jobs also retain their attachment reference for authorization. Profile images are shared; draft campsite images are owner/admin-only.

## Test and deploy

```sh
bash scripts/check-firebase.sh

firebase deploy --config firebase/firebase.json --project project-3bc1a00e-fb80-48ba-948 --only firestore:rules,storage
```

`scripts/check-firebase.sh` starts disposable emulators and runs the rules cases followed by the Kotlin repository integration suite. Rules logs and evaluator coverage JSON/HTML are captured under `firebase/tooling/build/reports/` before shutdown and uploaded by CI. Expression-budget exhaustion fails verification even when a negative test received a permission denial. Expected ordinary denials remain part of the suite.

CLI deployment requires a logged-in project member. Anonymous app accounts never deploy rules. Local files take effect in the cloud only after deployment. Rule tests do not deploy or use the real project.
