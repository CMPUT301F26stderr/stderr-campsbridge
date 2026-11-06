# Offline debug demo

Select `debug` in Android Studio and run `app`. Camping (Alex), Hosting (Jordan) and Admin (Taylor) use the production screens with one shared local dataset. Changing areas retains saved edits and the area’s navigation. The launcher does not initialize Firebase.

Start with the supplied campsite, lottery and reservation history, or create a campsite in Hosting. Save or publish it, offer calendar nights, then create a lottery from its management page. Camping can discover the same listing and join. The contextual action above the app opens registration or advances to draw results without waiting for real dates. Inbox opens the actual winning offer and booking flow. Reservations, simulated payments/refunds, calendar claims and notification history update together.

Use a camper cancellation and a separate host rejection to show both outcomes. Admin can inspect the same profiles, uploaded images and notification recipients. Notification preferences and read acknowledgements are saved per person. Location selection uses explicit sample places or coordinates offline; it does not load map tiles. Packaged scenery and selected photos work without a network connection.

Committed records, selected photos, demo time and the selected area survive application restart. Demo tools → Reset scenario restores the baseline for every person after confirmation. New-account onboarding, previews, the component gallery and isolated Task examples remain in Demo tools. Task example reset affects only that example.

Announcements, review writes and closed-app push delivery remain outside the offline demonstration. Use `firebaseDebug` for real Firebase services or `release` for delivery; those variants exclude the demo adapters. See [Firebase setup](../../../../../../../../../firebase/README.md). Run local demo contracts with `:app:testDebugUnitTest --tests '*demo.*'`; launcher journeys are in `app/src/androidTest`.
