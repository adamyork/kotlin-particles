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
- Don't use Kotlin extension functions.
- Whenever creating variables or function arguments, always use fully expanded names. Do not abbreviate.
- Don't put blank lines inside function or method bodies.
