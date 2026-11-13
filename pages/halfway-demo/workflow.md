**Halfway demo**

Use the [published Halfway release APK](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/tag/halfway-demo). It connects to the shared live Firebase project. The illustrated timeline shows that APK running on emulators with separate live sign-ins. Pixel9 has the same release installed and its live identity is assigned to admin. The presentation uses Pixel9 for admin, plus three emulators for owner, new camper and returning camper.

**Tuesday, November 17**

Start at 6:00 PM Edmonton time (MST). The main walkthrough budgets 17 minutes, leaving 3 minutes for handoffs or a delayed result. The presentation fixture opens registration at 5:00 PM and closes it at 6:08 PM. Booking then remains open until 6:00 PM Wednesday.

The live lottery is Riverside live draw at River Camp. Its booking example uses two nights, December 1 to December 3, for two guests. At CAD 65 per night, the simulated payment is CAD 130. The separate stay for owner rejection is December 9 to December 11. Forest clearing is a campsite draft; Next riverside weekend is a separate lottery draft whose registration opens Wednesday at 6:00 PM.

Screenshots were taken in the live release app during rehearsal and preparation. Follow the named device and dates in each step for Tuesday’s demo. Some profile examples show Alex; edit Riley when presenting step 1. The Active-list example shows Alex’s prepared December stay; open the winner’s fresh stay during step 7.

**Presenters and sessions**

Keep owner, new camper, returning camper and admin sessions signed in throughout. Each presentation device needs its own release-app identity. Use the saved Android Studio devices halfway-live-host, halfway-live-newcamper and halfway-live-camper. Keep their data through Tuesday; recreating an AVD creates another anonymous identity that must be rebound in the seed. Use the fixture bindings for owner, returning camper and admin; leave the new camper without a profile so onboarding remains visible. Copy the credited `assets/photos/banff.jpg` to the owner and new camper emulators’ Pictures folder as `Campsbridge-demo.jpg` before the walkthrough.

| Presenter | Part | Session |
| --- | --- | --- |
| Jingchao | Onboarding, then saved reservations, cancellation and rejection | New camper; later the winning camper and owner |
| Justin | Campsite draft, availability and future lottery | Owner |
| Aron | Explore filters, joining, Inbox and settings | New camper |
| Jixin | Joining, withdrawal, participant filters and receipt status | Returning camper; owner for the participant list |
| Yuduo | Winner booking and notification logs | Whichever camper wins; later admin |
| Yan | Admin Browse, owner profile and campsite image | Admin |

**Run sheet**

| Time | Action | Handoff |
| --- | --- | --- |
| 6:00 to 6:03 | Jingchao completes Riley’s onboarding, saves contact details and a profile photo, reads both app policies, and saves App alerts off then back on. | Leave the new camper on Explore. |
| 6:03 to 6:05 | Justin selects a promotional photo and publishes Forest clearing, edits an offered night, shows River Camp’s occupied December 9–10 nights, and publishes Next riverside weekend. | River Camp’s separate live lottery remains open. |
| 6:05 to 6:06:30 | Aron filters for Jasper, December 1–3 and two guests, joins River Camp’s lottery, then opens its saved-location map. | The first entrant is waiting. |
| 6:06:30 to 6:08 | Jixin joins from the returning camper, withdraws and rejoins. Justin hands the owner controls to Jixin for Participants and the Waiting filter, then takes them back for the draw. | Both entrants are waiting when registration closes. |
| 6:08 to 6:09 | Keep the owner in front until the draw and both Inbox receipts arrive. Show the loss and the win. | Yuduo takes the device that actually won. |
| 6:09 to 6:11:30 | Yuduo chooses December 1 arrival and December 3 checkout, two guests, accepts the rules, reviews CAD 130 and confirms. | Open the saved payment and accepted terms. |
| 6:11:30 to 6:14 | Jingchao opens the camper’s Active list and the fresh booking, cancels it and shows its refund. Jingchao takes the owner controls from Justin to reject the separate December stay, then opens its receipt on Alex’s emulator (from Jixin if Riley won). | Open the rejection from the camper’s Inbox. |
| 6:14 to 6:16 | Yan opens admin Browse, the current owner profile, River Camp and its image. | These are the same shared records used earlier. |
| 6:16 to 6:17 | Yuduo and Jixin show the win event’s read receipt and the booking event’s delivered receipt in admin Logs. | Finish on the shared notification history. |

**Click details**

Owner lists keep their selected Drafts tab after publishing. Choose Published before opening the live lottery or the published campsite. Participant filtering uses the All participants dropdown, rather than the Waiting label beside a person.

In Explore, search for River Camp and open its result. Once registration closes, River Camp no longer shows an active registration card; use Inbox to open each result. Use the actual winner without rerolling. If the live draw is delayed, the returning camper has a prepared Your spring escape win.

After cancellation, the fresh booking disappears from Active. Open the remaining future River Camp stay for owner rejection. The Halfway build has no Cancelled or Past reservation tab; saved records remain accessible through Inbox.

In admin Profiles, search for host.demo@example.com before opening Jordan Lee. Older demo profiles can have the same display name. From the owner profile, use Owned campsites to reach River Camp. In Images, search for halfway-river and open Campsite · halfway-river. A campsite row in Browse opens image inspection first.

Keep the owner in front through close, draw and receipt delivery. If the due draw has not completed, use Run draw on its detail page. Leave sessions signed in and hand over devices without clearing data, reinstalling or restarting the app during the presentation. Payment and refunds are simulated.

**Follow the demo**

Open the [illustrated timeline](index.html). Each timed segment shows the presenter, device, taps and handoff, followed by its Halfway user stories. Use the [printable story checklist](checklist.pdf) to tick off the stories with the TA. It lists stories numerically; the small trailing numbers refer to the nine demo steps. The [full screenshot archive](screens.html) is available for extra detail.
