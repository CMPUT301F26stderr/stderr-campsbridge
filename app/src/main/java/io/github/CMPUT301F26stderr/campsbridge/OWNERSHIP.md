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

## Shared foundation and feature boundaries

The application supplies `AppServices` and repository interfaces through constructor injection. `AppContainerFactory` constructs the Firebase services; the debug host constructs local services without Firebase initialization. `foregroundWork` composes durable session work with Android alerts in the Firebase host. Construction does not start observation or writes.

Feature graphs and factories are separate files so implementation can proceed without editing central registration. Unfinished factories are not invoked by their pending routes. Those routes show unavailable-feature feedback and support Back; they do not construct an absent ViewModel or report a successful operation.

| Boundary | Shared code supplied here | Remaining feature work |
| --- | --- | --- |
| Booking | Booking repository, inventory APIs, cancellation eligibility, calendar controls, destination IDs and factory contract. | `ui/booking/`, booking graph/factory, wizard state, validation/quote rules, submission/recovery and assigned eligibility helper. |
| Reservations | Saved aggregate/repository operations, `ReservationContext`, category type and graph/factory contracts. | Saved overview/payment/terms reader, lists, classification/ordering helpers, cancellation/rejection UI and controllers. |
| Inbox | Notification repository, shared `InboxItem`, content loader/text, Android adapters and graph/factory contract. | Receipt state, ordering/unread helpers, screen actions and target navigation. |
| Notification settings | Permission adapter and profile-parent destination. | Saved preference state/actions and settings screen; preserve the existing profile editor behavior. |
| Admin browsing | `AdminRead`, repository read APIs, shared image readers and graph/factory contract. | Browse/filter state, profile/image inspection and assigned profile predicates. |
| Notification inspection | Event/audience repository reads, shared content loader and independent graph/factory contract. | Event list, detail, audience state/screens and read lifetimes. |

Screens receive state and callbacks. Routes collect state with lifecycle awareness and connect navigation. ViewModels own feature state and explicit actions. Shared notification loading and image slots remain reusable readers; feature controllers do not copy their persistence logic.

Keep destination arguments to stable IDs and small context values. A saved reservation contains its payment and accepted terms; its future reader should observe that aggregate once across child pages. Notification audience facts come from the existing event, delivery work and receipts, not a new stored audience list. The audience query is read-only and does not prove an Android alert was shown.

Android permission/channel inspection, alert publication and cold/warm notification entry are shared platform work. The account-scoped resolver waits for a matching ready session and confirmed workspace before navigating. Receipt arrival alone does not navigate. The foreground publisher keeps alert delivery separate from durable Inbox receipt delivery.

`src/debug` owns local sample records, scenario selection, task hosts and controlled repository adapters. `DemoPreviewData` supplies one related snapshot and clock for entrant, owner and admin scenarios. Unsupported distributed writes fail explicitly. Feature-specific demo factories and test factories are separate files; future assignments replace their pending contracts alongside the real graph/factory implementation.

Accepted name/contact, campsite ordering/search/capacity and lottery-window functions remain connected. Availability differences, participant filtering and later feature helpers remain pending until their implementations are accepted. Debug task bindings use the same accepted functions, while isolated tests inject fixed results or a deliberately absent dependency.

Request changes to central navigation, shared repositories, common readers and demo dispatchers through the shared-code owner. Keep feature content, controllers, resources and tests beside their feature. The team Wiki and assigned issues record member ownership and acceptance.
