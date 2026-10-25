# Code boundaries and ownership

These packages belong to the single Gradle module `:app`. The boundaries below are coding conventions; separate build modules do not enforce them. Ownership here refers to responsibility for code and state, not assignments to team members.

## Package responsibilities

| Package | Owns | Boundary |
| --- | --- | --- |
| `domain/` | Application models, enums, invariants, request types, failures, and repository interfaces, grouped by feature. | Uses ordinary Kotlin/JVM types and coroutines. No Firebase SDK types, Android UI, document paths, or dependency on `data/`. |
| `data/` | Repository implementations, document codecs, queries, listeners, transactions, Storage operations, and durable persistence coordination. | Implements domain contracts and exposes application models. Firebase snapshots and serialization details stay here. |
| `di/` | Dependency construction through `AppContainerFactory`, `AppServices`, and the persistence `AppContainer`. | Supplies implementations through interfaces, including injected Firebase clients, clock, and randomness. Does not own screen behavior or business rules. |
| `ui/` | Compose presentation, shared components, theme, and controllers/ViewModels. | Uses domain contracts through controllers. Does not access Firestore or Storage directly. |
| `navigation/` | Destinations, route arguments, navigation graph, and route wiring. | Connects screens and controllers. Does not perform persistence operations or decide booking eligibility. |
| `platform/` | Android integrations such as identity/auth, location, QR scanning/rendering, file sharing, and OS/push notifications. | Keeps platform-specific implementation details behind small integration interfaces or components. |

At the repository root, `firebase/` owns the schema contract, rules, indexes, emulator configuration, and seed/migration tooling conventions. Android runtime persistence code belongs in `data/`. Collection and field definitions are documented in `firebase/SCHEMA.md`.

## Dependencies and control flow

```text
User action -> Compose callback -> ViewModel -> repository operation -> Firebase
UI state    <- Compose          <- ViewModel <- result or Flow       <- Firebase
```

- `domain/` does not depend on `data/`, UI, or Firebase.
- `data/` depends on `domain/` and Firebase SDKs.
- The shared control library is grouped by layout, actions, forms, selection, rows, summaries, feedback, pickers, images and calendar. Feature cards remain beside their feature; presentation-only navigation reports stable IDs. The debug gallery invokes these same production composables with local fixture state.
- Controllers receive domain repository interfaces through constructor injection. Fake implementations can satisfy the same contracts for tests and isolated UI work.
- `di/` knows concrete implementations and supplies dependencies to consumers. Navigation routes connect those dependencies to screens.
- Screens receive state and callbacks. Reusable components do not construct repositories or acquire ViewModels.
- Platform adapters keep Android-specific details out of application contracts. Identity/bootstrap setup belongs in application/platform wiring; controllers consume the resulting identity and services.

ViewModels expose read-only state and explicit action methods. Use `Flow` for observed repository data and suspending functions for actions. Controllers translate results and failures into presentation state, preserve input after failures, and propagate coroutine cancellation. Report confirmed success only after the repository operation succeeds.

## Domain and persistence ownership

Pure invariants belong in `domain/`: for example, checkout must follow check-in, guest count must be positive, and night count derives from the date range. Rules requiring current shared state and coordinated writes belong in the repository implementation: for example, verifying a winning opportunity and claiming every requested night atomically.

Feature folders indicate the primary owner of an operation, rather than restricting it to one collection:

- **Booking** owns reservation creation/cancellation and the coordinated inventory, simulated payment, and notification writes.
- **Lottery** owns membership, capacity changes, draws, winner outcomes, and lottery cancellation coordination.
- **Images** owns attachment publication, Storage transfers, replacement, and retryable blob cleanup.
- **Notifications** owns persisted events, inbox receipts, and resumable inbox delivery. Platform integration owns OS/push delivery.
- **Moderation** owns account/campsite removal and coordinates the resulting persistence cleanup.
- **Maintenance** observes permitted parent records, schedules due draws and resumes scoped notification work. It calls the operation that owns each invariant; it does not duplicate draw, booking or receipt rules.

A controller calls `bookings.reserve(request)` once. It must not separately create a reservation, charge a payment, claim nights, and send a notification: that sequence belongs to the repository operation. Transaction callbacks must avoid external side effects because Firestore can retry them. Storage and Firestore do not share a transaction, so image publication and cleanup require explicit retry coordination.

Draft creation requires a caller-allocated stable ID and returns the saved record. Retries preserve existing data rather than replacing edits. Owner reservation lists derive membership from retained campsite ownership; published campsite lottery lookup joins canonical offers with the owner's published records.

Give each persisted fact one authoritative owner. Derive values such as QR images, fixed Storage paths, and night counts from their inputs. Preserve historical snapshots such as accepted booking terms when current editable data cannot reconstruct them. Document deliberate query copies and coordination metadata in the schema, including the operation responsible for maintaining them.

Document codecs such as `ReservationDocument` translate between application models and Firestore fields. Screens and controllers do not perform this conversion. Avoid duplicate model layers or pass-through use cases unless concrete complexity or reuse justifies them.

## Current implementation and pending work

Domain contracts, repository implementations, codecs, and the persistence `AppContainer` are implemented. `AppRepositories` exposes the repository interfaces required by implemented features; both the Firebase container and the debug demo supply it. `AppServices` adds identity and local preferences; typed factories supply these dependencies to ViewModels. `AppContainer` only constructs dependencies. `AdminMaintenance.resume(adminId)` explicitly authorizes global removal, draw, notification and image recovery; it is unwired from ordinary startup. Lottery reads and draws have separate internal owners (`LotteryQueries`, `LotteryDraw`); booking reads and coordinated writes use `BookingQueries` and `BookingWrites`. A reservation owns its required nested simulated payment, so one observation contains cancellation and refund state together. Notification transaction writes and scoped inbox delivery are separate from the event/history repository.

The default debug launcher uses isolated in-memory demo accounts/preferences and private cached photos. It does not initialize Firebase. Firebase debug and release use MainActivity and the real service factory. Demo hosts, scenario selection and simulated repositories stay under `src/debug`; feature screens/controllers are shared.

The root ViewModel restores identity, observes profiles and local setup progress, and confirms permitted workspace selections. The root selects startup/setup/unavailable or the account-scoped ready navigation host. Workspace-prefixed profile graphs share one ProfileViewModel between menu and personal/contact editors; WorkspaceScaffold frames the primary menu, while editors own their own AppScreen and Back behavior. ProfileViewModel preserves small drafts in SavedStateHandle, validates through injected helpers and saves explicit profile fields without replacing unrelated account data. Name/contact parsers remain reserved and their production submissions are visibly pending. The shared image controller owns independent photo selection/save/remove/reconciliation; active image mutations block conflicting field actions, while idle uncertain publication remains retryable without trapping the editor. The platform adapter supplies native picker content and fresh upload streams. MainActivity supplies lifecycle state and the ready account to ForegroundWorkRunner. SessionPendingWork observes all current account capabilities, independently of the selected workspace, and processes due draws and parent-scoped notification jobs serially. Source changes request conflated passes; deadlines wake at eligibility and failures retry independently with exponential backoff capped at one minute. Backgrounding, setup/unavailable state, account changes and capability changes cancel the active session and its subscriptions.

Ordinary sessions discover owner lotteries and entrant/owner reservations; they do not scan global work or perform administrative removals/image cleanup. Notification discovery uses job identities, while per-job processing decodes the payload so malformed work can fail independently. Delivery is durable inbox fan-out, not OS/FCM push. The onboarding ViewModel owns draft progression, idempotent account creation, optional interests and confirmed owner activation, with navigation-owned saved input. The reserved display-name validator is injected; its production implementation remains pending and name submission is explicitly blocked. Closed-app scheduling, OS/push delivery and other feature controllers remain pending. Feature screens follow the approved prototypes.

When changing a repository contract or persisted field, update affected consumers, readers, writers, queries, fixtures, and relevant tests together. Record whether existing data requires compatibility handling, migration, or development reseeding.
