# Instructions for Claude

- Never comment code.
  - Exception: every Kotlin file must have this block, placed above any annotations on the first top-level class/enum/interface/object (after the package/import block, before the first declaration):
    ```
    /**
     * Author: Adam York
     * Copyright (c) Adam York
     */
    ```
    One block per file, even if the file has multiple top-level declarations.
- To verify UI/rendering behavior, add logging (e.g. `logger.debug`/`logger.info`) and ask the user to run the app and share the output. Do not launch headless browsers (Playwright, Puppeteer, etc.) or otherwise scrape/drive the app yourself.
- Do not create Kotlin extension functions.
- Whenever creating variables or function arguments, always use fully expanded names. Do not abbreviate.
- Don't put blank lines inside function or method bodies.
- Declaration order inside every class, top to bottom:
  1. Constants, in a companion object.
  2. Override vals (and vars).
  3. Public vals (and vars).
  4. Private vals (and vars).
  5. Override public functions.
  6. Override protected functions.
  7. Private functions.
  - Cases the list does not cover: `init` blocks go after the properties they use; non-override public functions go after the override public functions; non-override protected and abstract functions go with the protected functions; nested classes go right after the companion object.
