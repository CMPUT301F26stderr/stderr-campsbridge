# Campsbridge screen prototype

[Open the published storyboard](https://cmput301f26stderr.github.io/stderr-campsbridge/). Changes to `prototype/` on `main` are published by the **Publish storyboard** workflow, which can also be run manually. Deployment includes the viewer and screen assets, excluding this README and the generated ZIP.

Run `python3 -m http.server 8080` in this folder and open http://localhost:8080.

- `screens/` contains the 149 original Stitch HTML exports, unchanged.
- `wiring.json` defines destinations, role/onboarding branches, Back destinations, existing progress-to-result screen transitions, and screen-level requirement references. Its `requirements` catalog owns each requirement's description and any design limitation; each screen's `requirements` array lists the relevant IDs.
- `prototype.js` loads the designs in an iframe and follows those connections. Input values are retained while navigating; Restart clears them.
- `index.html`, `prototype.css`, and `hotspots.css` provide the viewer and transition outlines.

Red dotted outlines identify enabled click targets that lead to a different existing screen. Native fields, selected tabs, missing destinations, and unsupported controls are not highlighted. All designs, including unlinked alternatives, remain available in the screen browser.

Use **Red guidelines** to show or hide the outlines. They start enabled and keep the chosen visibility while navigating between screens.

The requirements bar lists the current screen's references. These describe design support, not completed implementation or test coverage. Archived user stories are excluded from the catalog. Firebase persistence is recorded as a feature requiring implementation and tests, with no screen reference assigned.

The viewer does not implement booking, payment, messaging, uploads, CSV generation, or other application features. Its connections only display existing designs. Original scripts included in the exports remain unchanged. Internet access is needed for their external fonts, Tailwind script, and images.
