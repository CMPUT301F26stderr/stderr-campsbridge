# AI-use citations

Keep the disclosure short and accurate. For this project, AI assistance includes application implementation, tests, debugging, UI work, documentation, and build/CI configuration. The README should name the tools, describe that scope and its extent, and link to the detailed citations.

The [course outline](https://ualberta-cmput301.github.io/general/outline.html) requires the company, AI tool, subject and date, plus a README statement estimating how extensively AI was used. Add the recorded model/version when available. Don't invent a model, prompt, date or review outcome.

Store relevant conversation turns under `citations/<member>/`. Keep the prompt and its replies together, with their dates and recorded models. These exports are selected project conversations, not complete session archives. Remove credentials, unrelated personal chats and private material. Keep enough information to disclose substantive AI assistance and preserve contribution credit.

Omit complete turns about Wiki edits, UML diagrams and CRC cards, including the prompt, replies and delegated reviews, from both repository citations and the combined Wiki page. Regenerate the Wiki page from the filtered files; keep the overall AI-use disclosure accurate.

For an identifiable output, link the actual project file using a full commit hash:

```text
https://github.com/CMPUT301F26stderr/stderr-campsbridge/blob/<40-character-commit>/<path>
```

Match the repository and full path. A matching filename alone isn't enough. Use a public historical version if the file moved or was deleted. Leave uncertain matches unlinked rather than citing a different file.

For AI-derived code, a nearby comment can follow this template:

```kotlin
// AI assistance: <company>, <tool/model>, "<subject>", <YYYY-MM-DD>.
```

Use the [UAlberta Library guide](https://guides.library.ualberta.ca/generative-ai/citation) when a formal reference is needed. Before publishing, read the disclosure and check that the links identify the work described.
