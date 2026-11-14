# Agent Instructions

This document defines the architectural standards, conventions, and engineering constraints for developing, maintaining, and debugging extensions within the `HikariCollection/extensions-source` repository. All contributors and automated agents must adhere strictly to these guidelines.

---

## 1. Architecture Overview

This repository uses modern Android Gradle architecture with Kotlin 2.x and compileSdk 37:

- **Core Module (`:core`)**: Provides the foundation interfaces, source contracts (`KeiSource`, `ConfigurableSource`), core models (`SManga`, `SChapter`, `Page`, `FilterList`, `MangasPage`), network helpers (`GET`, `POST`, `asJsoup`, `parseAs`, `rateLimit`), and URL builders.
- **Multisrc Themes (`:lib-multisrc:<theme>`)**: Reusable source templates (e.g. `madara`, `mangathemesia`, `mangataro`, `natsuid`) containing shared HTML/JSON scrapers and network abstractions.
- **Individual Extensions (`:src:<lang>:<slug>`)**: Standalone or theme-based source packages compiled into independent Android extension APKs.
- **Build-Logic (`gradle/build-logic`)**: Internal Gradle plugins (`HikariExtensionPlugin`, `HikariThemePlugin`) managing automated Android application packaging, source info JSON metadata generation (`hikari-source-info.json`), signing, and APK naming.

---

## 2. Directory & Package Standards

Every extension lives under `src/<lang>/<slug>/` following this exact structure:

```
src/<lang>/<slug>/
├── build.gradle.kts
└── src/
    └── main/
        ├── kotlin/app/hikari/extension/<lang>/<slug>/
        │   ├── <SourceName>.kt
        │   ├── Filters.kt
        │   └── Dto.kt (optional, for JSON/REST sources)
        └── res/
            ├── mipmap-mdpi/ic_launcher.png    (48x48)
            ├── mipmap-hdpi/ic_launcher.png    (72x72)
            ├── mipmap-xhdpi/ic_launcher.png   (96x96)
            ├── mipmap-xxhdpi/ic_launcher.png  (144x144)
            └── mipmap-xxxhdpi/ic_launcher.png (192x192)
```

### Naming Conventions
- **Package Name**: Automatically assigned by `HikariExtensionPlugin` as `app.hikari.extension.<lang>.<slug>`. Kotlin files must declare `package app.hikari.extension.<lang>.<slug>`.
- **Language Codes**: Use standard two-letter ISO 639-1 language codes (`en`, `id`, `es`, `fr`, `ja`, etc.), or `all` for multi-language catalogs.
- **Slug**: Short, lowercase, alphanumeric, without hyphens or spaces (e.g., `komikindo`, `bacakomik`, `vortexscans`).

---

## 3. Gradle Build DSL (`build.gradle.kts`)

All extensions apply `alias(libs.plugins.hikari.extension)` and configure the `hikari` extension block:

```kotlin
plugins {
    alias(libs.plugins.hikari.extension)
}

hikari {
    name.set("Source Name")
    versionCode.set(1)
    libVersion.set("1.1")
    contentWarning.set(ContentWarning.SAFE) // SAFE, MIXED, or NSFW

    source {
        lang.set("en")
        baseUrl = "https://example.com"
        versionId.set(1)
    }
}
```

### Build DSL Rules
- **ContentWarning**: Must be specified via `ContentWarning.SAFE`, `ContentWarning.MIXED`, or `ContentWarning.NSFW`.
- **Version Code**: Increment `versionCode` whenever the source is modified or updated.
- **Lib Version**: Matches the core extension library baseline (currently `"1.1"`).
- **Custom Package Suffix (`pkgName`)**: Optional suffix override (e.g., `pkgName.set("en.custom")`, without `app.hikari.extension.`) to set `applicationId` and `packageName` independently of the module directory.
- **Mirror URLs**: Optional fallback domains inside `source { ... }` via `mirrorUrls.set(listOf(...))` or `mirrorUrl("https://mirror.example.com")`.
- **Multi-Source / Multi-Lang**: Define multiple `source { ... }` blocks inside `hikari { ... }` rather than writing custom factories.
- **Theme-Based Extensions**: If an extension extends a theme from `lib-multisrc`, declare `theme.set("<theme-name>")` in the `hikari { ... }` block.

---

## 4. Source Implementation Rules (`KeiSource`)

Every source class must inherit from `app.hikari.extension.core.KeiSource`:

### Method Contracts
1. **Catalog & Search**:
   - `getPopularManga(page: Int): MangasPage`
   - `getLatestUpdates(page: Int): MangasPage`
   - `getSearchManga(page: Int, query: String, filters: FilterList): MangasPage`
2. **Metadata & Chapters**:
   - `getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate`
   - Use `coroutineScope { ... }` and `async { ... }` to fetch details and chapters concurrently when both flags are true.
3. **Deep Linking**:
   - `getMangaByUrl(url: HttpUrl): SManga?`
   - Validate `url.host == baseUrl.toHttpUrl().host` and path structure. Return `null` if the URL belongs to another source or does not represent a valid manga page.
4. **Reader Pages**:
   - `getPageList(chapter: SChapter): List<Page>`
   - Return clean, direct image URLs. Filter out advertisement placeholders, promotional banners, and loading spinners.
5. **Rate Limiting**:
   - Always configure rate limiting on the OkHttpClient builder:
     ```kotlin
     override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)
     ```

### Date Parsing Standards
- **Relative Dates**: When dates contain relative time strings (e.g. `"2 hours ago"`, `"1 hari yang lalu"`), compute epoch timestamps using `Calendar.getInstance()` offsets.
- **Absolute Dates**: Use thread-safe `SimpleDateFormat` or `DateTimeFormatter` with explicitly defined `Locale` and appropriate `TimeZone` (e.g., `Locale.US`, `TimeZone.getTimeZone("Asia/Jakarta")`).

### DTO & Serialization Guidelines
- **No Data Classes for DTOs**: Do not use `data class` for `@Serializable` JSON/Protobuf DTO models; regular `class` reduces Android bytecode size.
- **Field Visibility**: Make DTO fields `private` where feasible unless accessed externally.
- **Serial Names**: Only specify `@SerialName` when the remote JSON key cannot match standard Kotlin camelCase.
- **Shared Parsers**: Use `response.parseAs<T>()` from `:core` instead of creating redundant `Json` instances.

---

## 5. Launcher Icon Requirements

Every extension must include standard launcher PNG icons across all 5 mipmap densities:
- `res/mipmap-mdpi/ic_launcher.png`: 48 x 48 px
- `res/mipmap-hdpi/ic_launcher.png`: 72 x 72 px
- `res/mipmap-xhdpi/ic_launcher.png`: 96 x 96 px
- `res/mipmap-xxhdpi/ic_launcher.png`: 144 x 144 px
- `res/mipmap-xxxhdpi/ic_launcher.png`: 192 x 192 px

Icons must be square, cleanly scaled with bicubic interpolation, and placed directly in the module resource directories.

---

## 6. Quality Gates & Verification

Never commit, push, or mark an extension task complete without executing and passing all four verification gates in order:

### Gate 1: Package Conflict Audit
Ensure no duplicate Android application package IDs exist across modules:
```powershell
python .github/scripts/audit-packages.py
```

### Gate 2: Build Matrix Generation
Verify that the extension is correctly discovered and chunked for CI:
```powershell
cmd /c "set FULL_REBUILD=true&& python .github/scripts/generate-build-matrices.py HEAD"
```

### Gate 3: Individual Module Release Build
Confirm clean compilation, R-file generation, dexing, and source info JSON emission:
```powershell
.\gradlew.bat :src:<lang>:<slug>:assembleRelease
```

### Gate 4: Full Repository Build
Ensure zero regressions across all repository extensions and shared modules:
```powershell
.\gradlew.bat assembleRelease
```

---

## 7. Git & Commit Standards

- **Unified Commits**: Commit all active workspace changes into a single unified commit. Never split interrelated source files, icons, and configurations into separate commits.
- **Single-Line Imperative Header**: The commit subject must be a single imperative command line (Chris Beams standard) answering *"If applied, this commit will..."* (e.g. `Add Komiku, BacaKomik, and Vortex Scans extension sources`).
- **No Commit Body**: Strictly forbid commit bodies, bullet points, or multi-line messages.
- **No Conventional Prefixes**: Strictly forbid conventional commit tags (`feat:`, `fix:`, `chore:`, `refactor:`, etc.).
- **Zero Emojis**: Strictly forbid emojis in commit messages, filenames, and source code.
- **Explicit Instruction Only**: Never execute `git commit` or `git add` without explicit user instruction.
