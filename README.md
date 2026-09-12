# Lumen Keyboard

A full-featured Android keyboard (IME), aiming for real Gboard-level feature
parity, built natively — no cross-platform wrapper, no third-party keyboard
SDK, no paid services. Everything in it is free, including the bundled fonts
and every dependency, which matters since this is meant for a public release.

## Language: Kotlin + a little Java, on purpose

The app is primarily **Kotlin** (the modern, official language for native
Android — null-safety, far less boilerplate, and it's what the interactive
UI/state-machine code benefits most from). Two small, self-contained
**Java** utility classes are included as well:

- `LevenshteinUtil.java` — the edit-distance algorithm used by autocorrect
- `FileCopyUtils.java` — stream-to-file copying, used when importing a
  custom font or background image

Kotlin calls into both with zero glue code — that's normal Kotlin/Java
interop, not a special integration. They're kept as plain static utilities
(not stateful classes) since that's the lowest-risk place to mix languages
in one project.

## Features

**Typing**
- Custom-drawn keyboard (own `Canvas` `View`, not the deprecated `KeyboardView` API)
- Swipe (gesture) typing, matched against the offline dictionary
- Real autocorrect (not just suggestions), with backspace-to-undo right after
- Long-press accents & numbers (drag to pick, like Gboard)
- Smart Shift (tap = one capital, double-tap = Caps Lock), auto-repeat Backspace
- **Drag left on Backspace to delete whole words**; **drag on the space bar to move the cursor**
- **Long-press Space to open the system keyboard switcher** (works like a language/keyboard-switch key)
- Adaptive Enter key (Go / Search / Send / Next / Done)
- Optional permanent number row
- Word suggestion bar (offline)

**Input extras**
- Voice typing — tap the mic icon; uses Android's built-in `SpeechRecognizer`,
  no account, no extra app, no paid API
- Clipboard manager — auto-saved history, tap to paste, × to delete
- GIF search — real animated GIFs via Tenor's free tier, inserted with
  Android's `commitContent` rich-content API (same mechanism Gboard uses)
- Emoji panel

**Appearance**
- Light / Dark / AMOLED black / Follow-system themes, 6 accent-color presets
- **Custom fonts** — 5 bundled free (OFL-licensed) Google Fonts, or import
  any `.ttf` from your device
- **Custom background image** behind the keys
- Resizable keyboard height (80%–130%)
- Key sound (3 styles) + haptic feedback, independently toggleable

**Layouts**
- One-handed mode (anchors to left/right edge, quick on-keyboard button to restore)
- **Split keyboard** for tablets (two-thumb typing; letters split to both
  edges with a gap in the middle, common rows like space/enter stay full-width)
- Symbols & extended-symbols pages (`?123` / `=\<`)

## Two honest scope notes

**"Split", not "floating."** True floating/draggable IME windows are one of
the most version-fragile corners of the Android IME API. Split mode gives
real two-thumb tablet ergonomics without that fragility. A draggable
floating window is a reasonable future enhancement if you want to take it on.

**GIF search keeps your keys visible and redirects typing into the search
box**, instead of hiding the keyboard behind a search field. Since this app
*is* the system input method, it can't pop up a second keyboard to type into
its own search UI — so typing while GIF search is open updates the query
instead of the target app. Clipboard/Emoji panels don't need typing, so they
swap in for the keyboard entirely, like Gboard's panels do.

## Everything here is free (this matters for a public release)

| Piece | License / cost |
|---|---|
| Kotlin, AndroidX, Material Components | Apache 2.0, free |
| Glide (GIF image loading) | BSD/Apache 2.0, free |
| Bundled fonts (Poppins, Nunito, Lato, JetBrains Mono, Comfortaa) | SIL Open Font License 1.1 — free for any use, including commercial. License text ships in `assets/fonts/licenses/` as OFL requires. |
| Tenor GIF search | Free tier, no billing — you (or your users) just need a free API key |
| Android `SpeechRecognizer` (voice typing) | Built into Android, free |
| GitHub Actions build | Free for public repos |

No ads, no analytics, no paid SDKs, nothing gated behind a subscription.

## Project structure

```
LumenKeyboard/
├── app/src/main/java/com/lumen/keyboard/
│   ├── LumenInputMethodService.kt   # the IME itself — state machine & InputConnection calls
│   ├── KeyboardView.kt              # custom View: drawing, touch, gestures, split/one-handed layout
│   ├── KeyboardTheme.kt             # theme colors per mode
│   ├── PreferencesManager.kt        # typed SharedPreferences wrapper
│   ├── FontManager.kt               # bundled + custom font resolution
│   ├── BackgroundImageManager.kt    # custom background image import/storage/decoding
│   ├── SoundFeedback.kt             # click sound + vibration
│   ├── SuggestionBar.kt             # suggestion strip + clipboard/mic/GIF toolbar icons
│   ├── WordDictionary.kt            # offline word list
│   ├── AutoCorrector.kt             # autocorrect (uses LevenshteinUtil.java)
│   ├── GestureTypingEngine.kt       # swipe-typing word matching
│   ├── ClipboardHistoryManager.kt   # persisted clipboard history
│   ├── ClipboardPanelView.kt        # clipboard panel UI
│   ├── TenorApiClient.kt           # Tenor GIF search
│   ├── GifResultsStrip.kt          # GIF results strip UI
│   ├── VoiceBridge.kt / VoicePermissionActivity.kt / VoiceInputStrip.kt  # voice typing
│   ├── EmojiPanelView.kt            # emoji grid
│   ├── LevenshteinUtil.java         # (Java) edit-distance utility
│   ├── FileCopyUtils.java           # (Java) stream-copy utility
│   ├── MainActivity.kt              # "enable keyboard" onboarding screen
│   ├── SettingsActivity.kt          # preferences screen, incl. font/background pickers
│   └── model/
│       ├── Key.kt                   # KeyDef / KeyRow / KeyboardLayout data classes
│       └── Layouts.kt               # QWERTY (+ number row variant) + two symbol pages
├── app/src/main/assets/fonts/       # bundled OFL fonts + their license files
├── app/src/main/res/                # strings, colors, themes, XML layouts, adaptive icon
├── .github/workflows/android-build.yml  # CI: builds a debug APK on every push
└── build.gradle.kts / settings.gradle.kts / app/build.gradle.kts
```

## Build it

### Option A — GitHub Actions (recommended, matches how you said you build)
1. Push this whole folder to a new GitHub repo.
2. The included workflow (`.github/workflows/android-build.yml`) runs automatically
   on every push to `main` — it sets up JDK 17 + the Android SDK, generates the
   Gradle wrapper, and runs `./gradlew assembleDebug`.
3. Open the **Actions** tab → the latest run → download the `lumen-keyboard-debug-apk`
   artifact. That's your installable APK.
4. To build a signed **release** APK instead, add a signing config to
   `app/build.gradle.kts` and an `assembleRelease` step (kept to `assembleDebug`
   by default so it builds successfully with zero secrets configured).

### Option B — Android Studio
1. Open the `LumenKeyboard` folder as a project (Android Studio will generate
   the Gradle wrapper for you automatically).
2. Run on a device/emulator, or **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

Minimum supported Android version: **8.0 (API 26)**. Target/compile SDK: **34**.

### GIF search setup (optional)

GIF search needs a free Tenor API key (no billing, no OAuth):
1. Get a key at <https://tenor.com/gifapi> (Google account required).
2. In the app, open **Customize Appearance → GIFs → Tenor API key** and paste it in.

Leave it blank and GIF search just shows a short "add a key" message instead
of erroring — nothing else in the keyboard depends on it.

### Voice typing setup

Nothing to configure — it uses Android's built-in speech recognizer. The
first time someone taps the mic icon, Android will ask for microphone
permission (handled by a small invisible `VoicePermissionActivity`, since a
keyboard service can't request runtime permissions itself).

## Installing & enabling it on a phone

1. Install the APK.
2. Open the app → **"Enable in System Settings"** → turn on *Lumen Keyboard*.
3. Tap **"Switch to Lumen Keyboard"** (or long-press the space bar) to make
   it your active keyboard.
4. **"Customize Appearance"** opens the in-app settings — the same screen is
   also reachable from the system keyboard picker.

## Publishing this publicly — a few pointers

Since you mentioned this is for the public:
- **Privacy policy**: see `PRIVACY.md` — a plain-language starting point
  covering the mic, clipboard, and GIF-search network call. Play Store
  requires a privacy policy URL for apps requesting microphone access; you'll
  need to host this file's content somewhere public (a GitHub Pages link
  to this file works fine) and link it in your Play Console listing.
- **App name/branding**: see "Renaming / rebranding" below before you publish.
- **Signing**: the CI workflow only builds a debug APK. For a Play Store
  release you'll need a release keystore and signing config — happy to add
  that (with the keystore itself kept out of the repo, as secrets) if/when
  you're ready for that step.

## Renaming / rebranding

"Lumen" was picked for the "clean, glowing, professional" feel — but it's a
placeholder if you want something else. Two quick alternatives if you'd like
options: **Nimbus Keyboard** (soft, cloud-like) or **Cadence Keyboard**
(emphasizing typing rhythm/flow). To rename:

1. Find-and-replace `Lumen Keyboard` → your name in `strings.xml`.
2. Find-and-replace the package `com.lumen.keyboard` → your own applicationId
   (rename the `com/lumen/keyboard` folder to match, and update `namespace`/
   `applicationId` in `app/build.gradle.kts`).
3. Swap the accent color in `colors.xml` / `PreferencesManager.DEFAULT_ACCENT_HEX`.

## Extending it

- **New layout/language**: add a `KeyboardLayout` in `model/Layouts.kt`, then
  wire a switch-key to call `keyboardView.setKeyboardLayout(...)`.
- **Better dictionary/next-word prediction**: `WordDictionary`, `AutoCorrector`,
  and `GestureTypingEngine` all read from the same offline word list — swap
  in a frequency-ranked list (or an on-device ML model) and all three improve together.
- **Smarter swipe typing**: `GestureTypingEngine` currently does ordered-subsequence
  matching, not a weighted probability/geometry model — a good next step if
  you want SwiftKey-level accuracy.
- **More themes**: add a case to `Themes.forMode(...)` in `KeyboardTheme.kt`.
- **More fonts**: drop another `.ttf` (with its license) into `assets/fonts/`
  and add a line to `FontManager.builtIns`.
- **More GIF providers**: `TenorApiClient` is a thin, isolated wrapper —
  swap in GIPHY or another provider without touching the rest of the panel.
- **True floating keyboard window**: would replace the "split" tablet mode
  with a draggable/resizable `InputMethodService` window.

## License

MIT for this project's own code. Bundled fonts are SIL Open Font License 1.1
(see `assets/fonts/licenses/`) — free for any use, but keep their license
files if you redistribute them.
